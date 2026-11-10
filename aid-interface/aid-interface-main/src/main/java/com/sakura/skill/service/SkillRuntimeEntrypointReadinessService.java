package com.sakura.skill.service;

import com.sakura.common.exception.ServiceException;
import com.sakura.skill.domain.SakuraSkill;
import com.sakura.skill.domain.SakuraSkillRelation;
import com.sakura.skill.domain.SakuraSkillVersion;
import com.sakura.skill.mapper.SakuraSkillMapper;
import com.sakura.skill.mapper.SakuraSkillRelationMapper;
import com.sakura.skill.mapper.SakuraSkillVersionMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 校验当前 Runtime 用户入口及其固定子版本是否完整可执行。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SkillRuntimeEntrypointReadinessService {
    private static final String NORMAL = "0";
    private static final String ENTRYPOINT = SkillRuntimeCapabilities.SCOPE_ENTRYPOINT;
    private static final String INTERNAL = "INTERNAL";
    private static final String PUBLIC = SkillRuntimeCapabilities.VISIBILITY_PUBLIC;
    private static final String PRIVATE = "PRIVATE";
    private static final String PLATFORM = SkillRuntimeCapabilities.OWNER_PLATFORM;
    private static final String CHILD = "CHILD";
    private static final String ORCHESTRATOR = "ORCHESTRATOR";
    private static final String PROMPT = "PROMPT";
    private static final Map<String, Set<String>> REQUIRED_CHILDREN = Map.of(
            SkillRuntimeCapabilities.SCREENPLAY, Set.of("screenplay-write", "screenplay-review"));

    private final SakuraSkillMapper skillMapper;
    private final SakuraSkillVersionMapper versionMapper;
    private final SakuraSkillRelationMapper relationMapper;
    private final SkillModelService skillModelService;
    private final SkillPackageResourceLoader packageResourceLoader;

    /** 返回批量校验通过的入口；任一固定包损坏时按安全原则不暴露入口。 */
    public Map<Long, ReadyEntrypoint> findReady(List<SakuraSkill> identities) {
        try {
            return resolve(identities);
        } catch (RuntimeException error) {
            log.error("Skill Runtime入口就绪校验失败, candidateCount={}, errorType={}",
                    identities == null ? 0 : identities.size(), error.getClass().getSimpleName(), error);
            return Map.of();
        }
    }

    /** 校验单个入口并返回固定的根版本和子版本快照。 */
    public ReadyEntrypoint requireReady(SakuraSkill identity) {
        Map<Long, ReadyEntrypoint> ready = resolve(List.of(identity));
        ReadyEntrypoint result = ready.get(identity.getId());
        if (result == null) {
            log.warn("Skill Runtime入口未就绪, skillId={}, skillCode={}", identity.getId(), identity.getSkillCode());
            throw new ServiceException("Skill入口不可用");
        }
        return result;
    }

    private Map<Long, ReadyEntrypoint> resolve(List<SakuraSkill> identities) {
        if (identities == null || identities.isEmpty()) {
            return Map.of();
        }
        List<SakuraSkill> candidates = identities.stream().filter(this::validRootIdentity).toList();
        if (candidates.isEmpty()) {
            return Map.of();
        }
        Set<Long> rootVersionIds = candidates.stream().map(SakuraSkill::getCurrentVersionId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, SakuraSkillVersion> rootVersions = versionMapper.selectList(
                        Wrappers.<SakuraSkillVersion>lambdaQuery().in(SakuraSkillVersion::getId, rootVersionIds))
                .stream().collect(Collectors.toMap(SakuraSkillVersion::getId, Function.identity()));
        Set<Long> validRootVersionIds = candidates.stream()
                .filter(identity -> validRootVersion(identity, rootVersions.get(identity.getCurrentVersionId())))
                .map(SakuraSkill::getCurrentVersionId).collect(Collectors.toSet());
        if (validRootVersionIds.isEmpty()) {
            return Map.of();
        }

        List<SakuraSkillRelation> relations = relationMapper.selectList(Wrappers.<SakuraSkillRelation>lambdaQuery()
                .in(SakuraSkillRelation::getParentVersionId, validRootVersionIds)
                .eq(SakuraSkillRelation::getRelationType, CHILD)
                .eq(SakuraSkillRelation::getDelFlag, NORMAL)
                .orderByAsc(SakuraSkillRelation::getParentVersionId)
                .orderByAsc(SakuraSkillRelation::getId));
        Map<Long, List<SakuraSkillRelation>> relationsByRoot = relations.stream()
                .collect(Collectors.groupingBy(SakuraSkillRelation::getParentVersionId));
        Set<Long> childSkillIds = relations.stream().map(SakuraSkillRelation::getChildSkillId)
                .collect(Collectors.toSet());
        Set<Long> childVersionIds = relations.stream().map(SakuraSkillRelation::getChildVersionId)
                .collect(Collectors.toSet());
        Map<Long, SakuraSkill> children = childSkillIds.isEmpty() ? Map.of()
                : skillMapper.selectList(Wrappers.<SakuraSkill>lambdaQuery()
                        .select(SakuraSkill::getId, SakuraSkill::getSkillCode, SakuraSkill::getOwnerType,
                                SakuraSkill::getVisibility, SakuraSkill::getInvocationScope, SakuraSkill::getStatus,
                                SakuraSkill::getDelFlag)
                        .in(SakuraSkill::getId, childSkillIds)).stream()
                .collect(Collectors.toMap(SakuraSkill::getId, Function.identity()));
        Map<Long, SakuraSkillVersion> childVersions = childVersionIds.isEmpty() ? Map.of()
                : versionMapper.selectList(Wrappers.<SakuraSkillVersion>lambdaQuery()
                        .in(SakuraSkillVersion::getId, childVersionIds)).stream()
                .collect(Collectors.toMap(SakuraSkillVersion::getId, Function.identity()));

        Set<String> modelCodes = rootVersions.values().stream()
                .flatMap(version -> SkillModelConfiguration.from(version).selectableModelCodes().stream())
                .collect(Collectors.toSet());
        Set<String> availableModels = skillModelService.activeModels(modelCodes).keySet();

        Map<Long, ReadyEntrypoint> result = new LinkedHashMap<>();
        for (SakuraSkill identity : candidates) {
            SakuraSkillVersion rootVersion = rootVersions.get(identity.getCurrentVersionId());
            SkillModelConfiguration modelConfig = SkillModelConfiguration.from(rootVersion);
            if (!validRootVersion(identity, rootVersion)
                    || modelConfig.selectableModelCodes().stream().noneMatch(availableModels::contains)) {
                continue;
            }
            Map<String, ReadyChild> readyChildren = resolveRequiredChildren(identity.getSkillCode(),
                    relationsByRoot.getOrDefault(rootVersion.getId(), List.of()), children, childVersions);
            if (readyChildren == null) {
                continue;
            }
            Map<String, SakuraSkillVersion> packages = new LinkedHashMap<>();
            packages.put(identity.getSkillCode(), rootVersion);
            readyChildren.forEach((code, child) -> packages.put(code, child.version()));
            try {
                packageResourceLoader.verifyVersionsCached(packages);
                result.put(identity.getId(), new ReadyEntrypoint(rootVersion, Map.copyOf(readyChildren)));
            } catch (RuntimeException error) {
                log.error("Skill Runtime入口版本包不可用, skillId={}, skillCode={}, errorType={}",
                        identity.getId(), identity.getSkillCode(), error.getClass().getSimpleName(), error);
            }
        }
        return Map.copyOf(result);
    }

    private Map<String, ReadyChild> resolveRequiredChildren(String rootCode, List<SakuraSkillRelation> relations,
                                                             Map<Long, SakuraSkill> children,
                                                             Map<Long, SakuraSkillVersion> childVersions) {
        Set<String> requiredCodes = REQUIRED_CHILDREN.get(rootCode);
        if (requiredCodes == null) {
            return null;
        }
        Map<String, SakuraSkillRelation> byKey = relations.stream()
                .filter(relation -> requiredCodes.contains(relation.getRelationKey()))
                .collect(Collectors.toMap(SakuraSkillRelation::getRelationKey, Function.identity(), (left, right) -> left));
        Map<String, ReadyChild> result = new LinkedHashMap<>();
        for (String childCode : requiredCodes) {
            SakuraSkillRelation relation = byKey.get(childCode);
            SakuraSkill child = relation == null ? null : children.get(relation.getChildSkillId());
            SakuraSkillVersion version = relation == null ? null : childVersions.get(relation.getChildVersionId());
            if (relation == null || !Boolean.TRUE.equals(relation.getRequiredFlag())
                    || !validChild(childCode, child, version)) {
                return null;
            }
            result.put(childCode, new ReadyChild(child, version));
        }
        return result;
    }

    private boolean validRootIdentity(SakuraSkill identity) {
        return identity != null && identity.getId() != null
                && SkillRuntimeCapabilities.supports(identity.getSkillCode())
                && PLATFORM.equals(identity.getOwnerType())
                && PUBLIC.equals(identity.getVisibility())
                && ENTRYPOINT.equals(identity.getInvocationScope())
                && NORMAL.equals(identity.getStatus()) && NORMAL.equals(identity.getDelFlag())
                && identity.getCurrentVersionId() != null;
    }

    private boolean validRootVersion(SakuraSkill identity, SakuraSkillVersion version) {
        return version != null && Objects.equals(version.getSkillId(), identity.getId())
                && PUBLIC.equals(version.getVisibility()) && ENTRYPOINT.equals(version.getInvocationScope())
                && ORCHESTRATOR.equals(version.getExecutorType()) && validDigest(version.getPackageDigest())
                && SkillModelConfiguration.from(version).isValid()
                && NORMAL.equals(version.getStatus()) && NORMAL.equals(version.getDelFlag());
    }

    private boolean validChild(String childCode, SakuraSkill child, SakuraSkillVersion version) {
        return child != null && version != null && childCode.equals(child.getSkillCode())
                && PLATFORM.equals(child.getOwnerType()) && PRIVATE.equals(child.getVisibility())
                && INTERNAL.equals(child.getInvocationScope())
                && NORMAL.equals(child.getStatus()) && NORMAL.equals(child.getDelFlag())
                && Objects.equals(version.getSkillId(), child.getId())
                && PRIVATE.equals(version.getVisibility()) && INTERNAL.equals(version.getInvocationScope())
                && PROMPT.equals(version.getExecutorType())
                && validDigest(version.getPackageDigest())
                && NORMAL.equals(version.getStatus()) && NORMAL.equals(version.getDelFlag());
    }

    private boolean validDigest(String digest) {
        return digest != null && digest.matches("[0-9a-f]{64}");
    }

    public record ReadyEntrypoint(SakuraSkillVersion rootVersion, Map<String, ReadyChild> children) { }

    public record ReadyChild(SakuraSkill identity, SakuraSkillVersion version) { }
}
