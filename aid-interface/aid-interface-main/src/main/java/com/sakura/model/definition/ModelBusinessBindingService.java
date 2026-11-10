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
import com.sakura.orchestration.IAiOrchestrationService;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 维护业务功能对模型能力的绑定。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelBusinessBindingService {
    private static final Set<String> INPUT_ADAPTIVE_IMAGE_FUNCTIONS = Set.of(
            "main_character_image", "main_scene_image", "main_prop_image",
            "main_storyboard_image", "image_edit");

    private final SakuraAiBusinessModelBindingMapper bindings;
    private final SakuraAiModelCapabilityMapper capabilities;
    private final ISakuraAiModelService models;
    private final ISakuraAiModelFuncConfigService functions;
    private final IAiOrchestrationService orchestration;
    private final ModelFunctionBindingReconciler reconciler;

    public List<SakuraAiBusinessModelBinding> forModel(Long modelId) {
        return bindings.selectList(Wrappers.<SakuraAiBusinessModelBinding>lambdaQuery()
                .eq(SakuraAiBusinessModelBinding::getModelId, modelId).orderByAsc(SakuraAiBusinessModelBinding::getSortOrder));
    }

    public List<SakuraAiBusinessModelBinding> forFunction(String funcCode) {
        return bindings.selectList(Wrappers.<SakuraAiBusinessModelBinding>lambdaQuery()
                .eq(SakuraAiBusinessModelBinding::getFuncCode, funcCode).orderByAsc(SakuraAiBusinessModelBinding::getSortOrder));
    }

    @Transactional(rollbackFor = Exception.class)
    public int saveFunction(SakuraAiModelFuncConfig function, boolean creating, String actor) {
        if (!creating) {
            var current = functions.getOne(Wrappers.<SakuraAiModelFuncConfig>lambdaQuery()
                    .eq(SakuraAiModelFuncConfig::getId, function.getId()).eq(SakuraAiModelFuncConfig::getDelFlag, "0").last("FOR UPDATE"));
            if (current == null) fail("业务功能不存在");
            if (!Objects.equals(current.getFuncCode(), function.getFuncCode())) fail("业务功能编码不可修改");
        }
        orchestration.prepareFunctionUpdate(function, actor);
        reconciler.reconcile(function, actor);
        function.setUpdateBy(actor);
        return creating ? functions.insertSakuraAiModelFuncConfig(function) : functions.updateSakuraAiModelFuncConfig(function);
    }

    @Transactional(rollbackFor = Exception.class)
    public int deleteFunctions(Long[] ids) {
        if (ids == null || ids.length == 0) return 0;
        var selected = functions.list(Wrappers.<SakuraAiModelFuncConfig>lambdaQuery()
                .in(SakuraAiModelFuncConfig::getId, List.of(ids)).eq(SakuraAiModelFuncConfig::getDelFlag, "0")
                .orderByAsc(SakuraAiModelFuncConfig::getId).last("FOR UPDATE"));
        orchestration.validateFunctionConfigsRemovable(ids);
        int result = functions.deleteSakuraAiModelFuncConfigByIds(ids);
        if (result > 0 && !selected.isEmpty()) bindings.delete(Wrappers.<SakuraAiBusinessModelBinding>lambdaQuery()
                .in(SakuraAiBusinessModelBinding::getFuncCode, selected.stream().map(SakuraAiModelFuncConfig::getFuncCode).toList()));
        return result;
    }

    public List<SakuraAiBusinessModelBinding> legacyBindings(SakuraAiModel model) {
        List<SakuraAiBusinessModelBinding> result = new ArrayList<>();
        Set<String> availableCapabilities = LegacyModelDefinitionConverter.convert(model).stream()
                .filter(definition -> Boolean.TRUE.equals(definition.getEnabled()))
                .map(ModelCapabilityDefinition::getCode)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        for (SakuraAiModelFuncConfig function : functions.list(Wrappers.<SakuraAiModelFuncConfig>lambdaQuery().eq(SakuraAiModelFuncConfig::getDelFlag, "0"))) {
            if (function.getModelIds() == null || !JSON.parseArray(function.getModelIds(), Long.class).contains(model.getId())) continue;
            String defaultCode = LegacyModelDefinitionConverter.businessCode(
                    model, function.getFuncCode(), function.getGenerateMode());
            if (INPUT_ADAPTIVE_IMAGE_FUNCTIONS.contains(function.getFuncCode())
                    && availableCapabilities.contains("text_to_image")
                    && availableCapabilities.contains("image_to_image")) {
                String adaptiveDefault = Set.of("text_to_image", "image_to_image").contains(defaultCode)
                        ? defaultCode : "image_edit".equals(function.getFuncCode())
                                ? "image_to_image" : "text_to_image";
                result.add(legacyBinding(model.getId(), function.getFuncCode(), "text_to_image",
                        "text_to_image".equals(adaptiveDefault)));
                result.add(legacyBinding(model.getId(), function.getFuncCode(), "image_to_image",
                        "image_to_image".equals(adaptiveDefault)));
            } else {
                result.add(legacyBinding(model.getId(), function.getFuncCode(), defaultCode, true));
            }
        }
        return result;
    }

    private SakuraAiBusinessModelBinding legacyBinding(Long modelId, String funcCode,
            String capabilityCode, boolean defaultCapability) {
        SakuraAiBusinessModelBinding row = new SakuraAiBusinessModelBinding();
        row.setModelId(modelId);
        row.setFuncCode(funcCode);
        row.setCapabilityCode(capabilityCode);
        row.setDefaultCapability(defaultCapability);
        return row;
    }

    public String capability(Long modelId, String funcCode, String requested) {
        SakuraAiModelFuncConfig function = functions.getOne(Wrappers.<SakuraAiModelFuncConfig>lambdaQuery()
                .eq(SakuraAiModelFuncConfig::getFuncCode, funcCode).eq(SakuraAiModelFuncConfig::getDelFlag, "0").eq(SakuraAiModelFuncConfig::getStatus, "0"));
        if (function == null || function.getModelIds() == null || !JSON.parseArray(function.getModelIds(), Long.class).contains(modelId)) fail("模型不在业务池中");
        List<SakuraAiBusinessModelBinding> permitted = forFunction(funcCode).stream()
                .filter(b -> Objects.equals(b.getModelId(), modelId)).toList();
        if (permitted.isEmpty()) {
            if (capabilities.selectCount(Wrappers.<SakuraAiModelCapability>lambdaQuery().eq(SakuraAiModelCapability::getModelId, modelId)) > 0)
                fail("请先为业务绑定模型能力");
            if (requested != null && !requested.isBlank()) return requested;
            SakuraAiModel model = models.getById(modelId);
            if (model == null) fail("模型不存在");
            return LegacyModelDefinitionConverter.businessCode(
                    model, function.getFuncCode(), function.getGenerateMode());
        }
        if (requested != null && !requested.isBlank()) {
            if (permitted.stream().noneMatch(b -> requested.equals(b.getCapabilityCode()))) fail("业务未绑定此能力");
            return requireCompatibleCapability(modelId, funcCode, requested);
        }
        List<SakuraAiBusinessModelBinding> defaults = permitted.stream().filter(b -> Boolean.TRUE.equals(b.getDefaultCapability())).toList();
        if (defaults.size() != 1) fail("请明确调用能力");
        return requireCompatibleCapability(modelId, funcCode, defaults.get(0).getCapabilityCode());
    }

    private String requireCompatibleCapability(Long modelId, String funcCode, String capabilityCode) {
        if (!"main_storyboard_video".equals(funcCode)
                && !"main_storyboard_video_multi_pro".equals(funcCode)) return capabilityCode;
        SakuraAiModelCapability definition = capabilities.selectOne(Wrappers.<SakuraAiModelCapability>lambdaQuery()
                .eq(SakuraAiModelCapability::getModelId, modelId)
                .eq(SakuraAiModelCapability::getCapabilityCode, capabilityCode));
        if (definition != null && StoryboardVideoPoolCapabilityPolicy.rejects(
                funcCode, definition.getGenerateMode())) {
            fail("多参模型池绑定了首尾帧能力，请重新绑定正确的视频能力");
        }
        return capabilityCode;
    }

    @Transactional(rollbackFor = Exception.class)
    public void replaceForModel(Long modelId, List<SakuraAiBusinessModelBinding> requested, String actor) {
        if (requested == null) return;
        SakuraAiModel model = models.getById(modelId);
        List<SakuraAiBusinessModelBinding> existing = forModel(modelId);
        // A model edit returns its existing bindings verbatim.  Rewriting those rows would
        // unnecessarily revalidate every historical pool member (including deleted IDs).
        // A real binding change still follows the full pool and reference validation below.
        if (sameBindings(existing, requested)) return;
        Set<String> functionCodes = new LinkedHashSet<>();
        existing.forEach(b -> functionCodes.add(b.getFuncCode()));
        legacyBindings(model).forEach(b -> functionCodes.add(b.getFuncCode()));
        Set<String> unique = new LinkedHashSet<>();
        List<SakuraAiModelCapability> rows = capabilities.selectList(Wrappers.<SakuraAiModelCapability>lambdaQuery().eq(SakuraAiModelCapability::getModelId, modelId));
        Set<String> allowed = new LinkedHashSet<>();
        for (SakuraAiModelCapability row : rows) {
            ModelCapabilityDefinition d = JSON.parseObject(row.getDefinitionJson(), ModelCapabilityDefinition.class);
            if (Boolean.TRUE.equals(d.getEnabled())) allowed.add(d.getCode());
        }
        for (SakuraAiBusinessModelBinding binding : requested) {
            if (binding == null || binding.getFuncCode() == null || !allowed.contains(binding.getCapabilityCode())) fail("业务能力绑定无效");
            SakuraAiModelCapability chosen = rows.stream()
                    .filter(row -> Objects.equals(row.getCapabilityCode(), binding.getCapabilityCode()))
                    .findFirst().orElse(null);
            if (chosen != null && StoryboardVideoPoolCapabilityPolicy.rejects(
                    binding.getFuncCode(), chosen.getGenerateMode())) {
                fail("多参模型池不能绑定首尾帧能力，请使用首尾帧模型池");
            }
            if (!unique.add(binding.getFuncCode() + "/" + binding.getCapabilityCode())) fail("业务能力绑定重复");
            functionCodes.add(binding.getFuncCode());
            if (binding.getDefaultsJson() != null && !binding.getDefaultsJson().isBlank()) {
                var definition = rows.stream().filter(row -> Objects.equals(row.getCapabilityCode(), binding.getCapabilityCode()))
                        .map(row -> JSON.parseObject(row.getDefinitionJson(), ModelCapabilityDefinition.class)).findFirst().orElseThrow();
                ModelParameterValidator.validateBusinessDefaults(definition, JSON.parseObject(binding.getDefaultsJson()));
            }
        }
        for (String code : functionCodes.stream().sorted().toList()) {
            SakuraAiModelFuncConfig function = functions.getOne(Wrappers.<SakuraAiModelFuncConfig>lambdaQuery()
                    .eq(SakuraAiModelFuncConfig::getFuncCode, code).eq(SakuraAiModelFuncConfig::getDelFlag, "0").last("FOR UPDATE"));
            if (function == null || !Objects.equals(function.getModelType(), model.getModelType())) fail("业务模型类型不符");
            List<Long> ids = function.getModelIds() == null ? new ArrayList<>() : new ArrayList<>(JSON.parseArray(function.getModelIds(), Long.class));
            boolean selected = requested.stream().anyMatch(b -> code.equals(b.getFuncCode()));
            if (selected && requested.stream().filter(b -> code.equals(b.getFuncCode()) && Boolean.TRUE.equals(b.getDefaultCapability())).count() != 1) fail("请选择业务默认能力");
            boolean membershipChanged = selected ? !ids.contains(modelId) : ids.contains(modelId);
            if (selected && membershipChanged) ids.add(modelId);
            if (!selected && membershipChanged) ids.remove(modelId);
            if ("0".equals(function.getStatus()) && ids.isEmpty()) fail("业务至少绑定一模型");
            // 只修改同一模型在池内的能力选择时，模型池成员并未变化。历史池可能仍带有待治理的
            // 失效模型 ID，不应因为本次能力保存而重验、重写整个池；真正新增或移出成员时仍走
            // 完整的编排校验和引用迁移，不能借此绕过池约束。
            if (!membershipChanged) continue;
            function.setModelIds(JSON.toJSONString(ids));
            orchestration.prepareFunctionUpdate(function, actor);
            function.setUpdateBy(actor);
            function.setUpdateTime(DateUtils.getNowDate());
            functions.updateById(function);
        }
        bindings.delete(Wrappers.<SakuraAiBusinessModelBinding>lambdaQuery().eq(SakuraAiBusinessModelBinding::getModelId, modelId));
        int index = 0;
        for (SakuraAiBusinessModelBinding binding : requested) {
            SakuraAiBusinessModelBinding row = new SakuraAiBusinessModelBinding();
            row.setModelId(modelId); row.setFuncCode(binding.getFuncCode()); row.setCapabilityCode(binding.getCapabilityCode());
            row.setDefaultCapability(binding.getDefaultCapability());
            row.setDefaultsJson(binding.getDefaultsJson()); row.setSortOrder(index++);
            row.setCreateBy(actor); row.setCreateTime(DateUtils.getNowDate()); bindings.insert(row);
        }
    }

    private static boolean sameBindings(List<SakuraAiBusinessModelBinding> existing,
                                        List<SakuraAiBusinessModelBinding> requested) {
        if (existing.size() != requested.size()) return false;
        for (int index = 0; index < existing.size(); index++) {
            SakuraAiBusinessModelBinding left = existing.get(index);
            SakuraAiBusinessModelBinding right = requested.get(index);
            if (right == null || !Objects.equals(left.getFuncCode(), right.getFuncCode())
                    || !Objects.equals(left.getCapabilityCode(), right.getCapabilityCode())
                    || !Objects.equals(left.getDefaultCapability(), right.getDefaultCapability())
                    || !Objects.equals(left.getDefaultsJson(), right.getDefaultsJson())) return false;
        }
        return true;
    }

    private static void fail(String message) { log.info("业务模型绑定失败: {}", message); throw new ServiceException(message); }
}
