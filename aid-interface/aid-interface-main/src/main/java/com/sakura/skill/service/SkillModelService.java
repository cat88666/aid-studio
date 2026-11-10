package com.sakura.skill.service;

import cn.hutool.core.util.StrUtil;
import com.sakura.sakura.domain.SakuraAiModel;
import com.sakura.sakura.domain.SakuraAiProvider;
import com.sakura.sakura.service.ISakuraAiModelService;
import com.sakura.sakura.service.ISakuraAiProviderService;
import com.sakura.billing.service.IBillingDetailQueryService;
import com.sakura.common.exception.ServiceException;
import com.sakura.model.vo.CapabilityVO;
import com.sakura.skill.domain.SakuraSkillVersion;
import com.sakura.skill.vo.SkillAdminVO;
import com.sakura.skill.vo.SkillCatalogVO;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Skill 模型选择、停用回退及管理/C端模型投影的单一事实源。 */
@Service
@RequiredArgsConstructor
public class SkillModelService {
    private static final String NORMAL = "0";
    private static final String TEXT = "text";

    private final ISakuraAiModelService modelService;
    private final ISakuraAiProviderService providerService;
    private final IBillingDetailQueryService billingDetailQueryService;

    public Selection resolve(SakuraSkillVersion version, String requestedModelCode) {
        SkillModelConfiguration config = SkillModelConfiguration.from(version);
        if (!config.isValid()) {
            throw new ServiceException("Skill版本模型配置无效");
        }
        Map<String, SakuraAiModel> active = activeModels(config.selectableModelCodes());
        String requested = StrUtil.trim(requestedModelCode);
        if (StrUtil.isNotBlank(requested)) {
            if (!config.selectableModelCodes().contains(requested)) {
                throw new ServiceException("所选模型不属于当前Skill版本");
            }
            if (!active.containsKey(requested)) {
                throw new ServiceException("所选模型已停用，请重新选择");
            }
            return new Selection(requested, requested.equals(config.defaultModelCode()), config, active);
        }
        String effectiveDefault = active.containsKey(config.defaultModelCode())
                ? config.defaultModelCode()
                : config.selectableModelCodes().stream().filter(active::containsKey).findFirst().orElse(null);
        if (effectiveDefault == null) {
            throw new ServiceException("Skill暂无可用模型");
        }
        return new Selection(effectiveDefault, true, config, active);
    }

    public boolean hasActiveCandidate(SakuraSkillVersion version) {
        SkillModelConfiguration config = SkillModelConfiguration.from(version);
        return config.isValid() && !activeModels(config.selectableModelCodes()).isEmpty();
    }

    /** 一次模型 IN 查询和一次供应商 IN 查询，返回配置顺序下的 C 端模型视图。 */
    public CatalogProjection catalogProjection(SakuraSkillVersion version) {
        return catalogProjections(Map.of(0L, version)).getOrDefault(0L,
                new CatalogProjection(null, List.of()));
    }

    public Map<Long, CatalogProjection> catalogProjections(Map<Long, SakuraSkillVersion> versionsBySkillId) {
        if (versionsBySkillId == null || versionsBySkillId.isEmpty()) {
            return Map.of();
        }
        Set<String> codes = versionsBySkillId.values().stream()
                .flatMap(version -> SkillModelConfiguration.from(version).selectableModelCodes().stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, SakuraAiModel> active = activeModels(codes);
        Map<Long, SakuraAiProvider> providers = providers(active.values().stream()
                .map(SakuraAiModel::getProviderId).filter(Objects::nonNull).collect(Collectors.toSet()), true);
        BigDecimal globalFactor = billingDetailQueryService.readGlobalPriceFactor();
        Map<Long, CatalogProjection> result = new LinkedHashMap<>();
        versionsBySkillId.forEach((skillId, version) -> result.put(skillId,
                catalogProjection(version, active, providers, globalFactor)));
        return result;
    }

    private CatalogProjection catalogProjection(SakuraSkillVersion version,
                                                Map<String, SakuraAiModel> active,
                                                Map<Long, SakuraAiProvider> providers,
                                                BigDecimal globalFactor) {
        SkillModelConfiguration config = SkillModelConfiguration.from(version);
        if (!config.isValid()) {
            return new CatalogProjection(null, List.of());
        }
        String effectiveDefault = active.containsKey(config.defaultModelCode())
                ? config.defaultModelCode()
                : config.selectableModelCodes().stream().filter(active::containsKey).findFirst().orElse(null);
        if (effectiveDefault == null) {
            return new CatalogProjection(null, List.of());
        }
        List<SkillCatalogVO.ModelItem> items = new ArrayList<>();
        for (String code : config.selectableModelCodes()) {
            SakuraAiModel model = active.get(code);
            SakuraAiProvider provider = model == null ? null : providers.get(model.getProviderId());
            if (model == null || provider == null) {
                continue;
            }
            SkillCatalogVO.ModelItem item = new SkillCatalogVO.ModelItem();
            item.setModelCode(code);
            item.setModelName(model.getModelName());
            item.setModelLogo(model.getLogoUrl());
            item.setProviderName(provider.getProviderName());
            item.setProviderLogo(provider.getLogoUrl());
            item.setDefaultModel(code.equals(effectiveDefault));
            item.setCapability(capability(model));
            item.setBilling(billingDetailQueryService.buildModelBillingDetail(
                    model, provider.getProviderName(), provider.getLogoUrl(), globalFactor));
            items.add(item);
        }
        return new CatalogProjection(effectiveDefault, List.copyOf(items));
    }

    /** 管理端保留停用/软删除模型，确保旧版本引用可见但不可继续选中。 */
    public List<SkillAdminVO.TextModelOption> adminOptions() {
        List<SakuraAiModel> models = modelService.list(Wrappers.<SakuraAiModel>lambdaQuery()
                .eq(SakuraAiModel::getModelType, TEXT)
                .orderByDesc(SakuraAiModel::getPriority).orderByAsc(SakuraAiModel::getId));
        if (models.isEmpty()) {
            return List.of();
        }
        Map<Long, SakuraAiProvider> providers = providers(models.stream().map(SakuraAiModel::getProviderId)
                .filter(Objects::nonNull).collect(Collectors.toSet()), false);
        BigDecimal globalFactor = billingDetailQueryService.readGlobalPriceFactor();
        return models.stream().map(model -> {
            SakuraAiProvider provider = providers.get(model.getProviderId());
            boolean providerAvailable = provider != null && NORMAL.equals(provider.getStatus())
                    && NORMAL.equals(provider.getDelFlag());
            boolean available = NORMAL.equals(model.getStatus()) && NORMAL.equals(model.getDelFlag())
                    && providerAvailable;
            SkillAdminVO.TextModelOption item = new SkillAdminVO.TextModelOption();
            item.setModelCode(model.getModelCode());
            item.setModelName(model.getModelName());
            item.setCapabilityJson(model.getCapabilityJson());
            item.setCapability(capability(model));
            item.setModelLogo(model.getLogoUrl());
            item.setProviderName(provider == null ? null : provider.getProviderName());
            item.setProviderLogo(provider == null ? null : provider.getLogoUrl());
            item.setStatus(model.getStatus());
            item.setDelFlag(model.getDelFlag());
            item.setAvailable(available);
            item.setUnavailableReason(available ? null : unavailableReason(model, provider));
            if (provider != null) {
                item.setBilling(billingDetailQueryService.buildModelBillingDetail(model,
                        provider.getProviderName(), provider.getLogoUrl(), globalFactor));
            }
            return item;
        }).toList();
    }

    public Map<String, SakuraAiModel> activeModels(Collection<String> modelCodes) {
        Set<String> codes = modelCodes == null ? Set.of() : modelCodes.stream()
                .map(StrUtil::trim).filter(StrUtil::isNotBlank)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (codes.isEmpty()) {
            return Map.of();
        }
        List<SakuraAiModel> models = modelService.list(Wrappers.<SakuraAiModel>lambdaQuery()
                .in(SakuraAiModel::getModelCode, codes).eq(SakuraAiModel::getModelType, TEXT)
                .eq(SakuraAiModel::getStatus, NORMAL).eq(SakuraAiModel::getDelFlag, NORMAL));
        Set<Long> providerIds = models.stream().map(SakuraAiModel::getProviderId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> activeProviderIds = providers(providerIds, true).keySet();
        return models.stream().filter(model -> activeProviderIds.contains(model.getProviderId()))
                .collect(Collectors.toMap(SakuraAiModel::getModelCode, Function.identity(),
                        (left, right) -> left, LinkedHashMap::new));
    }

    private Map<Long, SakuraAiProvider> providers(Set<Long> ids, boolean activeOnly) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        var query = Wrappers.<SakuraAiProvider>lambdaQuery().select(SakuraAiProvider::getId,
                SakuraAiProvider::getProviderName, SakuraAiProvider::getLogoUrl,
                SakuraAiProvider::getStatus, SakuraAiProvider::getDelFlag).in(SakuraAiProvider::getId, ids);
        if (activeOnly) {
            query.eq(SakuraAiProvider::getStatus, NORMAL).eq(SakuraAiProvider::getDelFlag, NORMAL);
        }
        return providerService.list(query).stream().collect(Collectors.toMap(
                SakuraAiProvider::getId, Function.identity(), (left, right) -> left));
    }

    private CapabilityVO capability(SakuraAiModel model) {
        try {
            CapabilityVO capability = JSON.parseObject(
                    StrUtil.blankToDefault(model.getCapabilityJson(), "{}"), CapabilityVO.class);
            return capability == null ? new CapabilityVO() : capability;
        } catch (RuntimeException ignored) {
            return new CapabilityVO();
        }
    }

    private String unavailableReason(SakuraAiModel model, SakuraAiProvider provider) {
        if (!NORMAL.equals(model.getDelFlag())) return "模型已删除、需替换";
        if (!NORMAL.equals(model.getStatus())) return "模型已停用、需替换";
        if (provider == null || !NORMAL.equals(provider.getDelFlag())) return "供应商已删除、需替换";
        return "供应商已停用、需替换";
    }

    public record Selection(String modelCode, boolean effectiveDefault,
                            SkillModelConfiguration configuration,
                            Map<String, SakuraAiModel> activeModels) { }

    public record CatalogProjection(String defaultModelCode, List<SkillCatalogVO.ModelItem> models) { }
}
