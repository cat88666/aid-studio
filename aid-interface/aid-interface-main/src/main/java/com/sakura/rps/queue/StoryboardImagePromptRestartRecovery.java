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
 * 分镜图脚本批量任务（{@code storyboard_image_prompt_batch}）「重启自愈」回收策略。
 *
 * @author 视觉AID
 */
@Slf4j
@Component
public class StoryboardImagePromptRestartRecovery implements BatchTaskRestartRecovery
{
    private static final String TASK_TYPE = "storyboard_image_prompt_batch";
    /** 子任务 biz_task_type（写入 sakura_media_task），与 StoryboardImagePromptServiceImpl 保持一致 */
    private static final String BIZ_TASK_TYPE = "storyboard_image_prompt";

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

        // 产出判定：本任务是否已有成功的 LLM 子任务（已落库 image_prompt durable）
        boolean produced = hasSucceededSubTask(taskId);

        // 服务中断属于系统侧失败。本轮无论已产生多少输出，均按任务当前 trace 整笔退回；
        // 续生已复用统一计费周期，因此不再解析 remark 或直接操作账户。
        boolean refunded = false;
        try
        {
            refunded = extractBillingService.refundBilling(
                    taskId, userId, task.getBillingTraceId());
        }
        catch (Exception billingEx)
        {
            log.error("分镜图脚本任务回收计费异常: taskId={}", taskId, billingEx);
        }

        // 资金尚未收敛时不能先写任务终态，否则补偿入口会丢失。
        if (!refunded)
        {
            return false;
        }

        // 父任务终态：有成功子任务 → PARTIAL_FAILED（保留续生入口）；否则 FAILED
        LambdaUpdateWrapper<SakuraExtractTask> taskUpd = Wrappers.lambdaUpdate();
        taskUpd.eq(SakuraExtractTask::getId, taskId);
        taskUpd.eq(SakuraExtractTask::getStatus, TASK_STATUS_RECOVERING);
        taskUpd.eq(SakuraExtractTask::getBillingTraceId, task.getBillingTraceId());
        if (produced)
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

        log.warn("分镜图脚本任务重启回收完成: taskId={}, produced={}, updated={}",
                taskId, produced, updated);
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
