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
import com.sakura.sakura.domain.SakuraUserComicAsset;
import com.sakura.sakura.service.ISakuraUserComicAssetService;
import com.sakura.common.utils.poi.ExcelUtil;
import com.sakura.common.core.page.TableDataInfo;

/**
 * 用户自定义漫画参考资产Controller
 *
 * @author 视觉AID
 */
@RestController
@RequestMapping("/sakura/assetuser")
public class SakuraUserComicAssetController extends BaseController
{
    @Autowired
    private ISakuraUserComicAssetService sakuraUserComicAssetService;

    /**
     * 查询用户自定义漫画参考资产列表
     */
    @PreAuthorize("@ss.hasPermi('sakura:assetuser:list')")
    @GetMapping("/list")
    public TableDataInfo list(SakuraUserComicAsset sakuraUserComicAsset)
    {
        startPage();
        List<SakuraUserComicAsset> list = sakuraUserComicAssetService.selectSakuraUserComicAssetList(sakuraUserComicAsset);
        return getDataTable(list);
    }

    /**
     * 导出用户自定义漫画参考资产列表
     */
    @PreAuthorize("@ss.hasPermi('sakura:assetuser:export')")
    @Log(title = "用户自定义漫画参考资产", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, SakuraUserComicAsset sakuraUserComicAsset)
    {
        List<SakuraUserComicAsset> list = sakuraUserComicAssetService.selectSakuraUserComicAssetList(sakuraUserComicAsset);
        ExcelUtil<SakuraUserComicAsset> util = new ExcelUtil<SakuraUserComicAsset>(SakuraUserComicAsset.class);
        util.exportExcel(response, list, "用户自定义漫画参考资产数据");
    }

    /**
     * 获取用户自定义漫画参考资产详细信息
     */
    @PreAuthorize("@ss.hasPermi('sakura:assetuser:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return success(sakuraUserComicAssetService.selectSakuraUserComicAssetById(id));
    }

    /**
     * 新增用户自定义漫画参考资产
     */
    @PreAuthorize("@ss.hasPermi('sakura:assetuser:add')")
    @Log(title = "用户自定义漫画参考资产", businessType = BusinessType.INSERT,
            excludeParamNames = {"hiddenStylePromptJson"})
    @PostMapping
    public AjaxResult add(@RequestBody SakuraUserComicAsset sakuraUserComicAsset)
    {
        sakuraUserComicAsset.setCreateBy(getUsername());
        return toAjax(sakuraUserComicAssetService.insertSakuraUserComicAsset(sakuraUserComicAsset));
    }

    /**
     * 修改用户自定义漫画参考资产
     */
    @PreAuthorize("@ss.hasPermi('sakura:assetuser:edit')")
    @Log(title = "用户自定义漫画参考资产", businessType = BusinessType.UPDATE,
            excludeParamNames = {"hiddenStylePromptJson"})
    @PutMapping
    public AjaxResult edit(@RequestBody SakuraUserComicAsset sakuraUserComicAsset)
    {
        sakuraUserComicAsset.setUpdateBy(getUsername());
        return toAjax(sakuraUserComicAssetService.updateSakuraUserComicAsset(sakuraUserComicAsset));
    }

    /**
     * 删除用户自定义漫画参考资产
     */
    @PreAuthorize("@ss.hasPermi('sakura:assetuser:remove')")
    @Log(title = "用户自定义漫画参考资产", businessType = BusinessType.DELETE)
	@DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(sakuraUserComicAssetService.deleteSakuraUserComicAssetByIds(ids));
    }
}
