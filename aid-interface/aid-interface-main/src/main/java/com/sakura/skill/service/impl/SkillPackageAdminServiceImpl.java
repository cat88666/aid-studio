package com.sakura.skill.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import com.sakura.common.exception.ServiceException;
import com.sakura.skill.domain.SakuraSkill;
import com.sakura.skill.domain.SakuraSkillDraft;
import com.sakura.skill.domain.SakuraSkillRelation;
import com.sakura.skill.domain.SakuraSkillResource;
import com.sakura.skill.domain.SakuraSkillVersion;
import com.sakura.skill.dto.SkillPackageAdminRequests;
import com.sakura.skill.mapper.SakuraSkillDraftMapper;
import com.sakura.skill.mapper.SakuraSkillMapper;
import com.sakura.skill.mapper.SakuraSkillRelationMapper;
import com.sakura.skill.mapper.SakuraSkillResourceMapper;
import com.sakura.skill.mapper.SakuraSkillVersionMapper;
import com.sakura.skill.service.ISkillPackageAdminService;
import com.sakura.skill.service.SkillPackageDigestCalculator;
import com.sakura.skill.service.SkillPackageResourceLoader;
import com.sakura.skill.service.SkillRuntimeCapabilities;
import com.sakura.skill.service.SkillModelConfiguration;
import com.sakura.skill.service.SkillModelService;
import com.sakura.skill.vo.SkillAdminVO;
import com.sakura.skill.vo.SkillPackageAdminVO;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Skill 管理端版本包服务；发布版本只增不改。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SkillPackageAdminServiceImpl implements ISkillPackageAdminService {
    private static final String NORMAL = "0";
    private static final String DELETED = "1";
    private static final String ENABLED = "0";
    private static final String EDITING = "EDITING";
    private static final String DISCARDED = "DISCARDED";
    private static final String PUBLISHED = "PUBLISHED";
    private static final long MAX_SINGLE_RESOURCE_BYTES = 100L * 1024L;
    private static final long MAX_RESOURCE_BYTES = 512L * 1024L;
    private static final Set<String> RESOURCE_TYPES = Set.of("REFERENCE", "INSTRUCTION");
    private static final Set<String> MIME_TYPES = Set.of("text/markdown", "text/plain");

    private final SakuraSkillMapper skillMapper;
    private final SakuraSkillDraftMapper draftMapper;
    private final SakuraSkillVersionMapper versionMapper;
    private final SakuraSkillResourceMapper resourceMapper;
    private final SakuraSkillRelationMapper relationMapper;
    private final SkillModelService skillModelService;
    private final SkillPackageResourceLoader packageResourceLoader;
    private final TransactionTemplate transactionTemplate;

    @Override
    public SkillPackageAdminVO.VersionPageResult listVersions(
            SkillPackageAdminRequests.VersionPageRequest request) {
        SakuraSkill skill = requireSkillLight(request.getSkillId());
        Page<SakuraSkillVersion> page = versionMapper.selectPage(
                new Page<>(request.getPageNum(), request.getPageSize()),
                Wrappers.<SakuraSkillVersion>lambdaQuery()
                        .select(SakuraSkillVersion::getId, SakuraSkillVersion::getSkillId,
                                SakuraSkillVersion::getVersionCode, SakuraSkillVersion::getPublishStatus,
                                SakuraSkillVersion::getPackageDigest, SakuraSkillVersion::getStatus,
                                SakuraSkillVersion::getCreateBy, SakuraSkillVersion::getCreateTime)
                        .eq(SakuraSkillVersion::getSkillId, request.getSkillId())
                        .eq(SakuraSkillVersion::getDelFlag, NORMAL)
                        .orderByDesc(SakuraSkillVersion::getId));
        List<SkillPackageAdminVO.VersionSummary> data = page.getRecords().stream()
                .map(version -> toSummary(version, skill.getCurrentVersionId())).toList();
        return new SkillPackageAdminVO.VersionPageResult(
                page.getTotal(), data, skill.getCurrentVersionId());
    }

    @Override
    public SkillPackageAdminVO.VersionDetail getVersion(Long versionId) {
        SakuraSkillVersion version = requireVersion(versionId);
        SakuraSkill skill = requireSkillLight(version.getSkillId());
        packageResourceLoader.verifyVersion(skill.getSkillCode(), version);
        return toVersionDetail(skill, version);
    }

    @Override
    public SkillAdminVO.PageResult<SkillPackageAdminVO.DependencySkillOption> listDependencyOptions(
            SkillPackageAdminRequests.DependencySkillPageRequest request) {
        requireSkillLight(request.getSkillId());
        Page<SakuraSkill> page = skillMapper.selectPage(new Page<>(request.getPageNum(), request.getPageSize()),
                Wrappers.<SakuraSkill>lambdaQuery()
                        .select(SakuraSkill::getId, SakuraSkill::getSkillCode, SakuraSkill::getName,
                                SakuraSkill::getCurrentVersionId)
                        .ne(SakuraSkill::getId, request.getSkillId())
                        .ne(SakuraSkill::getSkillCode, SkillRuntimeCapabilities.RETIRED_SCREENPLAY_CHAT)
                        .eq(SakuraSkill::getInvocationScope, "INTERNAL")
                        .eq(SakuraSkill::getStatus, ENABLED).eq(SakuraSkill::getDelFlag, NORMAL)
                        .and(StrUtil.isNotBlank(request.getKeyword()), query -> query
                                .like(SakuraSkill::getSkillCode, StrUtil.trim(request.getKeyword()))
                                .or().like(SakuraSkill::getName, StrUtil.trim(request.getKeyword())))
                        .orderByAsc(SakuraSkill::getSkillCode));
        List<SkillPackageAdminVO.DependencySkillOption> data = page.getRecords().stream().map(child -> {
            SkillPackageAdminVO.DependencySkillOption option = new SkillPackageAdminVO.DependencySkillOption();
            option.setSkillId(child.getId());
            option.setSkillCode(child.getSkillCode());
            option.setName(SkillRuntimeCapabilities.displayName(child.getSkillCode(), child.getName()));
            option.setCurrentVersionId(child.getCurrentVersionId());
            return option;
        }).toList();
        return new SkillAdminVO.PageResult<>(page.getTotal(), data);
    }

    @Override
    public SkillAdminVO.PageResult<SkillPackageAdminVO.DependencyVersionOption> listDependencyVersionOptions(
            SkillPackageAdminRequests.DependencyVersionPageRequest request) {
        requireSkillLight(request.getParentSkillId());
        SakuraSkill child = requireSkillLight(request.getChildSkillId());
        if (Objects.equals(request.getParentSkillId(), child.getId())
                || !"INTERNAL".equals(child.getInvocationScope())
                || !ENABLED.equals(child.getStatus())) {
            log.warn("Skill子依赖目标错误, parentSkillId={}, childSkillId={}",
                    request.getParentSkillId(), request.getChildSkillId());
            throw new ServiceException("子Skill不可用");
        }
        Page<SakuraSkillVersion> page = versionMapper.selectPage(
                new Page<>(request.getPageNum(), request.getPageSize()),
                Wrappers.<SakuraSkillVersion>lambdaQuery()
                        .select(SakuraSkillVersion::getId, SakuraSkillVersion::getVersionCode)
                        .eq(SakuraSkillVersion::getSkillId, child.getId())
                        .eq(SakuraSkillVersion::getInvocationScope, "INTERNAL")
                        .eq(SakuraSkillVersion::getStatus, ENABLED)
                        .eq(SakuraSkillVersion::getDelFlag, NORMAL)
                        .like(StrUtil.isNotBlank(request.getKeyword()), SakuraSkillVersion::getVersionCode,
                                StrUtil.trim(request.getKeyword()))
                        .orderByDesc(SakuraSkillVersion::getId));
        List<SkillPackageAdminVO.DependencyVersionOption> data = page.getRecords().stream().map(version -> {
            SkillPackageAdminVO.DependencyVersionOption option =
                    new SkillPackageAdminVO.DependencyVersionOption();
            option.setId(version.getId());
            option.setVersionCode(version.getVersionCode());
            option.setCurrent(Objects.equals(child.getCurrentVersionId(), version.getId()));
            return option;
        }).toList();
        return new SkillAdminVO.PageResult<>(page.getTotal(), data);
    }

    @Override
    public List<SkillPackageAdminVO.DependencyLabel> listDependencyLabels(
            SkillPackageAdminRequests.DependencyLabelRequest request) {
        requireSkillLight(request.getParentSkillId());
        Set<Long> requestedVersionIds = new LinkedHashSet<>(request.getVersionIds());
        if (requestedVersionIds.size() > 16) {
            log.warn("Skill已选子版本标签过多, parentSkillId={}, count={}",
                    request.getParentSkillId(), requestedVersionIds.size());
            throw new ServiceException("已选子版本不能超过16个");
        }
        Map<Long, SakuraSkillVersion> versions = versionMapper.selectList(
                        Wrappers.<SakuraSkillVersion>lambdaQuery()
                                .select(SakuraSkillVersion::getId, SakuraSkillVersion::getSkillId,
                                        SakuraSkillVersion::getVersionCode)
                                .in(SakuraSkillVersion::getId, requestedVersionIds)).stream()
                .collect(Collectors.toMap(SakuraSkillVersion::getId, Function.identity()));
        Set<Long> childSkillIds = versions.values().stream().map(SakuraSkillVersion::getSkillId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, SakuraSkill> children = childSkillIds.isEmpty() ? Map.of()
                : skillMapper.selectList(Wrappers.<SakuraSkill>lambdaQuery()
                        .select(SakuraSkill::getId, SakuraSkill::getSkillCode, SakuraSkill::getName,
                                SakuraSkill::getCurrentVersionId)
                        .in(SakuraSkill::getId, childSkillIds)
                        .ne(SakuraSkill::getSkillCode, SkillRuntimeCapabilities.RETIRED_SCREENPLAY_CHAT)
                        .eq(SakuraSkill::getDelFlag, NORMAL)).stream()
                .collect(Collectors.toMap(SakuraSkill::getId, Function.identity()));
        return requestedVersionIds.stream().map(versionId -> {
            SakuraSkillVersion version = versions.get(versionId);
            SakuraSkill child = version == null ? null : children.get(version.getSkillId());
            if (version == null || child == null) {
                return null;
            }
            SkillPackageAdminVO.DependencyLabel label = new SkillPackageAdminVO.DependencyLabel();
            label.setChildSkillId(child.getId());
            label.setChildSkillCode(child.getSkillCode());
            label.setChildSkillName(SkillRuntimeCapabilities.displayName(
                    child.getSkillCode(), child.getName()));
            label.setChildVersionId(version.getId());
            label.setChildVersionCode(version.getVersionCode());
            label.setCurrent(Objects.equals(child.getCurrentVersionId(), version.getId()));
            return label;
        }).filter(Objects::nonNull).toList();
    }

    @Override
    public SkillPackageAdminVO.DraftDetail getDraft(Long skillId, Long baseVersionId, Long operatorId) {
        try {
            SakuraSkill skill = requireSkill(skillId);
            SakuraSkillDraft draft = draftMapper.selectOne(Wrappers.<SakuraSkillDraft>lambdaQuery()
                    .eq(SakuraSkillDraft::getSkillId, skillId).eq(SakuraSkillDraft::getOwnerUserId, operatorId)
                    .eq(SakuraSkillDraft::getStatus, EDITING).orderByDesc(SakuraSkillDraft::getId).last("limit 1"));
            if (draft != null) {
                return toDraftDetail(skill, draft, parseDraft(draft));
            }
            if (baseVersionId == null && skill.getCurrentVersionId() == null) {
                return toDraftDetail(skill, null, seedIdentityDocument(skill));
            }
            SakuraSkillVersion base = baseVersionId == null
                    ? requireCurrentVersion(skill) : requireVersion(baseVersionId);
            if (!Objects.equals(base.getSkillId(), skill.getId())) {
                log.warn("Skill草稿基础版本错误, skillId={}, versionId={}", skillId, baseVersionId);
                throw new ServiceException("基础版本错误");
            }
            DraftDocument document = seedDocument(skill, base);
            return toDraftDetail(skill, null, document);
        } catch (ServiceException error) {
            throw error;
        } catch (RuntimeException error) {
            log.error("Skill草稿读取失败, skillId={}, baseVersionId={}, operatorId={}",
                    skillId, baseVersionId, operatorId, error);
            throw new ServiceException("Skill草稿读取失败");
        }
    }

    @Override
    public SkillPackageAdminVO.DraftDetail saveDraft(SkillPackageAdminRequests.DraftSaveRequest request,
                                                      Long operatorId, String operatorName) {
        SakuraSkill skill = requireSkill(request.getSkillId());
        if (!baseVersionValid(skill, request.getBaseVersionId())) {
            log.warn("Skill草稿基础版本错误, skillId={}, versionId={}",
                    skill.getId(), request.getBaseVersionId());
            throw new ServiceException("基础版本错误");
        }
        DraftDocument incoming = document(request, request.getBaseVersionId());
        normalizeModelConfiguration(incoming);
        String incomingJson = canonicalDraftJson(incoming);
        String incomingDigest = SecureUtil.sha256(incomingJson);
        String operator = operator(operatorId, operatorName);
        SakuraSkillDraft saved = transactionTemplate.execute(status -> {
            Date now = new Date();
            if (request.getDraftId() == null) {
                SakuraSkillDraft existing = draftMapper.selectOne(Wrappers.<SakuraSkillDraft>lambdaQuery()
                        .eq(SakuraSkillDraft::getSkillId, skill.getId())
                        .eq(SakuraSkillDraft::getOwnerUserId, operatorId)
                        .eq(SakuraSkillDraft::getStatus, EDITING).last("limit 1 for update"));
                if (existing != null) {
                    if (Objects.equals(existing.getDraftDigest(), incomingDigest)) {
                        return existing;
                    }
                    log.warn("Skill草稿重复创建, skillId={}, operatorId={}", skill.getId(), operatorId);
                    throw new ServiceException("草稿已存在");
                }
                SakuraSkillDraft created = new SakuraSkillDraft();
                created.setSkillId(skill.getId());
                created.setOwnerUserId(operatorId);
                created.setActiveKey(activeDraftKey(operatorId, skill.getId()));
                created.setDraftJson(incomingJson);
                created.setDraftDigest(incomingDigest);
                created.setStatus(EDITING);
                created.setCreateBy(operator);
                created.setCreateTime(now);
                created.setUpdateBy(operator);
                created.setUpdateTime(now);
                try {
                    if (draftMapper.insert(created) != 1) {
                        log.error("Skill草稿创建失败, skillId={}, operatorId={}", skill.getId(), operatorId);
                        throw new ServiceException("草稿保存失败");
                    }
                } catch (DuplicateKeyException error) {
                    SakuraSkillDraft winner = draftMapper.selectOne(Wrappers.<SakuraSkillDraft>lambdaQuery()
                            .eq(SakuraSkillDraft::getSkillId, skill.getId())
                            .eq(SakuraSkillDraft::getOwnerUserId, operatorId)
                            .eq(SakuraSkillDraft::getStatus, EDITING).last("limit 1 for update"));
                    if (winner != null && Objects.equals(winner.getDraftDigest(), incomingDigest)) {
                        return winner;
                    }
                    log.warn("Skill活动草稿冲突, skillId={}, operatorId={}",
                            skill.getId(), operatorId);
                    throw new ServiceException("草稿已存在");
                }
                return created;
            }
            SakuraSkillDraft current = draftMapper.selectOne(Wrappers.<SakuraSkillDraft>lambdaQuery()
                    .eq(SakuraSkillDraft::getId, request.getDraftId())
                    .eq(SakuraSkillDraft::getSkillId, skill.getId())
                    .eq(SakuraSkillDraft::getOwnerUserId, operatorId)
                    .eq(SakuraSkillDraft::getStatus, EDITING).last("limit 1 for update"));
            if (current == null) {
                log.warn("Skill草稿不存在, draftId={}, operatorId={}", request.getDraftId(), operatorId);
                throw new ServiceException("草稿不存在");
            }
            if (Objects.equals(current.getDraftDigest(), incomingDigest)) {
                return current;
            }
            if (!Objects.equals(current.getDraftDigest(), request.getDraftDigest())) {
                log.warn("Skill草稿并发冲突, draftId={}, operatorId={}", request.getDraftId(), operatorId);
                throw new ServiceException("草稿已变化");
            }
            current.setDraftJson(incomingJson);
            current.setDraftDigest(incomingDigest);
            current.setUpdateBy(operator);
            current.setUpdateTime(now);
            int changed = draftMapper.update(current, Wrappers.<SakuraSkillDraft>lambdaUpdate()
                    .eq(SakuraSkillDraft::getId, current.getId())
                    .eq(SakuraSkillDraft::getDraftDigest, request.getDraftDigest())
                    .eq(SakuraSkillDraft::getStatus, EDITING));
            if (changed != 1) {
                log.warn("Skill草稿更新冲突, draftId={}, operatorId={}", request.getDraftId(), operatorId);
                throw new ServiceException("草稿已变化");
            }
            return current;
        });
        return toDraftDetail(skill, saved, incoming);
    }

    @Override
    public SkillPackageAdminVO.ValidationResult validateDraft(
            SkillPackageAdminRequests.PackagePayload request) {
        return validatePackage(request, null);
    }

    @Override
    public void discard(SkillPackageAdminRequests.DraftDiscardRequest request,
                        Long operatorId, String operatorName) {
        transactionTemplate.executeWithoutResult(status -> {
            SakuraSkillDraft draft = draftMapper.selectOne(Wrappers.<SakuraSkillDraft>lambdaQuery()
                    .eq(SakuraSkillDraft::getId, request.getDraftId())
                    .eq(SakuraSkillDraft::getOwnerUserId, operatorId)
                    .last("limit 1 for update"));
            if (draft == null) {
                log.warn("Skill待放弃草稿不存在, draftId={}, operatorId={}", request.getDraftId(), operatorId);
                throw new ServiceException("草稿不存在");
            }
            if (DISCARDED.equals(draft.getStatus())) {
                try {
                    if (Objects.equals(JSON.parseObject(draft.getDraftJson())
                            .getString("sourceDraftDigest"), request.getDraftDigest())) {
                        return;
                    }
                } catch (RuntimeException error) {
                    log.error("Skill放弃回执损坏, draftId={}", draft.getId(), error);
                    throw new ServiceException("放弃回执损坏");
                }
                log.warn("Skill草稿放弃重试参数冲突, draftId={}", draft.getId());
                throw new ServiceException("放弃参数已变化");
            }
            if (!EDITING.equals(draft.getStatus())) {
                log.warn("Skill草稿状态不可放弃, draftId={}, status={}", draft.getId(), draft.getStatus());
                throw new ServiceException("草稿不可放弃");
            }
            if (!Objects.equals(draft.getDraftDigest(), request.getDraftDigest())) {
                log.warn("Skill草稿放弃冲突, draftId={}, operatorId={}", request.getDraftId(), operatorId);
                throw new ServiceException("草稿已变化");
            }
            Date now = new Date();
            String receiptJson = JSON.toJSONString(Map.of(
                    "discarded", true, "sourceDraftDigest", request.getDraftDigest()));
            SakuraSkillDraft update = new SakuraSkillDraft();
            update.setId(draft.getId());
            update.setDraftJson(receiptJson);
            update.setDraftDigest(SecureUtil.sha256(receiptJson));
            update.setStatus(DISCARDED);
            update.setUpdateBy(operator(operatorId, operatorName));
            update.setUpdateTime(now);
            int changed = draftMapper.update(update, Wrappers.<SakuraSkillDraft>lambdaUpdate()
                    .set(SakuraSkillDraft::getActiveKey, null)
                    .eq(SakuraSkillDraft::getId, draft.getId())
                    .eq(SakuraSkillDraft::getDraftDigest, request.getDraftDigest())
                    .eq(SakuraSkillDraft::getStatus, EDITING));
            if (changed != 1) {
                log.warn("Skill草稿放弃收口冲突, draftId={}", draft.getId());
                throw new ServiceException("草稿已变化");
            }
        });
    }

    @Override
    public SkillPackageAdminVO.VersionDetail publish(SkillPackageAdminRequests.DraftPublishRequest request,
                                                      Long operatorId, String operatorName) {
        PublishedCoordinate coordinate = transactionTemplate.execute(status -> {
            SakuraSkillDraft draft = draftMapper.selectOne(Wrappers.<SakuraSkillDraft>lambdaQuery()
                    .eq(SakuraSkillDraft::getId, request.getDraftId())
                    .eq(SakuraSkillDraft::getOwnerUserId, operatorId)
                    .last("limit 1 for update"));
            if (draft == null) {
                log.warn("Skill草稿不存在, draftId={}, operatorId={}", request.getDraftId(), operatorId);
                throw new ServiceException("草稿不存在");
            }
            if (PUBLISHED.equals(draft.getStatus())) {
                return publishedReceipt(draft, request);
            }
            if (!EDITING.equals(draft.getStatus())) {
                log.warn("Skill草稿状态不可发布, draftId={}, status={}", draft.getId(), draft.getStatus());
                throw new ServiceException("草稿不可发布");
            }
            if (!Objects.equals(draft.getDraftDigest(), request.getDraftDigest())) {
                log.warn("Skill草稿发布冲突, draftId={}, operatorId={}", request.getDraftId(), operatorId);
                throw new ServiceException("草稿已变化");
            }
            SakuraSkill skill = skillMapper.selectOne(Wrappers.<SakuraSkill>lambdaQuery()
                    .eq(SakuraSkill::getId, draft.getSkillId()).eq(SakuraSkill::getDelFlag, NORMAL)
                    .last("limit 1 for update"));
            if (skill == null) {
                log.warn("Skill发布目标不存在, draftId={}", draft.getId());
                throw new ServiceException("Skill不存在");
            }
            if (versionMapper.selectCount(Wrappers.<SakuraSkillVersion>lambdaQuery()
                    .eq(SakuraSkillVersion::getSkillId, skill.getId())
                    .eq(SakuraSkillVersion::getVersionCode, request.getVersionCode())) > 0) {
                log.warn("Skill版本号重复, skillId={}, version={}", skill.getId(), request.getVersionCode());
                throw new ServiceException("版本号已存在");
            }
            DraftDocument document = parseDraft(draft);
            SkillPackageAdminVO.ValidationResult validation = validatePackage(document, skill);
            if (!Boolean.TRUE.equals(validation.getValid())) {
                log.warn("Skill草稿校验失败, draftId={}, errors={}", draft.getId(), validation.getErrors().size());
                throw new ServiceException("草稿校验失败");
            }
            Date now = new Date();
            String operator = operator(operatorId, operatorName);
            SakuraSkillVersion version = buildVersion(skill, document, request.getVersionCode(), operator, now);
            List<SakuraSkillResource> resources = buildResources(skill, version, document, operator, now);
            List<SakuraSkillRelation> relations = buildRelations(document, operator, now);
            version.setManifestJson(buildManifest(skill, version, resources));
            version.setPackageDigest(SkillPackageDigestCalculator.calculate(
                    skill.getSkillCode(), version, resources, relations));
            try {
                if (versionMapper.insert(version) != 1) {
                    log.error("Skill版本插入失败, skillId={}, version={}", skill.getId(),
                            request.getVersionCode());
                    throw new ServiceException("版本发布失败");
                }
            } catch (DuplicateKeyException error) {
                log.warn("Skill版本号并发重复, skillId={}, version={}", skill.getId(),
                        request.getVersionCode());
                throw new ServiceException("版本号已存在");
            }
            for (SakuraSkillResource resource : resources) {
                resource.setSkillVersionId(version.getId());
                if (resourceMapper.insert(resource) != 1) {
                    log.error("Skill资源插入失败, versionId={}, resourceKey={}",
                            version.getId(), resource.getResourceKey());
                    throw new ServiceException("资源发布失败");
                }
            }
            for (SakuraSkillRelation relation : relations) {
                relation.setParentVersionId(version.getId());
                if (relationMapper.insert(relation) != 1) {
                    log.error("Skill关系插入失败, versionId={}, relationKey={}",
                            version.getId(), relation.getRelationKey());
                    throw new ServiceException("关系发布失败");
                }
            }
            SakuraSkillDraft draftUpdate = new SakuraSkillDraft();
            draftUpdate.setId(draft.getId());
            String receiptJson = JSON.toJSONString(Map.of(
                    "publishedVersionId", version.getId(),
                    "versionCode", version.getVersionCode(),
                    "packageDigest", version.getPackageDigest(),
                    "sourceDraftDigest", request.getDraftDigest()));
            draftUpdate.setDraftJson(receiptJson);
            draftUpdate.setDraftDigest(SecureUtil.sha256(receiptJson));
            draftUpdate.setStatus(PUBLISHED);
            draftUpdate.setActiveKey(null);
            draftUpdate.setUpdateBy(operator);
            draftUpdate.setUpdateTime(now);
            int changed = draftMapper.update(draftUpdate, Wrappers.<SakuraSkillDraft>lambdaUpdate()
                    .set(SakuraSkillDraft::getActiveKey, null)
                    .eq(SakuraSkillDraft::getId, draft.getId())
                    .eq(SakuraSkillDraft::getDraftDigest, request.getDraftDigest())
                    .eq(SakuraSkillDraft::getStatus, EDITING));
            if (changed != 1) {
                log.warn("Skill草稿发布收口冲突, draftId={}", draft.getId());
                throw new ServiceException("草稿已变化");
            }
            return new PublishedCoordinate(skill.getId(), version.getId());
        });
        return getVersion(coordinate.versionId());
    }

    private PublishedCoordinate publishedReceipt(SakuraSkillDraft draft,
                                                  SkillPackageAdminRequests.DraftPublishRequest request) {
        try {
            JSONObject receipt = JSON.parseObject(draft.getDraftJson());
            Long versionId = receipt.getLong("publishedVersionId");
            String versionCode = receipt.getString("versionCode");
            String sourceDigest = receipt.getString("sourceDraftDigest");
            if (versionId != null && Objects.equals(versionCode, request.getVersionCode())
                    && Objects.equals(sourceDigest, request.getDraftDigest())
                    && versionMapper.selectCount(Wrappers.<SakuraSkillVersion>lambdaQuery()
                    .eq(SakuraSkillVersion::getId, versionId)
                    .eq(SakuraSkillVersion::getSkillId, draft.getSkillId())
                    .eq(SakuraSkillVersion::getDelFlag, NORMAL)) == 1) {
                return new PublishedCoordinate(draft.getSkillId(), versionId);
            }
        } catch (RuntimeException error) {
            log.error("Skill发布回执损坏, draftId={}", draft.getId(), error);
            throw new ServiceException("发布回执损坏");
        }
        log.warn("Skill发布重试参数冲突, draftId={}", draft.getId());
        throw new ServiceException("发布参数已变化");
    }

    @Override
    public void activate(SkillPackageAdminRequests.VersionActivateRequest request,
                         Long operatorId, String operatorName) {
        transactionTemplate.executeWithoutResult(status -> {
            SakuraSkill skill = skillMapper.selectOne(Wrappers.<SakuraSkill>lambdaQuery()
                    .eq(SakuraSkill::getId, request.getSkillId()).eq(SakuraSkill::getDelFlag, NORMAL)
                    .last("limit 1 for update"));
            if (skill == null) {
                log.warn("Skill切换目标不存在, skillId={}", request.getSkillId());
                throw new ServiceException("Skill不存在");
            }
            SakuraSkillVersion target = versionMapper.selectOne(Wrappers.<SakuraSkillVersion>lambdaQuery()
                    .eq(SakuraSkillVersion::getId, request.getVersionId())
                    .eq(SakuraSkillVersion::getSkillId, request.getSkillId())
                    .eq(SakuraSkillVersion::getDelFlag, NORMAL).last("limit 1 for update"));
            if (target == null) {
                log.warn("Skill版本归属错误, skillId={}, versionId={}",
                        request.getSkillId(), request.getVersionId());
                throw new ServiceException("版本归属错误");
            }
            if (Objects.equals(skill.getCurrentVersionId(), target.getId())) {
                return;
            }
            if (!Objects.equals(skill.getCurrentVersionId(), request.getExpectedCurrentVersionId())) {
                log.warn("Skill版本切换前置冲突, skillId={}, expected={}, actual={}", skill.getId(),
                        request.getExpectedCurrentVersionId(), skill.getCurrentVersionId());
                throw new ServiceException("当前版本已变化");
            }
            if (!ENABLED.equals(target.getStatus())) {
                log.warn("Skill版本已停用, versionId={}", request.getVersionId());
                throw new ServiceException("版本不可用");
            }
            requireRelationsAvailable(skill, target);
            SakuraSkill update = new SakuraSkill();
            update.setId(skill.getId());
            update.setCurrentVersionId(target.getId());
            update.setExecutorType(target.getExecutorType());
            update.setModelCode(target.getModelCode());
            update.setSystemPrompt(target.getSystemPrompt());
            update.setInputSchemaJson(target.getInputSchemaJson());
            update.setOutputSchemaJson(target.getOutputSchemaJson());
            update.setDefinitionJson(target.getDefinitionJson());
            update.setMaxOutputTokens(target.getMaxOutputTokens());
            update.setContextWindowTokens(target.getContextWindowTokens());
            update.setSafetyMarginTokens(target.getSafetyMarginTokens());
            update.setConfigHash(SecureUtil.sha256(skill.getSkillCode() + "|"
                    + target.getVersionCode() + "|" + target.getPackageDigest()));
            update.setUpdateBy(operator(operatorId, operatorName));
            update.setUpdateTime(new Date());
            var updateCondition = Wrappers.<SakuraSkill>lambdaUpdate()
                    .eq(SakuraSkill::getId, skill.getId()).eq(SakuraSkill::getDelFlag, NORMAL);
            if (request.getExpectedCurrentVersionId() == null) {
                updateCondition.isNull(SakuraSkill::getCurrentVersionId);
            } else {
                updateCondition.eq(SakuraSkill::getCurrentVersionId, request.getExpectedCurrentVersionId());
            }
            int changed = skillMapper.update(update, updateCondition);
            if (changed != 1) {
                log.warn("Skill版本切换冲突, skillId={}, versionId={}", skill.getId(), target.getId());
                throw new ServiceException("当前版本已变化");
            }
        });
    }

    private SkillPackageAdminVO.ValidationResult validatePackage(
            SkillPackageAdminRequests.PackagePayload payload, SakuraSkill knownSkill) {
        SkillPackageAdminVO.ValidationResult result = new SkillPackageAdminVO.ValidationResult();
        SkillModelConfiguration modelConfig = normalizeModelConfiguration(payload);
        result.setDraftDigest(SecureUtil.sha256(canonicalDraftJson(
                document(payload, payload.getBaseVersionId()))));
        SakuraSkill skill = knownSkill;
        try {
            if (skill == null) {
                skill = requireSkill(payload.getSkillId());
            }
        } catch (ServiceException error) {
            issue(result.getErrors(), "skillId", "Skill不存在");
        }
        validateJsonObject(payload.getInputSchemaJson(), "inputSchemaJson", "输入结构错误", result);
        validateJsonObject(payload.getOutputSchemaJson(), "outputSchemaJson", "输出结构错误", result);
        validateJsonObject(StrUtil.blankToDefault(payload.getDefinitionJson(), "{}"),
                "definitionJson", "定义内容错误", result);
        validateStructuredOutputSetting(payload.getDefinitionJson(), result);
        if (payload.getContextWindowTokens() != null && payload.getMaxOutputTokens() != null
                && payload.getSafetyMarginTokens() != null
                && (long) payload.getMaxOutputTokens() + payload.getSafetyMarginTokens()
                >= payload.getContextWindowTokens()) {
            issue(result.getErrors(), "contextWindowTokens", "上下文预算不足");
        }
        if (!modelConfig.isValid()) {
            issue(result.getErrors(), "defaultModelCode", "默认模型必须属于可选模型");
        } else if (skillModelService.activeModels(modelConfig.selectableModelCodes()).size()
                != modelConfig.selectableModelCodes().size()) {
            issue(result.getErrors(), "selectableModelCodes", "存在已停用或不可用模型");
        }
        validateResources(payload.getResources(), result);
        if (skill != null) {
            if (!baseVersionValid(skill, payload.getBaseVersionId())) {
                issue(result.getErrors(), "baseVersionId", "基础版本错误");
            }
            validateRelations(skill, payload, result);
        }
        result.setValid(result.getErrors().isEmpty());
        return result;
    }

    private void validateResources(List<SkillPackageAdminRequests.ResourceItem> source,
                                   SkillPackageAdminVO.ValidationResult result) {
        List<SkillPackageAdminRequests.ResourceItem> resources = source == null ? List.of() : source;
        Set<String> keys = new HashSet<>();
        long totalBytes = 0;
        for (int index = 0; index < resources.size(); index++) {
            SkillPackageAdminRequests.ResourceItem resource = resources.get(index);
            String field = "resources[" + index + "]";
            if (resource == null) {
                issue(result.getErrors(), field, "资源内容错误");
                continue;
            }
            if (!keys.add(resource.getResourceKey())) {
                issue(result.getErrors(), field + ".resourceKey", "资源标识重复");
            }
            String resourceType = StrUtil.blankToDefault(resource.getResourceType(), "REFERENCE")
                    .toUpperCase();
            if (!RESOURCE_TYPES.contains(resourceType)) {
                issue(result.getErrors(), field + ".resourceType", "资源类型错误");
            }
            String mimeType = StrUtil.blankToDefault(resource.getMimeType(), "text/markdown").toLowerCase();
            if (!MIME_TYPES.contains(mimeType)) {
                issue(result.getErrors(), field + ".mimeType", "媒体类型错误");
            }
            long contentBytes = value(resource.getContent()).getBytes(StandardCharsets.UTF_8).length;
            if (contentBytes > MAX_SINGLE_RESOURCE_BYTES) {
                issue(result.getErrors(), field + ".content", "单个资源过大");
            }
            totalBytes += contentBytes;
            validateRouteJson(resource.getRouteJson(), field + ".routeJson", result);
        }
        if (totalBytes > MAX_RESOURCE_BYTES) {
            issue(result.getErrors(), "resources", "资源总量过大");
        }
        if (resources.isEmpty()) {
            issue(result.getWarnings(), "resources", "未配置参考资源");
        }
    }

    private void validateRouteJson(String text, String field, SkillPackageAdminVO.ValidationResult result) {
        try {
            JSONObject route = JSON.parseObject(StrUtil.blankToDefault(text, "{}"));
            if (route == null) {
                throw new IllegalArgumentException();
            }
            Object always = route.get("always");
            if (always != null && !(always instanceof Boolean)) {
                issue(result.getErrors(), field, "always须为布尔值");
            }
            validateStringArray(route.get("operations"), field, result);
            validateStringArray(route.get("keywords"), field, result);
        } catch (RuntimeException error) {
            issue(result.getErrors(), field, "路由规则错误");
        }
    }

    private void validateStringArray(Object value, String field, SkillPackageAdminVO.ValidationResult result) {
        if (value == null) {
            return;
        }
        if (!(value instanceof JSONArray array)
                || array.stream().anyMatch(item -> !(item instanceof String text)
                || StrUtil.isBlank(text) || text.length() > 100)) {
            issue(result.getErrors(), field, "路由数组错误");
        }
    }

    private void validateRelations(SakuraSkill skill, SkillPackageAdminRequests.PackagePayload payload,
                                   SkillPackageAdminVO.ValidationResult result) {
        List<SkillPackageAdminRequests.RelationItem> relations = payload.getRelations() == null
                ? List.of() : payload.getRelations();
        Set<Long> childSkillIds = relations.stream().filter(Objects::nonNull)
                .map(SkillPackageAdminRequests.RelationItem::getChildSkillId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> childVersionIds = relations.stream().filter(Objects::nonNull)
                .map(SkillPackageAdminRequests.RelationItem::getChildVersionId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, SakuraSkill> children = childSkillIds.isEmpty() ? Map.of()
                : skillMapper.selectList(Wrappers.<SakuraSkill>lambdaQuery()
                .select(SakuraSkill::getId, SakuraSkill::getSkillCode, SakuraSkill::getInvocationScope,
                        SakuraSkill::getStatus, SakuraSkill::getDelFlag)
                .in(SakuraSkill::getId, childSkillIds)).stream()
                .collect(Collectors.toMap(SakuraSkill::getId, Function.identity()));
        Map<Long, SakuraSkillVersion> childVersions = childVersionIds.isEmpty() ? Map.of()
                : versionMapper.selectList(Wrappers.<SakuraSkillVersion>lambdaQuery()
                .select(SakuraSkillVersion::getId, SakuraSkillVersion::getSkillId,
                        SakuraSkillVersion::getInvocationScope, SakuraSkillVersion::getStatus,
                        SakuraSkillVersion::getDelFlag)
                .in(SakuraSkillVersion::getId, childVersionIds)).stream()
                .collect(Collectors.toMap(SakuraSkillVersion::getId, Function.identity()));
        Set<String> keys = new HashSet<>();
        for (int index = 0; index < relations.size(); index++) {
            SkillPackageAdminRequests.RelationItem relation = relations.get(index);
            String field = "relations[" + index + "]";
            if (relation == null) {
                issue(result.getErrors(), field, "子Skill错误");
                continue;
            }
            if (!keys.add(relation.getRelationKey())) {
                issue(result.getErrors(), field + ".relationKey", "关系标识重复");
            }
            if (Objects.equals(relation.getChildSkillId(), skill.getId())) {
                issue(result.getErrors(), field, "禁止循环引用");
                continue;
            }
            SakuraSkill child = children.get(relation.getChildSkillId());
            SakuraSkillVersion childVersion = childVersions.get(relation.getChildVersionId());
            if (child == null || childVersion == null
                    || !Objects.equals(child.getId(), childVersion.getSkillId())
                    || !"INTERNAL".equals(child.getInvocationScope())
                    || !ENABLED.equals(child.getStatus()) || !NORMAL.equals(child.getDelFlag())
                    || !"INTERNAL".equals(childVersion.getInvocationScope())
                    || !ENABLED.equals(childVersion.getStatus())
                    || !NORMAL.equals(childVersion.getDelFlag())) {
                issue(result.getErrors(), field, "子版本不可用");
            } else if (!Objects.equals(relation.getRelationKey(), child.getSkillCode())) {
                issue(result.getErrors(), field + ".relationKey", "关系标识须等于子Skill编码");
            }
        }
        Set<String> requiredChildren = new HashSet<>(requiredChildren(payload.getDefinitionJson()));
        requiredChildren.addAll(platformRequiredChildren(skill));
        requiredChildren.forEach(required -> {
            boolean present = relations.stream().filter(Objects::nonNull)
                    .anyMatch(relation -> required.equals(relation.getRelationKey())
                            && Boolean.TRUE.equals(relation.getRequiredFlag()));
            if (!present) {
                issue(result.getErrors(), "relations", "缺少子Skill:" + required);
            }
        });
        if (!childVersionIds.isEmpty() && relationGraphReachesSkill(childVersionIds, skill.getId())) {
            issue(result.getErrors(), "relations", "依赖图过深或存在循环");
        }
    }

    private Set<String> platformRequiredChildren(SakuraSkill skill) {
        if ("screenplay".equals(skill.getSkillCode())) {
            return Set.of("screenplay-write", "screenplay-review");
        }
        return Set.of();
    }

    private boolean relationGraphReachesSkill(Set<Long> startVersionIds, Long targetSkillId) {
        Set<Long> visited = new HashSet<>();
        Set<Long> frontier = new HashSet<>(startVersionIds);
        int depth = 0;
        while (!frontier.isEmpty()) {
            if (++depth > 32 || visited.size() + frontier.size() > 256) {
                return true;
            }
            visited.addAll(frontier);
            List<SakuraSkillRelation> nextRelations = relationMapper.selectList(
                    Wrappers.<SakuraSkillRelation>lambdaQuery()
                            .select(SakuraSkillRelation::getChildSkillId, SakuraSkillRelation::getChildVersionId)
                            .in(SakuraSkillRelation::getParentVersionId, frontier)
                            .eq(SakuraSkillRelation::getDelFlag, NORMAL));
            if (nextRelations.stream().anyMatch(relation ->
                    Objects.equals(relation.getChildSkillId(), targetSkillId))) {
                return true;
            }
            frontier = nextRelations.stream().map(SakuraSkillRelation::getChildVersionId)
                    .filter(Objects::nonNull).filter(versionId -> !visited.contains(versionId))
                    .collect(Collectors.toSet());
        }
        return false;
    }

    private List<String> requiredChildren(String definitionJson) {
        try {
            JSONArray children = JSON.parseObject(StrUtil.blankToDefault(definitionJson, "{}"))
                    .getJSONArray("children");
            if (children == null) {
                return List.of();
            }
            return children.stream().filter(String.class::isInstance).map(String.class::cast).toList();
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private void validateJsonObject(String text, String field, String message,
                                    SkillPackageAdminVO.ValidationResult result) {
        try {
            if (JSON.parseObject(text) == null) {
                issue(result.getErrors(), field, message);
            }
        } catch (RuntimeException error) {
            issue(result.getErrors(), field, message);
        }
    }

    /** Skill 版本结构化输出只能显式使用布尔值；缺省即普通文本。 */
    private void validateStructuredOutputSetting(String definitionJson,
                                                  SkillPackageAdminVO.ValidationResult result) {
        try {
            JSONObject definition = JSON.parseObject(StrUtil.blankToDefault(definitionJson, "{}"));
            Object configured = definition == null ? null : definition.get("structuredOutputEnabled");
            if (configured != null && !(configured instanceof Boolean)) {
                issue(result.getErrors(), "definitionJson.structuredOutputEnabled", "结构化输出开关必须为布尔值");
            }
        } catch (RuntimeException ignored) {
            // definitionJson 的通用校验已记录错误，避免重复提示。
        }
    }

    private SakuraSkillVersion buildVersion(SakuraSkill skill, DraftDocument document, String versionCode,
                                         String operator, Date now) {
        SakuraSkillVersion version = new SakuraSkillVersion();
        version.setSkillId(skill.getId());
        version.setVersionCode(versionCode);
        version.setVisibility(skill.getVisibility());
        version.setInvocationScope(skill.getInvocationScope());
        version.setPublishStatus("PRIVATE");
        version.setExecutorType(skill.getExecutorType());
        SkillModelConfiguration modelConfig = normalizeModelConfiguration(document);
        version.setModelCode(modelConfig.defaultModelCode());
        version.setModelConfigJson(modelConfig.toJson());
        version.setInputSchemaJson(compactObject(document.getInputSchemaJson()));
        version.setOutputSchemaJson(compactObject(document.getOutputSchemaJson()));
        version.setSystemPrompt(document.getSystemPrompt());
        version.setDefinitionJson(compactObject(StrUtil.blankToDefault(document.getDefinitionJson(), "{}")));
        version.setMaxOutputTokens(document.getMaxOutputTokens());
        version.setContextWindowTokens(document.getContextWindowTokens());
        version.setSafetyMarginTokens(document.getSafetyMarginTokens());
        version.setStatus(ENABLED);
        version.setDelFlag(NORMAL);
        version.setCreateBy(operator);
        version.setCreateTime(now);
        version.setUpdateBy(operator);
        version.setUpdateTime(now);
        version.setRemark("后台发布的不可变版本");
        return version;
    }

    private List<SakuraSkillResource> buildResources(SakuraSkill skill, SakuraSkillVersion version,
                                                   DraftDocument document, String operator, Date now) {
        List<SakuraSkillResource> result = new ArrayList<>();
        for (SkillPackageAdminRequests.ResourceItem item : safeResources(document.getResources())) {
            SakuraSkillResource resource = new SakuraSkillResource();
            resource.setResourceKey(StrUtil.trim(item.getResourceKey()));
            resource.setResourceType(StrUtil.blankToDefault(item.getResourceType(), "REFERENCE").toUpperCase());
            resource.setObjectKey("database:skills/" + skill.getSkillCode() + "/"
                    + version.getVersionCode() + "/" + item.getResourceKey());
            resource.setContentText(item.getContent());
            resource.setContentDigest(SecureUtil.sha256(item.getContent()));
            resource.setMimeType(StrUtil.blankToDefault(item.getMimeType(), "text/markdown").toLowerCase());
            resource.setSizeBytes((long) item.getContent().getBytes(StandardCharsets.UTF_8).length);
            resource.setRouteJson(compactObject(StrUtil.blankToDefault(item.getRouteJson(), "{}")));
            resource.setStatus(ENABLED);
            resource.setDelFlag(NORMAL);
            resource.setCreateBy(operator);
            resource.setCreateTime(now);
            resource.setUpdateBy(operator);
            resource.setUpdateTime(now);
            result.add(resource);
        }
        return result;
    }

    private List<SakuraSkillRelation> buildRelations(DraftDocument document, String operator, Date now) {
        List<SakuraSkillRelation> result = new ArrayList<>();
        for (SkillPackageAdminRequests.RelationItem item : safeRelations(document.getRelations())) {
            SakuraSkillRelation relation = new SakuraSkillRelation();
            relation.setChildSkillId(item.getChildSkillId());
            relation.setChildVersionId(item.getChildVersionId());
            relation.setRelationType("CHILD");
            relation.setRelationKey(StrUtil.trim(item.getRelationKey()));
            relation.setRequiredFlag(!Boolean.FALSE.equals(item.getRequiredFlag()));
            relation.setDelFlag(NORMAL);
            relation.setCreateBy(operator);
            relation.setCreateTime(now);
            relation.setUpdateBy(operator);
            relation.setUpdateTime(now);
            result.add(relation);
        }
        return result;
    }

    private String buildManifest(SakuraSkill skill, SakuraSkillVersion version,
                                 List<SakuraSkillResource> resources) {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("formatVersion", "2");
        manifest.put("code", skill.getSkillCode());
        manifest.put("version", version.getVersionCode());
        manifest.put("invocationScope", version.getInvocationScope());
        manifest.put("source", "DATABASE");
        manifest.put("digestAlgorithm", SkillPackageDigestCalculator.ALGORITHM_V3);
        manifest.put("resources", resources.stream().map(resource -> Map.of(
                "key", resource.getResourceKey(), "digest", resource.getContentDigest(),
                "route", JSON.parseObject(resource.getRouteJson()))).toList());
        return JSON.toJSONString(manifest);
    }

    private void requireRelationsAvailable(SakuraSkill parent, SakuraSkillVersion parentVersion) {
        List<SakuraSkillRelation> relations = relationMapper.selectList(Wrappers.<SakuraSkillRelation>lambdaQuery()
                .eq(SakuraSkillRelation::getParentVersionId, parentVersion.getId())
                .eq(SakuraSkillRelation::getRelationType, "CHILD")
                .eq(SakuraSkillRelation::getDelFlag, NORMAL)
                .orderByAsc(SakuraSkillRelation::getId).last("for update"));
        Set<String> requiredChildren = new HashSet<>(requiredChildren(parentVersion.getDefinitionJson()));
        requiredChildren.addAll(platformRequiredChildren(parent));
        for (String required : requiredChildren) {
            boolean present = relations.stream().anyMatch(relation -> required.equals(relation.getRelationKey())
                    && Boolean.TRUE.equals(relation.getRequiredFlag()));
            if (!present) {
                log.warn("Skill必需关系缺失, versionId={}, relationKey={}",
                        parentVersion.getId(), required);
                throw new ServiceException("必需关系缺失");
            }
        }
        Set<Long> skillIds = relations.stream().map(SakuraSkillRelation::getChildSkillId)
                .collect(Collectors.toSet());
        Set<Long> versionIds = relations.stream().map(SakuraSkillRelation::getChildVersionId)
                .collect(Collectors.toSet());
        Map<Long, SakuraSkill> children = skillIds.isEmpty() ? Map.of()
                : skillMapper.selectList(Wrappers.<SakuraSkill>lambdaQuery()
                        .in(SakuraSkill::getId, skillIds).orderByAsc(SakuraSkill::getId)
                        .last("for update")).stream()
                .collect(Collectors.toMap(SakuraSkill::getId, Function.identity()));
        Map<Long, SakuraSkillVersion> versions = versionIds.isEmpty() ? Map.of()
                : versionMapper.selectList(Wrappers.<SakuraSkillVersion>lambdaQuery()
                        .in(SakuraSkillVersion::getId, versionIds).orderByAsc(SakuraSkillVersion::getId)
                        .last("for update")).stream()
                .collect(Collectors.toMap(SakuraSkillVersion::getId, Function.identity()));
        SkillModelConfiguration modelConfig = SkillModelConfiguration.from(parentVersion);
        if (!modelConfig.isValid()
                || skillModelService.activeModels(modelConfig.selectableModelCodes()).size()
                != modelConfig.selectableModelCodes().size()) {
            log.warn("Skill目标版本模型不可用, versionId={}, modelCode={}",
                    parentVersion.getId(), parentVersion.getModelCode());
            throw new ServiceException("版本模型不可用");
        }
        Map<String, SakuraSkillVersion> packages = new LinkedHashMap<>();
        packages.put(parent.getSkillCode(), parentVersion);
        for (SakuraSkillRelation relation : relations) {
            SakuraSkill child = children.get(relation.getChildSkillId());
            SakuraSkillVersion version = versions.get(relation.getChildVersionId());
            if (child == null || version == null
                    || !Objects.equals(version.getSkillId(), child.getId())
                    || !Objects.equals(relation.getRelationKey(), child.getSkillCode())
                    || !"INTERNAL".equals(child.getInvocationScope())
                    || !ENABLED.equals(child.getStatus()) || !NORMAL.equals(child.getDelFlag())
                    || !"INTERNAL".equals(version.getInvocationScope())
                    || !ENABLED.equals(version.getStatus()) || !NORMAL.equals(version.getDelFlag())) {
                log.warn("Skill固定子版本不可用, parentVersionId={}, relationKey={}",
                        parentVersion.getId(), relation.getRelationKey());
                throw new ServiceException("子版本不可用");
            }
            packages.put(child.getSkillCode(), version);
        }
        packageResourceLoader.verifyVersions(packages);
    }

    private SkillPackageAdminVO.VersionDetail toVersionDetail(SakuraSkill skill, SakuraSkillVersion version) {
        SkillPackageAdminVO.VersionDetail result = new SkillPackageAdminVO.VersionDetail();
        result.setId(version.getId());
        result.setSkillId(version.getSkillId());
        result.setSkillCode(skill.getSkillCode());
        result.setVersionCode(version.getVersionCode());
        result.setVisibility(version.getVisibility());
        result.setInvocationScope(version.getInvocationScope());
        result.setPublishStatus(version.getPublishStatus());
        result.setExecutorType(version.getExecutorType());
        SkillModelConfiguration modelConfig = SkillModelConfiguration.from(version);
        result.setModelCode(modelConfig.defaultModelCode());
        result.setModelConfigJson(version.getModelConfigJson());
        result.setDefaultModelCode(modelConfig.defaultModelCode());
        result.setSelectableModelCodes(modelConfig.selectableModelCodes());
        result.setPackageDigest(version.getPackageDigest());
        result.setManifestJson(version.getManifestJson());
        result.setInputSchemaJson(version.getInputSchemaJson());
        result.setOutputSchemaJson(version.getOutputSchemaJson());
        result.setSystemPrompt(version.getSystemPrompt());
        result.setDefinitionJson(version.getDefinitionJson());
        result.setMaxOutputTokens(version.getMaxOutputTokens());
        result.setContextWindowTokens(version.getContextWindowTokens());
        result.setSafetyMarginTokens(version.getSafetyMarginTokens());
        result.setStatus(version.getStatus());
        result.setCurrent(Objects.equals(skill.getCurrentVersionId(), version.getId()));
        result.setCreateBy(version.getCreateBy());
        result.setCreateTime(version.getCreateTime());
        List<SakuraSkillResource> resources = selectPublishedResources(version);
        result.setResources(resources.stream().map(resource -> toResource(version, resource)).toList());
        result.setRelations(loadRelationItems(version.getId()));
        return result;
    }

    private SkillPackageAdminVO.ResourceItem toResource(SakuraSkillVersion version, SakuraSkillResource resource) {
        SkillPackageAdminVO.ResourceItem result = new SkillPackageAdminVO.ResourceItem();
        result.setId(resource.getId());
        result.setResourceKey(resource.getResourceKey());
        result.setResourceType(resource.getResourceType());
        result.setObjectKey(resource.getObjectKey());
        result.setContentDigest(resource.getContentDigest());
        result.setMimeType(resource.getMimeType());
        result.setSizeBytes(resource.getSizeBytes());
        result.setRouteJson(resource.getRouteJson());
        result.setContent(packageResourceLoader.readPublishedResource(version, resource));
        return result;
    }

    /**
     * Classpath packages predate content_text and must stay readable before the database-package migration.
     * Database packages opt in to the body column explicitly because their immutable content lives in the row.
     */
    private List<SakuraSkillResource> selectPublishedResources(SakuraSkillVersion version) {
        var query = Wrappers.<SakuraSkillResource>lambdaQuery();
        if (packageResourceLoader.isDatabasePackage(version)) {
            query.select(SakuraSkillResource::getId, SakuraSkillResource::getSkillVersionId,
                    SakuraSkillResource::getResourceKey, SakuraSkillResource::getResourceType,
                    SakuraSkillResource::getObjectKey, SakuraSkillResource::getContentDigest,
                    SakuraSkillResource::getMimeType, SakuraSkillResource::getSizeBytes,
                    SakuraSkillResource::getRouteJson, SakuraSkillResource::getContentText,
                    SakuraSkillResource::getStatus, SakuraSkillResource::getDelFlag);
        } else {
            query.select(SakuraSkillResource::getId, SakuraSkillResource::getSkillVersionId,
                    SakuraSkillResource::getResourceKey, SakuraSkillResource::getResourceType,
                    SakuraSkillResource::getObjectKey, SakuraSkillResource::getContentDigest,
                    SakuraSkillResource::getMimeType, SakuraSkillResource::getSizeBytes,
                    SakuraSkillResource::getRouteJson, SakuraSkillResource::getStatus,
                    SakuraSkillResource::getDelFlag);
        }
        return resourceMapper.selectList(query
                .eq(SakuraSkillResource::getSkillVersionId, version.getId())
                .eq(SakuraSkillResource::getDelFlag, NORMAL).orderByAsc(SakuraSkillResource::getId));
    }

    private List<SkillPackageAdminVO.RelationItem> loadRelationItems(Long parentVersionId) {
        List<SakuraSkillRelation> relations = relationMapper.selectList(Wrappers.<SakuraSkillRelation>lambdaQuery()
                .eq(SakuraSkillRelation::getParentVersionId, parentVersionId)
                .eq(SakuraSkillRelation::getDelFlag, NORMAL).orderByAsc(SakuraSkillRelation::getId));
        if (relations.isEmpty()) {
            return List.of();
        }
        Set<Long> skillIds = relations.stream().map(SakuraSkillRelation::getChildSkillId).collect(Collectors.toSet());
        Set<Long> versionIds = relations.stream().map(SakuraSkillRelation::getChildVersionId).collect(Collectors.toSet());
        Map<Long, SakuraSkill> skills = skillMapper.selectList(Wrappers.<SakuraSkill>lambdaQuery()
                        .select(SakuraSkill::getId, SakuraSkill::getSkillCode).in(SakuraSkill::getId, skillIds)).stream()
                .collect(Collectors.toMap(SakuraSkill::getId, Function.identity()));
        Map<Long, SakuraSkillVersion> versions = versionMapper.selectList(Wrappers.<SakuraSkillVersion>lambdaQuery()
                        .select(SakuraSkillVersion::getId, SakuraSkillVersion::getVersionCode)
                        .in(SakuraSkillVersion::getId, versionIds)).stream()
                .collect(Collectors.toMap(SakuraSkillVersion::getId, Function.identity()));
        return relations.stream().map(relation -> {
            SkillPackageAdminVO.RelationItem item = new SkillPackageAdminVO.RelationItem();
            item.setId(relation.getId());
            item.setRelationKey(relation.getRelationKey());
            item.setChildSkillId(relation.getChildSkillId());
            item.setChildVersionId(relation.getChildVersionId());
            item.setRequiredFlag(relation.getRequiredFlag());
            SakuraSkill child = skills.get(relation.getChildSkillId());
            SakuraSkillVersion childVersion = versions.get(relation.getChildVersionId());
            item.setChildSkillCode(child == null ? null : child.getSkillCode());
            item.setChildVersionCode(childVersion == null ? null : childVersion.getVersionCode());
            return item;
        }).toList();
    }

    private DraftDocument seedDocument(SakuraSkill skill, SakuraSkillVersion version) {
        packageResourceLoader.verifyVersion(skill.getSkillCode(), version);
        DraftDocument document = new DraftDocument();
        document.setBaseVersionId(version.getId());
        document.setSkillId(version.getSkillId());
        SkillModelConfiguration modelConfig = SkillModelConfiguration.from(version);
        document.setModelCode(modelConfig.defaultModelCode());
        document.setDefaultModelCode(modelConfig.defaultModelCode());
        document.setSelectableModelCodes(new ArrayList<>(modelConfig.selectableModelCodes()));
        document.setSystemPrompt(version.getSystemPrompt());
        document.setInputSchemaJson(version.getInputSchemaJson());
        document.setOutputSchemaJson(version.getOutputSchemaJson());
        document.setDefinitionJson(version.getDefinitionJson());
        document.setMaxOutputTokens(version.getMaxOutputTokens());
        document.setContextWindowTokens(version.getContextWindowTokens());
        document.setSafetyMarginTokens(version.getSafetyMarginTokens());
        List<SakuraSkillResource> resources = selectPublishedResources(version);
        document.setResources(resources.stream().map(resource -> {
            SkillPackageAdminRequests.ResourceItem item = new SkillPackageAdminRequests.ResourceItem();
            item.setResourceKey(resource.getResourceKey());
            item.setResourceType(resource.getResourceType());
            item.setMimeType(resource.getMimeType());
            item.setContent(packageResourceLoader.readPublishedResource(version, resource));
            item.setRouteJson(resource.getRouteJson());
            return item;
        }).toList());
        document.setRelations(loadRelationItems(version.getId()).stream().map(source -> {
            SkillPackageAdminRequests.RelationItem item = new SkillPackageAdminRequests.RelationItem();
            item.setRelationKey(source.getRelationKey());
            item.setChildSkillId(source.getChildSkillId());
            item.setChildVersionId(source.getChildVersionId());
            item.setRequiredFlag(source.getRequiredFlag());
            return item;
        }).toList());
        return document;
    }

    private DraftDocument seedIdentityDocument(SakuraSkill skill) {
        DraftDocument document = new DraftDocument();
        document.setBaseVersionId(null);
        document.setSkillId(skill.getId());
        document.setModelCode(skill.getModelCode());
        document.setDefaultModelCode(skill.getModelCode());
        document.setSelectableModelCodes(StrUtil.isBlank(skill.getModelCode())
                ? new ArrayList<>() : new ArrayList<>(List.of(skill.getModelCode())));
        document.setSystemPrompt(skill.getSystemPrompt());
        document.setInputSchemaJson(skill.getInputSchemaJson());
        document.setOutputSchemaJson(skill.getOutputSchemaJson());
        document.setDefinitionJson(StrUtil.blankToDefault(skill.getDefinitionJson(), "{}"));
        document.setMaxOutputTokens(skill.getMaxOutputTokens());
        document.setContextWindowTokens(skill.getContextWindowTokens());
        document.setSafetyMarginTokens(skill.getSafetyMarginTokens());
        return document;
    }

    private SkillPackageAdminVO.DraftDetail toDraftDetail(SakuraSkill skill, SakuraSkillDraft draft,
                                                           DraftDocument document) {
        SkillPackageAdminVO.DraftDetail result = new SkillPackageAdminVO.DraftDetail();
        copyPayload(document, result);
        result.setDraftId(draft == null ? null : draft.getId());
        result.setBaseVersionId(document.getBaseVersionId());
        result.setBaseVersionCode(versionCode(document.getBaseVersionId()));
        result.setSkillCode(skill.getSkillCode());
        result.setExecutorType(skill.getExecutorType());
        result.setInvocationScope(skill.getInvocationScope());
        result.setDraftDigest(draft == null
                ? SecureUtil.sha256(canonicalDraftJson(document)) : draft.getDraftDigest());
        result.setUpdateTime(draft == null ? null : draft.getUpdateTime());
        return result;
    }

    private String versionCode(Long versionId) {
        if (versionId == null) {
            return null;
        }
        SakuraSkillVersion version = versionMapper.selectOne(Wrappers.<SakuraSkillVersion>lambdaQuery()
                .select(SakuraSkillVersion::getId, SakuraSkillVersion::getVersionCode)
                .eq(SakuraSkillVersion::getId, versionId).last("limit 1"));
        return version == null ? null : version.getVersionCode();
    }

    private DraftDocument document(SkillPackageAdminRequests.PackagePayload source, Long baseVersionId) {
        DraftDocument result = new DraftDocument();
        result.setBaseVersionId(baseVersionId);
        copyPayload(source, result);
        result.setResources(new ArrayList<>(safeResources(source.getResources())));
        result.setRelations(new ArrayList<>(safeRelations(source.getRelations())));
        return result;
    }

    private void copyPayload(SkillPackageAdminRequests.PackagePayload source,
                             SkillPackageAdminRequests.PackagePayload target) {
        target.setSkillId(source.getSkillId());
        target.setBaseVersionId(source.getBaseVersionId());
        target.setModelCode(source.getModelCode());
        target.setDefaultModelCode(source.getDefaultModelCode());
        target.setSelectableModelCodes(new ArrayList<>(source.getSelectableModelCodes() == null
                ? List.of() : source.getSelectableModelCodes()));
        target.setSystemPrompt(source.getSystemPrompt());
        target.setInputSchemaJson(source.getInputSchemaJson());
        target.setOutputSchemaJson(source.getOutputSchemaJson());
        target.setDefinitionJson(source.getDefinitionJson());
        target.setMaxOutputTokens(source.getMaxOutputTokens());
        target.setContextWindowTokens(source.getContextWindowTokens());
        target.setSafetyMarginTokens(source.getSafetyMarginTokens());
        target.setResources(new ArrayList<>(safeResources(source.getResources())));
        target.setRelations(new ArrayList<>(safeRelations(source.getRelations())));
    }

    private String canonicalDraftJson(DraftDocument document) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("schemaVersion", 2);
        value.put("baseVersionId", document.getBaseVersionId());
        value.put("skillId", document.getSkillId());
        value.put("modelCode", document.getModelCode());
        value.put("defaultModelCode", document.getDefaultModelCode());
        value.put("selectableModelCodes", document.getSelectableModelCodes());
        value.put("systemPrompt", document.getSystemPrompt());
        value.put("inputSchemaJson", document.getInputSchemaJson());
        value.put("outputSchemaJson", document.getOutputSchemaJson());
        value.put("definitionJson", document.getDefinitionJson());
        value.put("maxOutputTokens", document.getMaxOutputTokens());
        value.put("contextWindowTokens", document.getContextWindowTokens());
        value.put("safetyMarginTokens", document.getSafetyMarginTokens());
        value.put("resources", safeResources(document.getResources()).stream()
                .map(item -> {
                    Map<String, Object> resource = new LinkedHashMap<>();
                    if (item == null) {
                        resource.put("invalid", true);
                        return resource;
                    }
                    resource.put("resourceKey", item.getResourceKey());
                    resource.put("resourceType", item.getResourceType());
                    resource.put("mimeType", item.getMimeType());
                    resource.put("content", item.getContent());
                    resource.put("routeJson", item.getRouteJson());
                    return resource;
                }).toList());
        value.put("relations", safeRelations(document.getRelations()).stream()
                .sorted(Comparator.comparing(item -> item == null ? null : item.getRelationKey(),
                        Comparator.nullsFirst(String::compareTo)))
                .map(item -> {
                    Map<String, Object> relation = new LinkedHashMap<>();
                    if (item == null) {
                        relation.put("invalid", true);
                        return relation;
                    }
                    relation.put("relationKey", item.getRelationKey());
                    relation.put("childSkillId", item.getChildSkillId());
                    relation.put("childVersionId", item.getChildVersionId());
                    relation.put("requiredFlag", item.getRequiredFlag());
                    return relation;
                }).toList());
        return JSON.toJSONString(value);
    }

    private DraftDocument parseDraft(SakuraSkillDraft draft) {
        try {
            String storedJson = draft.getDraftJson();
            DraftDocument result = JSON.parseObject(storedJson, DraftDocument.class);
            boolean storedDigestMatches = Objects.equals(SecureUtil.sha256(storedJson), draft.getDraftDigest());
            boolean currentCanonicalMatches = result != null && Objects.equals(
                    SecureUtil.sha256(canonicalDraftJson(result)), draft.getDraftDigest());
            if (result == null || (!storedDigestMatches && !currentCanonicalMatches)) {
                throw new IllegalArgumentException();
            }
            if (result.getSkillId() != null && !Objects.equals(result.getSkillId(), draft.getSkillId())) {
                throw new IllegalArgumentException();
            }
            if (result.getSkillId() == null) {
                result.setSkillId(draft.getSkillId());
            }
            return result;
        } catch (RuntimeException error) {
            log.error("Skill草稿内容损坏, draftId={}", draft.getId(), error);
            throw new ServiceException("草稿内容损坏");
        }
    }

    private SakuraSkill requireSkill(Long skillId) {
        SakuraSkill skill = skillMapper.selectOne(Wrappers.<SakuraSkill>lambdaQuery()
                .eq(SakuraSkill::getId, skillId).eq(SakuraSkill::getDelFlag, NORMAL).last("limit 1"));
        if (skill == null || SkillRuntimeCapabilities.RETIRED_SCREENPLAY_CHAT.equals(skill.getSkillCode())) {
            log.warn("Skill管理目标不存在, skillId={}", skillId);
            throw new ServiceException("Skill不存在");
        }
        return skill;
    }

    private SakuraSkill requireSkillLight(Long skillId) {
        SakuraSkill skill = skillMapper.selectOne(Wrappers.<SakuraSkill>lambdaQuery()
                .select(SakuraSkill::getId, SakuraSkill::getSkillCode, SakuraSkill::getName,
                        SakuraSkill::getInvocationScope, SakuraSkill::getCurrentVersionId,
                        SakuraSkill::getStatus, SakuraSkill::getDelFlag)
                .eq(SakuraSkill::getId, skillId).eq(SakuraSkill::getDelFlag, NORMAL).last("limit 1"));
        if (skill == null || SkillRuntimeCapabilities.RETIRED_SCREENPLAY_CHAT.equals(skill.getSkillCode())) {
            log.warn("Skill管理目标不存在, skillId={}", skillId);
            throw new ServiceException("Skill不存在");
        }
        return skill;
    }

    private SakuraSkillVersion requireVersion(Long versionId) {
        SakuraSkillVersion version = versionMapper.selectOne(Wrappers.<SakuraSkillVersion>lambdaQuery()
                .eq(SakuraSkillVersion::getId, versionId).eq(SakuraSkillVersion::getDelFlag, NORMAL)
                .last("limit 1"));
        if (version == null) {
            log.warn("Skill版本不存在, versionId={}", versionId);
            throw new ServiceException("版本不存在");
        }
        return version;
    }

    private SakuraSkillVersion requireCurrentVersion(SakuraSkill skill) {
        if (skill.getCurrentVersionId() == null) {
            log.warn("Skill当前版本缺失, skillId={}", skill.getId());
            throw new ServiceException("当前版本不存在");
        }
        SakuraSkillVersion version = versionMapper.selectOne(Wrappers.<SakuraSkillVersion>lambdaQuery()
                .eq(SakuraSkillVersion::getId, skill.getCurrentVersionId())
                .eq(SakuraSkillVersion::getSkillId, skill.getId())
                .eq(SakuraSkillVersion::getDelFlag, NORMAL).last("limit 1"));
        if (version == null) {
            log.warn("Skill当前版本无效, skillId={}, versionId={}",
                    skill.getId(), skill.getCurrentVersionId());
            throw new ServiceException("当前版本不存在");
        }
        return version;
    }

    private boolean baseVersionValid(SakuraSkill skill, Long versionId) {
        if (versionId == null) {
            return skill.getCurrentVersionId() == null;
        }
        return versionMapper.selectCount(Wrappers.<SakuraSkillVersion>lambdaQuery()
                .eq(SakuraSkillVersion::getId, versionId).eq(SakuraSkillVersion::getSkillId, skill.getId())
                .eq(SakuraSkillVersion::getDelFlag, NORMAL)) == 1;
    }

    private SkillPackageAdminVO.VersionSummary toSummary(SakuraSkillVersion version, Long currentVersionId) {
        SkillPackageAdminVO.VersionSummary result = new SkillPackageAdminVO.VersionSummary();
        result.setId(version.getId());
        result.setSkillId(version.getSkillId());
        result.setVersionCode(version.getVersionCode());
        result.setPublishStatus(version.getPublishStatus());
        result.setPackageDigest(version.getPackageDigest());
        result.setStatus(version.getStatus());
        result.setCurrent(Objects.equals(currentVersionId, version.getId()));
        result.setCreateBy(version.getCreateBy());
        result.setCreateTime(version.getCreateTime());
        return result;
    }

    private SkillModelConfiguration normalizeModelConfiguration(
            SkillPackageAdminRequests.PackagePayload payload) {
        String defaultCode = StrUtil.blankToDefault(
                StrUtil.trim(payload.getDefaultModelCode()), StrUtil.trim(payload.getModelCode()));
        List<String> candidates = payload.getSelectableModelCodes();
        SkillModelConfiguration config = SkillModelConfiguration.normalized(defaultCode, candidates, true);
        payload.setDefaultModelCode(config.defaultModelCode());
        payload.setSelectableModelCodes(new ArrayList<>(config.selectableModelCodes()));
        payload.setModelCode(config.defaultModelCode());
        return config;
    }

    private String compactObject(String text) {
        return JSON.toJSONString(JSON.parseObject(text));
    }

    private void issue(List<SkillPackageAdminVO.ValidationIssue> target, String field, String message) {
        target.add(new SkillPackageAdminVO.ValidationIssue(field, message));
    }

    private List<SkillPackageAdminRequests.ResourceItem> safeResources(
            List<SkillPackageAdminRequests.ResourceItem> resources) {
        return resources == null ? List.of() : resources;
    }

    private List<SkillPackageAdminRequests.RelationItem> safeRelations(
            List<SkillPackageAdminRequests.RelationItem> relations) {
        return relations == null ? List.of() : relations;
    }

    private String operator(Long operatorId, String operatorName) {
        return StrUtil.blankToDefault(operatorName, String.valueOf(operatorId));
    }

    private String activeDraftKey(Long operatorId, Long skillId) {
        return operatorId + ":" + skillId;
    }

    private String value(String source) {
        return source == null ? "" : source;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    private static class DraftDocument extends SkillPackageAdminRequests.PackagePayload {
        private Integer schemaVersion = 2;
    }

    private record PublishedCoordinate(Long skillId, Long versionId) { }
}
