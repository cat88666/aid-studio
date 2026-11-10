package com.sakura.skill.service.impl;

import com.sakura.skill.domain.SakuraSkill;
import com.sakura.skill.mapper.SakuraSkillMapper;
import com.sakura.skill.service.ISkillCatalogService;
import com.sakura.skill.service.SkillRuntimeCapabilities;
import com.sakura.skill.service.SkillRuntimeEntrypointReadinessService;
import com.sakura.skill.service.SkillModelService;
import com.sakura.skill.vo.SkillCatalogVO;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Catalog backed only by active, versioned Runtime entrypoints. */
@Service
@RequiredArgsConstructor
public class SkillCatalogServiceImpl implements ISkillCatalogService {
    private static final String NORMAL = "0";

    private final SakuraSkillMapper skillMapper;
    private final SkillRuntimeEntrypointReadinessService readinessService;
    private final SkillModelService skillModelService;

    @Override
    public List<SkillCatalogVO.Item> listEntrypoints() {
        List<SakuraSkill> candidates = skillMapper.selectList(Wrappers.<SakuraSkill>lambdaQuery()
                        .select(SakuraSkill::getId, SakuraSkill::getSkillCode, SakuraSkill::getName,
                                SakuraSkill::getDescription, SakuraSkill::getCapabilityDescription,
                                SakuraSkill::getIconUrl,
                                SakuraSkill::getOwnerType, SakuraSkill::getVisibility,
                                SakuraSkill::getInvocationScope, SakuraSkill::getCurrentVersionId,
                                SakuraSkill::getStatus, SakuraSkill::getDelFlag)
                        .eq(SakuraSkill::getOwnerType, SkillRuntimeCapabilities.OWNER_PLATFORM)
                        .eq(SakuraSkill::getVisibility, SkillRuntimeCapabilities.VISIBILITY_PUBLIC)
                        .eq(SakuraSkill::getInvocationScope, SkillRuntimeCapabilities.SCOPE_ENTRYPOINT)
                        .in(SakuraSkill::getSkillCode, SkillRuntimeCapabilities.CALLABLE_ENTRYPOINTS)
                        .isNotNull(SakuraSkill::getCurrentVersionId)
                        .eq(SakuraSkill::getStatus, NORMAL)
                        .eq(SakuraSkill::getDelFlag, NORMAL)
                        .orderByAsc(SakuraSkill::getId));
        if (candidates.isEmpty()) {
            return List.of();
        }
        Map<Long, SkillRuntimeEntrypointReadinessService.ReadyEntrypoint> ready =
                readinessService.findReady(candidates);
        Map<Long, SkillModelService.CatalogProjection> modelProjections =
                skillModelService.catalogProjections(ready.entrySet().stream().collect(Collectors.toMap(
                        Map.Entry::getKey, entry -> entry.getValue().rootVersion(),
                        (left, right) -> left)));
        return candidates.stream().filter(skill -> ready.containsKey(skill.getId()))
                .map(skill -> toItem(skill, modelProjections.get(skill.getId()))).toList();
    }

    private SkillCatalogVO.Item toItem(SakuraSkill skill, SkillModelService.CatalogProjection models) {
        SkillRuntimeCapabilities.Descriptor descriptor =
                SkillRuntimeCapabilities.descriptor(skill.getSkillCode());
        SkillCatalogVO.Item item = new SkillCatalogVO.Item();
        item.setId(skill.getId());
        item.setSkillCode(skill.getSkillCode());
        item.setName(SkillRuntimeCapabilities.displayName(skill.getSkillCode(), skill.getName()));
        item.setDescription(skill.getDescription());
        item.setCapabilityDescription(skill.getCapabilityDescription());
        item.setIconUrl(skill.getIconUrl());
        item.setCapability(descriptor.capability());
        item.setOutputKind(descriptor.outputKind());
        if (models != null) {
            item.setDefaultModelCode(models.defaultModelCode());
            item.setModels(models.models());
        }
        return item;
    }
}
