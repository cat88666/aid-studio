package com.sakura.episode.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.sakura.sakura.domain.SakuraComicEpisode;
import com.sakura.sakura.domain.SakuraComicProject;
import com.sakura.sakura.domain.SakuraComicScript;
import com.sakura.sakura.domain.SakuraEpisodeEditor;
import com.sakura.sakura.domain.SakuraRoleVoiceBinding;
import com.sakura.sakura.domain.SakuraScenePlot;
import com.sakura.sakura.domain.SakuraStoryboard;
import com.sakura.sakura.service.ISakuraComicAuditRecordService;
import com.sakura.sakura.service.ISakuraComicEpisodeService;
import com.sakura.sakura.service.ISakuraComicProjectService;
import com.sakura.sakura.service.ISakuraComicScriptService;
import com.sakura.sakura.service.ISakuraEpisodeEditorService;
import com.sakura.sakura.service.ISakuraRoleVoiceBindingService;
import com.sakura.sakura.service.ISakuraScenePlotService;
import com.sakura.sakura.service.ISakuraStoryboardService;
import com.sakura.common.exception.ServiceException;
import com.sakura.common.sakura.oss.util.MediaUrlResolver;
import com.sakura.common.page.SafePageUtils;
import com.sakura.common.utils.DateUtils;
import com.sakura.common.utils.StringUtils;
import com.sakura.compose.ComposeConstants;
import com.sakura.enums.AuditActionEnum;
import com.sakura.enums.AuditTargetTypeEnum;
import com.sakura.enums.CreationModeEnum;
import com.sakura.enums.EpisodeStatusEnum;
import com.sakura.enums.GenModeEnum;
import com.sakura.enums.ProjectTypeEnum;
import com.sakura.episode.dto.UserEpisodeCreateRequest;
import com.sakura.episode.dto.UserEpisodeQueryRequest;
import com.sakura.episode.dto.UserEpisodeUpdateRequest;
import com.sakura.episode.service.IUserEpisodeBusinessService;
import com.sakura.episode.vo.UserEpisodeVO;
import com.sakura.projectgenconfig.service.IProjectGenConfigService;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * 用户剧集业务Service实现
 *
 * @author 视觉AID
 */
@Slf4j
@Service
public class UserEpisodeBusinessServiceImpl implements IUserEpisodeBusinessService
{
    @Autowired
    private ISakuraComicEpisodeService sakuraComicEpisodeService;

    @Autowired
    private ISakuraComicProjectService sakuraComicProjectService;

    @Autowired
    private ISakuraEpisodeEditorService sakuraEpisodeEditorService;

    @Autowired
    private ISakuraComicAuditRecordService sakuraComicAuditRecordService;

    @Autowired
    private MediaUrlResolver mediaUrlResolver;

    @Autowired
    private ISakuraStoryboardService sakuraStoryboardService;

    @Autowired
    private IProjectGenConfigService projectGenConfigService;

    /** 剧本服务：删剧集时级联删除该集全部剧本版本 */
    @Autowired
    private ISakuraComicScriptService sakuraComicScriptService;

    /** 场次服务：删剧集时级联删除该集场次 */
    @Autowired
    private ISakuraScenePlotService sakuraScenePlotService;

    /** 角色音色绑定服务：删剧集时级联删除该集绑定 */
    @Autowired
    private ISakuraRoleVoiceBindingService sakuraRoleVoiceBindingService;

    /** 生成产物级联清理服务：删剧集时级联硬删其下 gen_record / audio_record 并清 OSS */
    @Autowired
    private com.sakura.media.cleanup.IGenerationArtifactCleanupService generationArtifactCleanupService;

    /** OSS 文件清理服务：清理剧集封面、成片视频等 OSS 文件 */
    @Autowired
    private com.sakura.media.cleanup.IMediaOssCleanupService mediaOssCleanupService;

    /** 项目内容修改守卫：项目公开期间禁止增删改剧集，须先关闭公开 */
    @Autowired
    private com.sakura.project.service.IProjectContentGuardService projectContentGuardService;

    /** 微信公众号推送：提交审核状态变更通知（内部吞异常，不影响主流程） */
    @Autowired
    private com.sakura.notify.wechat.service.IWechatNotifyService wechatNotifyService;

    /**
     * 校验项目归属并返回项目
     */
    private SakuraComicProject getAndCheckProject(Long projectId, Long userId)
    {
        LambdaQueryWrapper<SakuraComicProject> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(SakuraComicProject::getId, projectId);
        wrapper.eq(SakuraComicProject::getUserId, userId);
        wrapper.eq(SakuraComicProject::getDelFlag, "0");
        SakuraComicProject project = sakuraComicProjectService.getOne(wrapper);
        if (project == null) {
            log.info("剧集操作项目缺失或越权, projectId={}, userId={}", projectId, userId);
            throw new ServiceException("项目不存在");
        }
        return project;
    }

    /**
     * 校验剧集枚举字段
     */
    private void validateEnumFields(String genMode, String creationMode)
    {
        if (StringUtils.isNotEmpty(genMode) && GenModeEnum.getByValue(genMode) == null) {
            throw new RuntimeException("生成模式参数错误");
        }
        if (StringUtils.isNotEmpty(creationMode) && CreationModeEnum.getByValue(creationMode) == null) {
            throw new RuntimeException("创作模式参数错误");
        }
    }

    @Override
    public List<SakuraComicEpisode> selectUserEpisodeList(UserEpisodeQueryRequest request, Long userId)
    {
        // 先校验项目归属
        getAndCheckProject(request.getProjectId(), userId);

        LambdaQueryWrapper<SakuraComicEpisode> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(SakuraComicEpisode::getProjectId, request.getProjectId());
        wrapper.eq(SakuraComicEpisode::getUserId, userId);
        wrapper.eq(SakuraComicEpisode::getDelFlag, "0");
        if (StringUtils.isNotEmpty(request.getComicTitle())) {
            wrapper.like(SakuraComicEpisode::getComicTitle, request.getComicTitle());
        }
        if (request.getStatus() != null) {
            wrapper.eq(SakuraComicEpisode::getStatus, request.getStatus());
        }
        wrapper.orderByAsc(SakuraComicEpisode::getEpisodeNo);
        // 分页紧邻列表查询开启（归属校验查询在前会消费分页拦截，导致列表退化为全量）
        SafePageUtils.startClampedPage();
        return sakuraComicEpisodeService.list(wrapper);
    }

    @Override
    public SakuraComicEpisode selectUserEpisodeById(Long id, Long userId)
    {
        SakuraComicEpisode episode = sakuraComicEpisodeService.getOne(
                Wrappers.<SakuraComicEpisode>lambdaQuery()
                        .eq(SakuraComicEpisode::getId, id)
                        .eq(SakuraComicEpisode::getUserId, userId)
                        .eq(SakuraComicEpisode::getDelFlag, "0"));
        if (episode == null) {
            return null;
        }
        // 校验项目归属
        getAndCheckProject(episode.getProjectId(), userId);
        return episode;
    }

    @Override
    public SakuraComicEpisode insertUserEpisode(UserEpisodeCreateRequest request, Long userId)
    {
        // 校验项目归属
        SakuraComicProject project = getAndCheckProject(request.getProjectId(), userId);

        // 公开锁：项目公开期间禁止新增剧集，须先关闭公开
        projectContentGuardService.assertProjectEditable(project);

        // 电影类型不允许添加剧集
        if (ProjectTypeEnum.MOVIE.getValue().equals(project.getProjectType())) {
            throw new RuntimeException("电影类型项目无法添加剧集");
        }

        // 校验枚举字段
        validateEnumFields(request.getGenMode(), request.getCreationMode());

        // 计算集数：当前最大集数+1
        Long maxEpisodeNo = sakuraComicEpisodeService.getObj(
                Wrappers.<SakuraComicEpisode>lambdaQuery()
                        .select(SakuraComicEpisode::getEpisodeNo)
                        .eq(SakuraComicEpisode::getProjectId, request.getProjectId())
                        .eq(SakuraComicEpisode::getDelFlag, "0")
                        .orderByDesc(SakuraComicEpisode::getEpisodeNo)
                        .last("LIMIT 1"),
                obj -> obj != null ? ((Number) obj).longValue() : 0L);
        if (maxEpisodeNo == null) {
            maxEpisodeNo = 0L;
        }

        SakuraComicEpisode episode = new SakuraComicEpisode();
        episode.setProjectId(request.getProjectId());
        episode.setUserId(userId);
        episode.setEpisodeNo(maxEpisodeNo + 1);
        episode.setComicTitle(request.getComicTitle());
        episode.setComicDesc(request.getComicDesc());
        episode.setComicCoverUrl(request.getComicCoverUrl());
        // 从项目继承参数，剧集表没有这些字段，VO里从项目带出
        // 生成模式、创作模式：优先用请求传入的，没传则继承项目默认值
        episode.setGenMode(StringUtils.isNotEmpty(request.getGenMode()) ? request.getGenMode() : project.getDefaultGenMode());
        episode.setCreationMode(StringUtils.isNotEmpty(request.getCreationMode()) ? request.getCreationMode() : project.getDefaultCreationMode());
        episode.setStatus(0);
        episode.setDelFlag("0");
        episode.setCreateBy(String.valueOf(userId));
        episode.setCreateTime(DateUtils.getNowDate());
        sakuraComicEpisodeService.save(episode);
        return episode;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SakuraComicEpisode updateUserEpisode(UserEpisodeUpdateRequest request, Long userId)
    {
        // 查询剧集并校验归属
        SakuraComicEpisode episode = selectUserEpisodeById(request.getId(), userId);
        if (episode == null) {
            throw new RuntimeException("剧集不存在或无权限操作");
        }
        // 审核快照提交后保持不可变，避免审核员查看期间候选内容被并发替换。
        if (Objects.equals(episode.getStatus(), EpisodeStatusEnum.AUDITING.getValue())) {
            log.info("修改剧集失败，内容审核中: episodeId={}", episode.getId());
            throw new ServiceException("内容审核中");
        }

        SakuraComicProject project = getAndCheckProject(episode.getProjectId(), userId);
        // 公开期间只允许修改公开元数据并进入重审；生成参数仍按原规则锁定。
        boolean touchingContentFields = request.getGenMode() != null || request.getCreationMode() != null;
        if (touchingContentFields) {
            projectContentGuardService.assertProjectEditable(project);
        }

        // 校验枚举字段
        validateEnumFields(request.getGenMode(), request.getCreationMode());

        Integer beforeStatus = episode.getStatus();
        String candidateTitle = effectiveEpisodeTitle(episode);
        String candidateDesc = effectiveEpisodeDesc(episode);
        String candidateCoverUrl = effectiveEpisodeCoverUrl(episode);
        boolean metadataTouched = StringUtils.isNotEmpty(request.getComicTitle())
                || request.getComicDesc() != null || request.getComicCoverUrl() != null;
        if (StringUtils.isNotEmpty(request.getComicTitle())) {
            candidateTitle = request.getComicTitle().trim();
        }
        if (request.getComicDesc() != null) {
            candidateDesc = request.getComicDesc().trim();
        }
        if (request.getComicCoverUrl() != null) {
            String requestCoverUrl = request.getComicCoverUrl().trim();
            candidateCoverUrl = StrUtil.isBlank(requestCoverUrl)
                    ? requestCoverUrl : mediaUrlResolver.toRelativePath(requestCoverUrl);
        }
        boolean metadataChanged = (StringUtils.isNotEmpty(request.getComicTitle())
                && !Objects.equals(candidateTitle, effectiveEpisodeTitle(episode)))
                || (request.getComicDesc() != null
                && !Objects.equals(candidateDesc, effectiveEpisodeDesc(episode)))
                || (request.getComicCoverUrl() != null
                && !Objects.equals(candidateCoverUrl, effectiveEpisodeCoverUrl(episode)));
        boolean reviewMetadata = hasApprovedEpisodeRevision(episode, project)
                && metadataTouched && metadataChanged;
        boolean submittedForReview = reviewMetadata
                && !Objects.equals(beforeStatus, EpisodeStatusEnum.AUDITING.getValue());
        if (reviewMetadata) {
            assertEpisodeReviewMetadata(candidateDesc, candidateCoverUrl, episode.getId());
            // 保存完整待审快照，线上标题、描述、封面在审核通过前不变。
            episode.setPendingComicTitle(candidateTitle);
            episode.setPendingComicDesc(candidateDesc);
            episode.setPendingComicCoverUrl(candidateCoverUrl);
            episode.setStatus(EpisodeStatusEnum.AUDITING.getValue());
            episode.setStatusReason(null);
        }

        // 更新可修改字段（aspectRatio、scriptType、videoStyleType、videoStyleValue来自项目，不可修改）
        boolean needClearGenConfig = false; // 创作模式跨组切换时置 true，保存成功后清空项目级生成配置
        if (!reviewMetadata && StringUtils.isNotEmpty(request.getComicTitle())) {
            episode.setComicTitle(candidateTitle);
        }
        if (!reviewMetadata && request.getComicDesc() != null) {
            episode.setComicDesc(candidateDesc);
        }
        if (!reviewMetadata && request.getComicCoverUrl() != null) {
            episode.setComicCoverUrl(candidateCoverUrl);
        }
        if (request.getGenMode() != null) {
            episode.setGenMode(request.getGenMode());
        }
        if (request.getCreationMode() != null) {
            // 创作模式锁定：该剧集分镜一旦已生成，禁止再改创作模式（仅在实际变更时拦截）
            String newMode = request.getCreationMode();
            String oldMode = episode.getCreationMode();
            boolean changingCreationMode = !java.util.Objects.equals(newMode, oldMode); // 是否实际变更创作模式
            if (changingCreationMode
                    && hasStoryboard(episode.getProjectId(), episode.getId())) {
                log.info("修改剧集拒绝：剧集分镜已生成，创作模式锁定: projectId={}, episodeId={}, old={}, new={}",
                        episode.getProjectId(), episode.getId(), oldMode, newMode);
                throw new ServiceException("分镜已生成，不可改创作模式");
            }
            // 跨组切换（标准 i2v/multi ↔ 进阶 pro/auto_grid）：旧组配置不再适用，标记保存后清空项目级生成配置
            needClearGenConfig = changingCreationMode && CreationModeEnum.isCrossGroupSwitch(oldMode, newMode);
            episode.setCreationMode(newMode);
        }
        episode.setUpdateBy(String.valueOf(userId));
        episode.setUpdateTime(DateUtils.getNowDate());
        LambdaUpdateWrapper<SakuraComicEpisode> updateWrapper = Wrappers.lambdaUpdate();
        updateWrapper.eq(SakuraComicEpisode::getId, episode.getId());
        updateWrapper.eq(SakuraComicEpisode::getUserId, userId);
        updateWrapper.eq(SakuraComicEpisode::getStatus, beforeStatus);
        if (reviewMetadata) {
            // 实体更新默认忽略 null，需显式清空上一次驳回原因。
            updateWrapper.set(SakuraComicEpisode::getStatusReason, null);
        }
        boolean updated = sakuraComicEpisodeService.update(episode, updateWrapper);
        if (!updated) {
            log.info("修改剧集失败，状态已变化: episodeId={}, beforeStatus={}", episode.getId(), beforeStatus);
            throw new ServiceException("状态已变化");
        }
        if (submittedForReview) {
            sakuraComicAuditRecordService.saveAuditRecord(AuditTargetTypeEnum.EPISODE.getValue(), episode.getId(),
                    userId, AuditActionEnum.SUBMIT.getValue(), beforeStatus,
                    EpisodeStatusEnum.AUDITING.getValue(), null, String.valueOf(userId));
            wechatNotifyService.notifyContentAudit(AuditTargetTypeEnum.EPISODE.getValue(), episode.getId(),
                    com.sakura.notify.wechat.service.IWechatNotifyService.AUDIT_EVENT_SUBMITTED, null);
        }
        // 创作模式跨组切换且保存成功：清空该项目生成配置（项目级，按当前用户），使各场景回落矩阵默认
        if (needClearGenConfig) {
            projectGenConfigService.clearProjectConfig(episode.getProjectId(), userId);
        }
        return episode;
    }

    /** 是否已有审核通过基线，公开元数据变更必须进入待审槽。 */
    private boolean hasApprovedEpisodeRevision(SakuraComicEpisode episode, SakuraComicProject project)
    {
        return Objects.equals(episode.getStatus(), EpisodeStatusEnum.AUDIT_PASSED.getValue())
                || (Objects.equals(episode.getStatus(), EpisodeStatusEnum.AUDITING.getValue())
                && Objects.nonNull(project.getPublishTime()))
                || hasPendingEpisodeMetadata(episode);
    }

    /** 是否存在待审核的单集公开元数据快照。 */
    private boolean hasPendingEpisodeMetadata(SakuraComicEpisode episode)
    {
        return StrUtil.isNotBlank(episode.getPendingComicTitle())
                || StrUtil.isNotBlank(episode.getPendingComicDesc())
                || StrUtil.isNotBlank(episode.getPendingComicCoverUrl());
    }

    private String effectiveEpisodeTitle(SakuraComicEpisode episode)
    {
        return StrUtil.isNotBlank(episode.getPendingComicTitle())
                ? episode.getPendingComicTitle() : episode.getComicTitle();
    }

    private String effectiveEpisodeDesc(SakuraComicEpisode episode)
    {
        return StrUtil.isNotBlank(episode.getPendingComicDesc())
                ? episode.getPendingComicDesc() : episode.getComicDesc();
    }

    private String effectiveEpisodeCoverUrl(SakuraComicEpisode episode)
    {
        return StrUtil.isNotBlank(episode.getPendingComicCoverUrl())
                ? episode.getPendingComicCoverUrl() : episode.getComicCoverUrl();
    }

    /** 单集审核元数据统一校验：描述必填，封面选填但传入时必须是本站图片。 */
    private void assertEpisodeReviewMetadata(String comicDesc, String coverUrl, Long episodeId)
    {
        if (StrUtil.isBlank(comicDesc)) {
            log.info("提交剧集审核失败，单集描述缺失: episodeId={}", episodeId);
            throw new ServiceException("请填写剧集描述");
        }
        if (StrUtil.isNotBlank(coverUrl) && !mediaUrlResolver.isSiteImageUrl(coverUrl)) {
            log.info("提交剧集审核失败，单集封面地址无效: episodeId={}, coverUrl={}", episodeId, coverUrl);
            throw new ServiceException("封面图地址无效");
        }
    }

    /**
     * 判断指定项目+剧集下是否已存在有效分镜。
     * 用于创作模式锁定：分镜一旦生成，禁止再改该剧集创作模式。
     */
    private boolean hasStoryboard(Long projectId, Long episodeId) {
        if (projectId == null || episodeId == null) {
            return false;
        }
        return sakuraStoryboardService.count(
                Wrappers.<com.sakura.sakura.domain.SakuraStoryboard>lambdaQuery()
                        .eq(com.sakura.sakura.domain.SakuraStoryboard::getProjectId, projectId)
                        .eq(com.sakura.sakura.domain.SakuraStoryboard::getEpisodeId, episodeId)
                        .eq(com.sakura.sakura.domain.SakuraStoryboard::getDelFlag, "0")) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int softDeleteUserEpisodeById(Long id, Long userId)
    {
        // 查询剧集并校验归属
        SakuraComicEpisode episode = selectUserEpisodeById(id, userId);
        if (episode == null) {
            throw new RuntimeException("剧集不存在或无权限操作");
        }
        // 公开锁：项目公开期间禁止删除剧集，须先关闭公开
        projectContentGuardService.assertProjectEditable(episode.getProjectId());
        Long projectId = episode.getProjectId();
        // 收集本次需清理的 OSS 文件，最后统一登记（先完成所有 DB 删除，再删文件）
        java.util.List<String> filesToClean = new java.util.ArrayList<>();
        generationArtifactCleanupService.cleanupByEpisode(projectId, id);
        List<SakuraEpisodeEditor> editors = sakuraEpisodeEditorService.list(Wrappers.<SakuraEpisodeEditor>lambdaQuery()
                .select(SakuraEpisodeEditor::getId, SakuraEpisodeEditor::getFinalVideoUrl, SakuraEpisodeEditor::getCoverUrl)
                .eq(SakuraEpisodeEditor::getProjectId, projectId)
                .eq(SakuraEpisodeEditor::getEpisodeId, id));
        if (!editors.isEmpty()) {
            for (SakuraEpisodeEditor editor : editors) {
                filesToClean.add(editor.getFinalVideoUrl());
                filesToClean.add(editor.getCoverUrl());
            }
            sakuraEpisodeEditorService.remove(Wrappers.<SakuraEpisodeEditor>lambdaQuery()
                    .eq(SakuraEpisodeEditor::getProjectId, projectId)
                    .eq(SakuraEpisodeEditor::getEpisodeId, id));
        }
        sakuraStoryboardService.remove(Wrappers.<SakuraStoryboard>lambdaQuery()
                .eq(SakuraStoryboard::getProjectId, projectId)
                .eq(SakuraStoryboard::getEpisodeId, id));
        // 级联清理该集关联数据（对齐项目级联删除范围，防止孤儿行长期累积）：
        // 剧本全部版本、场次、角色音色绑定
        sakuraComicScriptService.remove(Wrappers.<SakuraComicScript>lambdaQuery()
                .eq(SakuraComicScript::getProjectId, projectId)
                .eq(SakuraComicScript::getEpisodeId, id));
        sakuraScenePlotService.remove(Wrappers.<SakuraScenePlot>lambdaQuery()
                .eq(SakuraScenePlot::getProjectId, projectId)
                .eq(SakuraScenePlot::getEpisodeId, id));
        sakuraRoleVoiceBindingService.remove(Wrappers.<SakuraRoleVoiceBinding>lambdaQuery()
                .eq(SakuraRoleVoiceBinding::getProjectId, projectId)
                .eq(SakuraRoleVoiceBinding::getEpisodeId, id));
        filesToClean.add(episode.getComicCoverUrl());
        int affected = sakuraComicEpisodeService.getBaseMapper().delete(Wrappers.<SakuraComicEpisode>lambdaQuery()
                .eq(SakuraComicEpisode::getId, id)
                .eq(SakuraComicEpisode::getUserId, userId)) > 0 ? 1 : 0;
        mediaOssCleanupService.cleanupFiles(filesToClean);
        return affected;
    }

    /**
     * 用户提交剧集审核（带归属校验）
     * 状态机：除「审核中(3)」「审核通过(4)」外的状态均可提交（成片导出成功后状态自动变为「完成未审核(2)」即可提审）
     * → 置为「审核中(3)」并清空状态原因，同时写入一条「提交审核」流水。
     *
     * @param id 剧集ID
     * @param userId 用户ID
     * @return 提交审核后的剧集
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SakuraComicEpisode submitAudit(Long id, Long userId)
    {
        // 查询并校验归属（含项目归属校验）
        SakuraComicEpisode episode = selectUserEpisodeById(id, userId);
        if (episode == null) {
            log.info("提交剧集审核失败，剧集不存在或无权限: episodeId={}, userId={}", id, userId);
            throw new ServiceException("剧集不存在");
        }
        Integer beforeStatus = episode.getStatus();
        // 校验当前状态是否允许提交审核
        if (Objects.equals(beforeStatus, EpisodeStatusEnum.AUDITING.getValue())) {
            throw new ServiceException("内容审核中");
        }
        // 内容前置校验：该剧集必须已有成品视频（sakura_episode_editor.final_video_url 非空）
        // 剧集成片在 sakura_episode_editor 中以剧集自身ID关联（电影不会进入本接口）；
        // 并发建档可能产生多行，取最新一条（与受理侧 selectLatestEditor 口径一致）
        SakuraEpisodeEditor editor = sakuraEpisodeEditorService.getOne(Wrappers.<SakuraEpisodeEditor>lambdaQuery()
                .eq(SakuraEpisodeEditor::getProjectId, episode.getProjectId())
                .eq(SakuraEpisodeEditor::getEpisodeId, id)
                .eq(SakuraEpisodeEditor::getDelFlag, "0")
                .orderByDesc(SakuraEpisodeEditor::getId)
                .last("LIMIT 1"));
        if (Objects.equals(beforeStatus, EpisodeStatusEnum.AUDIT_PASSED.getValue())
                && !hasPendingExportVideo(editor)
                && !hasPendingEpisodeMetadata(episode)) {
            // 已过审且没有待审新片或待审元数据时，无重审必要。
            throw new ServiceException("已通过审核");
        }
        if (Objects.isNull(editor) || StringUtils.isEmpty(editor.getFinalVideoUrl())) {
            log.info("提交剧集审核失败，剧集缺成品视频: episodeId={}", id);
            throw new ServiceException("请先合成视频");
        }
        if (Objects.equals(editor.getExportStatus(), ComposeConstants.EXPORT_STATUS_COMPOSING)) {
            log.info("提交剧集审核失败，视频正在合成: episodeId={}", id);
            throw new ServiceException("视频合成中");
        }
        assertEpisodeReviewMetadata(effectiveEpisodeDesc(episode), effectiveEpisodeCoverUrl(episode), id);
        // 置为审核中并清空上次状态原因
        Integer afterStatus = EpisodeStatusEnum.AUDITING.getValue();
        LambdaUpdateWrapper<SakuraComicEpisode> updateWrapper = Wrappers.lambdaUpdate();
        updateWrapper.eq(SakuraComicEpisode::getId, id);
        updateWrapper.eq(SakuraComicEpisode::getUserId, userId);
        updateWrapper.eq(SakuraComicEpisode::getStatus, beforeStatus);
        updateWrapper.set(SakuraComicEpisode::getStatus, afterStatus);
        updateWrapper.set(SakuraComicEpisode::getStatusReason, null);
        updateWrapper.set(SakuraComicEpisode::getUpdateTime, DateUtils.getNowDate());
        boolean updated = sakuraComicEpisodeService.update(updateWrapper);
        if (!updated) {
            log.info("提交剧集审核状态已变化: episodeId={}, beforeStatus={}", id, beforeStatus);
            throw new ServiceException("状态已变化");
        }
        // 写入审核流水（提交审核），操作人记为用户ID
        sakuraComicAuditRecordService.saveAuditRecord(AuditTargetTypeEnum.EPISODE.getValue(), id, userId,
                AuditActionEnum.SUBMIT.getValue(), beforeStatus, afterStatus, null, String.valueOf(userId));
        // 微信公众号推送：提交审核（推送服务内部吞异常，不影响提审主流程）
        wechatNotifyService.notifyContentAudit(AuditTargetTypeEnum.EPISODE.getValue(), id,
                com.sakura.notify.wechat.service.IWechatNotifyService.AUDIT_EVENT_SUBMITTED, null);
        // 回填最新状态返回
        episode.setStatus(afterStatus);
        episode.setStatusReason(null);
        return episode;
    }

    /** 待审地址存在且双槽指纹不相同，才表示确有需要重新审核的新成片。 */
    private boolean hasPendingExportVideo(SakuraEpisodeEditor editor)
    {
        return Objects.nonNull(editor) && StringUtils.isNotEmpty(editor.getPendingVideoUrl())
                && !(StrUtil.isNotBlank(editor.getFinalVideoFingerprint())
                && Objects.equals(editor.getFinalVideoFingerprint(), editor.getPendingVideoFingerprint()));
    }

    @Override
    public List<UserEpisodeVO> convertToVOList(List<SakuraComicEpisode> episodes)
    {
        List<UserEpisodeVO> result = new ArrayList<>();
        if (episodes == null || episodes.isEmpty()) {
            return result;
        }
        // 收集项目ID与剧集ID（列表接口通常同一项目，兼容多项目场景）
        Set<Long> projectIds = new LinkedHashSet<>();
        Set<Long> episodeIds = new LinkedHashSet<>();
        for (SakuraComicEpisode episode : episodes) {
            projectIds.add(episode.getProjectId());
            episodeIds.add(episode.getId());
        }
        // 查询字段精简：VO 只需项目的画面比例/剧本类型/风格字段（新增 VO 字段时此处必须同步补充）
        Map<Long, SakuraComicProject> projectMap = new HashMap<>();
        for (SakuraComicProject project : sakuraComicProjectService.list(Wrappers.<SakuraComicProject>lambdaQuery()
                .select(SakuraComicProject::getId, SakuraComicProject::getAspectRatio, SakuraComicProject::getScriptType,
                        SakuraComicProject::getVideoStyleType, SakuraComicProject::getVideoStyleValue)
                .in(SakuraComicProject::getId, projectIds)
                .eq(SakuraComicProject::getDelFlag, "0"))) {
            projectMap.put(project.getId(), project);
        }
        // 查询字段精简：成片展示只需 id/归属/成片地址/待审片/导出状态（新增 VO 字段时此处必须同步补充）
        // 按 id 升序遍历后写覆盖，同一集多条剪辑记录时保留最新一条
        Long userId = episodes.get(0).getUserId();
        Map<String, SakuraEpisodeEditor> editorMap = new HashMap<>();
        for (SakuraEpisodeEditor editor : sakuraEpisodeEditorService.list(Wrappers.<SakuraEpisodeEditor>lambdaQuery()
                .select(SakuraEpisodeEditor::getId, SakuraEpisodeEditor::getProjectId, SakuraEpisodeEditor::getEpisodeId,
                        SakuraEpisodeEditor::getFinalVideoUrl, SakuraEpisodeEditor::getPendingVideoUrl,
                        SakuraEpisodeEditor::getExportStatus)
                .eq(Objects.nonNull(userId), SakuraEpisodeEditor::getUserId, userId)
                .in(SakuraEpisodeEditor::getProjectId, projectIds)
                .in(SakuraEpisodeEditor::getEpisodeId, episodeIds)
                .eq(SakuraEpisodeEditor::getDelFlag, "0")
                .orderByAsc(SakuraEpisodeEditor::getId))) {
            editorMap.put(editor.getProjectId() + "_" + editor.getEpisodeId(), editor);
        }
        for (SakuraComicEpisode episode : episodes) {
            result.add(buildEpisodeVO(episode,
                    projectMap.get(episode.getProjectId()),
                    editorMap.get(episode.getProjectId() + "_" + episode.getId())));
        }
        return result;
    }

    @Override
    public UserEpisodeVO convertToVO(SakuraComicEpisode episode)
    {
        if (episode == null) {
            return null;
        }
        List<UserEpisodeVO> list = convertToVOList(Collections.singletonList(episode));
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 组装剧集 VO：剧集自身字段 + 所属项目配置字段 + 最新成片信息。
     *
     * @param episode 剧集实体
     * @param project 所属项目（可为 null）
     * @param editor  该集最新剪辑记录（可为 null，表示从未导出）
     * @return 剧集 VO
     */
    private UserEpisodeVO buildEpisodeVO(SakuraComicEpisode episode, SakuraComicProject project, SakuraEpisodeEditor editor)
    {
        UserEpisodeVO.UserEpisodeVOBuilder builder = UserEpisodeVO.builder()
                .id(episode.getId())
                .projectId(episode.getProjectId())
                .episodeNo(episode.getEpisodeNo())
                // 创作者看到待审候选版本；公开服务仍读取最后一次过审字段。
                .comicTitle(effectiveEpisodeTitle(episode))
                .comicDesc(effectiveEpisodeDesc(episode))
                .comicCoverUrl(effectiveEpisodeCoverUrl(episode))
                .genMode(episode.getGenMode())
                .creationMode(episode.getCreationMode())
                .status(episode.getStatus())
                .statusReason(episode.getStatusReason())
                .createTime(episode.getCreateTime())
                .updateTime(episode.getUpdateTime());
        if (project != null) {
            builder.aspectRatio(project.getAspectRatio())
                    .scriptType(project.getScriptType())
                    .videoStyleType(project.getVideoStyleType())
                    .videoStyleValue(project.getVideoStyleValue());
        }
        if (editor != null) {
            builder.episodeEditorId(editor.getId())
                    .finalVideoUrl(editor.getFinalVideoUrl())
                    .pendingVideoUrl(editor.getPendingVideoUrl())
                    .exportStatus(editor.getExportStatus());
        }
        return builder.build();
    }
}
