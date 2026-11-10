package com.sakura.sakura.controller;

import com.sakura.sakura.domain.SakuraEpisodeEditor;
import com.sakura.sakura.service.ISakuraEpisodeEditorService;
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
 * 剧集视频剪辑与成片最新状态Controller
 *
 * @author 视觉AID
 */
@RestController
@RequestMapping("/sakura/pisodeeditor")
public class SakuraEpisodeEditorController extends BaseController
{
    @Autowired
    private ISakuraEpisodeEditorService sakuraEpisodeEditorService;

    /**
     * 查询剧集视频剪辑与成片最新状态列表
     */
    @PreAuthorize("@ss.hasPermi('sakura:pisodeeditor:list')")
    @GetMapping("/list")
    public TableDataInfo list(SakuraEpisodeEditor sakuraEpisodeEditor)
    {
        startPage();
        List<SakuraEpisodeEditor> list = sakuraEpisodeEditorService.selectSakuraEpisodeEditorList(sakuraEpisodeEditor);
        return getDataTable(list);
    }

    /**
     * 导出剧集视频剪辑与成片最新状态列表
     */
    @PreAuthorize("@ss.hasPermi('sakura:pisodeeditor:export')")
    @Log(title = "剧集视频剪辑与成片最新状态", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, SakuraEpisodeEditor sakuraEpisodeEditor)
    {
        List<SakuraEpisodeEditor> list = sakuraEpisodeEditorService.selectSakuraEpisodeEditorList(sakuraEpisodeEditor);
        ExcelUtil<SakuraEpisodeEditor> util = new ExcelUtil<SakuraEpisodeEditor>(SakuraEpisodeEditor.class);
        util.exportExcel(response, list, "剧集视频剪辑与成片最新状态数据");
    }

    /**
     * 获取剧集视频剪辑与成片最新状态详细信息
     */
    @PreAuthorize("@ss.hasPermi('sakura:pisodeeditor:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return success(sakuraEpisodeEditorService.selectSakuraEpisodeEditorById(id));
    }

    /**
     * 新增剧集视频剪辑与成片最新状态
     */
    @PreAuthorize("@ss.hasPermi('sakura:pisodeeditor:add')")
    @Log(title = "剧集视频剪辑与成片最新状态", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody SakuraEpisodeEditor sakuraEpisodeEditor)
    {
        return toAjax(sakuraEpisodeEditorService.insertSakuraEpisodeEditor(sakuraEpisodeEditor));
    }

    /**
     * 修改剧集视频剪辑与成片最新状态
     */
    @PreAuthorize("@ss.hasPermi('sakura:pisodeeditor:edit')")
    @Log(title = "剧集视频剪辑与成片最新状态", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody SakuraEpisodeEditor sakuraEpisodeEditor)
    {
        return toAjax(sakuraEpisodeEditorService.updateSakuraEpisodeEditor(sakuraEpisodeEditor));
    }

    /**
     * 删除剧集视频剪辑与成片最新状态
     */
    @PreAuthorize("@ss.hasPermi('sakura:pisodeeditor:remove')")
    @Log(title = "剧集视频剪辑与成片最新状态", businessType = BusinessType.DELETE)
	@DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(sakuraEpisodeEditorService.deleteSakuraEpisodeEditorByIds(ids));
    }
}
