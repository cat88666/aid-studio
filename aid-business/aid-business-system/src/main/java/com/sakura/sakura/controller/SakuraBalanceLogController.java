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
import com.sakura.sakura.domain.SakuraBalanceLog;
import com.sakura.sakura.service.ISakuraBalanceLogService;
import com.sakura.common.utils.poi.ExcelUtil;
import com.sakura.common.core.page.TableDataInfo;

/**
 * 余额变动记录Controller
 *
 * @author 视觉AID
 */
@RestController
@RequestMapping("/sakura/balancelog")
public class SakuraBalanceLogController extends BaseController
{
    @Autowired
    private ISakuraBalanceLogService sakuraBalanceLogService;

    /**
     * 查询余额变动记录列表
     */
    @PreAuthorize("@ss.hasPermi('sakura:balancelog:list')")
    @GetMapping("/list")
    public TableDataInfo list(SakuraBalanceLog sakuraBalanceLog)
    {
        startPage();
        List<SakuraBalanceLog> list = sakuraBalanceLogService.selectSakuraBalanceLogList(sakuraBalanceLog);
        return getDataTable(list);
    }

    /**
     * 导出余额变动记录列表
     */
    @PreAuthorize("@ss.hasPermi('sakura:balancelog:export')")
    @Log(title = "余额变动记录", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, SakuraBalanceLog sakuraBalanceLog)
    {
        List<SakuraBalanceLog> list = sakuraBalanceLogService.selectSakuraBalanceLogList(sakuraBalanceLog);
        ExcelUtil<SakuraBalanceLog> util = new ExcelUtil<SakuraBalanceLog>(SakuraBalanceLog.class);
        util.exportExcel(response, list, "余额变动记录数据");
    }

    /**
     * 获取余额变动记录详细信息
     */
    @PreAuthorize("@ss.hasPermi('sakura:balancelog:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return success(sakuraBalanceLogService.selectSakuraBalanceLogById(id));
    }

    /**
     * 新增余额变动记录
     * 余额流水是审计账本，禁止任何 UI 层 CRUD，否则会破坏 AccountUpdateServiceImpl 的幂等判断。
     */
    @PreAuthorize("@ss.hasPermi('sakura:balancelog:add')")
    @Log(title = "余额变动记录", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody SakuraBalanceLog sakuraBalanceLog)
    {
        return AjaxResult.error(403, "余额流水为审计账本，禁止手动新增");
    }

    /**
     * 修改余额变动记录
     * 余额流水禁止手动修改。
     */
    @PreAuthorize("@ss.hasPermi('sakura:balancelog:edit')")
    @Log(title = "余额变动记录", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody SakuraBalanceLog sakuraBalanceLog)
    {
        return AjaxResult.error(403, "余额流水为审计账本，禁止手动修改");
    }

    /**
     * 删除余额变动记录
     * 余额流水禁止删除。
     */
    @PreAuthorize("@ss.hasPermi('sakura:balancelog:remove')")
    @Log(title = "余额变动记录", businessType = BusinessType.DELETE)
	@DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return AjaxResult.error(403, "余额流水为审计账本，禁止手动删除");
    }
}
