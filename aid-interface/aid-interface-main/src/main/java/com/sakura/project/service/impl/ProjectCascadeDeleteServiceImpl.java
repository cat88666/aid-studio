package com.sakura.project.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.sakura.sakura.domain.SakuraAudioAsset;
import com.sakura.sakura.domain.SakuraComicEpisode;
import com.sakura.sakura.domain.SakuraComicProject;
import com.sakura.sakura.domain.SakuraComicScript;
import com.sakura.sakura.domain.SakuraEpisodeEditor;
import com.sakura.sakura.domain.SakuraExtractTask;
import com.sakura.sakura.domain.SakuraRolePropScene;
import com.sakura.sakura.domain.SakuraRolePropSceneForm;
import com.sakura.sakura.domain.SakuraRolePropSceneFormImage;
import com.sakura.sakura.domain.SakuraRoleVoiceBinding;
import com.sakura.sakura.domain.SakuraScenePlot;
import com.sakura.sakura.domain.SakuraStoryboard;
import com.sakura.sakura.service.ISakuraAudioAssetService;
import com.sakura.sakura.service.ISakuraComicEpisodeService;
import com.sakura.sakura.service.ISakuraComicProjectService;
import com.sakura.sakura.service.ISakuraComicScriptService;
import com.sakura.sakura.service.ISakuraEpisodeEditorService;
import com.sakura.sakura.service.ISakuraExtractTaskService;
import com.sakura.sakura.service.ISakuraRolePropSceneFormImageService;
import com.sakura.sakura.service.ISakuraRolePropSceneFormService;
import com.sakura.sakura.service.ISakuraRolePropSceneService;
import com.sakura.sakura.service.ISakuraRoleVoiceBindingService;
import com.sakura.sakura.service.ISakuraScenePlotService;
import com.sakura.sakura.service.ISakuraStoryboardService;
import com.sakura.common.event.ProjectCascadeDeletingEvent;
import com.sakura.media.cleanup.IGenerationArtifactCleanupService;
import com.sakura.media.cleanup.IMediaOssCleanupService;
import com.sakura.project.service.IProjectCascadeDeleteService;
import com.sakura.projectgenconfig.service.IProjectGenConfigService;

import lombok.extern.slf4j.Slf4j;

/**
 * 项目级联删除服务实现。
 * 删除顺序：先清理各表 OSS 文件（本地必删成功、远程异步），再物理删库；最后删项目本身。
 *
 * @author 视觉AID
 */
@Slf4j
@Service
public class ProjectCascadeDeleteServiceImpl implements IProjectCascadeDeleteService
{
    /** 生成产物级联清理（gen_record / audio_record + OSS） */
    @Autowired
    private IGenerationArtifactCleanupService generationArtifactCleanupService;

    /** OSS 文件清理服务 */
    @Autowired
    private IMediaOssCleanupService mediaOssCleanupService;

    /** 成片导出记录 */
    @Autowired
    private ISakuraEpisodeEditorService sakuraEpisodeEditorService;

    /** 分镜 */
    @Autowired
    private ISakuraStoryboardService sakuraStoryboardService;

    /** 剧集 */
    @Autowired
    private ISakuraComicEpisodeService sakuraComicEpisodeService;

    /** 剧本 */
    @Autowired
    private ISakuraComicScriptService sakuraComicScriptService;

    /** RPS 形态图 */
    @Autowired
    private ISakuraRolePropSceneFormImageService rpsFormImageService;

    /** RPS 形态 */
    @Autowired
    private ISakuraRolePropSceneFormService rpsFormService;

    /** RPS 主资产（角色/道具/场景） */
    @Autowired
    private ISakuraRolePropSceneService rpsService;

    /** 场次 */
    @Autowired
    private ISakuraScenePlotService scenePlotService;

    /** 角色音色绑定 */
    @Autowired
    private ISakuraRoleVoiceBindingService roleVoiceBindingService;

    /** 资产提取任务 */
    @Autowired
    private ISakuraExtractTaskService extractTaskService;

    /** 音频资产 */
    @Autowired
    private ISakuraAudioAssetService audioAssetService;

    /** 项目生成配置 */
    @Autowired
    private IProjectGenConfigService projectGenConfigService;

    /** 项目 */
    @Autowired
    private ISakuraComicProjectService sakuraComicProjectService;

    /** 允许可选模块在项目主记录删除前清理自身业务数据。 */
    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    /**
     * 级联硬删除整个项目子树（含 OSS 清理）。
     *
     * @param projectId 项目ID
     * @param userId    操作用户ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteProjectCascade(Long projectId, Long userId)
    {
        if (Objects.isNull(projectId))
        {
            return;
        }
        // 统一收集本次需清理的 OSS 文件，所有 DB 删除完成后再登记清理（afterCommit 后台执行，回滚则不动文件）
        List<String> filesToClean = new ArrayList<>();

        generationArtifactCleanupService.cleanupByProject(projectId);

        List<SakuraEpisodeEditor> editors = sakuraEpisodeEditorService.list(Wrappers.<SakuraEpisodeEditor>lambdaQuery()
                .select(SakuraEpisodeEditor::getId, SakuraEpisodeEditor::getFinalVideoUrl, SakuraEpisodeEditor::getCoverUrl)
                .eq(SakuraEpisodeEditor::getProjectId, projectId));
        if (!editors.isEmpty())
        {
            for (SakuraEpisodeEditor editor : editors)
            {
                filesToClean.add(editor.getFinalVideoUrl());
                filesToClean.add(editor.getCoverUrl());
            }
            sakuraEpisodeEditorService.remove(Wrappers.<SakuraEpisodeEditor>lambdaQuery()
                    .eq(SakuraEpisodeEditor::getProjectId, projectId));
        }

        sakuraStoryboardService.remove(Wrappers.<SakuraStoryboard>lambdaQuery()
                .eq(SakuraStoryboard::getProjectId, projectId));

        List<SakuraComicEpisode> episodes = sakuraComicEpisodeService.list(Wrappers.<SakuraComicEpisode>lambdaQuery()
                .select(SakuraComicEpisode::getId, SakuraComicEpisode::getComicCoverUrl)
                .eq(SakuraComicEpisode::getProjectId, projectId));
        if (!episodes.isEmpty())
        {
            for (SakuraComicEpisode ep : episodes)
            {
                filesToClean.add(ep.getComicCoverUrl());
            }
            sakuraComicEpisodeService.remove(Wrappers.<SakuraComicEpisode>lambdaQuery()
                    .eq(SakuraComicEpisode::getProjectId, projectId));
        }

        sakuraComicScriptService.remove(Wrappers.<SakuraComicScript>lambdaQuery()
                .eq(SakuraComicScript::getProjectId, projectId));

        List<SakuraRolePropSceneFormImage> formImages = rpsFormImageService.list(
                Wrappers.<SakuraRolePropSceneFormImage>lambdaQuery()
                        .select(SakuraRolePropSceneFormImage::getId, SakuraRolePropSceneFormImage::getImageUrl)
                        .eq(SakuraRolePropSceneFormImage::getProjectId, projectId));
        if (!formImages.isEmpty())
        {
            for (SakuraRolePropSceneFormImage img : formImages)
            {
                filesToClean.add(img.getImageUrl());
            }
            rpsFormImageService.remove(Wrappers.<SakuraRolePropSceneFormImage>lambdaQuery()
                    .eq(SakuraRolePropSceneFormImage::getProjectId, projectId));
        }

        rpsFormService.remove(Wrappers.<SakuraRolePropSceneForm>lambdaQuery()
                .eq(SakuraRolePropSceneForm::getProjectId, projectId));
        rpsService.remove(Wrappers.<SakuraRolePropScene>lambdaQuery()
                .eq(SakuraRolePropScene::getProjectId, projectId));
        scenePlotService.remove(Wrappers.<SakuraScenePlot>lambdaQuery()
                .eq(SakuraScenePlot::getProjectId, projectId));
        roleVoiceBindingService.remove(Wrappers.<SakuraRoleVoiceBinding>lambdaQuery()
                .eq(SakuraRoleVoiceBinding::getProjectId, projectId));

        extractTaskService.remove(Wrappers.<SakuraExtractTask>lambdaQuery()
                .eq(SakuraExtractTask::getProjectId, projectId));

        List<SakuraAudioAsset> audioAssets = audioAssetService.list(Wrappers.<SakuraAudioAsset>lambdaQuery()
                .select(SakuraAudioAsset::getId, SakuraAudioAsset::getAudioUrl)
                .eq(SakuraAudioAsset::getProjectId, projectId));
        if (!audioAssets.isEmpty())
        {
            for (SakuraAudioAsset a : audioAssets)
            {
                filesToClean.add(a.getAudioUrl());
            }
            audioAssetService.remove(Wrappers.<SakuraAudioAsset>lambdaQuery()
                    .eq(SakuraAudioAsset::getProjectId, projectId));
        }

        projectGenConfigService.clearProjectConfig(projectId, userId);

        SakuraComicProject project = sakuraComicProjectService.getById(projectId);
        if (Objects.nonNull(project))
        {
            filesToClean.add(project.getCoverUrl());
        }
        applicationEventPublisher.publishEvent(new ProjectCascadeDeletingEvent(
                projectId, userId, Objects.isNull(project) ? null : project.getSourceSnapshotId()));
        sakuraComicProjectService.removeById(projectId);

        mediaOssCleanupService.cleanupFiles(filesToClean);

        log.info("项目级联硬删除完成, projectId={}, userId={}", projectId, userId);
    }
}
