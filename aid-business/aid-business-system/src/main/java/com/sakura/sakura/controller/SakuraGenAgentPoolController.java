package com.sakura.sakura.controller;

import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.sakura.sakura.domain.SakuraGenAgentPool;
import com.sakura.sakura.service.ISakuraGenAgentPoolService;
import com.sakura.common.annotation.Log;
import com.sakura.common.core.controller.BaseController;
import com.sakura.common.core.domain.AjaxResult;
import com.sakura.common.core.page.TableDataInfo;
import com.sakura.common.enums.BusinessType;
import com.sakura.common.utils.SecurityUtils;
import com.sakura.common.utils.poi.ExcelUtil;
import com.sakura.projectgenconfig.matrix.IGenAgentPoolAdminService;
import com.sakura.projectgenconfig.matrix.dto.GenPoolSaveCellRequest;
import jakarta.validation.Valid;

/**
 * 生成链路智能体可选池Controller（后台管理）
 *
 * @author 视觉AID
 */
@RestController
@RequestMapping("/sakura/genagentpool")
public class SakuraGenAgentPoolController extends BaseController
{
    @Autowired
    private ISakuraGenAgentPoolService sakuraGenAgentPoolService;

    @Autowired
    private IGenAgentPoolAdminService genAgentPoolAdminService;
    /**
     * 矩阵列表（按 步骤×业务场景×创作模式×剧本类型 聚合成格子）。
     */
    @PreAuthorize("@ss.hasPermi('sakura:genagentpool:list')")
    @GetMapping("/matrix")
    public AjaxResult matrix(String step)
    {
        return AjaxResult.success(genAgentPoolAdminService.listMatrix(step));
    }

    /**
     * 某业务场景下可选的智能体、模型及模型的场景级清晰度/比例选项（供矩阵格子联动下拉）。
     */
    @PreAuthorize("@ss.hasPermi('sakura:genagentpool:list')")
    @GetMapping("/options")
    public AjaxResult options(String biz)
    {
        return AjaxResult.success(genAgentPoolAdminService.getOptions(biz));
    }

    /**
     * 覆盖式保存一个矩阵格子
     */
    @PreAuthorize("@ss.hasPermi('sakura:genagentpool:edit')")
    @Log(title = "智能体矩阵", businessType = BusinessType.UPDATE)
    @PostMapping("/matrix/save")
    public AjaxResult saveCell(@Valid @RequestBody GenPoolSaveCellRequest request)
    {
        genAgentPoolAdminService.saveCell(request, SecurityUtils.getUsername());
        return AjaxResult.success("保存成功");
    }

    /**
     * 删除一个矩阵格子（软删该组合全部行）
     */
    @PreAuthorize("@ss.hasPermi('sakura:genagentpool:remove')")
    @Log(title = "智能体矩阵", businessType = BusinessType.DELETE)
    @PostMapping("/matrix/delete")
    public AjaxResult deleteCell(@RequestBody GenPoolSaveCellRequest request)
    {
        genAgentPoolAdminService.deleteCell(request.getStep(), request.getBizCategoryCode(),
                request.getCreationMode(), request.getScriptType(), SecurityUtils.getUsername());
        return AjaxResult.success("删除成功");
    }
    /**
     * 查询智能体可选池列表
     */
    @PreAuthorize("@ss.hasPermi('sakura:genagentpool:list')")
    @GetMapping("/list")
    public TableDataInfo list(SakuraGenAgentPool sakuraGenAgentPool)
    {
        startPage();
        List<SakuraGenAgentPool> list = sakuraGenAgentPoolService.selectSakuraGenAgentPoolList(sakuraGenAgentPool);
        return getDataTable(list);
    }

    /**
     * 导出智能体可选池列表
     */
    @PreAuthorize("@ss.hasPermi('sakura:genagentpool:export')")
    @Log(title = "智能体可选池", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, SakuraGenAgentPool sakuraGenAgentPool)
    {
        List<SakuraGenAgentPool> list = sakuraGenAgentPoolService.selectSakuraGenAgentPoolList(sakuraGenAgentPool);
        ExcelUtil<SakuraGenAgentPool> util = new ExcelUtil<SakuraGenAgentPool>(SakuraGenAgentPool.class);
        util.exportExcel(response, list, "智能体可选池数据");
    }

    /**
     * 获取智能体可选池详细信息
     */
    @PreAuthorize("@ss.hasPermi('sakura:genagentpool:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return success(sakuraGenAgentPoolService.selectSakuraGenAgentPoolById(id));
    }

    /**
     * 新增智能体可选池
     */
    @PreAuthorize("@ss.hasPermi('sakura:genagentpool:add')")
    @Log(title = "智能体可选池", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody SakuraGenAgentPool sakuraGenAgentPool)
    {
        return toAjax(sakuraGenAgentPoolService.insertSakuraGenAgentPool(sakuraGenAgentPool));
    }

    /**
     * 修改智能体可选池
     */
    @PreAuthorize("@ss.hasPermi('sakura:genagentpool:edit')")
    @Log(title = "智能体可选池", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody SakuraGenAgentPool sakuraGenAgentPool)
    {
        return toAjax(sakuraGenAgentPoolService.updateSakuraGenAgentPool(sakuraGenAgentPool));
    }

    /**
     * 删除智能体可选池
     */
    @PreAuthorize("@ss.hasPermi('sakura:genagentpool:remove')")
    @Log(title = "智能体可选池", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(sakuraGenAgentPoolService.deleteSakuraGenAgentPoolByIds(ids));
    }
}
