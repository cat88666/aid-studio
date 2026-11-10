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
import com.sakura.sakura.domain.SakuraStoryboard;
import com.sakura.sakura.service.ISakuraStoryboardService;
import com.sakura.common.utils.poi.ExcelUtil;
import com.sakura.common.core.page.TableDataInfo;

/**
 * 分镜时间轴主Controller
 *
 * @author 视觉AID
 */
@RestController
@RequestMapping("/sakura/storyboard")
public class SakuraStoryboardController extends BaseController
{
    @Autowired
    private ISakuraStoryboardService sakuraStoryboardService;

    /**
     * 查询分镜时间轴主列表
     */
    @PreAuthorize("@ss.hasPermi('sakura:storyboard:list')")
    @GetMapping("/list")
    public TableDataInfo list(SakuraStoryboard sakuraStoryboard)
    {
        startPage();
        List<SakuraStoryboard> list = sakuraStoryboardService.selectSakuraStoryboardList(sakuraStoryboard);
        return getDataTable(list);
    }

    /**
     * 导出分镜时间轴主列表
     */
    @PreAuthorize("@ss.hasPermi('sakura:storyboard:export')")
    @Log(title = "分镜时间轴主", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, SakuraStoryboard sakuraStoryboard)
    {
        List<SakuraStoryboard> list = sakuraStoryboardService.selectSakuraStoryboardList(sakuraStoryboard);
        ExcelUtil<SakuraStoryboard> util = new ExcelUtil<SakuraStoryboard>(SakuraStoryboard.class);
        util.exportExcel(response, list, "分镜时间轴主数据");
    }

    /**
     * 获取分镜时间轴主详细信息
     */
    @PreAuthorize("@ss.hasPermi('sakura:storyboard:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return success(sakuraStoryboardService.selectSakuraStoryboardById(id));
    }

    /**
     * 新增分镜时间轴主
     */
    @PreAuthorize("@ss.hasPermi('sakura:storyboard:add')")
    @Log(title = "分镜时间轴主", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody SakuraStoryboard sakuraStoryboard)
    {
        return toAjax(sakuraStoryboardService.insertSakuraStoryboard(sakuraStoryboard));
    }

    /**
     * 修改分镜时间轴主
     */
    @PreAuthorize("@ss.hasPermi('sakura:storyboard:edit')")
    @Log(title = "分镜时间轴主", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody SakuraStoryboard sakuraStoryboard)
    {
        return toAjax(sakuraStoryboardService.updateSakuraStoryboard(sakuraStoryboard));
    }

    /**
     * 删除分镜时间轴主
     */
    @PreAuthorize("@ss.hasPermi('sakura:storyboard:remove')")
    @Log(title = "分镜时间轴主", businessType = BusinessType.DELETE)
	@DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(sakuraStoryboardService.deleteSakuraStoryboardByIds(ids));
    }
}
