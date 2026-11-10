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
import com.sakura.sakura.domain.SakuraComicScript;
import com.sakura.sakura.service.ISakuraComicScriptService;
import com.sakura.common.utils.poi.ExcelUtil;
import com.sakura.common.core.page.TableDataInfo;

/**
 * 剧本原文与简化版Controller
 *
 * @author 视觉AID
 */
@RestController
@RequestMapping("/sakura/sakurascript")
public class SakuraComicScriptController extends BaseController
{
    @Autowired
    private ISakuraComicScriptService sakuraComicScriptService;

    /**
     * 查询剧本原文与简化版列表
     */
    @PreAuthorize("@ss.hasPermi('sakura:sakurascript:list')")
    @GetMapping("/list")
    public TableDataInfo list(SakuraComicScript sakuraComicScript)
    {
        startPage();
        List<SakuraComicScript> list = sakuraComicScriptService.selectSakuraComicScriptList(sakuraComicScript);
        return getDataTable(list);
    }

    /**
     * 导出剧本原文与简化版列表
     */
    @PreAuthorize("@ss.hasPermi('sakura:sakurascript:export')")
    @Log(title = "剧本原文与简化版", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, SakuraComicScript sakuraComicScript)
    {
        List<SakuraComicScript> list = sakuraComicScriptService.selectSakuraComicScriptList(sakuraComicScript);
        ExcelUtil<SakuraComicScript> util = new ExcelUtil<SakuraComicScript>(SakuraComicScript.class);
        util.exportExcel(response, list, "剧本原文与简化版数据");
    }

    /**
     * 获取剧本原文与简化版详细信息
     */
    @PreAuthorize("@ss.hasPermi('sakura:sakurascript:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return success(sakuraComicScriptService.selectSakuraComicScriptById(id));
    }

    /**
     * 新增剧本原文与简化版
     */
    @PreAuthorize("@ss.hasPermi('sakura:sakurascript:add')")
    @Log(title = "剧本原文与简化版", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody SakuraComicScript sakuraComicScript)
    {
        return toAjax(sakuraComicScriptService.insertSakuraComicScript(sakuraComicScript));
    }

    /**
     * 修改剧本原文与简化版
     */
    @PreAuthorize("@ss.hasPermi('sakura:sakurascript:edit')")
    @Log(title = "剧本原文与简化版", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody SakuraComicScript sakuraComicScript)
    {
        return toAjax(sakuraComicScriptService.updateSakuraComicScript(sakuraComicScript));
    }

    /**
     * 删除剧本原文与简化版
     */
    @PreAuthorize("@ss.hasPermi('sakura:sakurascript:remove')")
    @Log(title = "剧本原文与简化版", businessType = BusinessType.DELETE)
	@DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(sakuraComicScriptService.deleteSakuraComicScriptByIds(ids));
    }
}
