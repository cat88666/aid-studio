package com.sakura.media.resolver;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import com.sakura.sakura.domain.SakuraGenRecord;
import com.sakura.sakura.domain.media.SakuraMediaResult;
import com.sakura.sakura.domain.media.SakuraMediaTask;
import com.sakura.sakura.mapper.SakuraMediaResultMapper;
import com.sakura.sakura.mapper.SakuraMediaTaskMapper;
import com.sakura.sakura.service.ISakuraGenRecordService;
import com.sakura.common.sakura.oss.util.MediaUrlResolver;
import com.sakura.common.exception.ServiceException;
import com.sakura.enums.GenTypeEnum;
import com.sakura.media.dto.MediaVideoGenerateRequest;
import com.sakura.media.dto.ReferenceVideoInput;
import com.sakura.media.service.VerifiedMediaMetadataService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 按业务记录解析服务端可信参考视频。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReferenceVideoRecordResolver
{
    private static final int RECORD_STATUS_SUCCEEDED = 1;
    private static final String TASK_STATUS_SUCCEEDED = "SUCCEEDED";
    private static final String DEL_FLAG_NORMAL = "0";
    private static final Set<String> VIDEO_GEN_TYPES = Set.of(
            GenTypeEnum.I2V.getValue(), GenTypeEnum.MULTI.getValue(), GenTypeEnum.EDGE.getValue(),
            GenTypeEnum.UPLOAD_VIDEO.getValue(), GenTypeEnum.COMPOSE.getValue());

    private final ISakuraGenRecordService genRecordService;
    private final SakuraMediaTaskMapper mediaTaskMapper;
    private final SakuraMediaResultMapper mediaResultMapper;
    private final MediaUrlResolver mediaUrlResolver;
    private final VerifiedMediaMetadataService verifiedMediaMetadataService;

    /** 仅防止异常请求耗尽探测资源，模型实际数量上限仍由 capability 校验。 */
    @Value("${sakura.media.reference-video-resolve-max:50}")
    private int maxRecordsPerRequest = 50;

    /**
     * 重新解析请求中的记录 ID，并覆盖任何调用方预填的内部解析结果。
     *
     * @param request 媒体视频请求
     * @param userId 当前用户 ID
     */
    public void resolveAndApply(MediaVideoGenerateRequest request, Long userId)
    {
        resolveAndApply(request, userId, false);
    }

    /**
     * Resolve record ownership and trusted persisted metadata. Quote paths pass
     * {@code false}; submission paths pass {@code true} so missing metadata may be
     * completed from the server-owned media object before the upstream call.
     */
    public void resolveAndApply(MediaVideoGenerateRequest request, Long userId, boolean allowProbe)
    {
        if (Objects.isNull(request))
        {
            return;
        }
        request.setResolvedReferenceVideos(Collections.emptyList());
        if (CollectionUtil.isEmpty(request.getReferenceVideoRecordIds()))
        {
            return;
        }
        List<ReferenceVideoInput> resolved = resolve(
                request.getReferenceVideoRecordIds(), userId, request.getProjectId(), allowProbe);
        canonicalizeOptions(request, resolved);
        request.setResolvedReferenceVideos(resolved);
    }

    /**
     * 按提交顺序解析参考视频记录，重复 ID 只保留首次。
     *
     * @param recordIds sakura_gen_record 主键
     * @param userId 当前用户 ID
     * @param projectId 当前项目 ID
     * @return 服务端可信参考视频
     */
    public List<ReferenceVideoInput> resolve(List<Long> recordIds, Long userId, Long projectId)
    {
        return resolve(recordIds, userId, projectId, true);
    }

    /**
     * @param allowProbe whether the submission path may download the complete
     *                   server-owned object and obtain authoritative metadata
     */
    public List<ReferenceVideoInput> resolve(List<Long> recordIds, Long userId, Long projectId,
            boolean allowProbe)
    {
        if (CollectionUtil.isEmpty(recordIds))
        {
            return Collections.emptyList();
        }
        if (Objects.isNull(userId) || userId <= 0 || Objects.isNull(projectId) || projectId <= 0)
        {
            log.info("参考视频缺少归属范围: userId={}, projectId={}", userId, projectId);
            throw new ServiceException("参考视频不可用");
        }
        LinkedHashSet<Long> orderedIds = new LinkedHashSet<>();
        for (Long id : recordIds)
        {
            if (Objects.isNull(id) || id <= 0)
            {
                log.info("参考视频记录ID非法: id={}", id);
                throw new ServiceException("参考视频不可用");
            }
            orderedIds.add(id);
        }
        if (maxRecordsPerRequest <= 0 || orderedIds.size() > maxRecordsPerRequest)
        {
            log.info("参考视频记录数量超过解析上限: max={}, actual={}",
                    maxRecordsPerRequest, orderedIds.size());
            throw new ServiceException("参考视频数量超限");
        }

        List<SakuraGenRecord> records = genRecordService.list(
                Wrappers.<SakuraGenRecord>lambdaQuery()
                        .select(SakuraGenRecord::getId, SakuraGenRecord::getUserId, SakuraGenRecord::getProjectId,
                                SakuraGenRecord::getGenType, SakuraGenRecord::getFileUrl, SakuraGenRecord::getStatus,
                                SakuraGenRecord::getDelFlag, SakuraGenRecord::getBizSeq, SakuraGenRecord::getTaskId)
                        .in(SakuraGenRecord::getId, orderedIds));
        Map<Long, SakuraGenRecord> byId = new LinkedHashMap<>();
        for (SakuraGenRecord record : records)
        {
            byId.put(record.getId(), record);
        }
        List<Long> invalidIds = new ArrayList<>();
        for (Long id : orderedIds)
        {
            SakuraGenRecord record = byId.get(id);
            if (!isUsableRecord(record, userId, projectId))
            {
                invalidIds.add(id);
            }
        }
        if (CollectionUtil.isNotEmpty(invalidIds))
        {
            log.info("参考视频记录不可用: userId={}, projectId={}, ids={}", userId, projectId, invalidIds);
            throw new ServiceException("参考视频不可用");
        }

        List<SakuraMediaTask> relatedTasks = loadRelatedTasks(records, userId, projectId);
        Map<Long, SakuraMediaResult> resultByTaskId = loadMediaResults(relatedTasks);
        List<ReferenceVideoInput> resolved = new ArrayList<>(orderedIds.size());
        Set<String> emittedUrls = new LinkedHashSet<>();
        for (Long id : orderedIds)
        {
            SakuraGenRecord record = byId.get(id);
            String fullUrl = resolveHttpUrl(record.getFileUrl());
            if (StrUtil.isBlank(fullUrl))
            {
                log.info("参考视频地址不可用: recordId={}", record.getId());
                throw new ServiceException("参考视频不可用");
            }
            TrustedMetadata metadata = resolveMetadata(
                    record, fullUrl, relatedTasks, resultByTaskId, allowProbe);
            if (allowProbe && (Objects.isNull(metadata.durationMs) || metadata.durationMs <= 0))
            {
                log.info("参考视频时长不可用: recordId={}", record.getId());
                throw new ServiceException("视频时长未知");
            }
            if (!emittedUrls.add(fullUrl))
            {
                continue;
            }
            resolved.add(new ReferenceVideoInput(record.getId(), fullUrl, metadata.durationMs,
                    metadata.fileSizeBytes, metadata.width, metadata.height, metadata.fps,
                    StrUtil.trimToNull(metadata.format)));
        }
        return List.copyOf(resolved);
    }

    /** 已选择记录 ID 时，旧 URL 别名只允许表达同一组记录，不允许夹带额外裸 URL。 */
    private void canonicalizeOptions(MediaVideoGenerateRequest request, List<ReferenceVideoInput> resolved)
    {
        Map<String, Object> options = request.getOptions() == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(request.getOptions());
        Set<String> resolvedUrls = new LinkedHashSet<>();
        for (ReferenceVideoInput input : resolved)
        {
            resolvedUrls.add(input.getVideoUrl());
        }
        Set<String> declaredUrls = new LinkedHashSet<>();
        for (String key : List.of("featureVideoUrl", "referenceVideoUrl", "baseVideoUrl", "inputVideoUrl",
                "videoUrl", "video_url", "referenceVideos", "videos"))
        {
            addDeclaredUrls(declaredUrls, options.get(key));
        }
        if (declaredUrls.stream().anyMatch(value -> !resolvedUrls.contains(value)))
        {
            log.info("参考视频记录与裸URL冲突: recordCount={}, declaredCount={}",
                    resolved.size(), declaredUrls.size());
            throw new ServiceException("参考视频配置冲突");
        }
        for (String key : List.of("featureVideoUrl", "referenceVideoUrl", "baseVideoUrl", "inputVideoUrl",
                "videoUrl", "video_url", "referenceVideos", "videos"))
        {
            options.remove(key);
        }
        for (String key : List.of("referenceVideoDurations", "videoDurations", "inputVideoDurations",
                "inputVideoSeconds", "referenceVideoSeconds", "videoSeconds"))
        {
            options.remove(key);
        }
        options.put("referenceVideos", new ArrayList<>(resolvedUrls));
        boolean durationsComplete = resolved.stream().allMatch(input ->
                Objects.nonNull(input.getDurationMs()) && input.getDurationMs() > 0);
        if (durationsComplete)
        {
            options.put("referenceVideoDurations", resolved.stream()
                    .map(ReferenceVideoInput::getDurationMs)
                    .map(value -> BigDecimal.valueOf(value).movePointLeft(3).stripTrailingZeros())
                    .toList());
            long totalMs = resolved.stream()
                    .map(ReferenceVideoInput::getDurationMs)
                    .mapToLong(Long::longValue)
                    .sum();
            int totalSeconds = Math.toIntExact(Math.floorDiv(Math.addExact(totalMs, 999L), 1000L));
            options.put("referenceVideoSeconds", totalSeconds);
        }
        request.setOptions(options);
    }

    private void addDeclaredUrls(Set<String> target, Object value)
    {
        if (value instanceof List<?> values)
        {
            for (Object item : values)
            {
                addDeclaredUrls(target, item);
            }
            return;
        }
        if (value instanceof Map<?, ?> source)
        {
            for (String key : List.of("url", "videoUrl", "video_url", "fileUrl", "file_url"))
            {
                if (source.containsKey(key))
                {
                    addDeclaredUrls(target, source.get(key));
                    return;
                }
            }
        }
        if (Objects.nonNull(value) && StrUtil.isNotBlank(String.valueOf(value)))
        {
            String declaredUrl = String.valueOf(value).trim();
            if (mediaUrlResolver.isSiteImageUrl(declaredUrl))
            {
                declaredUrl = StrUtil.blankToDefault(mediaUrlResolver.toFullUrl(declaredUrl), declaredUrl);
            }
            target.add(declaredUrl);
        }
    }

    private boolean isUsableRecord(SakuraGenRecord record, Long userId, Long projectId)
    {
        return Objects.nonNull(record)
                && Objects.equals(userId, record.getUserId())
                && Objects.equals(projectId, record.getProjectId())
                && Objects.equals(RECORD_STATUS_SUCCEEDED, record.getStatus())
                && Objects.equals(DEL_FLAG_NORMAL, record.getDelFlag())
                && VIDEO_GEN_TYPES.contains(record.getGenType())
                && StrUtil.isNotBlank(record.getFileUrl());
    }

    private String resolveHttpUrl(String storedUrl)
    {
        if (!mediaUrlResolver.isSiteImageUrl(storedUrl))
        {
            return null;
        }
        String fullUrl = StrUtil.trimToNull(mediaUrlResolver.toFullUrl(storedUrl));
        if (StrUtil.isBlank(fullUrl))
        {
            return null;
        }
        String lower = fullUrl.toLowerCase(java.util.Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://") ? fullUrl : null;
    }

    private List<SakuraMediaTask> loadRelatedTasks(List<SakuraGenRecord> records, Long userId, Long projectId)
    {
        Set<Long> recordIds = new LinkedHashSet<>();
        Set<Long> bizSeqs = new LinkedHashSet<>();
        Set<Long> localTaskIds = new LinkedHashSet<>();
        Set<String> providerTaskIds = new LinkedHashSet<>();
        for (SakuraGenRecord record : records)
        {
            recordIds.add(record.getId());
            if (Objects.nonNull(record.getBizSeq()))
            {
                bizSeqs.add(record.getBizSeq());
            }
            String taskId = StrUtil.trimToNull(record.getTaskId());
            if (StrUtil.isNotBlank(taskId))
            {
                providerTaskIds.add(taskId);
                try
                {
                    localTaskIds.add(Long.valueOf(taskId));
                }
                catch (NumberFormatException ignore)
                {
                    // 非数字值是上游任务 ID，仅进入 provider_task_id 查询。
                }
            }
        }
        Map<Long, SakuraMediaTask> tasks = new LinkedHashMap<>();
        addTasks(tasks, selectTasks(userId, projectId, "callback", recordIds, Collections.emptySet()));
        addTasks(tasks, selectTasks(userId, projectId, "biz", bizSeqs, Collections.emptySet()));
        addTasks(tasks, selectTasks(userId, projectId, "id", localTaskIds, Collections.emptySet()));
        addTasks(tasks, selectTasks(userId, projectId, "provider", Collections.emptySet(), providerTaskIds));
        return new ArrayList<>(tasks.values());
    }

    private List<SakuraMediaTask> selectTasks(Long userId, Long projectId, String key,
            Set<Long> longValues, Set<String> textValues)
    {
        if (CollectionUtil.isEmpty(longValues) && CollectionUtil.isEmpty(textValues))
        {
            return Collections.emptyList();
        }
        var query = Wrappers.<SakuraMediaTask>lambdaQuery()
                .select(SakuraMediaTask::getId, SakuraMediaTask::getUserId, SakuraMediaTask::getProjectId,
                        SakuraMediaTask::getStatus, SakuraMediaTask::getOutputDurationSeconds,
                        SakuraMediaTask::getCallbackRecordId, SakuraMediaTask::getBizTaskId,
                        SakuraMediaTask::getProviderTaskId, SakuraMediaTask::getOssUrl)
                .eq(SakuraMediaTask::getUserId, userId)
                .eq(SakuraMediaTask::getProjectId, projectId)
                .eq(SakuraMediaTask::getStatus, TASK_STATUS_SUCCEEDED);
        switch (key)
        {
            case "callback" -> query.in(SakuraMediaTask::getCallbackRecordId, longValues);
            case "biz" -> query.in(SakuraMediaTask::getBizTaskId, longValues);
            case "id" -> query.in(SakuraMediaTask::getId, longValues);
            case "provider" -> query.in(SakuraMediaTask::getProviderTaskId, textValues);
            default -> throw new IllegalArgumentException("unsupported media task lookup");
        }
        return mediaTaskMapper.selectList(query);
    }

    private void addTasks(Map<Long, SakuraMediaTask> target, List<SakuraMediaTask> values)
    {
        for (SakuraMediaTask task : values)
        {
            target.putIfAbsent(task.getId(), task);
        }
    }

    private Map<Long, SakuraMediaResult> loadMediaResults(List<SakuraMediaTask> tasks)
    {
        if (CollectionUtil.isEmpty(tasks))
        {
            return Collections.emptyMap();
        }
        List<Long> taskIds = tasks.stream().map(SakuraMediaTask::getId).filter(Objects::nonNull).toList();
        List<SakuraMediaResult> results = mediaResultMapper.selectList(
                Wrappers.<SakuraMediaResult>lambdaQuery()
                        .select(SakuraMediaResult::getTaskId, SakuraMediaResult::getFileSize,
                                SakuraMediaResult::getDurationSeconds, SakuraMediaResult::getMimeType)
                        .in(SakuraMediaResult::getTaskId, taskIds));
        Map<Long, SakuraMediaResult> result = new LinkedHashMap<>();
        for (SakuraMediaResult row : results)
        {
            result.putIfAbsent(row.getTaskId(), row);
        }
        return result;
    }

    private TrustedMetadata resolveMetadata(SakuraGenRecord record, String fullUrl,
            List<SakuraMediaTask> tasks, Map<Long, SakuraMediaResult> resultByTaskId, boolean allowProbe)
    {
        TrustedMetadata metadata = new TrustedMetadata();
        SakuraMediaTask related = findRelatedTask(record, tasks);
        if (Objects.nonNull(related))
        {
            if (Objects.nonNull(related.getOutputDurationSeconds()) && related.getOutputDurationSeconds() > 0)
            {
                metadata.durationMs = Math.multiplyExact(related.getOutputDurationSeconds(), 1000L);
            }
            SakuraMediaResult result = resultByTaskId.get(related.getId());
            if (Objects.nonNull(result))
            {
                if (Objects.isNull(metadata.durationMs) && Objects.nonNull(result.getDurationSeconds())
                        && result.getDurationSeconds() > 0)
                {
                    metadata.durationMs = result.getDurationSeconds() * 1000L;
                }
                if (Objects.nonNull(result.getFileSize()) && result.getFileSize() > 0)
                {
                    metadata.fileSizeBytes = result.getFileSize();
                }
                metadata.format = formatFromMime(result.getMimeType());
            }
        }

        if (!allowProbe)
        {
            return metadata;
        }
        VerifiedMediaMetadataService.Metadata inspected =
                verifiedMediaMetadataService.inspect(fullUrl, "video");
        TrustedMetadata probed = new TrustedMetadata();
        probed.fileSizeBytes = inspected.sizeBytes() > 0 ? inspected.sizeBytes() : null;
        probed.durationMs = decimalMilliseconds(inspected.durationSeconds());
        probed.width = inspected.width() > 0 ? inspected.width() : null;
        probed.height = inspected.height() > 0 ? inspected.height() : null;
        probed.fps = inspected.fps() != null && inspected.fps().signum() > 0
                ? inspected.fps() : null;
        probed.format = StrUtil.trimToNull(inspected.format());
        metadata.merge(probed);
        return metadata;
    }

    private SakuraMediaTask findRelatedTask(SakuraGenRecord record, List<SakuraMediaTask> tasks)
    {
        String recordTaskId = StrUtil.trimToNull(record.getTaskId());
        for (SakuraMediaTask task : tasks)
        {
            if (Objects.equals(record.getId(), task.getCallbackRecordId())
                    || (Objects.nonNull(record.getBizSeq())
                            && Objects.equals(record.getBizSeq(), task.getBizTaskId()))
                    || (Objects.nonNull(recordTaskId) && Objects.nonNull(task.getId())
                            && Objects.equals(recordTaskId, String.valueOf(task.getId())))
                    || (Objects.nonNull(recordTaskId)
                            && Objects.equals(recordTaskId, StrUtil.trimToNull(task.getProviderTaskId()))))
            {
                return task;
            }
        }
        return null;
    }

    private Long decimalMilliseconds(BigDecimal seconds)
    {
        if (seconds == null || seconds.signum() <= 0)
        {
            return null;
        }
        return seconds.multiply(BigDecimal.valueOf(1000L))
                .setScale(0, RoundingMode.CEILING).longValueExact();
    }

    private String formatFromMime(String mimeType)
    {
        if (StrUtil.isBlank(mimeType) || !mimeType.contains("/"))
        {
            return null;
        }
        String subtype = StrUtil.trimToNull(mimeType.substring(mimeType.indexOf('/') + 1));
        return "quicktime".equalsIgnoreCase(subtype) ? "mov" : subtype;
    }

    private static final class TrustedMetadata
    {
        private Long durationMs;
        private Long fileSizeBytes;
        private Integer width;
        private Integer height;
        private BigDecimal fps;
        private String format;

        private void merge(TrustedMetadata value)
        {
            if (Objects.isNull(value))
            {
                return;
            }
            if (Objects.nonNull(value.durationMs)) { durationMs = value.durationMs; }
            if (Objects.nonNull(value.fileSizeBytes)) { fileSizeBytes = value.fileSizeBytes; }
            if (Objects.nonNull(value.width)) { width = value.width; }
            if (Objects.nonNull(value.height)) { height = value.height; }
            if (Objects.nonNull(value.fps)) { fps = value.fps; }
            if (StrUtil.isNotBlank(value.format)) { format = value.format; }
        }
    }

}
