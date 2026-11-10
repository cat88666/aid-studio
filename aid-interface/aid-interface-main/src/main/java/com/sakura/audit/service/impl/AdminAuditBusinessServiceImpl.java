package com.sakura.audit.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.sakura.sakura.domain.SakuraComicAuditRecord;
import com.sakura.sakura.domain.SakuraComicEpisode;
import com.sakura.sakura.domain.SakuraComicProject;
import com.sakura.sakura.domain.SakuraEpisodeEditor;
import com.sakura.sakura.domain.SakuraGenRecord;
import com.sakura.sakura.service.ISakuraComicAuditRecordService;
import com.sakura.sakura.service.ISakuraComicEpisodeService;
import com.sakura.sakura.service.ISakuraComicProjectService;
import com.sakura.sakura.service.ISakuraEpisodeEditorService;
import com.sakura.sakura.service.ISakuraGenRecordService;
import com.sakura.media.cleanup.IMediaOssCleanupService;
import com.sakura.publish.service.IPublishPermissionService;
import com.sakura.audit.dto.AdminAuditActionRequest;
import com.sakura.audit.dto.AdminAuditDetailRequest;
import com.sakura.audit.dto.AdminAuditRecordQueryRequest;
import com.sakura.audit.dto.AdminEpisodeAuditQueryRequest;
import com.sakura.audit.dto.AdminProjectAuditQueryRequest;
import com.sakura.audit.service.IAdminAuditBusinessService;
import com.sakura.audit.vo.AuditEpisodeDetailVO;
import com.sakura.audit.vo.AuditEpisodeListVO;
import com.sakura.audit.vo.AuditMovieDetailVO;
import com.sakura.audit.vo.AuditProjectDetailVO;
import com.sakura.audit.vo.AuditRecordVO;
import com.github.pagehelper.Page;
import com.sakura.common.core.domain.entity.SysUser;
import com.sakura.common.event.ProjectAuditPassedEvent;
import com.sakura.common.exception.ServiceException;
import com.sakura.common.sakura.oss.util.MediaUrlResolver;
import com.sakura.common.utils.DateUtils;
import com.sakura.core.service.ISysUserService;
import com.sakura.enums.AuditActionEnum;
import com.sakura.enums.AuditTargetTypeEnum;
import com.sakura.enums.EpisodeStatusEnum;
import com.sakura.enums.ProjectStatusEnum;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * 后台作品审核业务Service实现
 *
 * @author 视觉AID
 */
@Slf4j
@Service
public class AdminAuditBusinessServiceImpl implements IAdminAuditBusinessService {

    /** 删除标志：正常（未删除） */
    private static final String DEL_FLAG_NORMAL = "0";

    /** 电影成片在编辑器表中的剧集ID标识 */
    private static final Long MOVIE_EPISODE_ID = 0L;

    /** 项目类型：电影 */
    private static final String PROJECT_TYPE_MOVIE = "movie";

    /** 项目类型：剧集 */
    private static final String PROJECT_TYPE_SERIES = "series";

    /** 业务类型描述：电影 */
    private static final String BIZ_DESC_MOVIE = "电影";

    /** 业务类型描述：剧集 */
    private static final String BIZ_DESC_SERIES = "剧集";

    /** 是否公开：是 */
    private static final String IS_PUBLIC_YES = "1";

    /** 是否公开：否 */
    private static final String IS_PUBLIC_NO = "0";

    @Autowired
    private ISakuraComicProjectService sakuraComicProjectService;

    @Autowired
    private ISakuraComicEpisodeService sakuraComicEpisodeService;

    @Autowired
    private ISakuraComicAuditRecordService sakuraComicAuditRecordService;

    @Autowired
    private ISakuraEpisodeEditorService sakuraEpisodeEditorService;

    /** 生成记录服务（旧成片删除前的引用检查，防止删除仍被引用的文件） */
    @Autowired
    private ISakuraGenRecordService sakuraGenRecordService;

    /** OSS 文件清理服务（审核通过后回收被替换的旧成片文件） */
    @Autowired
    private IMediaOssCleanupService mediaOssCleanupService;

    /** 微信公众号推送：审核通过/驳回结果通知内容归属用户（内部吞异常，不影响审核主流程） */
    @Autowired
    private com.sakura.notify.wechat.service.IWechatNotifyService wechatNotifyService;

    /** 用户查询：审核详情带出作者昵称 */
    @Autowired
    private ISysUserService sysUserService;

    /** 发布权限校验 */
    @Autowired
    private IPublishPermissionService publishPermissionService;

    /** 发布封面地址校验，审核通过前再次确认仅使用本站资源。 */
    @Autowired
    private MediaUrlResolver mediaUrlResolver;

    /** 审核通过后同步通知快照模块固化新的发布版本。 */
    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    /**
     * 查询项目审核列表（后台）——仅剧集类项目（series）。
     */
    @Override
    public List<SakuraComicProject> selectAuditProjectList(AdminProjectAuditQueryRequest request) {
        return listProjectsByType(request, PROJECT_TYPE_SERIES);
    }

    /**
     * 查询电影审核列表（后台）——仅电影类项目（movie）。
     */
    @Override
    public List<SakuraComicProject> selectAuditMovieList(AdminProjectAuditQueryRequest request) {
        return listProjectsByType(request, PROJECT_TYPE_MOVIE);
    }

    /**
     * 按项目类型查询审核列表（项目审核/电影审核共用）。
     * 状态不传默认查「审核中」；审核中按更新时间升序（先提交先审），其它倒序。
     *
     * @param request     查询条件
     * @param projectType 强制限定的项目类型（series / movie）
     * @return 项目列表
     */
    private List<SakuraComicProject> listProjectsByType(AdminProjectAuditQueryRequest request, String projectType) {
        LambdaQueryWrapper<SakuraComicProject> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(SakuraComicProject::getDelFlag, DEL_FLAG_NORMAL);
        // 强制限定项目类型（项目审核=剧集类，电影审核=电影类）
        wrapper.eq(SakuraComicProject::getProjectType, projectType);
        // 状态：不传默认查「审核中」，便于审核人员直接看待办
        Integer status = request.getStatus() != null ? request.getStatus() : ProjectStatusEnum.AUDITING.getValue();
        wrapper.eq(SakuraComicProject::getStatus, status);
        if (StrUtil.isNotBlank(request.getProjectName())) {
            wrapper.like(SakuraComicProject::getProjectName, request.getProjectName());
        }
        if (request.getUserId() != null) {
            wrapper.eq(SakuraComicProject::getUserId, request.getUserId());
        }
        if (Objects.equals(status, ProjectStatusEnum.AUDITING.getValue())) {
            wrapper.orderByAsc(SakuraComicProject::getUpdateTime);
        } else {
            wrapper.orderByDesc(SakuraComicProject::getUpdateTime);
        }
        return sakuraComicProjectService.list(wrapper);
    }

    /**
     * 查询剧集审核列表（后台），附带所属项目类型（电影/剧集）。
     * 分页由 PageHelper 拦截剧集查询完成，转 VO 后需把 total 透传到新列表。
     */
    @Override
    public List<AuditEpisodeListVO> selectAuditEpisodeList(AdminEpisodeAuditQueryRequest request) {
        LambdaQueryWrapper<SakuraComicEpisode> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(SakuraComicEpisode::getDelFlag, DEL_FLAG_NORMAL);
        Integer status = request.getStatus() != null ? request.getStatus() : EpisodeStatusEnum.AUDITING.getValue();
        wrapper.eq(SakuraComicEpisode::getStatus, status);
        if (request.getProjectId() != null) {
            wrapper.eq(SakuraComicEpisode::getProjectId, request.getProjectId());
        }
        if (StrUtil.isNotBlank(request.getComicTitle())) {
            wrapper.like(SakuraComicEpisode::getComicTitle, request.getComicTitle());
        }
        if (request.getUserId() != null) {
            wrapper.eq(SakuraComicEpisode::getUserId, request.getUserId());
        }
        if (Objects.equals(status, EpisodeStatusEnum.AUDITING.getValue())) {
            wrapper.orderByAsc(SakuraComicEpisode::getUpdateTime);
        } else {
            wrapper.orderByDesc(SakuraComicEpisode::getUpdateTime);
        }
        List<SakuraComicEpisode> episodes = sakuraComicEpisodeService.list(wrapper);
        // 批量查所属项目类型，逐行标注电影/剧集
        Map<Long, String> projectTypeMap = loadProjectTypeMap(
                episodes.stream().map(SakuraComicEpisode::getProjectId).collect(Collectors.toList()));
        List<AuditEpisodeListVO> voList = episodes.stream()
                .map(ep -> {
                    String type = projectTypeMap.get(ep.getProjectId());
                    return AuditEpisodeListVO.builder()
                            .id(ep.getId())
                            .projectId(ep.getProjectId())
                            .projectType(type)
                            .projectTypeDesc(bizDesc(type))
                            .episodeNo(ep.getEpisodeNo())
                            .comicTitle(ep.getComicTitle())
                            .comicCoverUrl(ep.getComicCoverUrl())
                            .userId(ep.getUserId())
                            .status(ep.getStatus())
                            .statusReason(ep.getStatusReason())
                            .createTime(ep.getCreateTime())
                            .updateTime(ep.getUpdateTime())
                            .build();
                })
                .collect(Collectors.toList());
        // 透传分页总数
        if (episodes instanceof Page) {
            Page<AuditEpisodeListVO> voPage = new Page<>();
            voPage.setTotal(((Page<?>) episodes).getTotal());
            voPage.addAll(voList);
            return voPage;
        }
        return voList;
    }

    /**
     * 审核项目（通过/驳回）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void auditProject(AdminAuditActionRequest request, String operator) {
        // 驳回必须填写原因
        boolean pass = Boolean.TRUE.equals(request.getPass());
        String reason = request.getReason();
        if (!pass && StrUtil.isBlank(reason)) {
            log.info("审核项目失败，驳回原因为空: projectId={}", request.getId());
            throw new ServiceException("请填写原因");
        }
        SakuraComicProject project = sakuraComicProjectService.getOne(
                Wrappers.<SakuraComicProject>lambdaQuery()
                        .eq(SakuraComicProject::getId, request.getId())
                        .eq(SakuraComicProject::getDelFlag, DEL_FLAG_NORMAL));
        if (project == null) {
            log.info("审核项目失败，项目不存在: projectId={}", request.getId());
            throw new ServiceException("项目不存在");
        }
        Integer beforeStatus = project.getStatus();
        // 仅「审核中」可审核
        if (!Objects.equals(beforeStatus, ProjectStatusEnum.AUDITING.getValue())) {
            log.info("审核项目失败，非审核中状态: projectId={}, status={}", request.getId(), beforeStatus);
            throw new ServiceException("非审核状态");
        }
        boolean metadataChanged = hasPendingProjectMetadata(project);
        Integer afterStatus = pass ? ProjectStatusEnum.AUDIT_PASSED.getValue() : ProjectStatusEnum.AUDIT_FAILED.getValue();
        // 重审驳回保护：作品曾过审且本次存在待审成片或待审元数据时，
        // 状态保持「审核通过(4)」让线上旧版本继续展示，待审快照保留供用户修改后再提审；
        // 首次审核（从未过审）驳回仍按「审核失败(5)」处理
        if (!pass && isReauditOfPassedContent(AuditTargetTypeEnum.PROJECT.getValue(), request.getId(),
                request.getId(), MOVIE_EPISODE_ID, metadataChanged)) {
            afterStatus = ProjectStatusEnum.AUDIT_PASSED.getValue();
            log.info("重审驳回保护生效，项目状态保持已过审(线上旧版本继续展示), projectId={}", request.getId());
        }
        // 更新状态与状态原因（通过时原因可空、驳回时写入原因）
        Date now = DateUtils.getNowDate();
        boolean autoPublished = pass && !Objects.equals(IS_PUBLIC_YES, project.getIsPublic());
        if (pass) {
            assertProjectReviewContent(project);
            if (autoPublished) {
                publishPermissionService.assertCanPublish(project.getUserId());
            }
        }
        LambdaUpdateWrapper<SakuraComicProject> updateWrapper = Wrappers.lambdaUpdate();
        updateWrapper.eq(SakuraComicProject::getId, request.getId());
        updateWrapper.eq(SakuraComicProject::getStatus, beforeStatus);
        updateWrapper.set(SakuraComicProject::getStatus, afterStatus);
        updateWrapper.set(SakuraComicProject::getStatusReason, reason);
        updateWrapper.set(SakuraComicProject::getUpdateBy, operator);
        updateWrapper.set(SakuraComicProject::getUpdateTime, now);
        if (pass && metadataChanged) {
            // 审核状态与公开元数据在同一条 UPDATE 中转正，避免公开侧读到半完成版本。
            updateWrapper.set(SakuraComicProject::getProjectName, effectiveProjectName(project));
            updateWrapper.set(SakuraComicProject::getProjectDesc, effectiveProjectDesc(project));
            updateWrapper.set(SakuraComicProject::getCoverUrl, effectiveProjectCoverUrl(project));
            updateWrapper.set(SakuraComicProject::getPendingProjectName, null);
            updateWrapper.set(SakuraComicProject::getPendingProjectDesc, null);
            updateWrapper.set(SakuraComicProject::getPendingCoverUrl, null);
        }
        // 电影与剧集作品共用项目级公开状态，项目审核通过后在同一事务中自动发布。
        if (autoPublished) {
            updateWrapper.set(SakuraComicProject::getIsPublic, IS_PUBLIC_YES);
            updateWrapper.set(SakuraComicProject::getPublishTime, now);
        } else if (Objects.equals(afterStatus, ProjectStatusEnum.AUDIT_FAILED.getValue())) {
            // 审核失败的项目必须保持未发布，避免历史脏数据留下公开标记。
            updateWrapper.set(SakuraComicProject::getIsPublic, IS_PUBLIC_NO);
        }
        boolean updated = sakuraComicProjectService.update(updateWrapper);
        if (!updated) {
            log.info("审核项目失败，状态已变化: projectId={}, beforeStatus={}", request.getId(), beforeStatus);
            throw new ServiceException("状态已变化");
        }
        // 审核通过：电影项目存在待审新片时新片转正（覆盖旧片、旧片文件回收）
        if (pass && PROJECT_TYPE_MOVIE.equals(project.getProjectType())) {
            promotePendingVideo(request.getId(), MOVIE_EPISODE_ID, operator);
        }
        if (pass) {
            applicationEventPublisher.publishEvent(new ProjectAuditPassedEvent(
                    project.getId(), project.getUserId(), now.toInstant()));
        }
        // 写入审核流水
        Integer action = pass ? AuditActionEnum.PASS.getValue() : AuditActionEnum.REJECT.getValue();
        sakuraComicAuditRecordService.saveAuditRecord(AuditTargetTypeEnum.PROJECT.getValue(), request.getId(),
                project.getUserId(), action, beforeStatus, afterStatus, reason, operator);
        if (autoPublished) {
            // 自动发布沿用现有上架审核流水，不新增平行状态。
            sakuraComicAuditRecordService.saveAuditRecord(AuditTargetTypeEnum.PROJECT.getValue(), request.getId(),
                    project.getUserId(), AuditActionEnum.FORCE_ONLINE.getValue(),
                    afterStatus, afterStatus, reason, operator);
        }
        // 微信公众号推送：审核结果通知内容归属用户（推送服务内部吞异常，不影响审核主流程）
        wechatNotifyService.notifyContentAudit(AuditTargetTypeEnum.PROJECT.getValue(), request.getId(),
                pass ? com.sakura.notify.wechat.service.IWechatNotifyService.AUDIT_EVENT_PASSED
                        : com.sakura.notify.wechat.service.IWechatNotifyService.AUDIT_EVENT_REJECTED,
                reason);
        if (autoPublished) {
            wechatNotifyService.notifyContentAudit(AuditTargetTypeEnum.PROJECT.getValue(), request.getId(),
                    com.sakura.notify.wechat.service.IWechatNotifyService.AUDIT_EVENT_PUBLISHED, null);
        }
    }

    /**
     * 校验项目是否满足自动发布条件。
     *
     * @param project 项目
     */
    private void assertProjectReviewContent(SakuraComicProject project) {
        if (StrUtil.isBlank(effectiveProjectDesc(project))) {
            log.info("审核通过失败，项目描述缺失: projectId={}", project.getId());
            throw new ServiceException("请填写项目描述");
        }
        if (StrUtil.isBlank(effectiveProjectCoverUrl(project))) {
            log.info("审核通过失败，项目封面缺失: projectId={}", project.getId());
            throw new ServiceException("请上传封面图");
        }
        if (!mediaUrlResolver.isSiteImageUrl(effectiveProjectCoverUrl(project))) {
            log.info("审核通过失败，项目封面地址无效: projectId={}", project.getId());
            throw new ServiceException("封面图地址无效");
        }
        if (PROJECT_TYPE_MOVIE.equals(project.getProjectType())) {
            SakuraEpisodeEditor editor = getEditor(project.getId(), MOVIE_EPISODE_ID);
            if (!hasPlayableVideo(editor)) {
                log.info("审核通过失败，电影成片未就绪: projectId={}", project.getId());
                throw new ServiceException("成片未就绪");
            }
            return;
        }
        if (!hasApprovedEpisodeForProject(project.getId())) {
            log.info("审核通过失败，项目无过审剧集: projectId={}", project.getId());
            throw new ServiceException("暂无过审剧集");
        }
    }

    /** 项目审核时把正在重审、但历史已有 PASS 的单集视为有效线上版本。 */
    private boolean hasApprovedEpisodeForProject(Long projectId)
    {
        // 查询字段精简：仅需剧集主键与状态（新增使用字段时此处必须同步补充）。
        List<SakuraComicEpisode> episodes = sakuraComicEpisodeService.list(Wrappers.<SakuraComicEpisode>lambdaQuery()
                .select(SakuraComicEpisode::getId, SakuraComicEpisode::getStatus)
                .eq(SakuraComicEpisode::getProjectId, projectId)
                .in(SakuraComicEpisode::getStatus, EpisodeStatusEnum.AUDITING.getValue(),
                        EpisodeStatusEnum.AUDIT_PASSED.getValue())
                .eq(SakuraComicEpisode::getDelFlag, DEL_FLAG_NORMAL));
        if (episodes.isEmpty()) {
            return false;
        }
        if (episodes.stream().anyMatch(episode ->
                Objects.equals(episode.getStatus(), EpisodeStatusEnum.AUDIT_PASSED.getValue()))) {
            return true;
        }
        List<Long> auditingIds = episodes.stream().map(SakuraComicEpisode::getId).toList();
        return sakuraComicAuditRecordService.count(Wrappers.<SakuraComicAuditRecord>lambdaQuery()
                .eq(SakuraComicAuditRecord::getTargetType, AuditTargetTypeEnum.EPISODE.getValue())
                .in(SakuraComicAuditRecord::getTargetId, auditingIds)
                .eq(SakuraComicAuditRecord::getAction, AuditActionEnum.PASS.getValue())) > 0;
    }

    /**
     * 审核剧集（通过/驳回）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void auditEpisode(AdminAuditActionRequest request, String operator) {
        boolean pass = Boolean.TRUE.equals(request.getPass());
        String reason = request.getReason();
        if (!pass && StrUtil.isBlank(reason)) {
            log.info("审核剧集失败，驳回原因为空: episodeId={}", request.getId());
            throw new ServiceException("请填写原因");
        }
        SakuraComicEpisode episode = sakuraComicEpisodeService.getOne(
                Wrappers.<SakuraComicEpisode>lambdaQuery()
                        .eq(SakuraComicEpisode::getId, request.getId())
                        .eq(SakuraComicEpisode::getDelFlag, DEL_FLAG_NORMAL));
        if (episode == null) {
            log.info("审核剧集失败，剧集不存在: episodeId={}", request.getId());
            throw new ServiceException("剧集不存在");
        }
        Integer beforeStatus = episode.getStatus();
        if (!Objects.equals(beforeStatus, EpisodeStatusEnum.AUDITING.getValue())) {
            log.info("审核剧集失败，非审核中状态: episodeId={}, status={}", request.getId(), beforeStatus);
            throw new ServiceException("非审核状态");
        }
        boolean metadataChanged = hasPendingEpisodeMetadata(episode);
        Integer afterStatus = pass ? EpisodeStatusEnum.AUDIT_PASSED.getValue() : EpisodeStatusEnum.AUDIT_FAILED.getValue();
        // 重审驳回保护：该集旧片曾过审、本次驳回的只是待审新片 → 状态保持「审核通过(4)」，
        // 驳回原因写入 statusReason，待审新片保留；首次审核驳回仍按「审核失败(5)」处理
        if (!pass && isReauditOfPassedContent(AuditTargetTypeEnum.EPISODE.getValue(), request.getId(),
                episode.getProjectId(), request.getId(), metadataChanged)) {
            afterStatus = EpisodeStatusEnum.AUDIT_PASSED.getValue();
            log.info("重审驳回保护生效，剧集状态保持已过审(旧片继续展示), episodeId={}", request.getId());
        }
        LambdaUpdateWrapper<SakuraComicEpisode> updateWrapper = Wrappers.lambdaUpdate();
        updateWrapper.eq(SakuraComicEpisode::getId, request.getId());
        updateWrapper.eq(SakuraComicEpisode::getStatus, beforeStatus);
        if (pass) {
            assertEpisodeReviewContent(episode);
        }
        updateWrapper.set(SakuraComicEpisode::getStatus, afterStatus);
        updateWrapper.set(SakuraComicEpisode::getStatusReason, reason);
        updateWrapper.set(SakuraComicEpisode::getUpdateBy, operator);
        updateWrapper.set(SakuraComicEpisode::getUpdateTime, DateUtils.getNowDate());
        if (pass && metadataChanged) {
            updateWrapper.set(SakuraComicEpisode::getComicTitle, effectiveEpisodeTitle(episode));
            updateWrapper.set(SakuraComicEpisode::getComicDesc, effectiveEpisodeDesc(episode));
            updateWrapper.set(SakuraComicEpisode::getComicCoverUrl, effectiveEpisodeCoverUrl(episode));
            updateWrapper.set(SakuraComicEpisode::getPendingComicTitle, null);
            updateWrapper.set(SakuraComicEpisode::getPendingComicDesc, null);
            updateWrapper.set(SakuraComicEpisode::getPendingComicCoverUrl, null);
        }
        boolean updated = sakuraComicEpisodeService.update(updateWrapper);
        if (!updated) {
            log.info("审核剧集失败，状态已变化: episodeId={}, beforeStatus={}", request.getId(), beforeStatus);
            throw new ServiceException("状态已变化");
        }
        // 审核通过：该集存在待审新片时新片转正（覆盖旧片、旧片文件回收）
        if (pass) {
            promotePendingVideo(episode.getProjectId(), request.getId(), operator);
        }
        // 单集没有独立公开字段：所属项目已公开时本集立即可播，否则等待项目级审核通过后统一公开。
        Integer action = pass ? AuditActionEnum.PASS.getValue() : AuditActionEnum.REJECT.getValue();
        sakuraComicAuditRecordService.saveAuditRecord(AuditTargetTypeEnum.EPISODE.getValue(), request.getId(),
                episode.getUserId(), action, beforeStatus, afterStatus, reason, operator);
        // 微信公众号推送：审核结果通知内容归属用户（推送服务内部吞异常，不影响审核主流程）
        wechatNotifyService.notifyContentAudit(AuditTargetTypeEnum.EPISODE.getValue(), request.getId(),
                pass ? com.sakura.notify.wechat.service.IWechatNotifyService.AUDIT_EVENT_PASSED
                        : com.sakura.notify.wechat.service.IWechatNotifyService.AUDIT_EVENT_REJECTED,
                reason);
    }

    /**
     * 判定本次审核是否为「已过审内容的重审」：存在待审新片，且该对象历史上有过「审核通过」流水。
     * 满足时驳回不打回整体状态（旧片保持过审继续展示），仅新片被拒。
     * 判定异常按"非重审"处理（走常规驳回），保证审核主流程可用。
     *
     * @param targetType 审核对象类型（project/episode）
     * @param targetId   审核对象ID
     * @param projectId  剪辑记录定位：项目ID
     * @param episodeId  剪辑记录定位：剧集ID（电影为 0）
     * @return true=已过审内容的重审
     */
    private boolean isReauditOfPassedContent(String targetType, Long targetId, Long projectId, Long episodeId,
                                             boolean hasPendingMetadata) {
        try {
            // 查询字段精简：仅需待审片字段（新增使用字段时此处必须同步补充）
            SakuraEpisodeEditor editor = sakuraEpisodeEditorService.getOne(Wrappers.<SakuraEpisodeEditor>lambdaQuery()
                    .select(SakuraEpisodeEditor::getId, SakuraEpisodeEditor::getPendingVideoUrl)
                    .eq(SakuraEpisodeEditor::getProjectId, projectId)
                    .eq(SakuraEpisodeEditor::getEpisodeId, episodeId)
                    .eq(SakuraEpisodeEditor::getDelFlag, DEL_FLAG_NORMAL)
                    .orderByDesc(SakuraEpisodeEditor::getId)
                    .last("LIMIT 1"));
            boolean hasPendingVideo = Objects.nonNull(editor) && StrUtil.isNotBlank(editor.getPendingVideoUrl());
            if (!hasPendingVideo && !hasPendingMetadata) {
                return false;
            }
            // 历史过审流水：曾通过审核才有"旧片继续展示"的保护必要（首次审核中重新导出不享受保护）
            long passedCount = sakuraComicAuditRecordService.count(Wrappers.<SakuraComicAuditRecord>lambdaQuery()
                    .eq(SakuraComicAuditRecord::getTargetType, targetType)
                    .eq(SakuraComicAuditRecord::getTargetId, targetId)
                    .eq(SakuraComicAuditRecord::getAction, AuditActionEnum.PASS.getValue()));
            return passedCount > 0;
        } catch (Exception ex) {
            log.error("重审判定异常, targetType={}, targetId={}", targetType, targetId, ex);
            return false;
        }
    }

    /**
     * 审核通过后的待审新片转正：
     * pending_video_url 非空 → final_video_url 替换为新片、pending 清空；
     * 被替换的旧片文件登记 OSS 回收（若旧片仍被生成记录引用则跳过物理删除，防止其它引用变死链）。
     * 无待审新片时零影响；转正失败仅记日志，不阻断审核主流程。
     *
     * @param projectId 项目ID
     * @param episodeId 剧集ID（电影为 0）
     * @param operator  操作人
     */
    private void promotePendingVideo(Long projectId, Long episodeId, String operator) {
        try {
            // 查询字段精简：转正只需成片双槽字段（新增使用字段时此处必须同步补充）
            SakuraEpisodeEditor editor = sakuraEpisodeEditorService.getOne(Wrappers.<SakuraEpisodeEditor>lambdaQuery()
                    .select(SakuraEpisodeEditor::getId, SakuraEpisodeEditor::getFinalVideoUrl,
                            SakuraEpisodeEditor::getFinalVideoFingerprint,
                            SakuraEpisodeEditor::getPendingVideoUrl,
                            SakuraEpisodeEditor::getPendingVideoFingerprint)
                    .eq(SakuraEpisodeEditor::getProjectId, projectId)
                    .eq(SakuraEpisodeEditor::getEpisodeId, episodeId)
                    .eq(SakuraEpisodeEditor::getDelFlag, DEL_FLAG_NORMAL)
                    .orderByDesc(SakuraEpisodeEditor::getId)
                    .last("LIMIT 1"));
            if (Objects.isNull(editor) || StrUtil.isBlank(editor.getPendingVideoUrl())) {
                return;
            }
            String oldVideoUrl = editor.getFinalVideoUrl();
            String newVideoUrl = editor.getPendingVideoUrl();
            LambdaUpdateWrapper<SakuraEpisodeEditor> update = Wrappers.lambdaUpdate();
            update.eq(SakuraEpisodeEditor::getId, editor.getId());
            update.set(SakuraEpisodeEditor::getFinalVideoUrl, newVideoUrl);
            update.set(SakuraEpisodeEditor::getFinalVideoFingerprint,
                    editor.getPendingVideoFingerprint());
            update.set(SakuraEpisodeEditor::getPendingVideoUrl, null);
            update.set(SakuraEpisodeEditor::getPendingVideoFingerprint, null);
            update.set(SakuraEpisodeEditor::getUpdateBy, operator);
            update.set(SakuraEpisodeEditor::getUpdateTime, DateUtils.getNowDate());
            sakuraEpisodeEditorService.update(update);
            log.info("审核通过待审新片转正, editorId={}, projectId={}, episodeId={}", editor.getId(), projectId, episodeId);
            // 旧片文件回收：仍被生成记录（接口1成片等）引用时跳过删除，仅换 URL
            if (StrUtil.isNotBlank(oldVideoUrl) && !Objects.equals(oldVideoUrl, newVideoUrl)) {
                long refCount = sakuraGenRecordService.count(Wrappers.<SakuraGenRecord>lambdaQuery()
                        .eq(SakuraGenRecord::getFileUrl, oldVideoUrl)
                        .eq(SakuraGenRecord::getDelFlag, DEL_FLAG_NORMAL));
                if (refCount > 0) {
                    log.info("旧成片仍被生成记录引用,跳过文件删除, url={}, refCount={}", oldVideoUrl, refCount);
                } else {
                    mediaOssCleanupService.cleanupFiles(Collections.singletonList(oldVideoUrl));
                    log.info("旧成片文件已登记回收, url={}", oldVideoUrl);
                }
            }
        } catch (Exception ex) {
            // 转正属于审核后置增强，异常不回滚审核结果（可人工修复），仅记录告警
            log.error("待审新片转正异常, projectId={}, episodeId={}", projectId, episodeId, ex);
        }
    }

    /**
     * 查询审核流水记录列表（后台），转换为带描述的VO（含电影/剧集标注）。
     * 分页由 PageHelper 拦截内部查询完成，转 VO 后把 total 透传到新列表。
     */
    @Override
    public List<AuditRecordVO> selectAuditRecordList(AdminAuditRecordQueryRequest request) {
        SakuraComicAuditRecord query = new SakuraComicAuditRecord();
        query.setTargetType(request.getTargetType());
        query.setTargetId(request.getTargetId());
        query.setOwnerUserId(request.getOwnerUserId());
        query.setAction(request.getAction());
        List<SakuraComicAuditRecord> list = sakuraComicAuditRecordService.selectAuditRecordList(query);
        // 批量查项目类型：仅针对 target_type=project 的记录（剧集记录恒为剧集）
        List<Long> projectTargetIds = list.stream()
                .filter(r -> AuditTargetTypeEnum.PROJECT.getValue().equals(r.getTargetType()))
                .map(SakuraComicAuditRecord::getTargetId)
                .collect(Collectors.toList());
        Map<Long, String> projectTypeMap = loadProjectTypeMap(projectTargetIds);
        // 转换为VO并补充类型/动作/电影剧集的中文描述
        List<AuditRecordVO> voList = list.stream()
                .map(r -> convertToVO(r, projectTypeMap))
                .collect(Collectors.toList());
        // 透传分页总数
        if (list instanceof Page) {
            Page<AuditRecordVO> voPage = new Page<>();
            voPage.setTotal(((Page<?>) list).getTotal());
            voPage.addAll(voList);
            return voPage;
        }
        return voList;
    }

    /**
     * 查询项目审核详情（后台）：只审核项目封面与基本信息，不涉及成片视频。
     */
    @Override
    public AuditProjectDetailVO getProjectAuditDetail(AdminAuditDetailRequest request) {
        SakuraComicProject project = sakuraComicProjectService.getOne(
                Wrappers.<SakuraComicProject>lambdaQuery()
                        .eq(SakuraComicProject::getId, request.getId())
                        .eq(SakuraComicProject::getDelFlag, DEL_FLAG_NORMAL));
        if (project == null) {
            log.info("查询项目审核详情失败，项目不存在: projectId={}", request.getId());
            throw new ServiceException("项目不存在");
        }
        boolean metadataChanged = hasPendingProjectMetadata(project);
        return AuditProjectDetailVO.builder()
                .id(project.getId())
                .userId(project.getUserId())
                .authorNickname(loadAuthorNickname(project.getUserId()))
                .projectName(effectiveProjectName(project))
                .publishedProjectName(metadataChanged ? project.getProjectName() : null)
                .projectDesc(effectiveProjectDesc(project))
                .publishedProjectDesc(metadataChanged ? project.getProjectDesc() : null)
                .projectType(project.getProjectType())
                .projectTypeDesc(bizDesc(project.getProjectType()))
                .coverUrl(effectiveProjectCoverUrl(project))
                .publishedCoverUrl(metadataChanged ? project.getCoverUrl() : null)
                .metadataChanged(metadataChanged)
                .aspectRatio(project.getAspectRatio())
                .scriptType(project.getScriptType())
                .videoStyleType(project.getVideoStyleType())
                .videoStyleValue(project.getVideoStyleValue())
                .defaultGenMode(project.getDefaultGenMode())
                .defaultCreationMode(project.getDefaultCreationMode())
                .status(project.getStatus())
                .statusReason(project.getStatusReason())
                .isPublic(project.getIsPublic())
                .publishTime(project.getPublishTime())
                .createTime(project.getCreateTime())
                .updateTime(project.getUpdateTime())
                .build();
    }

    /**
     * 查询电影审核详情（后台）：审核封面 + 成品视频（episode_id=0）。
     */
    @Override
    public AuditMovieDetailVO getMovieAuditDetail(AdminAuditDetailRequest request) {
        SakuraComicProject project = sakuraComicProjectService.getOne(
                Wrappers.<SakuraComicProject>lambdaQuery()
                        .eq(SakuraComicProject::getId, request.getId())
                        .eq(SakuraComicProject::getDelFlag, DEL_FLAG_NORMAL));
        if (project == null) {
            log.info("查询电影审核详情失败，项目不存在: projectId={}", request.getId());
            throw new ServiceException("电影不存在");
        }
        boolean metadataChanged = hasPendingProjectMetadata(project);
        // 电影成片放在编辑器表 episode_id=0
        SakuraEpisodeEditor editor = getEditor(request.getId(), MOVIE_EPISODE_ID);
        return AuditMovieDetailVO.builder()
                .id(project.getId())
                .userId(project.getUserId())
                .authorNickname(loadAuthorNickname(project.getUserId()))
                .projectName(effectiveProjectName(project))
                .publishedProjectName(metadataChanged ? project.getProjectName() : null)
                .projectDesc(effectiveProjectDesc(project))
                .publishedProjectDesc(metadataChanged ? project.getProjectDesc() : null)
                .projectType(project.getProjectType())
                .projectTypeDesc(bizDesc(project.getProjectType()))
                .coverUrl(effectiveProjectCoverUrl(project))
                .publishedCoverUrl(metadataChanged ? project.getCoverUrl() : null)
                .metadataChanged(metadataChanged)
                .aspectRatio(project.getAspectRatio())
                .scriptType(project.getScriptType())
                .videoStyleType(project.getVideoStyleType())
                .videoStyleValue(project.getVideoStyleValue())
                .defaultGenMode(project.getDefaultGenMode())
                .defaultCreationMode(project.getDefaultCreationMode())
                .status(project.getStatus())
                .statusReason(project.getStatusReason())
                .isPublic(project.getIsPublic())
                .publishTime(project.getPublishTime())
                .finalVideoUrl(editor != null ? editor.getFinalVideoUrl() : null)
                // 待审新片：非空时本次审核对象是新片（finalVideoUrl 为公开中的旧片）
                .pendingVideoUrl(editor != null ? editor.getPendingVideoUrl() : null)
                .finalCoverUrl(editor != null ? editor.getCoverUrl() : null)
                .exportStatus(editor != null ? editor.getExportStatus() : null)
                .hasVideo(hasPlayableVideo(editor))
                .createTime(project.getCreateTime())
                .updateTime(project.getUpdateTime())
                .build();
    }

    /**
     * 查询剧集审核详情（含成品视频在线地址）。
     * 成片定位按类型：电影 episode_id=0，剧集 episode_id=该剧集ID。
     */
    @Override
    public AuditEpisodeDetailVO getEpisodeAuditDetail(AdminAuditDetailRequest request) {
        SakuraComicEpisode episode = sakuraComicEpisodeService.getOne(
                Wrappers.<SakuraComicEpisode>lambdaQuery()
                        .eq(SakuraComicEpisode::getId, request.getId())
                        .eq(SakuraComicEpisode::getDelFlag, DEL_FLAG_NORMAL));
        if (episode == null) {
            log.info("查询剧集审核详情失败，剧集不存在: episodeId={}", request.getId());
            throw new ServiceException("剧集不存在");
        }
        // 关联项目（取画风/比例/类型等上下文，便于审核）
        SakuraComicProject project = sakuraComicProjectService.getOne(
                Wrappers.<SakuraComicProject>lambdaQuery()
                        .eq(SakuraComicProject::getId, episode.getProjectId())
                        .eq(SakuraComicProject::getDelFlag, DEL_FLAG_NORMAL));
        String projectType = project != null ? project.getProjectType() : null;
        // 成片定位：电影取 episode_id=0，剧集取 episode_id=该剧集ID
        Long editorEpisodeId = PROJECT_TYPE_MOVIE.equals(projectType) ? MOVIE_EPISODE_ID : episode.getId();
        SakuraEpisodeEditor editor = getEditor(episode.getProjectId(), editorEpisodeId);
        boolean metadataChanged = hasPendingEpisodeMetadata(episode);
        return AuditEpisodeDetailVO.builder()
                .id(episode.getId())
                .projectId(episode.getProjectId())
                .projectName(project != null ? project.getProjectName() : null)
                .projectType(projectType)
                .projectTypeDesc(bizDesc(projectType))
                .projectDesc(project != null ? project.getProjectDesc() : null)
                .userId(episode.getUserId())
                .authorNickname(loadAuthorNickname(episode.getUserId()))
                .episodeNo(episode.getEpisodeNo())
                .comicTitle(effectiveEpisodeTitle(episode))
                .publishedComicTitle(metadataChanged ? episode.getComicTitle() : null)
                .comicDesc(effectiveEpisodeDesc(episode))
                .publishedComicDesc(metadataChanged ? episode.getComicDesc() : null)
                .comicCoverUrl(effectiveEpisodeCoverUrl(episode))
                .publishedComicCoverUrl(metadataChanged ? episode.getComicCoverUrl() : null)
                .metadataChanged(metadataChanged)
                .genMode(episode.getGenMode())
                .creationMode(episode.getCreationMode())
                .aspectRatio(project != null ? project.getAspectRatio() : null)
                .scriptType(project != null ? project.getScriptType() : null)
                .videoStyleType(project != null ? project.getVideoStyleType() : null)
                .videoStyleValue(project != null ? project.getVideoStyleValue() : null)
                .status(episode.getStatus())
                .statusReason(episode.getStatusReason())
                .finalVideoUrl(editor != null ? editor.getFinalVideoUrl() : null)
                // 待审新片：非空时本次审核对象是新片（finalVideoUrl 为公开中的旧片）
                .pendingVideoUrl(editor != null ? editor.getPendingVideoUrl() : null)
                .finalCoverUrl(editor != null ? editor.getCoverUrl() : null)
                .exportStatus(editor != null ? editor.getExportStatus() : null)
                .hasVideo(hasPlayableVideo(editor))
                .createTime(episode.getCreateTime())
                .updateTime(episode.getUpdateTime())
                .build();
    }

    /**
     * 查询成片编辑器记录（项目ID + 剧集ID 唯一）
     *
     * @param projectId 项目ID
     * @param episodeId 剧集ID（电影为0）
     * @return 编辑器记录，不存在返回null
     */
    private SakuraEpisodeEditor getEditor(Long projectId, Long episodeId) {
        return sakuraEpisodeEditorService.getOne(
                Wrappers.<SakuraEpisodeEditor>lambdaQuery()
                        .eq(SakuraEpisodeEditor::getProjectId, projectId)
                        .eq(SakuraEpisodeEditor::getEpisodeId, episodeId)
                        .eq(SakuraEpisodeEditor::getDelFlag, DEL_FLAG_NORMAL)
                        .orderByDesc(SakuraEpisodeEditor::getId)
                        .last("LIMIT 1"));
    }

    /** 审核动作执行前再次校验单集候选版本，防止提交后数据被并发清理。 */
    private void assertEpisodeReviewContent(SakuraComicEpisode episode)
    {
        if (StrUtil.isBlank(effectiveEpisodeDesc(episode))) {
            log.info("审核通过失败，单集描述缺失: episodeId={}", episode.getId());
            throw new ServiceException("请填写剧集描述");
        }
        if (StrUtil.isNotBlank(effectiveEpisodeCoverUrl(episode))
                && !mediaUrlResolver.isSiteImageUrl(effectiveEpisodeCoverUrl(episode))) {
            log.info("审核通过失败，单集封面地址无效: episodeId={}", episode.getId());
            throw new ServiceException("封面图地址无效");
        }
        SakuraEpisodeEditor editor = getEditor(episode.getProjectId(), episode.getId());
        if (!hasPlayableVideo(editor)) {
            log.info("审核通过失败，剧集成片未就绪: episodeId={}", episode.getId());
            throw new ServiceException("成片未就绪");
        }
    }

    private boolean hasPendingProjectMetadata(SakuraComicProject project)
    {
        return StrUtil.isNotBlank(project.getPendingProjectName())
                || StrUtil.isNotBlank(project.getPendingProjectDesc())
                || StrUtil.isNotBlank(project.getPendingCoverUrl());
    }

    private String effectiveProjectName(SakuraComicProject project)
    {
        return StrUtil.isNotBlank(project.getPendingProjectName())
                ? project.getPendingProjectName() : project.getProjectName();
    }

    private String effectiveProjectDesc(SakuraComicProject project)
    {
        return StrUtil.isNotBlank(project.getPendingProjectDesc())
                ? project.getPendingProjectDesc() : project.getProjectDesc();
    }

    private String effectiveProjectCoverUrl(SakuraComicProject project)
    {
        return StrUtil.isNotBlank(project.getPendingCoverUrl())
                ? project.getPendingCoverUrl() : project.getCoverUrl();
    }

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

    /**
     * 是否存在可播放的成品视频（有地址且导出成功）
     */
    private Boolean hasPlayableVideo(SakuraEpisodeEditor editor) {
        if (editor == null) {
            return Boolean.FALSE;
        }
        String reviewVideoUrl = StrUtil.isNotBlank(editor.getPendingVideoUrl())
                ? editor.getPendingVideoUrl() : editor.getFinalVideoUrl();
        return StrUtil.isNotBlank(reviewVideoUrl)
                && Objects.equals(editor.getExportStatus(), EXPORT_STATUS_SUCCESS);
    }

    /** 成片导出状态：导出成功 */
    private static final Integer EXPORT_STATUS_SUCCESS = 2;

    /**
     * 批量查询项目类型：projectId → projectType（用于标注电影/剧集）
     *
     * @param projectIds 项目ID集合（可含重复/空，内部去重过滤）
     * @return projectId → projectType 映射（缺失则不含该键）
     */
    private Map<Long, String> loadProjectTypeMap(List<Long> projectIds) {
        if (projectIds == null || projectIds.isEmpty()) {
            return new HashMap<>();
        }
        Set<Long> ids = projectIds.stream().filter(Objects::nonNull).collect(Collectors.toCollection(HashSet::new));
        if (ids.isEmpty()) {
            return new HashMap<>();
        }
        List<SakuraComicProject> projects = sakuraComicProjectService.list(
                Wrappers.<SakuraComicProject>lambdaQuery()
                        .select(SakuraComicProject::getId, SakuraComicProject::getProjectType)
                        .in(SakuraComicProject::getId, ids));
        Map<Long, String> map = new HashMap<>();
        for (SakuraComicProject p : projects) {
            map.put(p.getId(), p.getProjectType());
        }
        return map;
    }

    /**
     * 项目类型 → 电影/剧集 文案（movie=电影，其它/空=剧集）
     */
    private String bizDesc(String projectType) {
        return PROJECT_TYPE_MOVIE.equals(projectType) ? BIZ_DESC_MOVIE : BIZ_DESC_SERIES;
    }

    /**
     * 查询作者昵称（账号不存在时返回空，不阻断审核详情）
     *
     * @param userId 用户ID
     * @return 昵称，查不到返回null
     */
    private String loadAuthorNickname(Long userId) {
        if (Objects.isNull(userId)) {
            return null;
        }
        SysUser user = sysUserService.selectUserById(userId);
        return Objects.isNull(user) ? null : user.getNickName();
    }

    /**
     * 实体转VO，补充类型/动作/电影剧集的中文描述
     *
     * @param record         审核记录
     * @param projectTypeMap 项目类型映射（仅 project 记录用到）
     */
    private AuditRecordVO convertToVO(SakuraComicAuditRecord record, Map<Long, String> projectTypeMap) {
        AuditTargetTypeEnum targetTypeEnum = AuditTargetTypeEnum.getByValue(record.getTargetType());
        AuditActionEnum actionEnum = AuditActionEnum.getByValue(record.getAction());
        // 业务类型（电影/剧集）：项目记录取项目类型；剧集记录恒为剧集
        String bizType;
        String bizTypeDesc;
        if (AuditTargetTypeEnum.PROJECT.getValue().equals(record.getTargetType())) {
            bizType = projectTypeMap.get(record.getTargetId());
            bizTypeDesc = bizDesc(bizType);
        } else {
            bizType = "series";
            bizTypeDesc = BIZ_DESC_SERIES;
        }
        return AuditRecordVO.builder()
                .id(record.getId())
                .targetType(record.getTargetType())
                .targetTypeDesc(targetTypeEnum != null ? targetTypeEnum.getDesc() : null)
                .bizType(bizType)
                .bizTypeDesc(bizTypeDesc)
                .targetId(record.getTargetId())
                .ownerUserId(record.getOwnerUserId())
                .action(record.getAction())
                .actionDesc(actionEnum != null ? actionEnum.getDesc() : null)
                .beforeStatus(record.getBeforeStatus())
                .afterStatus(record.getAfterStatus())
                .auditReason(record.getAuditReason())
                .operator(record.getOperator())
                .createTime(record.getCreateTime())
                .build();
    }
}
