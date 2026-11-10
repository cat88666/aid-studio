package com.sakura.compose.service;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.sakura.sakura.domain.SakuraAudioRecord;
import com.sakura.sakura.domain.SakuraEpisodeEditor;
import com.sakura.sakura.domain.media.SakuraMediaTask;
import com.sakura.sakura.mapper.SakuraAudioRecordMapper;
import com.sakura.sakura.mapper.SakuraEpisodeEditorMapper;
import com.sakura.sakura.mapper.SakuraMediaTaskMapper;
import com.sakura.compose.ComposeConstants;
import com.sakura.media.enums.MediaTaskStatus;
import com.sakura.rps.queue.BatchTaskExternalActivityProbe;
import com.sakura.rps.queue.BatchTaskLogicalType;

import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;

/**
 * 旧一键配音与剧集导出的数据库判活器。
 *
 * @author 视觉AID
 */
@Component
@RequiredArgsConstructor
public class ComposeBatchActivityProbe implements BatchTaskExternalActivityProbe {

    private static final String DEL_FLAG_NORMAL = "0";
    private static final String LEGACY_BATCH_PREFIX = "cb_";
    private static final List<String> MEDIA_TERMINAL_STATUSES = List.of(
            MediaTaskStatus.SUCCEEDED.name(), MediaTaskStatus.FAILED.name());

    private final SakuraAudioRecordMapper sakuraAudioRecordMapper;
    private final SakuraMediaTaskMapper sakuraMediaTaskMapper;
    private final SakuraEpisodeEditorMapper sakuraEpisodeEditorMapper;

    @Override
    public boolean supports(String logicalType) {
        return Objects.equals(BatchTaskLogicalType.STORYBOARD_AUDIO_GENERATE, logicalType)
                || Objects.equals(BatchTaskLogicalType.EPISODE_EXPORT, logicalType);
    }

    @Override
    public boolean hasActiveTask(Long projectId, Long episodeId, String logicalType) {
        if (Objects.equals(BatchTaskLogicalType.STORYBOARD_AUDIO_GENERATE, logicalType)) {
            return hasActiveVoiceover(projectId, episodeId);
        }
        return Objects.equals(BatchTaskLogicalType.EPISODE_EXPORT, logicalType)
                && hasActiveExport(projectId, episodeId);
    }

    public boolean hasActiveVoiceover(Long projectId, Long episodeId) {
        Long activeAudioRecords = sakuraAudioRecordMapper.selectCount(Wrappers.<SakuraAudioRecord>lambdaQuery()
                .eq(SakuraAudioRecord::getProjectId, projectId)
                .eq(SakuraAudioRecord::getEpisodeId, episodeId)
                .likeRight(SakuraAudioRecord::getComposeBatchId, LEGACY_BATCH_PREFIX)
                .eq(SakuraAudioRecord::getDelFlag, DEL_FLAG_NORMAL)
                .and(wrapper -> wrapper.isNull(SakuraAudioRecord::getStatus)
                        .or().notIn(SakuraAudioRecord::getStatus, MEDIA_TERMINAL_STATUSES)));
        if (activeAudioRecords != null && activeAudioRecords > 0) {
            return true;
        }

        QueryWrapper<SakuraAudioRecord> succeededBatchQuery = new QueryWrapper<>();
        succeededBatchQuery.select("compose_batch_id")
                .eq("project_id", projectId)
                .eq("episode_id", episodeId)
                .likeRight("compose_batch_id", LEGACY_BATCH_PREFIX)
                .eq("del_flag", DEL_FLAG_NORMAL)
                .groupBy("compose_batch_id")
                .having("SUM(CASE WHEN status IS NULL OR status <> {0} THEN 1 ELSE 0 END) = 0",
                        MediaTaskStatus.SUCCEEDED.name());
        List<String> succeededBatchIds = sakuraAudioRecordMapper.selectObjs(succeededBatchQuery).stream()
                .filter(Objects::nonNull).map(String::valueOf).toList();
        if (succeededBatchIds.isEmpty()) {
            return false;
        }
        List<SakuraMediaTask> composeTasks = sakuraMediaTaskMapper.selectList(
                Wrappers.<SakuraMediaTask>lambdaQuery()
                        .select(SakuraMediaTask::getId, SakuraMediaTask::getComposeBatchId,
                                SakuraMediaTask::getStatus, SakuraMediaTask::getOssUrl)
                        .in(SakuraMediaTask::getComposeBatchId, succeededBatchIds)
                        .eq(SakuraMediaTask::getProjectId, projectId)
                        .eq(SakuraMediaTask::getEpisodeId, episodeId)
                        .eq(SakuraMediaTask::getMediaType, ComposeConstants.MEDIA_TYPE_COMPOSE)
                        .orderByDesc(SakuraMediaTask::getId));
        Map<String, SakuraMediaTask> latestComposeByBatch = new LinkedHashMap<>();
        for (SakuraMediaTask composeTask : composeTasks) {
            latestComposeByBatch.putIfAbsent(composeTask.getComposeBatchId(), composeTask);
        }
        for (String batchId : succeededBatchIds) {
            // 全部 SUCCEEDED 到事件监听器创建 COMPOSE 之间也属于活跃窗口；不能仅靠 Redis 新鲜度。
            SakuraMediaTask composeTask = latestComposeByBatch.get(batchId);
            if (composeTask == null || composeTask.getStatus() == null
                    || composeTask.getStatus().isBlank()
                    || !MEDIA_TERMINAL_STATUSES.contains(composeTask.getStatus())) {
                return true;
            }
            if (MediaTaskStatus.SUCCEEDED.name().equals(composeTask.getStatus())
                    && StrUtil.isBlank(composeTask.getOssUrl())) {
                return true;
            }
        }
        return false;
    }

    public boolean hasActiveExport(Long projectId, Long episodeId) {
        Long active = sakuraEpisodeEditorMapper.selectCount(Wrappers.<SakuraEpisodeEditor>lambdaQuery()
                .eq(SakuraEpisodeEditor::getProjectId, projectId)
                .eq(SakuraEpisodeEditor::getEpisodeId, episodeId)
                .eq(SakuraEpisodeEditor::getExportStatus, ComposeConstants.EXPORT_STATUS_COMPOSING)
                .eq(SakuraEpisodeEditor::getDelFlag, DEL_FLAG_NORMAL));
        return active != null && active > 0;
    }
}
