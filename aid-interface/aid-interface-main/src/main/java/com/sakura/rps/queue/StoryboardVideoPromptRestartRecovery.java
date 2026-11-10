package com.sakura.rps.queue;

import com.sakura.common.error.TaskErrorSnapshot;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import com.sakura.sakura.domain.SakuraExtractTask;
import com.sakura.sakura.domain.media.SakuraMediaTask;
import com.sakura.sakura.mapper.SakuraMediaTaskMapper;
import com.sakura.sakura.service.ISakuraExtractTaskService;
import com.sakura.common.utils.DateUtils;
import com.sakura.rps.service.IExtractBillingService;

import lombok.extern.slf4j.Slf4j;

/**
 * 视频提示词批量任务（{@code storyboard_video_prompt_batch}）「重启自愈」回收策略。
 *
 * @author 视觉AID
 */
@Slf4j
@Component
public class StoryboardVideoPromptRestartRecovery implements BatchTaskRestartRecovery
{
    private static final String TASK_TYPE = "storyboard_video_prompt_batch";
    /** 子任务 biz_task_type（写入 sakura_media_task），与 StoryboardVideoPromptServiceImpl 保持一致 */
    private static final String BIZ_TASK_TYPE = "storyboard_video_prompt";

    private static final String MEDIA_STATUS_SUCCEEDED = "SUCCEEDED";
    private static final String TASK_STATUS_FAILED = "FAILED";
    private static final String TASK_STATUS_PARTIAL_FAILED = "PARTIAL_FAILED";
    private static final String TASK_STATUS_RECOVERING = "RECOVERING";

    @Autowired
    private ISakuraExtractTaskService extractTaskService;

    @Autowired
    private SakuraMediaTaskMapper sakuraMediaTaskMapper;

    @Autowired
    private IExtractBillingService extractBillingService;

    @Override
    public boolean supports(String taskType)
    {
        return TASK_TYPE.equals(taskType);
    }

    @Override
    public boolean recover(Long taskId)
    {
        SakuraExtractTask task = extractTaskService.selectSakuraExtractTaskById(taskId);
        if (Objects.isNull(task) || !TASK_TYPE.equals(task.getTaskType()))
        {
            return false; // 非本类型，交回通用回收
        }
        Long userId = task.getUserId();

        if (!TASK_STATUS_RECOVERING.equals(task.getStatus()))
        {
            LambdaUpdateWrapper<SakuraExtractTask> claim = Wrappers.lambdaUpdate();
            claim.eq(SakuraExtractTask::getId, taskId);
            claim.eq(SakuraExtractTask::getStatus, task.getStatus());
            claim.eq(SakuraExtractTask::getBillingTraceId, task.getBillingTraceId());
            claim.set(SakuraExtractTask::getStatus, TASK_STATUS_RECOVERING);
            claim.set(SakuraExtractTask::getUpdateTime, DateUtils.getNowDate());
            if (!extractTaskService.update(claim))
            {
                return false;
            }
            task.setStatus(TASK_STATUS_RECOVERING);
        }

        // 产出判定：本任务是否已有成功的 LLM 子任务（已落库 video_prompt durable）
        boolean produced = hasSucceededSubTask(taskId);
        // 服务中断属于系统侧失败，统一按任务当前 trace 整笔退回。
        // 不能用 0 token 结算：固定价/包次模型的 0 token 仍可能产生全额费用。
        boolean refunded = false;
        try
        {
            refunded = extractBillingService.refundBilling(
                    taskId, userId, task.getBillingTraceId());
        }
        catch (Exception billingEx)
        {
            log.error("视频提示词任务回收计费异常: taskId={}", taskId, billingEx);
        }
        if (!refunded)
        {
            return false;
        }

        // 有历史或本轮输出时保留续生入口；当前周期已退款，后续续生会创建新 trace。
        boolean partialFailed = produced;
        LambdaUpdateWrapper<SakuraExtractTask> taskUpd = Wrappers.lambdaUpdate();
        taskUpd.eq(SakuraExtractTask::getId, taskId);
        taskUpd.eq(SakuraExtractTask::getStatus, TASK_STATUS_RECOVERING);
        taskUpd.eq(SakuraExtractTask::getBillingTraceId, task.getBillingTraceId());
        if (partialFailed)
        {
            taskUpd.set(SakuraExtractTask::getStatus, TASK_STATUS_PARTIAL_FAILED);
            taskUpd.set(SakuraExtractTask::getErrorMessage, "服务重启中断，部分已完成，可继续生成")
                .set(SakuraExtractTask::getErrorDetailJson, TaskErrorSnapshot.fromMessage("服务重启中断，部分已完成，可继续生成"));
        }
        else
        {
            taskUpd.set(SakuraExtractTask::getStatus, TASK_STATUS_FAILED);
            taskUpd.set(SakuraExtractTask::getErrorMessage, "服务重启中断，已退回")
                .set(SakuraExtractTask::getErrorDetailJson, TaskErrorSnapshot.fromMessage("服务重启中断，已退回"));
        }
        taskUpd.set(SakuraExtractTask::getRemark, null);
        taskUpd.set(SakuraExtractTask::getUpdateTime, DateUtils.getNowDate());
        boolean updated = extractTaskService.update(taskUpd);

        log.warn("视频提示词任务重启回收完成: taskId={}, produced={}, updated={}, 终态={}",
                taskId, produced, updated, partialFailed ? TASK_STATUS_PARTIAL_FAILED : TASK_STATUS_FAILED);
        return updated;
    }

    /** 本任务是否已有成功 LLM 子任务（sakura_media_task） */
    private boolean hasSucceededSubTask(Long taskId)
    {
        Long cnt = sakuraMediaTaskMapper.selectCount(
                Wrappers.<SakuraMediaTask>lambdaQuery()
                        .eq(SakuraMediaTask::getBizTaskId, taskId)
                        .eq(SakuraMediaTask::getBizTaskType, BIZ_TASK_TYPE)
                        .eq(SakuraMediaTask::getStatus, MEDIA_STATUS_SUCCEEDED));
        return cnt != null && cnt > 0;
    }
}
