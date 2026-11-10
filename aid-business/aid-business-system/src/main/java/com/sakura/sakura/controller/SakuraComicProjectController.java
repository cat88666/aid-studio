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
import com.sakura.sakura.domain.SakuraComicProject;
import com.sakura.sakura.service.ISakuraComicProjectService;
import com.sakura.common.utils.poi.ExcelUtil;
import com.sakura.common.core.page.TableDataInfo;

/**
 * 漫剧项目主Controller
 *
 * @author 视觉AID
 */
@RestController
@RequestMapping("/sakura/sakuraproject")
public class SakuraComicProjectController extends BaseController
{
    @Autowired
    private ISakuraComicProjectService sakuraComicProjectService;

    /**
     * 查询漫剧项目主列表
     */
    @PreAuthorize("@ss.hasPermi('sakura:sakuraproject:list')")
    @GetMapping("/list")
    public TableDataInfo list(SakuraComicProject sakuraComicProject)
    {
        startPage();
        List<SakuraComicProject> list = sakuraComicProjectService.selectSakuraComicProjectList(sakuraComicProject);
        return getDataTable(list);
    }

    /**
     * 导出漫剧项目主列表
     */
    @PreAuthorize("@ss.hasPermi('sakura:sakuraproject:export')")
    @Log(title = "漫剧项目主", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, SakuraComicProject sakuraComicProject)
    {
        List<SakuraComicProject> list = sakuraComicProjectService.selectSakuraComicProjectList(sakuraComicProject);
        ExcelUtil<SakuraComicProject> util = new ExcelUtil<SakuraComicProject>(SakuraComicProject.class);
        util.exportExcel(response, list, "漫剧项目主数据");
    }

    /**
     * 获取漫剧项目主详细信息
     */
    @PreAuthorize("@ss.hasPermi('sakura:sakuraproject:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return success(sakuraComicProjectService.selectSakuraComicProjectById(id));
    }

    /**
     * 新增漫剧项目主
     */
    @PreAuthorize("@ss.hasPermi('sakura:sakuraproject:add')")
    @Log(title = "漫剧项目主", businessType = BusinessType.INSERT,
            excludeParamNames = {"hiddenStylePromptJson"})
    @PostMapping
    public AjaxResult add(@RequestBody SakuraComicProject sakuraComicProject)
    {
        sakuraComicProject.setCreateBy(getUsername());
        return toAjax(sakuraComicProjectService.insertSakuraComicProject(sakuraComicProject));
    }

    /**
     * 修改漫剧项目主
     */
    @PreAuthorize("@ss.hasPermi('sakura:sakuraproject:edit')")
    @Log(title = "漫剧项目主", businessType = BusinessType.UPDATE,
            excludeParamNames = {"hiddenStylePromptJson"})
    @PutMapping
    public AjaxResult edit(@RequestBody SakuraComicProject sakuraComicProject)
    {
        sakuraComicProject.setUpdateBy(getUsername());
        return toAjax(sakuraComicProjectService.updateSakuraComicProject(sakuraComicProject));
    }

    /**
     * 删除漫剧项目主
     */
    @PreAuthorize("@ss.hasPermi('sakura:sakuraproject:remove')")
    @Log(title = "漫剧项目主", businessType = BusinessType.DELETE)
	@DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(sakuraComicProjectService.deleteSakuraComicProjectByIds(ids));
    }
}
