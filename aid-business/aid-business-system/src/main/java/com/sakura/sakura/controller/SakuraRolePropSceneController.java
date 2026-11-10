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
import com.sakura.sakura.domain.SakuraRolePropScene;
import com.sakura.sakura.service.ISakuraRolePropSceneService;
import com.sakura.common.utils.poi.ExcelUtil;
import com.sakura.common.core.page.TableDataInfo;

/**
 * 角色道具场景Controller
 *
 * @author 视觉AID
 */
@RestController
@RequestMapping("/sakura/scenecp")
public class SakuraRolePropSceneController extends BaseController
{
    @Autowired
    private ISakuraRolePropSceneService sakuraRolePropSceneService;

    /**
     * 查询角色道具场景列表
     */
    @PreAuthorize("@ss.hasPermi('sakura:scenecp:list')")
    @GetMapping("/list")
    public TableDataInfo list(SakuraRolePropScene sakuraRolePropScene)
    {
        startPage();
        List<SakuraRolePropScene> list = sakuraRolePropSceneService.selectSakuraRolePropSceneList(sakuraRolePropScene);
        return getDataTable(list);
    }

    /**
     * 导出角色道具场景列表
     */
    @PreAuthorize("@ss.hasPermi('sakura:scenecp:export')")
    @Log(title = "角色道具场景", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, SakuraRolePropScene sakuraRolePropScene)
    {
        List<SakuraRolePropScene> list = sakuraRolePropSceneService.selectSakuraRolePropSceneList(sakuraRolePropScene);
        ExcelUtil<SakuraRolePropScene> util = new ExcelUtil<SakuraRolePropScene>(SakuraRolePropScene.class);
        util.exportExcel(response, list, "角色道具场景数据");
    }

    /**
     * 获取角色道具场景详细信息
     */
    @PreAuthorize("@ss.hasPermi('sakura:scenecp:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return success(sakuraRolePropSceneService.selectSakuraRolePropSceneById(id));
    }

    /**
     * 新增角色道具场景
     */
    @PreAuthorize("@ss.hasPermi('sakura:scenecp:add')")
    @Log(title = "角色道具场景", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody SakuraRolePropScene sakuraRolePropScene)
    {
        return toAjax(sakuraRolePropSceneService.insertSakuraRolePropScene(sakuraRolePropScene));
    }

    /**
     * 修改角色道具场景
     */
    @PreAuthorize("@ss.hasPermi('sakura:scenecp:edit')")
    @Log(title = "角色道具场景", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody SakuraRolePropScene sakuraRolePropScene)
    {
        return toAjax(sakuraRolePropSceneService.updateSakuraRolePropScene(sakuraRolePropScene));
    }

    /**
     * 删除角色道具场景
     */
    @PreAuthorize("@ss.hasPermi('sakura:scenecp:remove')")
    @Log(title = "角色道具场景", businessType = BusinessType.DELETE)
	@DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(sakuraRolePropSceneService.deleteSakuraRolePropSceneByIds(ids));
    }
}
