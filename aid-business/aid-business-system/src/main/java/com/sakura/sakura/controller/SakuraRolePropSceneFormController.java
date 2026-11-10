package com.sakura.sakura.controller;

import com.sakura.sakura.domain.SakuraRolePropSceneForm;
import com.sakura.sakura.service.ISakuraRolePropSceneFormService;
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

/**
 * 角色道具场景形态(从)Controller
 *
 * @author 视觉AID
 */
@RestController
@RequestMapping("/sakura/rolepropsceneform")
public class SakuraRolePropSceneFormController extends BaseController
{
    @Autowired
    private ISakuraRolePropSceneFormService sakuraRolePropSceneFormService;

    /**
     * 查询角色道具场景形态(从)列表
     */
    @PreAuthorize("@ss.hasPermi('sakura:rolepropsceneform:list')")
    @GetMapping("/list")
    public TableDataInfo list(SakuraRolePropSceneForm sakuraRolePropSceneForm)
    {
        startPage();
        List<SakuraRolePropSceneForm> list = sakuraRolePropSceneFormService.selectSakuraRolePropSceneFormList(sakuraRolePropSceneForm);
        return getDataTable(list);
    }

    /**
     * 导出角色道具场景形态(从)列表
     */
    @PreAuthorize("@ss.hasPermi('sakura:rolepropsceneform:export')")
    @Log(title = "角色道具场景形态(从)", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, SakuraRolePropSceneForm sakuraRolePropSceneForm)
    {
        List<SakuraRolePropSceneForm> list = sakuraRolePropSceneFormService.selectSakuraRolePropSceneFormList(sakuraRolePropSceneForm);
        ExcelUtil<SakuraRolePropSceneForm> util = new ExcelUtil<SakuraRolePropSceneForm>(SakuraRolePropSceneForm.class);
        util.exportExcel(response, list, "角色道具场景形态(从)数据");
    }

    /**
     * 获取角色道具场景形态(从)详细信息
     */
    @PreAuthorize("@ss.hasPermi('sakura:rolepropsceneform:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return success(sakuraRolePropSceneFormService.selectSakuraRolePropSceneFormById(id));
    }

    /**
     * 新增角色道具场景形态(从)
     */
    @PreAuthorize("@ss.hasPermi('sakura:rolepropsceneform:add')")
    @Log(title = "角色道具场景形态(从)", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody SakuraRolePropSceneForm sakuraRolePropSceneForm)
    {
        return toAjax(sakuraRolePropSceneFormService.insertSakuraRolePropSceneForm(sakuraRolePropSceneForm));
    }

    /**
     * 修改角色道具场景形态(从)
     */
    @PreAuthorize("@ss.hasPermi('sakura:rolepropsceneform:edit')")
    @Log(title = "角色道具场景形态(从)", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody SakuraRolePropSceneForm sakuraRolePropSceneForm)
    {
        return toAjax(sakuraRolePropSceneFormService.updateSakuraRolePropSceneForm(sakuraRolePropSceneForm));
    }

    /**
     * 删除角色道具场景形态(从)
     */
    @PreAuthorize("@ss.hasPermi('sakura:rolepropsceneform:remove')")
    @Log(title = "角色道具场景形态(从)", businessType = BusinessType.DELETE)
	@DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(sakuraRolePropSceneFormService.deleteSakuraRolePropSceneFormByIds(ids));
    }
}
