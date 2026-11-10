package com.sakura.sakura.controller;

import com.sakura.sakura.domain.SakuraAiModelFuncConfig;
import com.sakura.sakura.service.ISakuraAiModelFuncConfigService;
import com.sakura.common.annotation.Log;
import com.sakura.common.core.controller.BaseController;
import com.sakura.common.core.domain.AjaxResult;
import com.sakura.common.core.page.TableDataInfo;
import com.sakura.common.enums.BusinessType;
import com.sakura.common.utils.poi.ExcelUtil;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import com.sakura.orchestration.IAiOrchestrationService;

/**
 * AI模型功能配置Controller
 *
 * @author 视觉AID
 */
@RestController
@RequestMapping("/sakura/funcconfig")
public class SakuraAiModelFuncConfigController extends BaseController
{
    @Autowired
    private ISakuraAiModelFuncConfigService sakuraAiModelFuncConfigService;

    @Autowired
    private IAiOrchestrationService orchestrationService;

    @Autowired
    private com.sakura.model.definition.ModelBusinessBindingService modelBindings;

    /**
     * 查询AI模型功能配置列表
     */
    @PreAuthorize("@ss.hasAnyPermi('sakura:funcconfig:query,sakura:funcconfig:list')")
    @GetMapping("/list")
    public TableDataInfo list(SakuraAiModelFuncConfig sakuraAiModelFuncConfig)
    {
        startPage();
        List<SakuraAiModelFuncConfig> list = sakuraAiModelFuncConfigService.selectSakuraAiModelFuncConfigList(sakuraAiModelFuncConfig);
        return getDataTable(list);
    }

    /**
     * 导出AI模型功能配置列表
     */
    @PreAuthorize("@ss.hasPermi('sakura:funcconfig:export')")
    @Log(title = "AI模型功能配置", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, SakuraAiModelFuncConfig sakuraAiModelFuncConfig)
    {
        List<SakuraAiModelFuncConfig> list = sakuraAiModelFuncConfigService.selectSakuraAiModelFuncConfigList(sakuraAiModelFuncConfig);
        ExcelUtil<SakuraAiModelFuncConfig> util = new ExcelUtil<SakuraAiModelFuncConfig>(SakuraAiModelFuncConfig.class);
        util.exportExcel(response, list, "AI模型功能配置数据");
    }

    /**
     * 获取AI模型功能配置详细信息
     */
    @PreAuthorize("@ss.hasPermi('sakura:funcconfig:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        SakuraAiModelFuncConfig config = sakuraAiModelFuncConfigService.selectSakuraAiModelFuncConfigById(id);
        if (config != null) config.setModelBindings(modelBindings.forFunction(config.getFuncCode()));
        return success(config);
    }

    /**
     * 新增AI模型功能配置
     */
    @PreAuthorize("@ss.hasPermi('sakura:funcconfig:add')")
    @Log(title = "AI模型功能配置", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody SakuraAiModelFuncConfig sakuraAiModelFuncConfig)
    {
        return toAjax(modelBindings.saveFunction(sakuraAiModelFuncConfig, true, getUsername()));
    }

    /**
     * 修改AI模型功能配置
     */
    @PreAuthorize("@ss.hasPermi('sakura:funcconfig:edit')")
    @Log(title = "AI模型功能配置", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody SakuraAiModelFuncConfig sakuraAiModelFuncConfig)
    {
        return toAjax(modelBindings.saveFunction(sakuraAiModelFuncConfig, false, getUsername()));
    }

    /**
     * 删除AI模型功能配置
     */
    @PreAuthorize("@ss.hasPermi('sakura:funcconfig:remove')")
    @Log(title = "AI模型功能配置", businessType = BusinessType.DELETE)
	@DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(modelBindings.deleteFunctions(ids));
    }
}
