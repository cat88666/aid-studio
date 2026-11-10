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
import com.sakura.sakura.domain.SakuraComicEpisode;
import com.sakura.sakura.service.ISakuraComicEpisodeService;
import com.sakura.common.utils.poi.ExcelUtil;
import com.sakura.common.core.page.TableDataInfo;

/**
 * 剧集信息Controller
 *
 * @author 视觉AID
 */
@RestController
@RequestMapping("/sakura/sakuracomicepisode")
public class SakuraComicEpisodeController extends BaseController
{
    @Autowired
    private ISakuraComicEpisodeService sakuraComicEpisodeService;

    /**
     * 查询剧集信息列表
     */
    @PreAuthorize("@ss.hasPermi('sakura:sakuracomicepisode:list')")
    @GetMapping("/list")
    public TableDataInfo list(SakuraComicEpisode sakuraComicEpisode)
    {
        startPage();
        List<SakuraComicEpisode> list = sakuraComicEpisodeService.selectSakuraComicEpisodeList(sakuraComicEpisode);
        return getDataTable(list);
    }

    /**
     * 导出剧集信息列表
     */
    @PreAuthorize("@ss.hasPermi('sakura:sakuracomicepisode:export')")
    @Log(title = "剧集信息", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, SakuraComicEpisode sakuraComicEpisode)
    {
        List<SakuraComicEpisode> list = sakuraComicEpisodeService.selectSakuraComicEpisodeList(sakuraComicEpisode);
        ExcelUtil<SakuraComicEpisode> util = new ExcelUtil<SakuraComicEpisode>(SakuraComicEpisode.class);
        util.exportExcel(response, list, "剧集信息数据");
    }

    /**
     * 获取剧集信息详细信息
     */
    @PreAuthorize("@ss.hasPermi('sakura:sakuracomicepisode:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return success(sakuraComicEpisodeService.selectSakuraComicEpisodeById(id));
    }

    /**
     * 新增剧集信息
     */
    @PreAuthorize("@ss.hasPermi('sakura:sakuracomicepisode:add')")
    @Log(title = "剧集信息", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody SakuraComicEpisode sakuraComicEpisode)
    {
        return toAjax(sakuraComicEpisodeService.insertSakuraComicEpisode(sakuraComicEpisode));
    }

    /**
     * 修改剧集信息
     */
    @PreAuthorize("@ss.hasPermi('sakura:sakuracomicepisode:edit')")
    @Log(title = "剧集信息", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody SakuraComicEpisode sakuraComicEpisode)
    {
        return toAjax(sakuraComicEpisodeService.updateSakuraComicEpisode(sakuraComicEpisode));
    }

    /**
     * 删除剧集信息
     */
    @PreAuthorize("@ss.hasPermi('sakura:sakuracomicepisode:remove')")
    @Log(title = "剧集信息", businessType = BusinessType.DELETE)
	@DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(sakuraComicEpisodeService.deleteSakuraComicEpisodeByIds(ids));
    }
}
