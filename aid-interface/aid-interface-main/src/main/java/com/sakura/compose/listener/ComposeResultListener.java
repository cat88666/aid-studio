package com.sakura.compose.listener;

import java.util.Date;
import java.util.Objects;

import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.sakura.sakura.domain.SakuraAudioRecord;
import com.sakura.sakura.domain.SakuraComicEpisode;
import com.sakura.sakura.domain.SakuraComicProject;
import com.sakura.sakura.domain.SakuraEpisodeEditor;
import com.sakura.sakura.domain.SakuraExtractTask;
import com.sakura.sakura.domain.SakuraGenRecord;
import com.sakura.sakura.domain.media.SakuraMediaTask;
import com.sakura.sakura.mapper.SakuraAudioRecordMapper;
import com.sakura.sakura.mapper.SakuraEpisodeEditorMapper;
import com.sakura.sakura.mapper.SakuraGenRecordMapper;
import com.sakura.sakura.mapper.SakuraMediaTaskMapper;
import com.sakura.sakura.service.ISakuraComicEpisodeService;
import com.sakura.sakura.service.ISakuraComicProjectService;
import com.sakura.sakura.service.ISakuraExtractTaskService;
import com.sakura.compose.ComposeConstants;
import com.sakura.compose.service.ComposeBatchSlotCoordinator;
import com.sakura.enums.EpisodeStatusEnum;
import com.sakura.enums.ProjectStatusEnum;
import com.sakura.media.enums.MediaTaskStatus;
import com.sakura.media.event.MediaTaskOssPersistedEvent;
import com.sakura.rps.queue.BatchParentSubmissionGuard;
import com.sakura.rps.queue.BatchTaskExecutionRejectedException;

import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * COMPOSE 成片业务回写监听器：成片 OSS 持久化完成后，以相对路径回填业务表。
 *
 * @author 视觉AID
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ComposeResultListener {

    /** 删除标志：正常 */
    private static final String DEL_FLAG_NORMAL = "0";

    /** 电影成片在 sakura_episode_editor 中的剧集ID标识 */
    private static final Long MOVIE_EPISODE_ID = 0L;

    /** 媒体任务 Mapper */
    private final SakuraMediaTaskMapper sakuraMediaTaskMapper;

    /** 抽卡记录 Mapper（接口1 回写） */
    private final SakuraGenRecordMapper sakuraGenRecordMapper;

    /** 配音记录 Mapper（接口1 回写取分镜归属） */
    private final SakuraAudioRecordMapper sakuraAudioRecordMapper;

    /** 剧集剪辑 Mapper（接口2 回写） */
    private final SakuraEpisodeEditorMapper sakuraEpisodeEditorMapper;

    /** 项目服务（成片导出成功后项目状态联动为「完成未提交(2)」可提审） */
    private final ISakuraComicProjectService sakuraComicProjectService;

    /** 剧集服务（成片导出成功后剧集状态联动为「完成未审核(2)」可提审） */
    private final ISakuraComicEpisodeService sakuraComicEpisodeService;

    /** 批量业务父任务服务（取消后的迟到合成回写隔离） */
    @Resource
    private ISakuraExtractTaskService sakuraExtractTaskService;

    @Resource
    private ComposeBatchSlotCoordinator composeBatchSlotCoordinator;

    @Resource
    private BatchParentSubmissionGuard batchParentSubmissionGuard;

    @EventListener
    @Order(300)
    public void onMediaTaskOssPersisted(MediaTaskOssPersistedEvent event) {
        if (Objects.isNull(event) || Objects.isNull(event.getTaskId())) {
            return;
        }
        try {
            SakuraMediaTask task = sakuraMediaTaskMapper.selectById(event.getTaskId());
            if (Objects.isNull(task) || !ComposeConstants.MEDIA_TYPE_COMPOSE.equals(task.getMediaType())) {
                return;
            }
            if (!MediaTaskStatus.SUCCEEDED.name().equals(task.getStatus()) || StrUtil.isBlank(task.getOssUrl())) {
                log.warn("COMPOSE 成片回写跳过(未成功或 ossUrl 为空), taskId={}", task.getId());
                return;
            }
            String category = task.getCallbackCategory();
            if (ComposeConstants.CALLBACK_EPISODE_EDITOR.equalsIgnoreCase(category)) {
                writeEpisodeEditor(task);
            } else {
                // 默认接口1：落 sakura_gen_record（genType=compose）
                writeGenRecord(task);
            }
        } catch (Exception ex) {
            log.error("ComposeResultListener 成片回写异常, taskId={}", event.getTaskId(), ex);
        }
    }

    /**
     * 接口2 成功回写（双槽位）：
     * 内容已进入审核流程（项目/剧集状态=审核中(3)或审核通过(4)）→ 新成片写 pending_video_url 待审槽，
     * final_video_url 保留旧片继续公开展示，重新过审后由审核通过钩子转正；
     * 其它状态（草稿/制作中/完成未提交/审核失败）→ 照旧直接写 final_video_url 并清空待审槽。
     * 两种情况均置 exportStatus=2 + exportProgress=100。
     *
     * @param task COMPOSE 任务
     */
    private void writeEpisodeEditor(SakuraMediaTask task) {
        if (Objects.isNull(task.getCallbackRecordId())) {
            log.warn("COMPOSE 接口2 回写缺少 callbackRecordId, taskId={}", task.getId());
            return;
        }
        // 查询字段精简：成片槽位、指纹和当前任务令牌用于回调幂等及陈旧任务隔离。
        SakuraEpisodeEditor editor = sakuraEpisodeEditorMapper.selectOne(new LambdaQueryWrapper<SakuraEpisodeEditor>()
                .select(SakuraEpisodeEditor::getId, SakuraEpisodeEditor::getProjectId, SakuraEpisodeEditor::getEpisodeId,
                        SakuraEpisodeEditor::getFinalVideoUrl, SakuraEpisodeEditor::getFinalVideoFingerprint,
                        SakuraEpisodeEditor::getPendingVideoUrl, SakuraEpisodeEditor::getPendingVideoFingerprint,
                        SakuraEpisodeEditor::getExportStatus, SakuraEpisodeEditor::getExportTaskId,
                        SakuraEpisodeEditor::getExportFingerprint)
                .eq(SakuraEpisodeEditor::getId, task.getCallbackRecordId())
                .last("LIMIT 1"));
        if (Objects.isNull(editor)) {
            log.warn("COMPOSE 接口2 回写记录不存在, taskId={}, episodeEditorId={}",
                    task.getId(), task.getCallbackRecordId());
            return;
        }
        if (!Objects.equals(String.valueOf(task.getId()), editor.getExportTaskId())) {
            log.warn("COMPOSE 接口2 陈旧任务回写已忽略, taskId={}, episodeEditorId={}, activeTaskId={}",
                    task.getId(), editor.getId(), editor.getExportTaskId());
            return;
        }
        Integer contentStatus = resolveContentStatus(editor);
        String exportFingerprint = editor.getExportFingerprint();

        LambdaUpdateWrapper<SakuraEpisodeEditor> update = new LambdaUpdateWrapper<>();
        update.eq(SakuraEpisodeEditor::getId, editor.getId());
        update.eq(SakuraEpisodeEditor::getExportTaskId, String.valueOf(task.getId()));
        update.eq(SakuraEpisodeEditor::getExportStatus, ComposeConstants.EXPORT_STATUS_COMPOSING);
        String slot;
        if (isAuditing(contentStatus)) {
            // 审核候选提交后不可变：竞态完成的新任务只收口导出状态，不覆盖正在审核的槽位。
            String auditedFingerprint = StrUtil.isNotBlank(editor.getPendingVideoUrl())
                    ? editor.getPendingVideoFingerprint() : editor.getFinalVideoFingerprint();
            update.set(SakuraEpisodeEditor::getExportFingerprint, auditedFingerprint);
            slot = "ignored-auditing";
            log.warn("COMPOSE 审核中候选快照受保护, taskId={}, episodeEditorId={}",
                    task.getId(), editor.getId());
        } else if (isAuditPassed(contentStatus)
                && StrUtil.isNotBlank(editor.getFinalVideoFingerprint())
                && Objects.equals(exportFingerprint, editor.getFinalVideoFingerprint())) {
            // 已过审内容相同，不制造待审槽，也不会再次触发审核。
            update.set(SakuraEpisodeEditor::getPendingVideoUrl, null);
            update.set(SakuraEpisodeEditor::getPendingVideoFingerprint, null);
            slot = "reused-final";
        } else if (isAuditPassed(contentStatus)) {
            update.set(SakuraEpisodeEditor::getPendingVideoUrl, task.getOssUrl());
            update.set(SakuraEpisodeEditor::getPendingVideoFingerprint, exportFingerprint);
            slot = "pending";
        } else {
            update.set(SakuraEpisodeEditor::getFinalVideoUrl, task.getOssUrl());
            update.set(SakuraEpisodeEditor::getFinalVideoFingerprint, exportFingerprint);
            // 覆盖 final 时清残留待审片，避免陈旧 pending 误触发"待重审"提示
            update.set(SakuraEpisodeEditor::getPendingVideoUrl, null);
            update.set(SakuraEpisodeEditor::getPendingVideoFingerprint, null);
            slot = "final";
        }
        update.set(SakuraEpisodeEditor::getExportStatus, ComposeConstants.EXPORT_STATUS_SUCCESS);
        update.set(SakuraEpisodeEditor::getExportProgress, 100);
        update.set(SakuraEpisodeEditor::getErrorMsg, null);
        update.set(SakuraEpisodeEditor::getUpdateTime, new Date());
        int updated = sakuraEpisodeEditorMapper.update(null, update);
        if (updated != 1) {
            log.warn("COMPOSE 接口2 回写状态已变化, taskId={}, episodeEditorId={}",
                    task.getId(), editor.getId());
            return;
        }
        log.info("COMPOSE 接口2 成片回写完成, taskId={}, episodeEditorId={}, slot={}",
                task.getId(), task.getCallbackRecordId(), slot);
        // 成片已生成：项目/剧集状态从草稿/制作中联动为「完成(2)」可提审（条件更新，审核流程中的状态不受影响）
        markAuditableAfterExport(task.getCallbackRecordId());
        composeBatchSlotCoordinator.releaseExport(editor.getId(), String.valueOf(task.getId()));
    }

    /**
     * 判断成片对应内容是否已进入审核流程（审核中(3)或审核通过(4)）。
     * 处于该区间时旧成片可能正在公开展示（公开口径 status∈(3,4) 且 is_public=1），
     * 新成片必须走待审槽，不允许直接覆盖。
     *
     * @param editor 剪辑记录（含归属）
     * @return true=审核中或已过审
     */
    private Integer resolveContentStatus(SakuraEpisodeEditor editor) {
        if (Objects.isNull(editor.getProjectId()) || Objects.isNull(editor.getEpisodeId())) {
            return null;
        }
        if (Objects.equals(editor.getEpisodeId(), MOVIE_EPISODE_ID)) {
            SakuraComicProject project = sakuraComicProjectService.getOne(Wrappers.<SakuraComicProject>lambdaQuery()
                    .select(SakuraComicProject::getId, SakuraComicProject::getStatus)
                    .eq(SakuraComicProject::getId, editor.getProjectId())
                    .eq(SakuraComicProject::getDelFlag, DEL_FLAG_NORMAL)
                    .last("LIMIT 1"));
            return Objects.isNull(project) ? null : project.getStatus();
        }
        SakuraComicEpisode episode = sakuraComicEpisodeService.getOne(Wrappers.<SakuraComicEpisode>lambdaQuery()
                .select(SakuraComicEpisode::getId, SakuraComicEpisode::getStatus)
                .eq(SakuraComicEpisode::getId, editor.getEpisodeId())
                .eq(SakuraComicEpisode::getDelFlag, DEL_FLAG_NORMAL)
                .last("LIMIT 1"));
        return Objects.isNull(episode) ? null : episode.getStatus();
    }

    private boolean isAuditing(Integer contentStatus) {
        return Objects.equals(contentStatus, ProjectStatusEnum.AUDITING.getValue());
    }

    private boolean isAuditPassed(Integer contentStatus) {
        return Objects.equals(contentStatus, ProjectStatusEnum.AUDIT_PASSED.getValue());
    }

    /**
     * 成片导出成功后的审核状态联动：
     * 电影成片（episode_id=0）→ 项目状态 草稿(0)/制作中(1) → 完成未提交(2)；
     * 剧集成片（episode_id=剧集ID）→ 剧集状态 草稿(0)/制作中(1) → 完成未审核(2)。
     * 仅从 0/1 推进（条件更新幂等）：审核中(3)/审核通过(4)/审核失败(5) 不在此处变更，
     * 避免 OSS 持久化事件重复触发时把已进入审核流程的状态拉回。
     * 状态联动失败仅记录日志，不影响成片回写主链路。
     *
     * @param episodeEditorId 剪辑记录ID
     */
    private void markAuditableAfterExport(Long episodeEditorId) {
        try {
            // 查询字段精简：状态联动只需归属字段（新增使用字段时此处必须同步补充）
            SakuraEpisodeEditor editor = sakuraEpisodeEditorMapper.selectOne(new LambdaQueryWrapper<SakuraEpisodeEditor>()
                    .select(SakuraEpisodeEditor::getId, SakuraEpisodeEditor::getProjectId, SakuraEpisodeEditor::getEpisodeId)
                    .eq(SakuraEpisodeEditor::getId, episodeEditorId)
                    .last("LIMIT 1"));
            if (Objects.isNull(editor) || Objects.isNull(editor.getProjectId())
                    || Objects.isNull(editor.getEpisodeId())) {
                return;
            }
            if (Objects.equals(editor.getEpisodeId(), MOVIE_EPISODE_ID)) {
                // 电影：项目级成片，项目状态推进为「完成未提交(2)」
                boolean updated = sakuraComicProjectService.update(Wrappers.<SakuraComicProject>lambdaUpdate()
                        .eq(SakuraComicProject::getId, editor.getProjectId())
                        .eq(SakuraComicProject::getDelFlag, DEL_FLAG_NORMAL)
                        .in(SakuraComicProject::getStatus, ProjectStatusEnum.DRAFT.getValue(),
                                ProjectStatusEnum.PROCESSING.getValue())
                        .set(SakuraComicProject::getStatus, ProjectStatusEnum.FINISHED_UNSUBMITTED.getValue())
                        .set(SakuraComicProject::getUpdateTime, new Date()));
                if (updated) {
                    log.info("COMPOSE 成片导出成功，项目状态联动为完成可提审, projectId={}", editor.getProjectId());
                }
                return;
            }
            // 剧集：该集状态推进为「完成未审核(2)」
            boolean updated = sakuraComicEpisodeService.update(Wrappers.<SakuraComicEpisode>lambdaUpdate()
                    .eq(SakuraComicEpisode::getId, editor.getEpisodeId())
                    .eq(SakuraComicEpisode::getDelFlag, DEL_FLAG_NORMAL)
                    .in(SakuraComicEpisode::getStatus, EpisodeStatusEnum.DRAFT.getValue(),
                            EpisodeStatusEnum.PROCESSING.getValue())
                    .set(SakuraComicEpisode::getStatus, EpisodeStatusEnum.FINISHED_UNAUDITED.getValue())
                    .set(SakuraComicEpisode::getUpdateTime, new Date()));
            if (updated) {
                log.info("COMPOSE 成片导出成功，剧集状态联动为完成可提审, projectId={}, episodeId={}",
                        editor.getProjectId(), editor.getEpisodeId());
            }
        } catch (Exception ex) {
            // 状态联动属于增强逻辑，异常不阻断成片回写主链路
            log.error("COMPOSE 成片导出成功后状态联动异常, episodeEditorId={}", episodeEditorId, ex);
        }
    }

    /**
     * 接口1 成功回写：新增 sakura_gen_record（genType=compose），仅存相对路径。
     *
     * @param task COMPOSE 任务
     */
    private void writeGenRecord(SakuraMediaTask task) {
        if (StrUtil.startWith(task.getComposeBatchId(), "cb_")) {
            batchParentSubmissionGuard.execute("voiceover:" + task.getComposeBatchId(), () -> {
                writeGenRecordGuarded(task);
                return null;
            });
            return;
        }
        if (Objects.nonNull(task.getParentTaskId())) {
            String executionTraceId = extractParentExecutionTrace(task);
            try {
                batchParentSubmissionGuard.executeManagedBusinessCommit(
                        task.getParentTaskId(), executionTraceId, () -> {
                            writeGenRecordGuarded(task);
                            return null;
                        });
            } catch (BatchTaskExecutionRejectedException rejected) {
                log.info("COMPOSE 父任务执行周期已变化，跳过业务回写: taskId={}, parentTaskId={}",
                        task.getId(), task.getParentTaskId());
            }
            return;
        }
        writeGenRecordGuarded(task);
    }

    private void writeGenRecordGuarded(SakuraMediaTask task) {
        if (isCancelledStoryboardAudioParent(task.getParentTaskId())) {
            log.info("COMPOSE 已取消批量配音成片回写已忽略, taskId={}, parentTaskId={}",
                    task.getId(), task.getParentTaskId());
            return;
        }
        if (isCancelledVoiceoverBatch(task.getComposeBatchId())) {
            log.info("COMPOSE 已取消一键配音成片回写已忽略, taskId={}, batchId={}",
                    task.getId(), task.getComposeBatchId());
            composeBatchSlotCoordinator.releaseVoiceover(task.getComposeBatchId());
            return;
        }
        // 幂等：OSS 持久化事件可能多次发布（轮询终态 + OSS 定时补偿），按 providerTaskId + genType 去重，避免重复插入成片记录
        if (StrUtil.isNotBlank(task.getProviderTaskId())) {
            Long existed = sakuraGenRecordMapper.selectCount(new LambdaQueryWrapper<SakuraGenRecord>()
                    .eq(SakuraGenRecord::getTaskId, task.getProviderTaskId())
                    .eq(SakuraGenRecord::getGenType, ComposeConstants.GEN_TYPE_COMPOSE)
                    .eq(SakuraGenRecord::getDelFlag, "0"));
            if (Objects.nonNull(existed) && existed > 0) {
                log.info("COMPOSE 接口1 成片已回写,跳过重复插入, taskId={}, providerTaskId={}",
                        task.getId(), task.getProviderTaskId());
                composeBatchSlotCoordinator.releaseVoiceover(task.getComposeBatchId());
                return;
            }
        }
        SakuraAudioRecord sourceAudioRecord = loadFirstAudioRecord(task.getComposeBatchId());
        Long storyboardId = Objects.nonNull(task.getCallbackRecordId())
                ? task.getCallbackRecordId()
                : (Objects.isNull(sourceAudioRecord) ? null : sourceAudioRecord.getStoryboardId());
        if (Objects.isNull(storyboardId)) {
            log.error("COMPOSE 接口1 成片回写缺少 storyboardId, taskId={}, batchId={}",
                    task.getId(), task.getComposeBatchId());
            return;
        }
        Long projectId = Objects.nonNull(task.getProjectId())
                ? task.getProjectId()
                : (Objects.isNull(sourceAudioRecord) ? null : sourceAudioRecord.getProjectId());
        Long episodeId = Objects.nonNull(task.getEpisodeId())
                ? task.getEpisodeId()
                : (Objects.isNull(sourceAudioRecord) ? null : sourceAudioRecord.getEpisodeId());
        SakuraGenRecord record = new SakuraGenRecord();
        record.setUserId(task.getUserId());
        record.setProjectId(projectId);
        record.setEpisodeId(episodeId);
        record.setStoryboardId(storyboardId);
        record.setGenType(ComposeConstants.GEN_TYPE_COMPOSE);
        record.setFileUrl(task.getOssUrl());
        record.setTaskId(task.getProviderTaskId());
        record.setStatus(ComposeConstants.GEN_STATUS_SUCCESS);
        record.setVideoDuration(task.getOutputDurationSeconds());
        record.setIsSelected(0);
        record.setDelFlag("0");
        record.setCreateTime(new Date());
        sakuraGenRecordMapper.insert(record);
        log.info("COMPOSE 接口1 成片回写完成, taskId={}, genRecordId={}, batchId={}",
                task.getId(), record.getId(), task.getComposeBatchId());
        composeBatchSlotCoordinator.releaseVoiceover(task.getComposeBatchId());
    }

    private String extractParentExecutionTrace(SakuraMediaTask task) {
        if (Objects.isNull(task) || StrUtil.isBlank(task.getRequestJson())) {
            return null;
        }
        try {
            return com.alibaba.fastjson2.JSON.parseObject(task.getRequestJson())
                    .getString("parentExecutionTraceId");
        } catch (Exception ex) {
            log.warn("COMPOSE 解析父执行周期失败: taskId={}", task.getId());
            return null;
        }
    }

    private boolean isCancelledVoiceoverBatch(String composeBatchId) {
        if (StrUtil.isBlank(composeBatchId)) {
            return false;
        }
        Long cancelled = sakuraAudioRecordMapper.selectCount(new LambdaQueryWrapper<SakuraAudioRecord>()
                .eq(SakuraAudioRecord::getComposeBatchId, composeBatchId)
                .eq(SakuraAudioRecord::getStatus, MediaTaskStatus.FAILED.name())
                .eq(SakuraAudioRecord::getErrorMessage, "用户取消")
                .eq(SakuraAudioRecord::getDelFlag, DEL_FLAG_NORMAL));
        return cancelled != null && cancelled > 0;
    }

    private boolean isCancelledStoryboardAudioParent(Long parentTaskId) {
        if (Objects.isNull(parentTaskId)) {
            return false;
        }
        SakuraExtractTask parent = sakuraExtractTaskService.selectSakuraExtractTaskById(parentTaskId);
        if (Objects.isNull(parent)
                || !"storyboard_audio_generate".equals(parent.getTaskType())) {
            return false;
        }
        return "CANCELLED".equals(parent.getStatus())
                || ("FAILED".equals(parent.getStatus()) && "用户取消".equals(parent.getErrorMessage()));
    }

    /**
     * 按合成批次查询一条配音记录，用于成片记录复用分镜归属。
     *
     * @param composeBatchId 合成批次号
     * @return 配音记录
     */
    private SakuraAudioRecord loadFirstAudioRecord(String composeBatchId) {
        if (StrUtil.isBlank(composeBatchId)) {
            return null;
        }
        return sakuraAudioRecordMapper.selectOne(new LambdaQueryWrapper<SakuraAudioRecord>()
                .select(SakuraAudioRecord::getId, SakuraAudioRecord::getProjectId,
                        SakuraAudioRecord::getEpisodeId, SakuraAudioRecord::getStoryboardId)
                .eq(SakuraAudioRecord::getComposeBatchId, composeBatchId)
                .eq(SakuraAudioRecord::getDelFlag, "0")
                .isNotNull(SakuraAudioRecord::getStoryboardId)
                .orderByAsc(SakuraAudioRecord::getId)
                .last("LIMIT 1"));
    }
}
