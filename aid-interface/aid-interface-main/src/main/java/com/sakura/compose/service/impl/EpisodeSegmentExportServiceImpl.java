package com.sakura.compose.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.sakura.sakura.domain.SakuraAudioRecord;
import com.sakura.sakura.domain.SakuraComicProject;
import com.sakura.sakura.domain.SakuraGenRecord;
import com.sakura.sakura.domain.SakuraStoryboard;
import com.sakura.sakura.mapper.SakuraAudioRecordMapper;
import com.sakura.sakura.mapper.SakuraGenRecordMapper;
import com.sakura.sakura.service.ISakuraComicProjectService;
import com.sakura.sakura.service.ISakuraStoryboardService;
import com.sakura.common.exception.ServiceException;
import com.sakura.common.utils.SecurityUtils;
import com.sakura.compose.dto.EpisodeSegmentVideoItem;
import com.sakura.compose.dto.EpisodeSegmentVideosRequest;
import com.sakura.compose.dto.EpisodeSegmentVideosResult;
import com.sakura.compose.service.EpisodeSegmentExportService;
import com.sakura.media.enums.MediaTaskStatus;
import com.sakura.voice.util.DialogueSubtitleFormatter;
import com.sakura.voice.util.DialogueTextSanitizer;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 分段素材批量导出实现：分镜最终视频 + 配音 + 对口型视频清单（只读查询，不产生任务与扣费）。
 *
 * @author 视觉AID
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EpisodeSegmentExportServiceImpl implements EpisodeSegmentExportService {

    /** 删除标志：正常 */
    private static final String DEL_FLAG_NORMAL = "0";

    /** 分镜服务（取顺序/台词/最终选中ID） */
    private final ISakuraStoryboardService sakuraStoryboardService;

    /** 项目服务（归属校验） */
    private final ISakuraComicProjectService sakuraComicProjectService;

    /** 抽卡记录 Mapper（最终视频） */
    private final SakuraGenRecordMapper sakuraGenRecordMapper;

    /** 配音记录 Mapper（配音/对口型视频） */
    private final SakuraAudioRecordMapper sakuraAudioRecordMapper;

    @Override
    public EpisodeSegmentVideosResult listSegmentVideos(EpisodeSegmentVideosRequest request) {
        if (Objects.isNull(request) || Objects.isNull(request.getProjectId())
                || Objects.isNull(request.getEpisodeId())) {
            log.error("分段素材清单入参非法: projectId/episodeId 为空");
            throw new ServiceException("参数有误");
        }
        Long userId = SecurityUtils.getUserId();
        // 项目归属校验：只能导出自己项目的素材
        SakuraComicProject project = sakuraComicProjectService.getById(request.getProjectId());
        if (Objects.isNull(project) || !Objects.equals(project.getUserId(), userId)) {
            log.error("分段素材清单项目缺失或越权, projectId={}, userId={}", request.getProjectId(), userId);
            throw new ServiceException("项目不存在");
        }

        // 查询字段精简：清单只需顺序/标题/台词/最终选中ID（新增出参字段时此处必须同步补充）
        List<SakuraStoryboard> storyboards = sakuraStoryboardService.list(
                Wrappers.<SakuraStoryboard>lambdaQuery()
                        .select(SakuraStoryboard::getId, SakuraStoryboard::getSortOrder, SakuraStoryboard::getTitle,
                                SakuraStoryboard::getDialogueText, SakuraStoryboard::getFinalVideoId,
                                SakuraStoryboard::getFinalAudioId)
                        .eq(SakuraStoryboard::getProjectId, request.getProjectId())
                        .eq(SakuraStoryboard::getEpisodeId, request.getEpisodeId())
                        .eq(SakuraStoryboard::getUserId, userId)
                        .eq(SakuraStoryboard::getDelFlag, DEL_FLAG_NORMAL)
                        .orderByAsc(SakuraStoryboard::getSortOrder)
                        .orderByAsc(SakuraStoryboard::getId));

        EpisodeSegmentVideosResult result = new EpisodeSegmentVideosResult();
        result.setProjectId(request.getProjectId());
        result.setEpisodeId(request.getEpisodeId());
        result.setItems(new ArrayList<>());
        if (CollectionUtil.isEmpty(storyboards)) {
            result.setTotalSegments(0);
            result.setVideoReadyCount(0);
            result.setDubbedCount(0);
            return result;
        }

        Map<Long, SakuraGenRecord> videoById = loadFinalVideos(storyboards, userId);
        Map<Long, SakuraAudioRecord> audioBySb = loadAudioRecords(storyboards, userId);

        int videoReady = 0;
        int dubbed = 0;
        for (SakuraStoryboard sb : storyboards) {
            EpisodeSegmentVideoItem item = new EpisodeSegmentVideoItem();
            item.setStoryboardId(sb.getId());
            item.setSortOrder(Objects.isNull(sb.getSortOrder()) ? null : sb.getSortOrder().intValue());
            item.setTitle(sb.getTitle());
            // 字幕统一格式化：剥 [角色_形象] 等结构标记，输出「人物：说的话」
            item.setSubtitle(DialogueSubtitleFormatter.format(sb.getDialogueText()));

            SakuraGenRecord video = Objects.isNull(sb.getFinalVideoId()) ? null : videoById.get(sb.getFinalVideoId());
            if (Objects.nonNull(video)) {
                item.setGenRecordId(video.getId());
                item.setVideoUrl(video.getFileUrl());
                item.setVideoDurationSeconds(video.getVideoDuration());
                videoReady++;
            }
            SakuraAudioRecord audio = audioBySb.get(sb.getId());
            if (Objects.nonNull(audio)) {
                item.setAudioRecordId(audio.getId());
                item.setAudioUrl(audio.getAudioUrl());
                item.setAudioDurationMs(audio.getDurationMs());
                item.setLipSyncVideoUrl(StrUtil.isBlank(audio.getSyncVideoUrl()) ? null : audio.getSyncVideoUrl());
                dubbed++;
            }
            item.setHasDubbing(Objects.nonNull(audio));
            result.getItems().add(item);
        }
        result.setTotalSegments(storyboards.size());
        result.setVideoReadyCount(videoReady);
        result.setDubbedCount(dubbed);
        return result;
    }

    /**
     * 批量加载各分镜的最终视频（final_video_id 指向的抽卡记录，即被设为主视频的那条）。
     *
     * @param storyboards 分镜列表
     * @param userId      当前用户ID
     * @return 记录ID → 抽卡记录
     */
    private Map<Long, SakuraGenRecord> loadFinalVideos(List<SakuraStoryboard> storyboards, Long userId) {
        Set<Long> finalVideoIds = new LinkedHashSet<>();
        for (SakuraStoryboard sb : storyboards) {
            if (Objects.nonNull(sb.getFinalVideoId())) {
                finalVideoIds.add(sb.getFinalVideoId());
            }
        }
        Map<Long, SakuraGenRecord> result = new HashMap<>();
        if (finalVideoIds.isEmpty()) {
            return result;
        }
        // 查询字段精简：清单只需地址/时长（新增出参字段时此处必须同步补充）
        List<SakuraGenRecord> records = sakuraGenRecordMapper.selectList(
                new LambdaQueryWrapper<SakuraGenRecord>()
                        .select(SakuraGenRecord::getId, SakuraGenRecord::getFileUrl, SakuraGenRecord::getVideoDuration)
                        .in(SakuraGenRecord::getId, finalVideoIds)
                        .eq(SakuraGenRecord::getUserId, userId)
                        .eq(SakuraGenRecord::getDelFlag, DEL_FLAG_NORMAL)
                        .isNotNull(SakuraGenRecord::getFileUrl));
        for (SakuraGenRecord record : records) {
            result.put(record.getId(), record);
        }
        return result;
    }

    /**
     * 批量加载各分镜配音：优先最终选中（final_audio_id），其次最新成功；均无则该段无配音。
     * 与时间轴自动初始化同口径，保证导出素材与剪辑器展示一致。
     *
     * @param storyboards 分镜列表
     * @param userId      当前用户ID
     * @return 分镜ID → 配音记录
     */
    private Map<Long, SakuraAudioRecord> loadAudioRecords(List<SakuraStoryboard> storyboards, Long userId) {
        Set<Long> storyboardIds = new LinkedHashSet<>();
        for (SakuraStoryboard sb : storyboards) {
            storyboardIds.add(sb.getId());
        }
        // 查询字段精简：清单只需地址/时长/对口型视频/配音文本（新增出参字段时此处必须同步补充）
        List<SakuraAudioRecord> records = sakuraAudioRecordMapper.selectList(
                new LambdaQueryWrapper<SakuraAudioRecord>()
                        .select(SakuraAudioRecord::getId, SakuraAudioRecord::getStoryboardId, SakuraAudioRecord::getAudioUrl,
                                SakuraAudioRecord::getDurationMs, SakuraAudioRecord::getSyncVideoUrl,
                                SakuraAudioRecord::getTtsText)
                        .eq(SakuraAudioRecord::getUserId, userId)
                        .in(SakuraAudioRecord::getStoryboardId, storyboardIds)
                        .eq(SakuraAudioRecord::getStatus, MediaTaskStatus.SUCCEEDED.name())
                        .eq(SakuraAudioRecord::getDelFlag, DEL_FLAG_NORMAL)
                        .isNotNull(SakuraAudioRecord::getAudioUrl)
                        .orderByAsc(SakuraAudioRecord::getId));
        Map<Long, SakuraAudioRecord> byId = new HashMap<>();
        // 后写覆盖（id 升序遍历）：latestBySb 即该分镜最新成功配音
        Map<Long, SakuraAudioRecord> latestBySb = new HashMap<>();
        for (SakuraAudioRecord record : records) {
            // 「无台词」占位配音（历史脏数据，内容为朗读"无"字）不进导出清单：该段视为无配音
            if (DialogueTextSanitizer.isNoDialoguePlaceholder(record.getTtsText())) {
                continue;
            }
            byId.put(record.getId(), record);
            latestBySb.put(record.getStoryboardId(), record);
        }
        Map<Long, SakuraAudioRecord> result = new HashMap<>();
        for (SakuraStoryboard sb : storyboards) {
            SakuraAudioRecord chosen = Objects.nonNull(sb.getFinalAudioId()) ? byId.get(sb.getFinalAudioId()) : null;
            if (Objects.isNull(chosen)) {
                chosen = latestBySb.get(sb.getId());
            }
            if (Objects.nonNull(chosen)) {
                result.put(sb.getId(), chosen);
            }
        }
        return result;
    }
}
