package com.sakura.sakura.controller;

import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.sakura.common.annotation.Log;
import com.sakura.common.core.controller.BaseController;
import com.sakura.common.core.domain.AjaxResult;
import com.sakura.common.enums.BusinessType;
import com.sakura.sakura.domain.SakuraAiModel;
import com.sakura.sakura.domain.SakuraAiProvider;
import com.sakura.sakura.controller.support.AiConfigJsonValidator;
import com.sakura.sakura.service.ISakuraAiModelService;
import com.sakura.sakura.service.ISakuraAiProviderService;
import com.sakura.media.service.ConcurrencyConfigValidator;
import com.sakura.media.provider.VideoProviderClient;
import com.sakura.model.service.IAiModelBusinessService;
import com.sakura.common.utils.poi.ExcelUtil;
import com.sakura.common.core.page.TableDataInfo;
import com.sakura.common.utils.SecurityUtils;
import com.sakura.orchestration.IAiOrchestrationService;
import com.sakura.orchestration.dto.ModelPoolBindingChangeRequest;
import com.sakura.orchestration.dto.ModelPoolBindingQueryRequest;

/**
 * AI底层模型配置与算力计费Controller
 *
 * @author 视觉AID
 */
@RestController
@RequestMapping("/sakura/sakuramodel")
public class SakuraAiModelController extends BaseController
{
    @Autowired
    private ISakuraAiModelService sakuraAiModelService;

    @Autowired
    private List<VideoProviderClient> videoProviderClients;

    @Autowired
    private com.sakura.newapi.NewApiService newApiService;

    @Autowired
    private com.sakura.model.definition.ModelDefinitionService modelDefinitions;

    @Autowired
    private com.sakura.model.definition.ModelProtocolRegistry modelProtocols;

    @Autowired
    private com.sakura.model.definition.ModelMigrationService modelMigration;

    @Autowired
    private com.sakura.model.definition.ModelMigrationJournal migrationJournal;

    @PreAuthorize("@ss.hasPermi('sakura:sakuramodel:edit') and @ss.hasPermi('sakura:funcconfig:edit')")
    @GetMapping("/migrations")
    public AjaxResult migrations() { return success(migrationJournal.recent()); }

    @PreAuthorize("@ss.hasPermi('sakura:sakuramodel:edit') and @ss.hasPermi('sakura:funcconfig:edit')")
    @PostMapping("/migrations/{id}/rollback")
    @Log(title = "恢复模型迁移配置", businessType = BusinessType.UPDATE)
    public AjaxResult rollbackMigration(@PathVariable Long id) {
        return toAjax(migrationJournal.rollback(id, SecurityUtils.getUsername()));
    }

    @PreAuthorize("@ss.hasPermi('sakura:sakuramodel:edit')")
    @GetMapping("/migration-preview")
    @io.swagger.v3.oas.annotations.Operation(summary = "预检模型身份、能力与配置冲突")
    public AjaxResult migrationPreview() {
        return success(modelMigration.preview());
    }

    @PreAuthorize("@ss.hasPermi('sakura:sakuramodel:edit') and @ss.hasPermi('sakura:funcconfig:edit')")
    @PostMapping("/migrate")
    @Log(title = "合并模型能力", businessType = BusinessType.UPDATE)
    @io.swagger.v3.oas.annotations.Operation(summary = "按已核验的映射迁移模型", description = "校验所有源记录版本及能力覆盖，在同一事务内更新模型、旧标识和业务绑定。")
    public AjaxResult migrate(@RequestBody com.sakura.model.definition.ModelMigrationService.MigrationRequest request) {
        return toAjax(modelMigration.apply(request, SecurityUtils.getUsername()));
    }

    @PreAuthorize("@ss.hasPermi('sakura:sakuramodel:list')")
    @GetMapping("/protocol-options")
    @io.swagger.v3.oas.annotations.Operation(summary = "查询模型类型支持的调用协议")
    public AjaxResult protocolOptions(String modelType) {
        return success(modelProtocols.options(modelType));
    }

    @lombok.Data
    public static class ParameterPreviewRequest {
        private com.sakura.sakura.domain.model.ModelCapabilityDefinition definition;
        private java.util.Map<String, Object> parameters;
    }

    @PreAuthorize("@ss.hasAnyPermi('sakura:sakuramodel:add,sakura:sakuramodel:edit')")
    @PostMapping("/parameter-preview")
    @io.swagger.v3.oas.annotations.Operation(summary = "校验模型参数表单", description = "仅运行参数约束，不提交生成任务或产生费用。")
    public AjaxResult parameterPreview(@RequestBody ParameterPreviewRequest request) {
        return success(modelDefinitions.preview(request.getDefinition(), request.getParameters()));
    }

    /** 参考音频等能力位归属按服务商编码判定，写入前需按 provider_id 反查 provider_code。 */
    @Autowired
    private ISakuraAiProviderService sakuraAiProviderService;

    @Autowired
    private com.sakura.model.service.ModelSkuCoverageService skuCoverageService;

    @PreAuthorize("@ss.hasPermi('sakura:sakuramodel:edit')")
    @PostMapping("/sku-coverage")
    @io.swagger.v3.oas.annotations.Operation(summary = "检查模型能力与SKU覆盖", description = "只读检查当前草稿的有限计费组合，返回缺价组合、条件冲突与待核验项；不创建价格或任务。")
    public AjaxResult skuCoverage(@RequestBody SakuraAiModel draft) {
        SakuraAiModel effective = new SakuraAiModel();
        if (draft.getId() != null) {
            SakuraAiModel current = sakuraAiModelService.selectSakuraAiModelById(draft.getId());
            if (current == null) throw new com.sakura.common.exception.ServiceException("模型不存在");
            cn.hutool.core.bean.BeanUtil.copyProperties(current, effective);
        }
        String priorCapability = effective.getCapabilityJson();
        String priorBilling = effective.getBillingRuleJson();
        cn.hutool.core.bean.BeanUtil.copyProperties(draft, effective,
                cn.hutool.core.bean.copier.CopyOptions.create().setIgnoreNullValue(true));
        effective.setCapabilityJson(com.sakura.sakura.service.support.ModelConfigurationMerge.merge(priorCapability, draft.getCapabilityJson()));
        effective.setBillingRuleJson(com.sakura.sakura.service.support.ModelConfigurationMerge.merge(priorBilling, draft.getBillingRuleJson()));
        return AjaxResult.success(skuCoverageService.inspect(effective));
    }

    /** 并发上限层级校验器（全局 ≥ 供应商 ≥ 模型） */
    @Autowired
    private ConcurrencyConfigValidator concurrencyConfigValidator;

    /**
     * 按 funcCode 查询可用模型池（供后台管理下拉使用）。
     *
     * @param funcCode 业务场景编码（= sceneCode = sakura_ai_model_func_config.func_code）
     * @return 该场景下可选模型列表（按 modelIds 配置顺序）
     */
    @Autowired
    private IAiModelBusinessService aiModelBusinessService;

    /** 跨模型池、智能体、矩阵和项目配置执行一致的引用校验。 */
    @Autowired
    private IAiOrchestrationService orchestrationService;

    @PreAuthorize("@ss.hasPermi('sakura:sakuramodel:list')")
    @GetMapping("/listByFunc")
    public AjaxResult listByFunc(String funcCode)
    {
        return success(aiModelBusinessService.listAvailableModelsByFuncCode(funcCode));
    }

    /**
     * 真实模型总览：按真实模型标识展示各供应商的模型及统一启停状态。
     *
     * @param keyword 搜索关键字（匹配真实模型名/展示码/展示名称，可空）
     * @return 真实模型分组列表
     */
    @PreAuthorize("@ss.hasPermi('sakura:sakuramodel:list')")
    @GetMapping("/realModelOverview")
    public AjaxResult realModelOverview(String keyword)
    {
        return success(sakuraAiModelService.selectRealModelOverview(keyword));
    }

    @PreAuthorize("@ss.hasAnyPermi('sakura:funcconfig:query,sakura:funcconfig:list,sakura:funcconfig:edit,sakura:sakuramodel:list')")
    @PostMapping("/pool-bindings/query")
    @io.swagger.v3.oas.annotations.Operation(summary = "查询模型池绑定关系", description = "返回有效模型、模型池及当前有序绑定关系；模型列表为空时查询全部模型。")
    public AjaxResult getModelPoolBindings(@RequestBody(required = false) ModelPoolBindingQueryRequest request)
    {
        return success(orchestrationService.getModelPoolBindings(request));
    }

    @PreAuthorize("@ss.hasPermi('sakura:funcconfig:edit')")
    @Log(title = "模型批量绑定模型池", businessType = BusinessType.UPDATE)
    @PostMapping("/pool-bindings/bind")
    @io.swagger.v3.oas.annotations.Operation(summary = "批量绑定模型池", description = "按模型池事务更新有序模型关系；重复绑定保持幂等，不修改模型能力、SKU、价格或状态。")
    public AjaxResult bindModelsToPools(@RequestBody ModelPoolBindingChangeRequest request)
    {
        return success(orchestrationService.bindModelsToPools(request, SecurityUtils.getUsername()));
    }

    @PreAuthorize("@ss.hasPermi('sakura:funcconfig:edit')")
    @Log(title = "模型批量移出模型池", businessType = BusinessType.UPDATE)
    @PostMapping("/pool-bindings/unbind")
    @io.swagger.v3.oas.annotations.Operation(summary = "批量移出模型池", description = "按模型池事务移除关系；重复移除保持幂等，启用模型池不得被清空，活动引用仍受统一编排校验保护。")
    public AjaxResult unbindModelsFromPools(@RequestBody ModelPoolBindingChangeRequest request)
    {
        return success(orchestrationService.unbindModelsFromPools(request, SecurityUtils.getUsername()));
    }

    @Autowired
    private com.sakura.orchestration.ModelPoolRemovalPreviewService removalPreviewService;

    @PreAuthorize("@ss.hasPermi('sakura:funcconfig:edit')")
    @PostMapping("/pool-bindings/removal-preview")
    @io.swagger.v3.oas.annotations.Operation(summary = "预览移出模型的业务引用", description = "只读返回各业务池的智能体、矩阵和项目引用数量，以及保留在池中的可用替代文本模型；不调用模型，不修改配置。")
    public AjaxResult previewModelPoolRemoval(@RequestBody ModelPoolBindingChangeRequest request)
    {
        return success(removalPreviewService.preview(request));
    }

    /**
     * 查询AI底层模型配置与算力计费列表
     */
    @PreAuthorize("@ss.hasPermi('sakura:sakuramodel:list')")
    @GetMapping("/list")
    public TableDataInfo list(SakuraAiModel sakuraAiModel)
    {
        startPage();
        List<SakuraAiModel> list = sakuraAiModelService.selectSakuraAiModelList(sakuraAiModel);
        modelDefinitions.preload(list);
        return getDataTable(list);
    }

    /**
     * 导出AI底层模型配置与算力计费列表
     */
    @PreAuthorize("@ss.hasPermi('sakura:sakuramodel:export')")
    @Log(title = "AI底层模型配置与算力计费", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, SakuraAiModel sakuraAiModel)
    {
        List<SakuraAiModel> list = sakuraAiModelService.selectSakuraAiModelList(sakuraAiModel);
        ExcelUtil<SakuraAiModel> util = new ExcelUtil<SakuraAiModel>(SakuraAiModel.class);
        util.exportExcel(response, list, "AI底层模型配置与算力计费数据");
    }

    /**
     * 获取AI底层模型配置与算力计费详细信息
     */
    @PreAuthorize("@ss.hasPermi('sakura:sakuramodel:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return success(modelDefinitions.detail(id));
    }

    /**
     * 新增AI底层模型配置与算力计费
     */
    @PreAuthorize("@ss.hasPermi('sakura:sakuramodel:add')")
    @Log(title = "AI底层模型配置与算力计费", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody SakuraAiModel sakuraAiModel)
    {
        checkBusinessBindingPermission(sakuraAiModel);
        // 写入前统一校验所有 JSON 列（billing_rule_json / capability_json / 调度策略 等），
        // 避免非 JSON 字符串污染计费 / 调度 / 能力解析链路
        validateModelConfiguration(sakuraAiModel);
        // 并发上限层级校验：模型上限不得超过所属供应商与全局上限
        concurrencyConfigValidator.validateModelSave(sakuraAiModel);
        SakuraAiProvider provider = sakuraAiProviderService.selectSakuraAiProviderById(sakuraAiModel.getProviderId());
        if (provider != null && "NEW_API".equals(provider.getIntegrationType())) {
            newApiService.verifyManualModel(sakuraAiModel);
        }
        return toAjax(modelDefinitions.save(sakuraAiModel, true));
    }

    /**
     * 修改AI底层模型配置与算力计费
     */
    @PreAuthorize("@ss.hasPermi('sakura:sakuramodel:edit')")
    @Log(title = "AI底层模型配置与算力计费", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody SakuraAiModel sakuraAiModel)
    {
        checkBusinessBindingPermission(sakuraAiModel);
        if (sakuraAiModel.getConfigVersion() == null) {
            throw new com.sakura.common.exception.ServiceException("请刷新后保存配置");
        }
        SakuraAiModel current = sakuraAiModelService.selectSakuraAiModelById(sakuraAiModel.getId());
        if (current == null) {
            throw new com.sakura.common.exception.ServiceException("模型不存在");
        }
        // 校验合并后的完整配置，而非把局部更新误当整份配置；实际写入仍由服务层执行 CAS。
        SakuraAiModel effective = new SakuraAiModel();
        cn.hutool.core.bean.BeanUtil.copyProperties(current, effective);
        cn.hutool.core.bean.BeanUtil.copyProperties(sakuraAiModel, effective,
                cn.hutool.core.bean.copier.CopyOptions.create().setIgnoreNullValue(true));
        effective.setCapabilityJson(com.sakura.sakura.service.support.ModelConfigurationMerge.merge(
                current.getCapabilityJson(), sakuraAiModel.getCapabilityJson()));
        effective.setBillingRuleJson(com.sakura.sakura.service.support.ModelConfigurationMerge.merge(
                current.getBillingRuleJson(), sakuraAiModel.getBillingRuleJson()));
        if (sakuraAiModel.getModelCode() == null) sakuraAiModel.setModelCode(current.getModelCode());
        validateModelConfiguration(effective);
        // 并发上限层级校验：模型上限不得超过所属供应商与全局上限
        concurrencyConfigValidator.validateModelSave(effective);
        return toAjax(modelDefinitions.save(sakuraAiModel, false));
    }

    private void checkBusinessBindingPermission(SakuraAiModel model) {
        if (model.getBusinessBindings() != null && !SecurityUtils.hasPermi("sakura:funcconfig:edit")) {
            throw new com.sakura.common.exception.ServiceException("没有修改业务绑定的权限");
        }
    }

    /**
     * 反查模型所属服务商编码。
     * 能力位是否会被真正下发取决于 Provider 路由，而路由以 provider_code 为最高优先级，
     * 模型表只存 provider_id，故校验前必须补齐该值。
     *
     * @param model 待写入模型
     * @return 服务商编码；服务商不存在或未选择时返回 null
     */
    private void validateModelConfiguration(SakuraAiModel model)
    {
        String providerCode = resolveProviderCode(model);
        boolean referenceAudioDeliverable = videoProviderClients.stream().anyMatch(client ->
                client.supportsProviderCode(providerCode)
                        && client.supportsProtocol(model.getProtocol())
                        && client.supportsReferenceAudioInput());
        AiConfigJsonValidator.validate(model, providerCode, referenceAudioDeliverable);
    }

    private String resolveProviderCode(SakuraAiModel model)
    {
        if (model == null)
        {
            return null;
        }
        Long providerId = model.getProviderId();
        if (providerId == null && model.getId() != null)
        {
            // 编辑表单未回传服务商时按库中已存值判定，避免合法模型被误判成「服务商未实现下发」
            SakuraAiModel persisted = sakuraAiModelService.selectSakuraAiModelById(model.getId());
            providerId = persisted == null ? null : persisted.getProviderId();
        }
        if (providerId == null)
        {
            return null;
        }
        SakuraAiProvider provider = sakuraAiProviderService.selectSakuraAiProviderById(providerId);
        return provider == null ? null : provider.getProviderCode();
    }

    /**
     * 删除AI底层模型配置与算力计费
     */
    @PreAuthorize("@ss.hasPermi('sakura:sakuramodel:remove')")
    @Log(title = "AI底层模型配置与算力计费", businessType = BusinessType.DELETE)
	@DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(orchestrationService.deleteModelsIfUnreferenced(ids, SecurityUtils.getUsername()));
    }
}
