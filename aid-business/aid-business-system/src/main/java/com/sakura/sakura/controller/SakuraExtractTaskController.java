package com.sakura.sakura.controller;

import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
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
import com.sakura.sakura.domain.SakuraExtractTask;
import com.sakura.sakura.service.ISakuraExtractTaskService;
import com.sakura.rps.service.IAssetExtractService;
import com.sakura.rps.service.IExtractBillingService;
import com.sakura.common.utils.poi.ExcelUtil;
import com.sakura.common.core.page.TableDataInfo;

/**
 * 资产提取任务Controller
 *
 * @author 视觉AID
 */
@RestController
@RequestMapping("/sakura/extracttask")
@Slf4j
public class SakuraExtractTaskController extends BaseController
{
    @Autowired
    private ISakuraExtractTaskService sakuraExtractTaskService;

    @Autowired
    private IAssetExtractService assetExtractService;

    @Autowired
    private IExtractBillingService extractBillingService;

    /**
     * 查询资产提取任务列表
     */
    @PreAuthorize("@ss.hasPermi('sakura:extracttask:list')")
    @GetMapping("/list")
    public TableDataInfo list(SakuraExtractTask sakuraExtractTask)
    {
        startPage();
        List<SakuraExtractTask> list = sakuraExtractTaskService.selectSakuraExtractTaskList(sakuraExtractTask);
        return getDataTable(list);
    }

    /**
     * 导出资产提取任务列表
     */
    @PreAuthorize("@ss.hasPermi('sakura:extracttask:export')")
    @Log(title = "资产提取任务", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, SakuraExtractTask sakuraExtractTask)
    {
        List<SakuraExtractTask> list = sakuraExtractTaskService.selectSakuraExtractTaskList(sakuraExtractTask);
        ExcelUtil<SakuraExtractTask> util = new ExcelUtil<SakuraExtractTask>(SakuraExtractTask.class);
        util.exportExcel(response, list, "资产提取任务数据");
    }

    /**
     * 获取资产提取任务详细信息
     */
    @PreAuthorize("@ss.hasPermi('sakura:extracttask:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return success(sakuraExtractTaskService.selectSakuraExtractTaskById(id));
    }

    /**
     * 新增资产提取任务
     * 提取任务由业务入口驱动，禁止 UI 新增，避免绕过扣费 / 状态机。
     */
    @PreAuthorize("@ss.hasPermi('sakura:extracttask:add')")
    @Log(title = "资产提取任务", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody SakuraExtractTask sakuraExtractTask)
    {
        return AjaxResult.error(403, "提取任务由业务流水线写入，禁止手动新增");
    }

    /**
     * 修改资产提取任务
     * 禁止改 status / billingTraceId / billingSnapshotJson，避免伪造 SUCCEEDED 跳过退款。
     */
    @PreAuthorize("@ss.hasPermi('sakura:extracttask:edit')")
    @Log(title = "资产提取任务", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody SakuraExtractTask sakuraExtractTask)
    {
        return AjaxResult.error(403, "提取任务关键字段与扣费联动，禁止手动修改");
    }

    /**
     * 删除资产提取任务
     * 禁止手动删除，保留审计完整性。
     */
    @PreAuthorize("@ss.hasPermi('sakura:extracttask:remove')")
    @Log(title = "资产提取任务", businessType = BusinessType.DELETE)
	@DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return AjaxResult.error(403, "提取任务禁止手动删除");
    }

    /**
     * 强制清理僵尸提取任务（管理员操作）
     * 将指定 taskId 的 PENDING/PROCESSING 任务强制标记为 FAILED，退冻结 + 释放 Redis 锁。
     * 用于 consumer 崩溃 / 消息丢失导致任务永远卡住的紧急恢复场景。
     */
    @PreAuthorize("@ss.hasPermi('sakura:extracttask:edit')")
    @Log(title = "资产提取任务", businessType = BusinessType.UPDATE)
    @PostMapping("/reclaim/{taskId}")
    public AjaxResult reclaimTask(@PathVariable Long taskId)
    {
        SakuraExtractTask task = sakuraExtractTaskService.selectSakuraExtractTaskById(taskId);
        if (task == null)
        {
            return AjaxResult.error("任务不存在");
        }
        String status = task.getStatus();
        if (!"PENDING".equals(status) && !"PROCESSING".equals(status))
        {
            return AjaxResult.error("任务已终态（" + status + "），无需清理");
        }
        // 委托内部方法做完整清理（标记 FAILED + 退款 + 释放锁）
        try
        {
            forceReclaimSingleTask(task);
            return AjaxResult.success("清理成功，taskId=" + taskId);
        }
        catch (Exception e)
        {
            return AjaxResult.error("清理失败: " + e.getMessage());
        }
    }

    /**
     * 单条任务强制清理（内部方法）
     */
    private void forceReclaimSingleTask(SakuraExtractTask task)
    {
        Long taskId = task.getId();
        com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<SakuraExtractTask> update =
                com.baomidou.mybatisplus.core.toolkit.Wrappers.lambdaUpdate();
        update.eq(SakuraExtractTask::getId, taskId);
        update.in(SakuraExtractTask::getStatus, "PENDING", "PROCESSING");
        if (task.getBillingTraceId() == null)
        {
            update.isNull(SakuraExtractTask::getBillingTraceId);
        }
        else
        {
            update.eq(SakuraExtractTask::getBillingTraceId, task.getBillingTraceId());
        }
        update.set(SakuraExtractTask::getStatus, "FAILED");
        update.set(SakuraExtractTask::getErrorMessage, "管理员手动清理");
        update.set(SakuraExtractTask::getUpdateTime, new java.util.Date());
        int rows = sakuraExtractTaskService.getBaseMapper().update(null, update);
        if (rows <= 0)
        {
            log.warn("管理员清理任务CAS未命中，跳过退款和锁释放: taskId={}, expectedTraceId={}",
                    taskId, task.getBillingTraceId());
            return;
        }
        if (task.getUserId() != null && task.getBillingTraceId() != null
                && !task.getBillingTraceId().isBlank())
        {
            try
            {
                boolean refunded = extractBillingService.refundBilling(
                        taskId, task.getUserId(), task.getBillingTraceId());
                if (!refunded)
                {
                    log.warn("管理员清理任务退款未收口: taskId={}, expectedTraceId={}",
                            taskId, task.getBillingTraceId());
                }
            }
            catch (Exception ex)
            {
                // 管理清理接口仍继续释放旧任务业务锁，但账务异常必须留痕供补偿/人工核对。
                log.error("管理员清理任务退款异常: taskId={}, expectedTraceId={}",
                        taskId, task.getBillingTraceId(), ex);
            }
        }
        assetExtractService.releaseExtractLockForTask(taskId, task.getBillingTraceId());
        assetExtractService.releaseBatchFormLocks(taskId, task.getTaskType(), task.getBillingTraceId());
    }
}
