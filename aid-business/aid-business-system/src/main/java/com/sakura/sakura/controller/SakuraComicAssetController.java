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
import com.sakura.sakura.domain.SakuraComicAsset;
import com.sakura.sakura.service.ISakuraComicAssetService;
import com.sakura.common.utils.poi.ExcelUtil;
import com.sakura.common.core.page.TableDataInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 项目提取资产Controller
 *
 * @author 视觉AID
 */
@RestController
@RequestMapping("/sakura/sakuracomicasset")
@Tag(name = "后台官方素材管理")
public class SakuraComicAssetController extends BaseController
{
    @Autowired
    private ISakuraComicAssetService sakuraComicAssetService;

    /**
     * 查询项目提取资产列表
     */
    @PreAuthorize("@ss.hasPermi('sakura:sakuracomicasset:list')")
    @GetMapping("/list")
    @Operation(summary = "查询官方素材列表", description = "风格素材支持分类筛选，推荐优先、排序号升序、ID升序")
    public TableDataInfo list(SakuraComicAsset sakuraComicAsset)
    {
        startPage();
        List<SakuraComicAsset> list = sakuraComicAssetService.selectSakuraComicAssetList(sakuraComicAsset);
        return getDataTable(list);
    }

    /**
     * 导出项目提取资产列表
     */
    @PreAuthorize("@ss.hasPermi('sakura:sakuracomicasset:export')")
    @Log(title = "项目提取资产", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, SakuraComicAsset sakuraComicAsset)
    {
        List<SakuraComicAsset> list = sakuraComicAssetService.selectSakuraComicAssetList(sakuraComicAsset);
        ExcelUtil<SakuraComicAsset> util = new ExcelUtil<SakuraComicAsset>(SakuraComicAsset.class);
        util.exportExcel(response, list, "项目提取资产数据");
    }

    /**
     * 获取项目提取资产详细信息
     */
    @PreAuthorize("@ss.hasPermi('sakura:sakuracomicasset:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return success(sakuraComicAssetService.selectSakuraComicAssetById(id));
    }

    /**
     * 获取可维护的风格分类选项。
     */
    @PreAuthorize("@ss.hasPermi('sakura:sakuracomicasset:list')")
    @GetMapping("/style/categories")
    @Operation(summary = "查询风格分类选项")
    public AjaxResult styleCategories()
    {
        return success(sakuraComicAssetService.listStyleCategoryOptions(false));
    }

    /**
     * 新增项目提取资产
     */
    @PreAuthorize("@ss.hasPermi('sakura:sakuracomicasset:add')")
    @Log(title = "项目提取资产", businessType = BusinessType.INSERT,
            excludeParamNames = {"hiddenStylePromptJson"})
    @PostMapping
    public AjaxResult add(@RequestBody SakuraComicAsset sakuraComicAsset)
    {
        sakuraComicAsset.setCreateBy(getUsername());
        return toAjax(sakuraComicAssetService.insertSakuraComicAsset(sakuraComicAsset));
    }

    /**
     * 修改项目提取资产
     */
    @PreAuthorize("@ss.hasPermi('sakura:sakuracomicasset:edit')")
    @Log(title = "项目提取资产", businessType = BusinessType.UPDATE,
            excludeParamNames = {"hiddenStylePromptJson"})
    @PutMapping
    public AjaxResult edit(@RequestBody SakuraComicAsset sakuraComicAsset)
    {
        sakuraComicAsset.setUpdateBy(getUsername());
        return toAjax(sakuraComicAssetService.updateSakuraComicAsset(sakuraComicAsset));
    }

    /**
     * 删除项目提取资产
     */
    @PreAuthorize("@ss.hasPermi('sakura:sakuracomicasset:remove')")
    @Log(title = "项目提取资产", businessType = BusinessType.DELETE)
	@DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(sakuraComicAssetService.deleteSakuraComicAssetByIds(ids));
    }
}
