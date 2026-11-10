package com.sakura.project.snapshot.service.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.sakura.sakura.domain.SakuraAudioAsset;
import com.sakura.sakura.domain.SakuraAudioRecord;
import com.sakura.sakura.domain.SakuraComicEpisode;
import com.sakura.sakura.domain.SakuraComicProject;
import com.sakura.sakura.domain.SakuraComicScript;
import com.sakura.sakura.domain.SakuraEpisodeEditor;
import com.sakura.sakura.domain.SakuraGenRecord;
import com.sakura.sakura.domain.SakuraProjectGenConfig;
import com.sakura.sakura.domain.SakuraRolePropScene;
import com.sakura.sakura.domain.SakuraRolePropSceneForm;
import com.sakura.sakura.domain.SakuraRolePropSceneFormImage;
import com.sakura.sakura.domain.SakuraRoleVoiceBinding;
import com.sakura.sakura.domain.SakuraScenePlot;
import com.sakura.sakura.domain.SakuraStoryboard;
import com.sakura.common.sakura.oss.util.MediaUrlResolver;
import com.sakura.compose.dto.timeline.TimelineData;
import com.sakura.compose.dto.timeline.TimelineSegment;
import com.sakura.enums.GenTypeEnum;
import com.sakura.episode.vo.UserEpisodeVO;
import com.sakura.project.snapshot.model.ProjectSnapshotDocument;
import com.sakura.project.vo.UserProjectVO;
import com.sakura.rps.resolver.StoryboardAudioReferenceResolver;
import com.sakura.rps.resolver.StoryboardAudioReferenceResolver.DialogueSegment;
import com.sakura.rps.vo.RpsAssetVO;
import com.sakura.rps.vo.RpsFormImageVO;
import com.sakura.rps.vo.RpsFormVO;
import com.sakura.rps.voice.vo.RoleVoiceBindingVO;
import com.sakura.script.helper.UserScriptContentHash;
import com.sakura.script.vo.UserScriptVO;
import com.sakura.storyboard.vo.StoryboardRefImageVO;
import com.sakura.storyboard.vo.StoryboardSpeakerVoiceVO;
import com.sakura.storyboard.vo.StoryboardVO;
import com.sakura.voice.util.DialogueSubtitleFormatter;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 把冻结的发布文档装配成与正常工作台一致的公开 VO 契约。 */
@Slf4j
@Component
@RequiredArgsConstructor
class ProjectSnapshotStageAssembler
{
    private static final long MOVIE_EPISODE_ID = 0L;
    private static final String MOVIE = "movie";
    private static final String CHARACTER = "character";
    private static final String SCENE = "scene";
    private static final String PROP = "prop";
    private static final String NORMAL = "0";
    private static final String SUCCEEDED = "SUCCEEDED";

    private final MediaUrlResolver mediaUrlResolver;
    private final StoryboardAudioReferenceResolver audioReferenceResolver = new StoryboardAudioReferenceResolver();

    Contracts assemble(ProjectSnapshotDocument document, Long snapshotId)
    {
        Map<Long, SakuraEpisodeEditor> editors = indexEditors(document.getEditors());
        Map<Long, SakuraRoleVoiceBinding> bindings = indexBindings(document.getVoiceBindings());
        Map<Long, RoleVoiceBindingVO> frozenVoiceByAsset = new LinkedHashMap<>();
        Map<Long, RoleVoiceBindingVO> frozenVoiceByBinding = new LinkedHashMap<>();
        for (RoleVoiceBindingVO view : document.getVoiceBindingViews()) {
            frozenVoiceByAsset.put(view.getAssetId(), view);
            frozenVoiceByBinding.put(view.getBindingId(), view);
        }
        Map<Long, List<SakuraRolePropSceneForm>> forms = group(document.getForms(), SakuraRolePropSceneForm::getAssetId);
        Map<Long, List<SakuraRolePropSceneFormImage>> images = group(
                document.getFormImages(), SakuraRolePropSceneFormImage::getFormId);
        Map<Long, SakuraGenRecord> records = index(document.getGenerationRecords(), SakuraGenRecord::getId);
        Map<Long, SakuraAudioRecord> audioRecordsById = index(document.getAudioRecords(), SakuraAudioRecord::getId);
        Map<Long, List<SakuraGenRecord>> recordsByStoryboard = group(
                document.getGenerationRecords(), SakuraGenRecord::getStoryboardId);
        Map<Long, List<SakuraRoleVoiceBinding>> bindingsByAsset = group(
                document.getVoiceBindings(), SakuraRoleVoiceBinding::getAssetId);
        Map<Long, List<SakuraAudioAsset>> audioAssets = group(
                document.getAudioAssets(), SakuraAudioAsset::getAudioRecordId);
        Map<String, SakuraRolePropScene> characters = indexCharacters(document.getAssets());

        Map<Long, Object> episodes = new LinkedHashMap<>();
        for (SakuraComicEpisode episode : document.getEpisodes()) {
            episodes.put(episode.getId(), episode(document.getProject(), episode, editors.get(episode.getId())));
        }
        Map<Long, Object> scripts = new LinkedHashMap<>();
        for (SakuraComicScript script : currentScripts(document.getScripts())) {
            scripts.put(script.getId(), script(script));
        }
        Map<Long, Object> assets = new LinkedHashMap<>();
        for (SakuraRolePropScene asset : document.getAssets()) {
            List<RpsFormVO> formViews = forms.getOrDefault(asset.getId(), List.of()).stream()
                    .map(form -> form(form, images.getOrDefault(form.getId(), List.of()), asset.getAssetType()))
                    .toList();
            RpsAssetVO view = asset(asset, formViews);
            SakuraRoleVoiceBinding binding = bindings.get(asset.getId());
            if (binding != null && CHARACTER.equals(asset.getAssetType())) {
                view.setVoiceBinding(publicVoice(Objects.requireNonNullElseGet(
                        frozenVoiceByAsset.get(asset.getId()), () -> voice(binding))));
            }
            assets.put(asset.getId(), view);
        }
        Map<Long, Object> scenePlots = new LinkedHashMap<>();
        for (SakuraScenePlot plot : document.getScenePlots()) {
            scenePlots.put(plot.getId(), scenePlot(plot));
        }
        Map<Long, Object> storyboards = new LinkedHashMap<>();
        for (SakuraStoryboard storyboard : document.getStoryboards()) {
            storyboards.put(storyboard.getId(), storyboard(storyboard, records,
                    recordsByStoryboard.getOrDefault(storyboard.getId(), List.of()),
                    audioRecordsById, characters, bindingsByAsset));
        }
        Map<Long, Object> generationConfigs = new LinkedHashMap<>();
        for (SakuraProjectGenConfig config : document.getGenerationConfigs()) {
            generationConfigs.put(config.getId(), generationConfig(config));
        }
        Map<Long, Object> generationRecords = new LinkedHashMap<>();
        Map<String, Integer> mediaIndexes = new HashMap<>();
        Map<Long, SakuraStoryboard> storyboardById = index(document.getStoryboards(), SakuraStoryboard::getId);
        for (SakuraGenRecord record : document.getGenerationRecords()) {
            String noun = displayNoun(record.getGenType());
            int index = mediaIndexes.merge(record.getStoryboardId() + "|" + noun, 1, Integer::sum);
            generationRecords.put(record.getId(), generationRecord(record,
                    storyboardById.get(record.getStoryboardId()), noun, index));
        }
        Map<Long, Object> audioRecords = new LinkedHashMap<>();
        Map<Long, Map<String, Object>> audioMeta = new LinkedHashMap<>();
        Map<Long, String> lipSyncStatuses = Objects.requireNonNullElse(
                document.getAudioLipSyncStatuses(), Map.of());
        for (SakuraAudioRecord record : document.getAudioRecords()) {
            audioRecords.put(record.getId(), audioRecord(record, lipSyncStatuses.get(record.getId())));
            List<SakuraAudioAsset> frozenAssets = audioAssets.getOrDefault(record.getId(), List.of());
            if (!frozenAssets.isEmpty()) {
                audioMeta.put(record.getId(), Map.of("assets", frozenAssets.stream()
                        .map(this::audioAsset).toList()));
            }
        }
        Map<Long, Object> voiceBindings = new LinkedHashMap<>();
        for (SakuraRoleVoiceBinding binding : document.getVoiceBindings()) {
            if (NORMAL.equals(binding.getStatus())) {
                voiceBindings.put(binding.getId(), publicVoice(Objects.requireNonNullElseGet(
                        frozenVoiceByBinding.get(binding.getId()), () -> voice(binding))));
            }
        }
        Map<Long, Object> timelines = new LinkedHashMap<>();
        for (SakuraEpisodeEditor editor : document.getEditors()) {
            timelines.put(editor.getId(), timeline(editor));
        }
        return new Contracts(project(document.getProject(), snapshotId, editors,
                document.getEpisodes().size(), !document.getAssets().isEmpty()), episodes, scripts,
                assets, scenePlots, storyboards, generationConfigs, generationRecords,
                audioRecords, audioMeta, voiceBindings, timelines);
    }

    private UserProjectVO project(SakuraComicProject project, Long snapshotId,
                                  Map<Long, SakuraEpisodeEditor> editors, int episodeCount,
                                  boolean styleLocked)
    {
        SakuraEpisodeEditor movieEditor = editors.get(MOVIE_EPISODE_ID);
        UserProjectVO.UserProjectVOBuilder builder = UserProjectVO.builder()
                .id(project.getId()).projectName(project.getProjectName()).projectDesc(project.getProjectDesc())
                .projectType(project.getProjectType()).coverUrl(project.getCoverUrl())
                .aspectRatio(project.getAspectRatio()).scriptType(project.getScriptType())
                .videoStyleType(project.getVideoStyleType()).videoStyleValue(project.getVideoStyleValue())
                .styleSource(project.getStyleSource()).styleAssetId(project.getStyleAssetId())
                .styleLocked(styleLocked).defaultGenMode(project.getDefaultGenMode())
                .defaultCreationMode(project.getDefaultCreationMode()).currentStep(project.getCurrentStep())
                .status(project.getStatus())
                .publishedSnapshotId(snapshotId).sourceProjectId(project.getSourceProjectId())
                .sourceSnapshotId(project.getSourceSnapshotId())
                .episodeCount(MOVIE.equals(project.getProjectType()) ? null : (long) episodeCount)
                .createTime(project.getCreateTime()).updateTime(project.getUpdateTime());
        if (MOVIE.equals(project.getProjectType()) && movieEditor != null) {
            builder.episodeEditorId(movieEditor.getId()).finalVideoUrl(movieEditor.getFinalVideoUrl())
                    .pendingVideoUrl(movieEditor.getPendingVideoUrl()).exportStatus(movieEditor.getExportStatus());
        }
        return builder.build();
    }

    private UserEpisodeVO episode(SakuraComicProject project, SakuraComicEpisode episode, SakuraEpisodeEditor editor)
    {
        UserEpisodeVO.UserEpisodeVOBuilder builder = UserEpisodeVO.builder()
                .id(episode.getId()).projectId(episode.getProjectId()).episodeNo(episode.getEpisodeNo())
                .comicTitle(episode.getComicTitle()).comicDesc(episode.getComicDesc())
                .comicCoverUrl(episode.getComicCoverUrl()).aspectRatio(project.getAspectRatio())
                .scriptType(project.getScriptType()).videoStyleType(project.getVideoStyleType())
                .videoStyleValue(project.getVideoStyleValue()).genMode(episode.getGenMode())
                .creationMode(episode.getCreationMode()).status(episode.getStatus())
                .createTime(episode.getCreateTime()).updateTime(episode.getUpdateTime());
        if (editor != null) {
            builder.episodeEditorId(editor.getId()).finalVideoUrl(editor.getFinalVideoUrl())
                    .pendingVideoUrl(editor.getPendingVideoUrl()).exportStatus(editor.getExportStatus());
        }
        return builder.build();
    }

    private UserScriptVO script(SakuraComicScript script)
    {
        return UserScriptVO.builder().id(script.getId()).projectId(script.getProjectId())
                .episodeId(script.getEpisodeId()).originalText(script.getOriginalText())
                .contentHash(UserScriptContentHash.calculate(script.getOriginalText()))
                .isExtracted(script.getIsExtracted()).comicVersion(script.getComicVersion())
                .status(script.getStatus()).createTime(script.getCreateTime()).updateTime(script.getUpdateTime()).build();
    }

    private RpsAssetVO asset(SakuraRolePropScene asset, List<RpsFormVO> forms)
    {
        RpsAssetVO.RpsAssetVOBuilder builder = RpsAssetVO.builder().id(asset.getId())
                .assetType(asset.getAssetType()).createSource(asset.getCreateSource()).assetName(asset.getName())
                .introduction(asset.getIntroduction()).forms(forms);
        if (CHARACTER.equals(asset.getAssetType())) {
            builder.aliasesName(asset.getAliasesName()).gender(asset.getGender()).ageRange(asset.getAgeRange())
                    .roleLevel(asset.getRoleLevel()).expectedAppearances(parseObjectList(asset.getExpectedAppearances()));
            enrichCharacter(builder, asset.getProfileData());
        } else if (SCENE.equals(asset.getAssetType())) {
            builder.summary(asset.getSummary()).hasCrowd(asset.getHasCrowd())
                    .crowdDescription(asset.getCrowdDescription())
                    .availableSlots(parseStringList(asset.getAvailableSlots()));
        } else if (PROP.equals(asset.getAssetType())) {
            builder.summary(asset.getSummary());
        }
        return builder.build();
    }

    private RpsFormVO form(SakuraRolePropSceneForm form, List<SakuraRolePropSceneFormImage> images,
                           String assetType)
    {
        SakuraRolePropSceneFormImage current = images.stream().filter(it -> Objects.equals(it.getIsUse(), 1))
                .findFirst().orElse(null);
        SakuraRolePropSceneFormImage display = current == null && !images.isEmpty()
                ? images.get(images.size() - 1) : current;
        List<RpsFormImageVO> imageViews = images.stream().map(image -> RpsFormImageVO.builder()
                .id(image.getId()).name(image.getName()).imageUrl(image.getImageUrl())
                .sourceType(image.getSourceType()).descriptionIndex(image.getDescriptionIndex())
                .isUse(image.getIsUse()).imageStatus(image.getImageStatus())
                .referenceImages(parseStringList(image.getReferenceImages())).build()).toList();
        int variants = StrUtil.isBlank(form.getPromptText()) ? 0 : 1;
        RpsFormVO.RpsFormVOBuilder builder = RpsFormVO.builder().id(form.getId()).assetType(assetType)
                .name(form.getName()).changeReason(form.getChangeReason()).createSource(form.getCreateSource())
                .imageUrl(display == null ? null : display.getImageUrl())
                .visualDescStatus(form.getVisualDescStatus()).canAutoGenerateImage(variants > 0)
                .promptVariantCount(variants).imageCount(imageViews.size())
                .currentImageId(current == null ? null : current.getId()).images(imageViews);
        enrichForm(builder, form.getPromptText(), assetType);
        RpsFormVO view = builder.build();
        // The owner workbench uses this field to regenerate media; anonymous preview only needs
        // the resulting visual description and selected images.
        view.setPrompt(null);
        return view;
    }

    private Map<String, Object> storyboard(SakuraStoryboard storyboard, Map<Long, SakuraGenRecord> records,
                                           List<SakuraGenRecord> storyboardRecords,
                                           Map<Long, SakuraAudioRecord> audioRecordsById,
                                           Map<String, SakuraRolePropScene> characters,
                                           Map<Long, List<SakuraRoleVoiceBinding>> bindingsByAsset)
    {
        SakuraGenRecord image = latestSelected(storyboardRecords, List.of(
                GenTypeEnum.IMAGE.getValue(), GenTypeEnum.GRID.getValue()));
        SakuraGenRecord video = records.get(storyboard.getFinalVideoId());
        SakuraGenRecord compose = latestSelected(storyboardRecords, GenTypeEnum.composeVideoValues());
        StoryboardVO view = StoryboardVO.builder().id(storyboard.getId()).projectId(storyboard.getProjectId())
                .episodeId(storyboard.getEpisodeId()).sortOrder(storyboard.getSortOrder()).title(storyboard.getTitle())
                .storyScript(storyboard.getStoryScript()).dialogueText(storyboard.getDialogueText())
                .subtitleText(DialogueSubtitleFormatter.format(storyboard.getDialogueText()))
                .finalImageId(storyboard.getFinalImageId()).finalVideoId(storyboard.getFinalVideoId())
                .finalAudioId(storyboard.getFinalAudioId()).finalImageUrl(image == null ? null : image.getFileUrl())
                .finalVideoUrl(video == null ? null : video.getFileUrl())
                .finalComposeVideoUrl(compose == null ? null : compose.getFileUrl())
                .referenceImages(referenceImages(image))
                .createTime(storyboard.getCreateTime()).build();
        fillVoice(view, storyboard, audioRecordsById, characters, bindingsByAsset);
        return publicStoryboard(view);
    }

    private Map<String, Object> generationRecord(SakuraGenRecord record, SakuraStoryboard storyboard,
                                                  String noun, int index)
    {
        String shot = resolveShotNo(storyboard);
        Map<String, Object> data = new LinkedHashMap<>();
        put(data, "id", record.getId());
        put(data, "displayName", "分镜" + shot + "-" + noun + index);
        put(data, "storyboardId", record.getStoryboardId());
        put(data, "genType", record.getGenType());
        put(data, "fileUrl", mediaUrlResolver.toFullUrl(record.getFileUrl()));
        put(data, "baseImageId", record.getBaseImageId());
        put(data, "firstImageId", record.getFirstImageId());
        put(data, "lastImageId", record.getLastImageId());
        put(data, "videoDuration", record.getVideoDuration());
        put(data, "soundDesc", record.getSoundDesc());
        put(data, "isSelected", record.getIsSelected());
        put(data, "createTime", record.getCreateTime());
        return data;
    }

    private String displayNoun(String genType)
    {
        return GenTypeEnum.originalVideoValues().contains(genType)
                || GenTypeEnum.composeVideoValues().contains(genType) ? "视频" : "图片";
    }

    private String resolveShotNo(SakuraStoryboard storyboard)
    {
        if (storyboard == null) {
            return "";
        }
        JSONObject params = parseObject(storyboard.getScriptParams());
        Object shotNo = params == null ? null : params.get("镜号");
        if (shotNo != null && StrUtil.isNotBlank(String.valueOf(shotNo))) {
            return String.valueOf(shotNo).trim();
        }
        if (storyboard.getSortOrder() != null) {
            return String.valueOf(storyboard.getSortOrder());
        }
        return String.valueOf(storyboard.getId());
    }

    private Map<String, Object> audioRecord(SakuraAudioRecord record, String frozenLipSyncStatus)
    {
        String lipSyncStatus = frozenLipSyncStatus != null ? frozenLipSyncStatus
                : !Objects.equals(record.getEnableLipSync(), 1) ? null
                : StrUtil.isNotBlank(record.getSyncVideoUrl()) ? SUCCEEDED : null;
        Map<String, Object> data = new LinkedHashMap<>();
        put(data, "id", record.getId());
        put(data, "storyboardId", record.getStoryboardId());
        put(data, "audioSource", record.getAudioSource());
        put(data, "audioUrl", mediaUrlResolver.toFullUrl(record.getAudioUrl()));
        put(data, "durationMs", record.getDurationMs());
        put(data, "ttsText", record.getTtsText());
        put(data, "enableLipSync", record.getEnableLipSync());
        put(data, "status", record.getStatus());
        put(data, "syncVideoUrl", mediaUrlResolver.toFullUrl(record.getSyncVideoUrl()));
        put(data, "lipSyncStatus", lipSyncStatus);
        put(data, "createTime", record.getCreateTime());
        return data;
    }

    private RoleVoiceBindingVO voice(SakuraRoleVoiceBinding binding)
    {
        RoleVoiceBindingVO view = new RoleVoiceBindingVO();
        view.setBindingId(binding.getId());
        view.setAssetId(binding.getAssetId());
        view.setVoiceName(binding.getVoiceName());
        view.setAvatarUrl(binding.getAvatarUrl());
        view.setSampleUrl(binding.getSampleUrl());
        view.setSampleText(binding.getSampleText());
        view.setLanguage(binding.getLanguage());
        view.setGender(binding.getGender());
        view.setAgeRange(binding.getAgeRange());
        return view;
    }

    private RoleVoiceBindingVO publicVoice(RoleVoiceBindingVO source)
    {
        RoleVoiceBindingVO view = new RoleVoiceBindingVO();
        view.setBindingId(source.getBindingId());
        view.setAssetId(source.getAssetId());
        view.setVoiceName(source.getVoiceName());
        view.setAvatarUrl(source.getAvatarUrl());
        view.setSampleUrl(source.getSampleUrl());
        view.setSampleText(source.getSampleText());
        view.setLanguage(source.getLanguage());
        view.setGender(source.getGender());
        view.setAgeRange(source.getAgeRange());
        return view;
    }

    private Map<String, Object> publicStoryboard(StoryboardVO source)
    {
        Map<String, Object> data = new LinkedHashMap<>();
        put(data, "id", source.getId());
        put(data, "projectId", source.getProjectId());
        put(data, "episodeId", source.getEpisodeId());
        put(data, "sortOrder", source.getSortOrder());
        put(data, "title", source.getTitle());
        put(data, "storyScript", source.getStoryScript());
        put(data, "dialogueText", source.getDialogueText());
        put(data, "subtitleText", source.getSubtitleText());
        put(data, "voiceType", source.getVoiceType());
        put(data, "speakerRoles", source.getSpeakerRoles());
        if (source.getSpeakerVoices() != null) {
            put(data, "speakerVoices", source.getSpeakerVoices().stream().map(voice -> {
                Map<String, Object> item = new LinkedHashMap<>();
                put(item, "roleName", voice.getRoleName());
                put(item, "assetId", voice.getAssetId());
                put(item, "voiceBound", voice.isVoiceBound());
                put(item, "voiceName", voice.getVoiceName());
                return item;
            }).toList());
        }
        put(data, "audioStatus", source.getAudioStatus());
        put(data, "finalImageId", source.getFinalImageId());
        put(data, "finalVideoId", source.getFinalVideoId());
        put(data, "finalAudioId", source.getFinalAudioId());
        put(data, "finalImageUrl", mediaUrlResolver.toFullUrl(source.getFinalImageUrl()));
        put(data, "finalVideoUrl", mediaUrlResolver.toFullUrl(source.getFinalVideoUrl()));
        put(data, "finalComposeVideoUrl", mediaUrlResolver.toFullUrl(source.getFinalComposeVideoUrl()));
        put(data, "referenceImages", source.getReferenceImages());
        put(data, "createTime", source.getCreateTime());
        return data;
    }

    private Map<String, Object> timeline(SakuraEpisodeEditor editor)
    {
        TimelineData timeline = null;
        if (StrUtil.isNotBlank(editor.getTimelineJson())) {
            try {
                timeline = JSON.parseObject(editor.getTimelineJson(), TimelineData.class);
                resolveTimelineUrls(timeline);
                sanitizePublicTimeline(timeline);
            } catch (Exception e) {
                log.warn("发布快照时间线解析失败，降级为空工程: editorId={}", editor.getId());
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        put(data, "episodeEditorId", editor.getId());
        put(data, "projectId", editor.getProjectId());
        put(data, "episodeId", editor.getEpisodeId());
        put(data, "exportStatus", editor.getExportStatus());
        put(data, "exportProgress", editor.getExportProgress());
        put(data, "finalVideoUrl", mediaUrlResolver.toFullUrl(editor.getFinalVideoUrl()));
        put(data, "timeline", timeline);
        return data;
    }

    /** 模型池与智能体池属于实时内部配置，公开快照只固化正常 VO 中的已选值。 */
    private Map<String, Object> generationConfig(SakuraProjectGenConfig config)
    {
        Map<String, Object> data = new LinkedHashMap<>();
        put(data, "sceneCode", config.getSceneCode());
        put(data, "resolution", config.getResolution());
        put(data, "aspectRatio", config.getAspectRatio());
        put(data, "source", "project");
        return data;
    }

    private Map<String, Object> scenePlot(SakuraScenePlot plot)
    {
        Map<String, Object> data = new LinkedHashMap<>();
        put(data, "id", plot.getId());
        put(data, "sceneId", plot.getSceneId());
        put(data, "projectId", plot.getProjectId());
        put(data, "episodeId", plot.getEpisodeId());
        put(data, "sceneCode", plot.getSceneCode());
        put(data, "plotContent", plot.getPlotContent());
        put(data, "plotSummary", plot.getPlotSummary());
        put(data, "characters", parseJson(plot.getCharacters()));
        put(data, "characterActions", parseJson(plot.getCharacterActions()));
        put(data, "characterStates", parseJson(plot.getCharacterStates()));
        put(data, "keyDialogues", parseJson(plot.getKeyDialogues()));
        put(data, "sceneFunction", plot.getSceneFunction());
        put(data, "timeOfDay", plot.getTimeOfDay());
        put(data, "eraCoordinate", plot.getEraCoordinate());
        put(data, "dateCoordinate", plot.getDateCoordinate());
        put(data, "weather", plot.getWeather());
        put(data, "createSource", plot.getCreateSource());
        return data;
    }

    private Map<String, Object> audioAsset(SakuraAudioAsset asset)
    {
        Map<String, Object> data = new LinkedHashMap<>();
        put(data, "id", asset.getId());
        put(data, "audioUrl", mediaUrlResolver.toFullUrl(asset.getAudioUrl()));
        put(data, "fileSize", asset.getFileSize());
        put(data, "audioFormat", asset.getAudioFormat());
        put(data, "sampleRate", asset.getSampleRate());
        put(data, "assetTitle", asset.getAssetTitle());
        return data;
    }

    private void fillVoice(StoryboardVO view, SakuraStoryboard storyboard,
                           Map<Long, SakuraAudioRecord> audioRecordsById,
                           Map<String, SakuraRolePropScene> characters,
                           Map<Long, List<SakuraRoleVoiceBinding>> bindingsByAsset)
    {
        List<DialogueSegment> segments = audioReferenceResolver.parse(storyboard.getDialogueText());
        boolean narration = false;
        boolean dialogue = false;
        LinkedHashSet<String> roles = new LinkedHashSet<>();
        for (DialogueSegment segment : segments) {
            if (segment.isNarration()) {
                narration = true;
            } else {
                dialogue = true;
                if (StrUtil.isNotBlank(segment.getRoleName())) {
                    roles.add(segment.getRoleName().trim());
                }
            }
        }
        view.setVoiceType(narration && dialogue ? "mixed" : dialogue ? "dialogue" : narration ? "narration" : null);
        if (!roles.isEmpty()) {
            view.setSpeakerRoles(new ArrayList<>(roles));
            view.setSpeakerVoices(roles.stream().map(role -> {
                SakuraRolePropScene asset = characters.get(normalizeName(role));
                SakuraRoleVoiceBinding binding = asset == null ? null
                        : preferredBinding(bindingsByAsset.getOrDefault(asset.getId(), List.of()),
                                storyboard.getEpisodeId());
                return StoryboardSpeakerVoiceVO.builder().roleName(role)
                        .assetId(asset == null ? null : asset.getId()).voiceBound(binding != null)
                        .voiceName(binding == null ? null : binding.getVoiceName()).build();
            }).toList());
        }
        SakuraAudioRecord selected = audioRecordsById.get(storyboard.getFinalAudioId());
        view.setAudioStatus(selected == null ? "NONE" : selected.getStatus());
    }

    private SakuraRoleVoiceBinding preferredBinding(List<SakuraRoleVoiceBinding> bindings, Long episodeId)
    {
        return bindings.stream().filter(it -> NORMAL.equals(it.getStatus()))
                .filter(it -> Objects.equals(it.getEpisodeId(), episodeId)
                        || Objects.equals(it.getEpisodeId(), MOVIE_EPISODE_ID))
                .max(Comparator.comparing((SakuraRoleVoiceBinding it) ->
                        Objects.equals(it.getEpisodeId(), episodeId) ? 1 : 0))
                .orElse(null);
    }

    private SakuraGenRecord latestSelected(List<SakuraGenRecord> records, List<String> types)
    {
        return records.stream().filter(it -> types.contains(it.getGenType()))
                .filter(it -> Objects.equals(it.getIsSelected(), 1))
                .max(Comparator.comparing(SakuraGenRecord::getId)).orElse(null);
    }

    private List<StoryboardRefImageVO> referenceImages(SakuraGenRecord record)
    {
        if (record == null || StrUtil.isBlank(record.getGenParams())) {
            return null;
        }
        try {
            JSONObject root = JSON.parseObject(record.getGenParams());
            JSONArray manifest = root == null ? null : root.getJSONArray("referenceManifest");
            if (manifest == null || manifest.isEmpty()) {
                return null;
            }
            List<StoryboardRefImageVO> result = new ArrayList<>();
            for (int i = 0; i < manifest.size(); i++) {
                JSONObject ref = manifest.getJSONObject(i);
                if (ref != null) {
                    result.add(StoryboardRefImageVO.builder().n(ref.getInteger("n"))
                            .name(ref.getString("name")).assetKind(ref.getString("assetKind"))
                            .assetName(ref.getString("assetName")).url(ref.getString("url"))
                            .type(ref.getString("type")).build());
                }
            }
            return result.isEmpty() ? null : result;
        } catch (Exception e) {
            log.warn("发布快照分镜参考图解析失败，降级为空: recordId={}", record.getId());
            return null;
        }
    }

    private void resolveTimelineUrls(TimelineData timeline)
    {
        if (timeline == null) {
            return;
        }
        if (timeline.getSegments() != null) {
            for (TimelineSegment segment : timeline.getSegments()) {
                if (segment != null && segment.getVideo() != null) {
                    segment.getVideo().setUrl(mediaUrlResolver.toFullUrl(segment.getVideo().getUrl()));
                }
                if (segment != null && segment.getVoice() != null) {
                    segment.getVoice().setUrl(mediaUrlResolver.toFullUrl(segment.getVoice().getUrl()));
                }
            }
        }
        if (timeline.getBgm() != null) {
            timeline.getBgm().setUrl(mediaUrlResolver.toFullUrl(timeline.getBgm().getUrl()));
        }
    }

    private void sanitizePublicTimeline(TimelineData timeline)
    {
        timeline.setExtraJson(null);
        if (timeline.getSegments() == null) {
            return;
        }
        for (TimelineSegment segment : timeline.getSegments()) {
            if (segment == null) {
                continue;
            }
            if (segment.getVoice() != null) {
                segment.getVoice().setVoiceLibraryId(null);
                segment.getVoice().setVoiceModelId(null);
                segment.getVoice().setTimbreCode(null);
                segment.getVoice().setEmotion(null);
                segment.getVoice().setSpeed(null);
                segment.getVoice().setPitch(null);
            }
            if (segment.getSubtitle() != null) {
                segment.getSubtitle().setSourceMediaFingerprint(null);
                segment.getSubtitle().setSourceDialogueFingerprint(null);
                segment.getSubtitle().setRecognitionProvider(null);
                segment.getSubtitle().setRecognitionError(null);
            }
        }
    }

    private void enrichCharacter(RpsAssetVO.RpsAssetVOBuilder builder, String profileData)
    {
        JSONObject root = parseObject(profileData);
        if (root == null) {
            return;
        }
        builder.archetype(root.getString("archetype")).eraPeriod(root.getString("era_period"))
                .occupation(root.getString("occupation")).costumeTier(root.getInteger("costume_tier"))
                .socialClass(root.getString("social_class"))
                .visualKeywords(stringList(root.get("visual_keywords")))
                .personalityTags(stringList(root.get("personality_tags")))
                .suggestedColors(stringList(root.get("suggested_colors")))
                .primaryIdentifier(root.getString("primary_identifier"));
    }

    private void enrichForm(RpsFormVO.RpsFormVOBuilder builder, String promptText, String assetType)
    {
        JSONObject root = firstObject(promptText);
        if (root == null) {
            return;
        }
        if (CHARACTER.equals(assetType)) {
            Integer appearanceId = root.getInteger("appearanceId");
            builder.descriptions(root.getString("descriptions"))
                    .appearanceId(appearanceId != null ? appearanceId : root.getInteger("appearance_id"));
        } else if (SCENE.equals(assetType)) {
            Integer hasCrowd = root.getInteger("hasCrowd");
            builder.prompt(root.getString("prompt")).summary(root.getString("summary"))
                    .introduction(root.getString("introduction"))
                    .hasCrowd(hasCrowd != null ? hasCrowd : root.getInteger("has_crowd"))
                    .crowdDescription(StrUtil.blankToDefault(root.getString("crowdDescription"),
                            root.getString("crowd_description")))
                    .availableSlots(stringList(root.containsKey("availableSlots")
                            ? root.get("availableSlots") : root.get("available_slots")));
        } else if (PROP.equals(assetType)) {
            builder.prompt(root.getString("prompt")).summary(root.getString("summary"))
                    .introduction(root.getString("introduction"));
        }
    }

    private JSONObject firstObject(String json)
    {
        Object value = parseJson(json);
        if (value instanceof JSONObject object) {
            return object;
        }
        if (value instanceof JSONArray array && !array.isEmpty() && array.get(0) instanceof JSONObject object) {
            return object;
        }
        return null;
    }

    private JSONObject parseObject(String json)
    {
        Object value = parseJson(json);
        return value instanceof JSONObject object ? object : null;
    }

    private Object parseJson(String json)
    {
        if (StrUtil.isBlank(json)) {
            return null;
        }
        try {
            return JSON.parse(json);
        } catch (Exception ignored) {
            return json;
        }
    }

    private List<Object> parseObjectList(String json)
    {
        Object value = parseJson(json);
        return value instanceof JSONArray array ? new ArrayList<>(array) : null;
    }

    private List<String> parseStringList(String json)
    {
        Object value = parseJson(json);
        return stringList(value);
    }

    private List<String> stringList(Object value)
    {
        if (!(value instanceof Iterable<?> values)) {
            return null;
        }
        List<String> result = new ArrayList<>();
        for (Object item : values) {
            if (item != null) {
                result.add(String.valueOf(item));
            }
        }
        return result;
    }

    private Map<Long, SakuraEpisodeEditor> indexEditors(List<SakuraEpisodeEditor> editors)
    {
        Map<Long, SakuraEpisodeEditor> result = new LinkedHashMap<>();
        for (SakuraEpisodeEditor editor : editors) {
            result.put(editor.getEpisodeId(), editor);
        }
        return result;
    }

    /** 与 detailByProject 一致：每个集优先使用中版本，否则取版本号最高的草稿。 */
    private List<SakuraComicScript> currentScripts(List<SakuraComicScript> scripts)
    {
        Map<Long, SakuraComicScript> result = new LinkedHashMap<>();
        for (SakuraComicScript candidate : scripts) {
            if (!Objects.equals(candidate.getStatus(), 0) && !Objects.equals(candidate.getStatus(), 1)) {
                continue;
            }
            SakuraComicScript current = result.get(candidate.getEpisodeId());
            if (current == null
                    || Objects.equals(candidate.getStatus(), 1) && !Objects.equals(current.getStatus(), 1)
                    || Objects.equals(candidate.getStatus(), current.getStatus())
                    && Objects.requireNonNullElse(candidate.getComicVersion(), 0)
                    > Objects.requireNonNullElse(current.getComicVersion(), 0)) {
                result.put(candidate.getEpisodeId(), candidate);
            }
        }
        return new ArrayList<>(result.values());
    }

    private Map<Long, SakuraRoleVoiceBinding> indexBindings(List<SakuraRoleVoiceBinding> bindings)
    {
        Map<Long, SakuraRoleVoiceBinding> result = new LinkedHashMap<>();
        for (SakuraRoleVoiceBinding binding : bindings) {
            if (NORMAL.equals(binding.getStatus())) {
                result.put(binding.getAssetId(), binding);
            }
        }
        return result;
    }

    private Map<String, SakuraRolePropScene> indexCharacters(List<SakuraRolePropScene> assets)
    {
        Map<String, SakuraRolePropScene> result = new HashMap<>();
        for (SakuraRolePropScene asset : assets) {
            if (!CHARACTER.equals(asset.getAssetType())) {
                continue;
            }
            result.put(normalizeName(asset.getName()), asset);
            if (StrUtil.isNotBlank(asset.getNameNormalized())) {
                result.put(normalizeName(asset.getNameNormalized()), asset);
            }
        }
        return result;
    }

    private String normalizeName(String value)
    {
        if (StrUtil.isBlank(value)) {
            return "";
        }
        String main = value.trim().split("_", 2)[0];
        return main.replaceAll("\\s+", "").toLowerCase();
    }

    private <T> Map<Long, T> index(List<T> source, java.util.function.Function<T, Long> key)
    {
        Map<Long, T> result = new LinkedHashMap<>();
        for (T item : source) {
            result.put(key.apply(item), item);
        }
        return result;
    }

    private <T> Map<Long, List<T>> group(List<T> source, java.util.function.Function<T, Long> key)
    {
        Map<Long, List<T>> result = new LinkedHashMap<>();
        for (T item : source) {
            result.computeIfAbsent(key.apply(item), ignored -> new ArrayList<>()).add(item);
        }
        return result;
    }

    private void put(Map<String, Object> target, String key, Object value)
    {
        if (value != null) {
            target.put(key, value);
        }
    }

    record Contracts(UserProjectVO project, Map<Long, Object> episodes, Map<Long, Object> scripts,
                     Map<Long, Object> assets, Map<Long, Object> scenePlots,
                     Map<Long, Object> storyboards, Map<Long, Object> generationConfigs,
                     Map<Long, Object> generationRecords, Map<Long, Object> audioRecords,
                     Map<Long, Map<String, Object>> audioMeta, Map<Long, Object> voiceBindings,
                     Map<Long, Object> timelines)
    {
    }
}
