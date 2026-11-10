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
import com.sakura.sakura.domain.SakuraUserAiConfig;
import com.sakura.sakura.service.ISakuraUserAiConfigService;
import com.sakura.common.utils.poi.ExcelUtil;
import com.sakura.common.core.page.TableDataInfo;

/**
 * 用户自定义AI大模型配置(配置覆盖用)Controller
 *
 * @author 视觉AID
 */
@RestController
@RequestMapping("/sakura/diymodelconfig")
public class SakuraUserAiConfigController extends BaseController
{
    @Autowired
    private ISakuraUserAiConfigService sakuraUserAiConfigService;

    /**
     * 查询用户自定义AI大模型配置(配置覆盖用)列表
     */
    @PreAuthorize("@ss.hasPermi('sakura:diymodelconfig:list')")
    @GetMapping("/list")
    public TableDataInfo list(SakuraUserAiConfig sakuraUserAiConfig)
    {
        startPage();
        List<SakuraUserAiConfig> list = sakuraUserAiConfigService.selectSakuraUserAiConfigList(sakuraUserAiConfig);
        return getDataTable(list);
    }

    /**
     * 导出用户自定义AI大模型配置(配置覆盖用)列表
     */
    @PreAuthorize("@ss.hasPermi('sakura:diymodelconfig:export')")
    @Log(title = "用户自定义AI大模型配置(配置覆盖用)", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, SakuraUserAiConfig sakuraUserAiConfig)
    {
        List<SakuraUserAiConfig> list = sakuraUserAiConfigService.selectSakuraUserAiConfigList(sakuraUserAiConfig);
        ExcelUtil<SakuraUserAiConfig> util = new ExcelUtil<SakuraUserAiConfig>(SakuraUserAiConfig.class);
        util.exportExcel(response, list, "用户自定义AI大模型配置(配置覆盖用)数据");
    }

    /**
     * 获取用户自定义AI大模型配置(配置覆盖用)详细信息
     */
    @PreAuthorize("@ss.hasPermi('sakura:diymodelconfig:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return success(sakuraUserAiConfigService.selectSakuraUserAiConfigById(id));
    }

    /**
     * 新增用户自定义AI大模型配置(配置覆盖用)
     */
    @PreAuthorize("@ss.hasPermi('sakura:diymodelconfig:add')")
    @Log(title = "用户自定义AI大模型配置(配置覆盖用)", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody SakuraUserAiConfig sakuraUserAiConfig)
    {
        return toAjax(sakuraUserAiConfigService.insertSakuraUserAiConfig(sakuraUserAiConfig));
    }

    /**
     * 修改用户自定义AI大模型配置(配置覆盖用)
     */
    @PreAuthorize("@ss.hasPermi('sakura:diymodelconfig:edit')")
    @Log(title = "用户自定义AI大模型配置(配置覆盖用)", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody SakuraUserAiConfig sakuraUserAiConfig)
    {
        return toAjax(sakuraUserAiConfigService.updateSakuraUserAiConfig(sakuraUserAiConfig));
    }

    /**
     * 删除用户自定义AI大模型配置(配置覆盖用)
     */
    @PreAuthorize("@ss.hasPermi('sakura:diymodelconfig:remove')")
    @Log(title = "用户自定义AI大模型配置(配置覆盖用)", businessType = BusinessType.DELETE)
	@DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(sakuraUserAiConfigService.deleteSakuraUserAiConfigByIds(ids));
    }
}
