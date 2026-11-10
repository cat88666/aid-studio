package com.sakura.model.definition;

import com.sakura.sakura.domain.SakuraAiBusinessModelBinding;
import com.sakura.sakura.domain.SakuraAiModel;
import com.sakura.sakura.domain.SakuraAiModelCapability;
import com.sakura.sakura.domain.SakuraAiModelFuncConfig;
import com.sakura.sakura.domain.model.ModelCapabilityDefinition;
import com.sakura.sakura.mapper.SakuraAiBusinessModelBindingMapper;
import com.sakura.sakura.mapper.SakuraAiModelCapabilityMapper;
import com.sakura.sakura.service.ISakuraAiModelFuncConfigService;
import com.sakura.sakura.service.ISakuraAiModelService;
import com.sakura.common.exception.ServiceException;
import com.sakura.common.utils.DateUtils;
import com.sakura.orchestration.dto.ModelPoolCapabilitySelection;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModelFunctionBindingReconciler {
    private final SakuraAiBusinessModelBindingMapper bindings;
    private final SakuraAiModelCapabilityMapper capabilities;
    private final ISakuraAiModelFuncConfigService functions;
    private final ISakuraAiModelService models;

    /** 普通功能池编辑入口：保留既有绑定，新增模型按唯一能力或唯一默认能力自动选择。 */
    @Transactional(rollbackFor = Exception.class)
    public void reconcile(SakuraAiModelFuncConfig function, String actor) {
        reconcile(function, actor, Collections.emptyMap());
    }

    /** 批量绑定入口可按模型显式指定能力；未指定时仍执行安全的默认选择。 */
    @Transactional(rollbackFor = Exception.class)
    public void reconcile(SakuraAiModelFuncConfig function, String actor,
            Map<Long, ModelPoolCapabilitySelection> requestedSelections) {
        List<Long> ids = function.getModelIds() == null ? List.of() : JSON.parseArray(function.getModelIds(), Long.class);
        List<SakuraAiBusinessModelBinding> previous = bindings.selectList(Wrappers.<SakuraAiBusinessModelBinding>lambdaQuery()
                .eq(SakuraAiBusinessModelBinding::getFuncCode, function.getFuncCode()));
        SakuraAiModelFuncConfig persisted = function.getId() == null ? null : functions.getById(function.getId());
        Set<Long> existingIds = persisted == null || persisted.getModelIds() == null
                ? Collections.emptySet() : new LinkedHashSet<>(JSON.parseArray(persisted.getModelIds(), Long.class));
        List<SakuraAiBusinessModelBinding> selected = new ArrayList<>();
        for (Long id : ids) {
            List<ModelCapabilityDefinition> definitions = capabilities.selectList(Wrappers.<SakuraAiModelCapability>lambdaQuery()
                    .eq(SakuraAiModelCapability::getModelId, id)).stream()
                    .map(row -> JSON.parseObject(row.getDefinitionJson(), ModelCapabilityDefinition.class)).toList();
            if (definitions.isEmpty()) continue;
            List<ModelCapabilityDefinition> candidates = definitions.stream()
                    .filter(d -> Boolean.TRUE.equals(d.getEnabled())).toList();
            if (candidates.isEmpty()) {
                fail("模型【" + modelLabel(id) + "】与模型池【" + poolLabel(function) + "】没有兼容能力");
            }

            List<SakuraAiBusinessModelBinding> rows;
            ModelPoolCapabilitySelection requested = requestedSelections == null ? null : requestedSelections.get(id);
            if (requested != null) {
                rows = requestedRows(id, requested, candidates, function);
            } else {
                // Only retained members may reuse their previous selection. An orphan row is never a new binding.
                List<SakuraAiBusinessModelBinding> source = function.getModelBindings() == null
                        ? (existingIds.contains(id) ? previous : List.of()) : function.getModelBindings();
                rows = source.stream()
                        .filter(row -> Objects.equals(row.getModelId(), id)).toList();
                if (rows.isEmpty() && function.getModelBindings() == null) {
                    List<ModelCapabilityDefinition> defaults = candidates.stream()
                            .filter(d -> Boolean.TRUE.equals(d.getDefaultCapability())).toList();
                    if (candidates.size() == 1) {
                        rows = List.of(binding(id, candidates.get(0).getCode(), true));
                    } else if (defaults.size() == 1) {
                        rows = List.of(binding(id, defaults.get(0).getCode(), true));
                    }
                }
            }
            if (rows.isEmpty() || rows.stream().filter(row -> Boolean.TRUE.equals(row.getDefaultCapability())).count() != 1) {
                fail("模型【" + modelLabel(id) + "】在模型池【" + poolLabel(function) + "】需要选择默认能力");
            }
            Set<String> codes = new LinkedHashSet<>();
            for (SakuraAiBusinessModelBinding row : rows) {
                ModelCapabilityDefinition definition = candidates.stream()
                        .filter(d -> Objects.equals(d.getCode(), row.getCapabilityCode())).findFirst().orElse(null);
                if (definition == null || !codes.add(row.getCapabilityCode())) {
                    fail("模型【" + modelLabel(id) + "】在模型池【" + poolLabel(function) + "】的能力不可用或重复");
                }
                boolean historicalSelection = existingIds.contains(id) && previous.stream().anyMatch(old ->
                        Objects.equals(old.getModelId(), id)
                                && Objects.equals(old.getCapabilityCode(), row.getCapabilityCode()));
                if (StoryboardVideoPoolCapabilityPolicy.rejects(function.getFuncCode(), definition.getGenerateMode())
                        && !historicalSelection) {
                    fail("多参模型池不能绑定首尾帧能力，请使用首尾帧模型池");
                }
                if (row.getDefaultsJson() != null && !row.getDefaultsJson().isBlank())
                    ModelParameterValidator.validateBusinessDefaults(definition, JSON.parseObject(row.getDefaultsJson()));
                SakuraAiBusinessModelBinding copy = new SakuraAiBusinessModelBinding();
                copy.setModelId(id); copy.setFuncCode(function.getFuncCode()); copy.setCapabilityCode(row.getCapabilityCode());
                copy.setDefaultCapability(row.getDefaultCapability()); copy.setDefaultsJson(row.getDefaultsJson());
                copy.setSortOrder(selected.size()); copy.setCreateBy(actor); copy.setCreateTime(DateUtils.getNowDate()); selected.add(copy);
            }
        }
        bindings.delete(Wrappers.<SakuraAiBusinessModelBinding>lambdaQuery().eq(SakuraAiBusinessModelBinding::getFuncCode, function.getFuncCode()));
        selected.forEach(bindings::insert);
    }

    /** 移出模型池时也清理已有的孤立能力绑定，保证下次绑定从空关系开始。 */
    @Transactional(rollbackFor = Exception.class)
    public void removeForModels(String funcCode, List<Long> modelIds) {
        if (funcCode == null || modelIds == null || modelIds.isEmpty()) return;
        bindings.delete(Wrappers.<SakuraAiBusinessModelBinding>lambdaQuery()
                .eq(SakuraAiBusinessModelBinding::getFuncCode, funcCode)
                .in(SakuraAiBusinessModelBinding::getModelId, modelIds));
    }

    private List<SakuraAiBusinessModelBinding> requestedRows(Long modelId, ModelPoolCapabilitySelection requested,
            List<ModelCapabilityDefinition> candidates, SakuraAiModelFuncConfig function) {
        Set<String> allowed = candidates.stream().map(ModelCapabilityDefinition::getCode)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> codes = requested.getCapabilityCodes() == null ? Collections.emptySet()
                : requested.getCapabilityCodes().stream().filter(Objects::nonNull).map(String::trim)
                        .filter(code -> !code.isBlank()).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        String defaultCode = requested.getDefaultCapabilityCode() == null ? null : requested.getDefaultCapabilityCode().trim();
        if (codes.isEmpty() || defaultCode == null || defaultCode.isBlank() || !codes.contains(defaultCode)) {
            fail("模型【" + modelLabel(modelId) + "】在模型池【" + poolLabel(function) + "】需要选择默认能力");
        }
        if (!allowed.containsAll(codes)) {
            fail("模型【" + modelLabel(modelId) + "】与模型池【" + poolLabel(function) + "】没有兼容能力");
        }
        if (candidates.stream().anyMatch(d -> codes.contains(d.getCode())
                && StoryboardVideoPoolCapabilityPolicy.rejects(function.getFuncCode(), d.getGenerateMode()))) {
            fail("多参模型池不能绑定首尾帧能力，请使用首尾帧模型池");
        }
        return codes.stream().map(code -> binding(modelId, code, Objects.equals(code, defaultCode))).toList();
    }

    private SakuraAiBusinessModelBinding binding(Long modelId, String capabilityCode, boolean defaultCapability) {
        SakuraAiBusinessModelBinding row = new SakuraAiBusinessModelBinding();
        row.setModelId(modelId);
        row.setCapabilityCode(capabilityCode);
        row.setDefaultCapability(defaultCapability);
        return row;
    }

    private String modelLabel(Long modelId) {
        SakuraAiModel model = models.getById(modelId);
        if (model == null) return "#" + modelId;
        if (model.getModelName() != null && !model.getModelName().isBlank()) return model.getModelName();
        if (model.getModelCode() != null && !model.getModelCode().isBlank()) return model.getModelCode();
        return "#" + modelId;
    }

    private String poolLabel(SakuraAiModelFuncConfig function) {
        if (function.getFuncName() != null && !function.getFuncName().isBlank()) return function.getFuncName();
        return function.getFuncCode();
    }

    private static void fail(String message) { log.info("业务模型绑定失败: {}", message); throw new ServiceException(message); }
}
