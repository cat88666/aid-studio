package com.sakura.rps.queue;

import com.sakura.common.error.TaskErrorSnapshot;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import com.sakura.sakura.domain.SakuraExtractTask;
import com.sakura.sakura.domain.SakuraStoryboardBatch;
import com.sakura.sakura.domain.SakuraStoryboardShotGroupPlan;
import com.sakura.sakura.service.ISakuraExtractTaskService;
import com.sakura.sakura.service.ISakuraStoryboardBatchService;
import com.sakura.sakura.service.ISakuraStoryboardShotGroupPlanService;
import com.sakura.billing.service.IAccountUpdateService;
import com.sakura.common.utils.DateUtils;
import com.sakura.rps.helper.StoryboardResumeBillingExecutor;
import com.sakura.rps.service.IExtractBillingService;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * 回收服务重启中断的分镜脚本批量任务。
 *
 * @author 视觉AID
 */
@Slf4j
@Component
public class StoryboardScriptRestartRecovery implements BatchTaskRestartRecovery
{
    /** 本组件只处理该任务类型 */
    public static final String TASK_TYPE_STORYBOARD_SCRIPT_BATCH = "storyboard_script_batch";

    private static final String DEL_FLAG_NORMAL = "0";

    private static final String BATCH_STATUS_PENDING = "PENDING";
    private static final String BATCH_STATUS_PROCESSING = "PROCESSING";
    private static final String BATCH_STATUS_SUCCEEDED = "SUCCEEDED";
    private static final String BATCH_STATUS_FAILED = "FAILED";
    private static final String BATCH_STATUS_CANCELLED = "CANCELLED";

    private static final String BILLING_STATUS_FROZEN = "FROZEN";

    private static final String TASK_STATUS_FAILED = "FAILED";
    private static final String TASK_STATUS_PARTIAL_FAILED = "PARTIAL_FAILED";
    private static final String TASK_STATUS_RECOVERING = "RECOVERING";

    /** 业务类型：创作（与 StoryboardScriptServiceImpl 续生结算保持一致） */
    private static final String BIZ_TYPE_CREATE = "create";

    /** 续生标记前缀（resume 接口写入 task.remark：RESUME_TRACE:{traceId}|FROZEN:{amount}） */
    private static final String RESUME_MARKER_PREFIX = "RESUME_TRACE:";

    @Autowired
    private ISakuraExtractTaskService extractTaskService;

    @Autowired
    private ISakuraStoryboardBatchService storyboardBatchService;

    @Autowired
    private ISakuraStoryboardShotGroupPlanService shotGroupPlanService;

    @Autowired
    private IExtractBillingService extractBillingService;

    @Autowired
    private IAccountUpdateService accountUpdateService;

    /**
     * 判断是否为本组件负责回收的任务类型。
     *
     * @param taskType 任务类型
     * @return true=分镜脚本批量任务
     */
    public boolean supports(String taskType)
    {
        return TASK_TYPE_STORYBOARD_SCRIPT_BATCH.equals(taskType);
    }

    /**
     * 回收被服务重启打断的分镜脚本批量任务。
     *
     * @param taskId 父任务 ID
     * @return true=已按本类型回收（调用方无需再走通用回收）；false=非本类型 / 任务不存在，交回通用回收
     */
    @Override
    public boolean recover(Long taskId)
    {
        SakuraExtractTask task = extractTaskService.selectSakuraExtractTaskById(taskId);
        if (Objects.isNull(task) || !TASK_TYPE_STORYBOARD_SCRIPT_BATCH.equals(task.getTaskType()))
        {
            return false; // 非本类型，交回通用回收
        }
        Long userId = task.getUserId();

        if (!TASK_STATUS_RECOVERING.equals(task.getStatus()))
        {
            LambdaUpdateWrapper<SakuraExtractTask> claim = Wrappers.lambdaUpdate();
            claim.eq(SakuraExtractTask::getId, taskId);
            claim.eq(SakuraExtractTask::getStatus, task.getStatus());
            if (StrUtil.isBlank(task.getBillingTraceId()))
            {
                claim.isNull(SakuraExtractTask::getBillingTraceId);
            }
            else
            {
                claim.eq(SakuraExtractTask::getBillingTraceId, task.getBillingTraceId());
            }
            claim.set(SakuraExtractTask::getStatus, TASK_STATUS_RECOVERING);
            claim.set(SakuraExtractTask::getUpdateTime, DateUtils.getNowDate());
            if (!extractTaskService.update(claim))
            {
                return false;
            }
            task.setStatus(TASK_STATUS_RECOVERING);
        }

        // 本任务全部批次
        List<SakuraStoryboardBatch> allBatches = storyboardBatchService.list(
                Wrappers.<SakuraStoryboardBatch>lambdaQuery()
                        .eq(SakuraStoryboardBatch::getParentTaskId, taskId)
                        .eq(SakuraStoryboardBatch::getDelFlag, DEL_FLAG_NORMAL));

        List<SakuraStoryboardBatch> inflightBatches = allBatches.stream()
                .filter(b -> BATCH_STATUS_PENDING.equalsIgnoreCase(b.getStatus())
                        || BATCH_STATUS_PROCESSING.equalsIgnoreCase(b.getStatus()))
                .collect(Collectors.toList());
        for (SakuraStoryboardBatch b : inflightBatches)
        {
            // 同步内存对象状态，便于后续按 status 汇总冻结金额
            b.setStatus(BATCH_STATUS_FAILED);
            LambdaUpdateWrapper<SakuraStoryboardBatch> upd = Wrappers.lambdaUpdate();
            upd.eq(SakuraStoryboardBatch::getId, b.getId());
            upd.set(SakuraStoryboardBatch::getStatus, BATCH_STATUS_FAILED);
            upd.set(SakuraStoryboardBatch::getBillingStatus, BILLING_STATUS_FROZEN);
            upd.set(SakuraStoryboardBatch::getErrorMessage, "服务重启中断");
            upd.set(SakuraStoryboardBatch::getUpdateTime, DateUtils.getNowDate());
            storyboardBatchService.update(upd);
            // 同步镜头组计划状态为 FAILED
            if (Objects.nonNull(b.getShotGroupPlanId()))
            {
                LambdaUpdateWrapper<SakuraStoryboardShotGroupPlan> planUpd = Wrappers.lambdaUpdate();
                planUpd.eq(SakuraStoryboardShotGroupPlan::getId, b.getShotGroupPlanId());
                planUpd.set(SakuraStoryboardShotGroupPlan::getStatus, BATCH_STATUS_FAILED);
                planUpd.set(SakuraStoryboardShotGroupPlan::getErrorMsg, "服务重启中断");
                planUpd.set(SakuraStoryboardShotGroupPlan::getUpdateTime, DateUtils.getNowDate());
                shotGroupPlanService.update(planUpd);
            }
        }

        if (CollectionUtil.isEmpty(allBatches))
        {
            LambdaUpdateWrapper<SakuraStoryboardShotGroupPlan> danglingPlanUpd = Wrappers.lambdaUpdate();
            danglingPlanUpd.eq(SakuraStoryboardShotGroupPlan::getTaskId, taskId);
            danglingPlanUpd.eq(SakuraStoryboardShotGroupPlan::getDelFlag, DEL_FLAG_NORMAL);
            danglingPlanUpd.in(SakuraStoryboardShotGroupPlan::getStatus, BATCH_STATUS_PENDING, BATCH_STATUS_PROCESSING);
            danglingPlanUpd.set(SakuraStoryboardShotGroupPlan::getStatus, BATCH_STATUS_FAILED);
            danglingPlanUpd.set(SakuraStoryboardShotGroupPlan::getErrorMsg, "服务重启中断");
            danglingPlanUpd.set(SakuraStoryboardShotGroupPlan::getUpdateTime, DateUtils.getNowDate());
            shotGroupPlanService.update(danglingPlanUpd);
        }

        long succeededCount = allBatches.stream()
                .filter(b -> BATCH_STATUS_SUCCEEDED.equalsIgnoreCase(b.getStatus()))
                .count();

        // 续生轮：父任务 remark 带 RESUME_TRACE 标记（resume 接口写入，未跑完则未清除）
        boolean isResume = StrUtil.isNotBlank(task.getRemark()) && task.getRemark().startsWith(RESUME_MARKER_PREFIX);

        boolean billingSucceeded = false;
        try
        {
            if (isResume)
            {
                billingSucceeded = settleResumeRound(taskId, userId, task.getRemark(), allBatches);
            }
            else
            {
                // 首跑被中断：父任务一次冻结，整笔退回（含已成功批次——重启属系统侧故障，从宽退款）
                billingSucceeded = extractBillingService.refundBilling(
                        taskId, userId, task.getBillingTraceId());
            }
        }
        catch (Exception billingEx)
        {
            log.error("[RESTART-RECOVER] 分镜脚本任务回收计费异常: taskId={}", taskId, billingEx);
        }
        if (billingSucceeded && !isResume)
        {
            markAllBatchesRefunded(allBatches);
        }

        LambdaUpdateWrapper<SakuraExtractTask> taskUpd = Wrappers.lambdaUpdate();
        taskUpd.eq(SakuraExtractTask::getId, taskId);
        taskUpd.eq(SakuraExtractTask::getStatus, TASK_STATUS_RECOVERING);
        if (StrUtil.isBlank(task.getBillingTraceId()))
        {
            taskUpd.isNull(SakuraExtractTask::getBillingTraceId);
        }
        else
        {
            taskUpd.eq(SakuraExtractTask::getBillingTraceId, task.getBillingTraceId());
        }
        if (succeededCount > 0)
        {
            taskUpd.set(SakuraExtractTask::getStatus, TASK_STATUS_PARTIAL_FAILED);
            taskUpd.set(SakuraExtractTask::getErrorMessage, billingSucceeded
                    ? "服务重启中断，部分已完成，可继续生成"
                    : "服务重启中断，计费处理中")
                .set(SakuraExtractTask::getErrorDetailJson, TaskErrorSnapshot.fromMessage(billingSucceeded
                    ? "服务重启中断，部分已完成，可继续生成"
                    : "服务重启中断，计费处理中"));
        }
        else
        {
            taskUpd.set(SakuraExtractTask::getStatus, TASK_STATUS_FAILED);
            taskUpd.set(SakuraExtractTask::getErrorMessage, billingSucceeded
                    ? "服务重启中断，已退回"
                    : "服务重启中断，计费处理中")
                .set(SakuraExtractTask::getErrorDetailJson, TaskErrorSnapshot.fromMessage(billingSucceeded
                    ? "服务重启中断，已退回"
                    : "服务重启中断，计费处理中"));
        }
        if (billingSucceeded && isResume)
        {
            taskUpd.set(SakuraExtractTask::getRemark, null);
        }
        taskUpd.set(SakuraExtractTask::getUpdateTime, DateUtils.getNowDate());
        boolean updated = extractTaskService.update(taskUpd);

        log.warn("[RESTART-RECOVER] 分镜脚本任务重启回收完成: taskId={}, 在途批次={}, 成功批次={}, 续生轮={}",
                taskId, inflightBatches.size(), succeededCount, isResume);
        return updated;
    }

    /** 收口续生标记所对应轮次的计费。 */
    private boolean settleResumeRound(Long taskId, Long userId, String marker,
                                      List<SakuraStoryboardBatch> allBatches)
    {
        StoryboardResumeBillingExecutor.ResumeMarker resumeMarker;
        try
        {
            resumeMarker = StoryboardResumeBillingExecutor.parseMarker(marker);
        }
        catch (Exception e)
        {
            log.error("[RESTART-RECOVER] 解析 RESUME_TRACE 失败，跳过续生结算（交统一补偿兜底）: taskId={}, remark={}",
                    taskId, marker, e);
            return false;
        }
        List<SakuraStoryboardBatch> thisRound = allBatches.stream()
                .filter(b -> Objects.equals(b.getRetryRound(), resumeMarker.retryRound()))
                .collect(Collectors.toList());

        BigDecimal succeededAmount = BigDecimal.ZERO;
        for (SakuraStoryboardBatch b : thisRound)
        {
            BigDecimal amt = Objects.isNull(b.getFrozenAmount()) ? BigDecimal.ZERO : b.getFrozenAmount();
            if (BATCH_STATUS_SUCCEEDED.equalsIgnoreCase(b.getStatus()))
            {
                succeededAmount = succeededAmount.add(amt);
            }
        }
        BigDecimal unsuccessAmount = resumeMarker.frozenAmount()
                .subtract(succeededAmount).max(BigDecimal.ZERO);

        BigDecimal finalSucceededAmount = succeededAmount;
        StoryboardResumeBillingExecutor.BillingResult result = StoryboardResumeBillingExecutor.execute(() ->
        {
            if (finalSucceededAmount.compareTo(BigDecimal.ZERO) > 0)
            {
                accountUpdateService.settle(userId, finalSucceededAmount, resumeMarker.traceId(),
                        BIZ_TYPE_CREATE, "分镜脚本续生结算");
            }
        }, () -> {
            if (unsuccessAmount.compareTo(BigDecimal.ZERO) > 0)
            {
                accountUpdateService.refund(userId, unsuccessAmount, resumeMarker.traceId(),
                        BIZ_TYPE_CREATE, "分镜脚本续生退款");
            }
        });
        if (!result.succeeded())
        {
            log.error("[RESTART-RECOVER] 分镜脚本续生轮结算失败: taskId={}, traceId={}",
                    taskId, resumeMarker.traceId(), result.error());
            return false;
        }
        markResumeRoundBillingTerminal(thisRound);
        log.info("[RESTART-RECOVER] 分镜脚本续生轮结算完成: taskId={}, traceId={}, round={}, settled={}, refunded={}",
                taskId, resumeMarker.traceId(), resumeMarker.retryRound(), succeededAmount, unsuccessAmount);
        return true;
    }

    private void markResumeRoundBillingTerminal(List<SakuraStoryboardBatch> batches)
    {
        for (SakuraStoryboardBatch batch : batches)
        {
            LambdaUpdateWrapper<SakuraStoryboardBatch> update = Wrappers.lambdaUpdate();
            update.eq(SakuraStoryboardBatch::getId, batch.getId());
            update.set(SakuraStoryboardBatch::getBillingStatus,
                    StoryboardResumeBillingExecutor.terminalBatchBillingStatus(
                            false, BATCH_STATUS_SUCCEEDED.equalsIgnoreCase(batch.getStatus())));
            update.set(SakuraStoryboardBatch::getUpdateTime, DateUtils.getNowDate());
            storyboardBatchService.update(update);
        }
    }

    private void markAllBatchesRefunded(List<SakuraStoryboardBatch> batches)
    {
        for (SakuraStoryboardBatch batch : batches)
        {
            LambdaUpdateWrapper<SakuraStoryboardBatch> update = Wrappers.lambdaUpdate();
            update.eq(SakuraStoryboardBatch::getId, batch.getId());
            update.set(SakuraStoryboardBatch::getBillingStatus,
                    StoryboardResumeBillingExecutor.terminalBatchBillingStatus(true,
                            BATCH_STATUS_SUCCEEDED.equalsIgnoreCase(batch.getStatus())));
            update.set(SakuraStoryboardBatch::getUpdateTime, DateUtils.getNowDate());
            storyboardBatchService.update(update);
        }
    }
}
