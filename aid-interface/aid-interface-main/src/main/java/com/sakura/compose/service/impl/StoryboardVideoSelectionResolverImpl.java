package com.sakura.compose.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.sakura.sakura.domain.SakuraGenRecord;
import com.sakura.sakura.domain.SakuraStoryboard;
import com.sakura.sakura.mapper.SakuraGenRecordMapper;
import com.sakura.sakura.service.ISakuraStoryboardService;
import com.sakura.compose.service.StoryboardVideoSelectionResolver;
import com.sakura.enums.GenTypeEnum;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 分镜当前选用视频解析实现。
 *
 * @author 视觉AID
 */
@Service
@RequiredArgsConstructor
public class StoryboardVideoSelectionResolverImpl implements StoryboardVideoSelectionResolver {

    private static final String DEL_FLAG_NORMAL = "0";
    private static final int GEN_STATUS_SUCCESS = 1;
    private static final int SELECTED_YES = 1;
    private static final List<String> VIDEO_GEN_TYPES = List.of(
            GenTypeEnum.I2V.getValue(), GenTypeEnum.MULTI.getValue(), GenTypeEnum.EDGE.getValue(),
            GenTypeEnum.UPLOAD_VIDEO.getValue(), GenTypeEnum.COMPOSE.getValue());

    private final ISakuraStoryboardService sakuraStoryboardService;
    private final SakuraGenRecordMapper sakuraGenRecordMapper;

    @Override
    public Map<Long, SakuraGenRecord> resolve(Long projectId, Long episodeId, Long userId,
                                           Collection<Long> storyboardIds) {
        if (projectId == null || episodeId == null || userId == null
                || CollectionUtil.isEmpty(storyboardIds)) {
            return Map.of();
        }
        Set<Long> distinctIds = new LinkedHashSet<>();
        for (Long storyboardId : storyboardIds) {
            if (storyboardId != null) {
                distinctIds.add(storyboardId);
            }
        }
        if (distinctIds.isEmpty()) {
            return Map.of();
        }
        // 查询字段精简：解析只消费主键与最终视频指针。
        List<SakuraStoryboard> storyboards = sakuraStoryboardService.list(
                Wrappers.<SakuraStoryboard>lambdaQuery()
                        .select(SakuraStoryboard::getId, SakuraStoryboard::getFinalVideoId)
                        .eq(SakuraStoryboard::getProjectId, projectId)
                        .eq(SakuraStoryboard::getEpisodeId, episodeId)
                        .eq(SakuraStoryboard::getUserId, userId)
                        .eq(SakuraStoryboard::getDelFlag, DEL_FLAG_NORMAL)
                        .in(SakuraStoryboard::getId, distinctIds));
        if (CollectionUtil.isEmpty(storyboards)) {
            return Map.of();
        }
        return resolveRecords(storyboards, userId);
    }

    /** 查询候选记录并按统一优先级选出每个分镜的一条。 */
    private Map<Long, SakuraGenRecord> resolveRecords(List<SakuraStoryboard> storyboards, Long userId) {
        Set<Long> storyboardIds = new LinkedHashSet<>();
        for (SakuraStoryboard storyboard : storyboards) {
            storyboardIds.add(storyboard.getId());
        }
        // 查询字段精简：调用方只消费地址、时长、记录ID与类型。
        List<SakuraGenRecord> records = sakuraGenRecordMapper.selectList(
                new LambdaQueryWrapper<SakuraGenRecord>()
                        .select(SakuraGenRecord::getId, SakuraGenRecord::getStoryboardId, SakuraGenRecord::getFileUrl,
                                SakuraGenRecord::getVideoDuration, SakuraGenRecord::getIsSelected,
                                SakuraGenRecord::getGenType, SakuraGenRecord::getUpdateTime)
                        .eq(SakuraGenRecord::getUserId, userId)
                        .in(SakuraGenRecord::getStoryboardId, storyboardIds)
                        .in(SakuraGenRecord::getGenType, VIDEO_GEN_TYPES)
                        .eq(SakuraGenRecord::getStatus, GEN_STATUS_SUCCESS)
                        .eq(SakuraGenRecord::getDelFlag, DEL_FLAG_NORMAL)
                        .isNotNull(SakuraGenRecord::getFileUrl)
                        .orderByAsc(SakuraGenRecord::getId));
        Map<Long, SakuraGenRecord> byId = new HashMap<>();
        Map<Long, SakuraGenRecord> latestByStoryboard = new HashMap<>();
        Map<Long, SakuraGenRecord> latestSelectedByStoryboard = new HashMap<>();
        Map<Long, SakuraGenRecord> latestSelectedComposeByStoryboard = new HashMap<>();
        for (SakuraGenRecord record : records) {
            byId.put(record.getId(), record);
            latestByStoryboard.put(record.getStoryboardId(), record);
            if (Objects.equals(record.getIsSelected(), SELECTED_YES)) {
                latestSelectedByStoryboard.put(record.getStoryboardId(), record);
                if (GenTypeEnum.COMPOSE.getValue().equals(record.getGenType())) {
                    latestSelectedComposeByStoryboard.put(record.getStoryboardId(), record);
                }
            }
        }
        Map<Long, SakuraGenRecord> result = new HashMap<>();
        for (SakuraStoryboard storyboard : storyboards) {
            SakuraGenRecord selectedCompose = latestSelectedComposeByStoryboard.get(storyboard.getId());
            SakuraGenRecord selectedOriginal = storyboard.getFinalVideoId() == null
                    ? null : byId.get(storyboard.getFinalVideoId());
            // 原视频与配音成片是上下游关系：两者历史上可能同时保持选中，必须取最后一次明确选择的记录。
            SakuraGenRecord chosen = chooseLatestSelection(selectedOriginal, selectedCompose);
            if (chosen == null) {
                chosen = latestSelectedByStoryboard.get(storyboard.getId());
            }
            if (chosen == null) {
                chosen = latestByStoryboard.get(storyboard.getId());
            }
            if (chosen != null) {
                result.put(storyboard.getId(), chosen);
            }
        }
        return result;
    }

    /** 历史双选数据按更新时间判定最后一次选择，时间缺失时以记录ID作为稳定兜底。 */
    SakuraGenRecord chooseLatestSelection(SakuraGenRecord original, SakuraGenRecord compose) {
        if (original == null) {
            return compose;
        }
        if (compose == null) {
            return original;
        }
        Date originalTime = original.getUpdateTime();
        Date composeTime = compose.getUpdateTime();
        if (originalTime != null && composeTime != null && !originalTime.equals(composeTime)) {
            return originalTime.after(composeTime) ? original : compose;
        }
        return original.getId() >= compose.getId() ? original : compose;
    }
}
