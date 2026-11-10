package com.sakura.publish.service.impl;

import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.sakura.sakura.domain.SakuraComicProject;
import com.sakura.sakura.domain.SakuraPublishWhitelist;
import com.sakura.sakura.domain.SakuraUserProfile;
import com.sakura.sakura.domain.vo.SakuraPublishItemVo;
import com.sakura.sakura.domain.vo.SakuraPublishUserVo;
import com.sakura.sakura.domain.vo.SakuraPublishWhitelistVo;
import com.sakura.sakura.service.ISakuraComicAuditRecordService;
import com.sakura.sakura.service.ISakuraComicProjectService;
import com.sakura.sakura.service.ISakuraPublishWhitelistService;
import com.sakura.sakura.service.ISakuraUserProfileService;
import com.sakura.common.core.domain.entity.SysUser;
import com.sakura.common.exception.ServiceException;
import com.sakura.common.utils.DateUtils;
import com.sakura.core.service.ISysUserService;
import com.sakura.enums.AuditActionEnum;
import com.sakura.enums.AuditTargetTypeEnum;
import com.sakura.enums.ProjectStatusEnum;
import com.sakura.notify.wechat.service.IWechatNotifyService;
import com.sakura.publish.dto.AdminPublishActionRequest;
import com.sakura.publish.dto.AdminPublishListRequest;
import com.sakura.publish.dto.AdminPublishPermissionRequest;
import com.sakura.publish.dto.AdminPublishUserSearchRequest;
import com.sakura.publish.dto.AdminWhitelistAddRequest;
import com.sakura.publish.dto.AdminWhitelistQueryRequest;
import com.sakura.publish.dto.AdminWhitelistRemoveRequest;
import com.sakura.publish.service.IAdminPublishBusinessService;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * 后台发布管理业务Service实现
 *
 * @author 视觉AID
 */
@Slf4j
@Service
public class AdminPublishBusinessServiceImpl implements IAdminPublishBusinessService
{
    /** 删除标志：正常（未删除） */
    private static final String DEL_FLAG_NORMAL = "0";

    /** 是否公开：是 */
    private static final String IS_PUBLIC_YES = "1";

    /** 是否公开：否 */
    private static final String IS_PUBLIC_NO = "0";

    /** 用户级发布权限：允许 */
    private static final Integer USER_PUBLISH_ENABLED = 1;

    /** 用户级发布权限：禁止 */
    private static final Integer USER_PUBLISH_DISABLED = 0;

    @Autowired
    private ISakuraComicProjectService sakuraComicProjectService;

    @Autowired
    private ISakuraComicAuditRecordService sakuraComicAuditRecordService;

    @Autowired
    private ISakuraPublishWhitelistService sakuraPublishWhitelistService;

    @Autowired
    private ISakuraUserProfileService sakuraUserProfileService;

    @Autowired
    private ISysUserService sysUserService;

    /** 微信公众号推送：发布成功/审核回撤结果通知内容归属用户（内部吞异常，不影响主流程） */
    @Autowired
    private IWechatNotifyService wechatNotifyService;

    /**
     * 查询发布管理列表（审核通过未发布 + 已发布）
     *
     * @param request 查询条件
     * @return 发布管理列表
     */
    @Override
    public List<SakuraPublishItemVo> selectPublishList(AdminPublishListRequest request)
    {
        return sakuraComicProjectService.selectPublishItemVoList(request.getPublishState(),
                request.getProjectName(), request.getProjectType(), request.getKeyword());
    }

    /**
     * 上架作品（审核通过未发布 → 已发布）
     *
     * @param request  操作请求（原因可选）
     * @param operator 操作人
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void forceOnline(AdminPublishActionRequest request, String operator)
    {
        SakuraComicProject project = loadProject(request.getId());
        // 上架前提：必须审核通过
        if (!Objects.equals(project.getStatus(), ProjectStatusEnum.AUDIT_PASSED.getValue()))
        {
            log.info("上架失败，项目未审核通过: projectId={}, status={}", request.getId(), project.getStatus());
            throw new ServiceException("请先通过审核");
        }
        // 已发布幂等拦截
        if (Objects.equals(IS_PUBLIC_YES, project.getIsPublic()))
        {
            log.info("上架失败，作品已是发布状态: projectId={}", request.getId());
            throw new ServiceException("作品已发布");
        }
        // 置为公开并记录发布时间与操作原因
        LambdaUpdateWrapper<SakuraComicProject> updateWrapper = Wrappers.lambdaUpdate();
        updateWrapper.eq(SakuraComicProject::getId, request.getId());
        updateWrapper.set(SakuraComicProject::getIsPublic, IS_PUBLIC_YES);
        updateWrapper.set(SakuraComicProject::getPublishTime, DateUtils.getNowDate());
        updateWrapper.set(SakuraComicProject::getStatusReason, request.getReason());
        updateWrapper.set(SakuraComicProject::getUpdateBy, operator);
        updateWrapper.set(SakuraComicProject::getUpdateTime, DateUtils.getNowDate());
        sakuraComicProjectService.update(updateWrapper);
        // 写入审核流水（后台上架，状态不变）
        sakuraComicAuditRecordService.saveAuditRecord(AuditTargetTypeEnum.PROJECT.getValue(), request.getId(),
                project.getUserId(), AuditActionEnum.FORCE_ONLINE.getValue(),
                project.getStatus(), project.getStatus(), request.getReason(), operator);
        // 微信公众号推送：发布成功（推送服务内部吞异常，不影响主流程）
        wechatNotifyService.notifyContentAudit(AuditTargetTypeEnum.PROJECT.getValue(), request.getId(),
                IWechatNotifyService.AUDIT_EVENT_PUBLISHED, null);
    }

    /**
     * 下架作品（已发布 → 未发布，审核状态保留）
     *
     * @param request  操作请求（原因必填）
     * @param operator 操作人
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void forceOffline(AdminPublishActionRequest request, String operator)
    {
        if (StrUtil.isBlank(request.getReason()))
        {
            log.info("审核回撤失败，回撤理由为空: projectId={}", request.getId());
            throw new ServiceException("请填写原因");
        }
        SakuraComicProject project = loadProject(request.getId());
        // 未发布幂等拦截
        if (!Objects.equals(IS_PUBLIC_YES, project.getIsPublic()))
        {
            log.info("下架失败，作品未发布: projectId={}", request.getId());
            throw new ServiceException("作品未发布");
        }
        // 关闭公开并写入下架原因（审核状态保留，发布时间不清空）
        LambdaUpdateWrapper<SakuraComicProject> updateWrapper = Wrappers.lambdaUpdate();
        updateWrapper.eq(SakuraComicProject::getId, request.getId());
        updateWrapper.set(SakuraComicProject::getIsPublic, IS_PUBLIC_NO);
        updateWrapper.set(SakuraComicProject::getStatusReason, request.getReason());
        updateWrapper.set(SakuraComicProject::getUpdateBy, operator);
        updateWrapper.set(SakuraComicProject::getUpdateTime, DateUtils.getNowDate());
        sakuraComicProjectService.update(updateWrapper);
        // 写入审核流水（后台下架，状态不变）
        sakuraComicAuditRecordService.saveAuditRecord(AuditTargetTypeEnum.PROJECT.getValue(), request.getId(),
                project.getUserId(), AuditActionEnum.FORCE_OFFLINE.getValue(),
                project.getStatus(), project.getStatus(), request.getReason(), operator);
    }

    /**
     * 回撤审核（撤销审核通过并同步下架，状态转审核失败）
     *
     * @param request  操作请求（原因必填）
     * @param operator 操作人
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revokeAudit(AdminPublishActionRequest request, String operator)
    {
        if (StrUtil.isBlank(request.getReason()))
        {
            throw new ServiceException("请填写原因");
        }
        SakuraComicProject project = loadProject(request.getId());
        // 回撤前提：当前必须是审核通过状态
        if (!Objects.equals(project.getStatus(), ProjectStatusEnum.AUDIT_PASSED.getValue()))
        {
            log.info("回撤失败，项目非审核通过状态: projectId={}, status={}", request.getId(), project.getStatus());
            throw new ServiceException("非审核通过状态");
        }
        Integer beforeStatus = project.getStatus();
        Integer afterStatus = ProjectStatusEnum.AUDIT_FAILED.getValue();
        // 状态转审核失败并同步下架，回撤原因写入状态原因（用户可见后修改重新提审）
        LambdaUpdateWrapper<SakuraComicProject> updateWrapper = Wrappers.lambdaUpdate();
        updateWrapper.eq(SakuraComicProject::getId, request.getId());
        updateWrapper.eq(SakuraComicProject::getStatus, beforeStatus);
        updateWrapper.set(SakuraComicProject::getStatus, afterStatus);
        updateWrapper.set(SakuraComicProject::getIsPublic, IS_PUBLIC_NO);
        updateWrapper.set(SakuraComicProject::getStatusReason, request.getReason());
        updateWrapper.set(SakuraComicProject::getUpdateBy, operator);
        updateWrapper.set(SakuraComicProject::getUpdateTime, DateUtils.getNowDate());
        boolean updated = sakuraComicProjectService.update(updateWrapper);
        if (!updated)
        {
            log.info("审核回撤失败，状态已变化: projectId={}, beforeStatus={}", request.getId(), beforeStatus);
            throw new ServiceException("状态已变化");
        }
        // 写入审核流水（审核回撤）
        sakuraComicAuditRecordService.saveAuditRecord(AuditTargetTypeEnum.PROJECT.getValue(), request.getId(),
                project.getUserId(), AuditActionEnum.REVOKE.getValue(),
                beforeStatus, afterStatus, request.getReason(), operator);
        // 微信公众号推送：审核回撤（推送服务内部吞异常，不影响主流程）
        wechatNotifyService.notifyContentAudit(AuditTargetTypeEnum.PROJECT.getValue(), request.getId(),
                IWechatNotifyService.AUDIT_EVENT_REVOKED, request.getReason());
    }

    /**
     * 查询发布白名单列表（联表用户信息）
     *
     * @param request 查询条件
     * @return 白名单列表
     */
    @Override
    public List<SakuraPublishWhitelistVo> selectWhitelist(AdminWhitelistQueryRequest request)
    {
        return sakuraPublishWhitelistService.selectWhitelistVoList(request.getKeyword());
    }

    /**
     * 添加发布白名单
     *
     * @param request  添加请求
     * @param operator 操作人
     */
    @Override
    public void addWhitelist(AdminWhitelistAddRequest request, String operator)
    {
        // 校验用户存在
        SysUser user = sysUserService.selectUserById(request.getUserId());
        if (Objects.isNull(user))
        {
            log.info("添加白名单失败，用户不存在: userId={}", request.getUserId());
            throw new ServiceException("用户不存在");
        }
        // 已在名单内幂等拦截
        if (sakuraPublishWhitelistService.existsByUserId(request.getUserId()))
        {
            log.info("添加白名单失败，用户已在名单内: userId={}", request.getUserId());
            throw new ServiceException("用户已在名单");
        }
        SakuraPublishWhitelist entity = new SakuraPublishWhitelist();
        entity.setUserId(request.getUserId());
        entity.setRemark(request.getRemark());
        entity.setCreateBy(operator);
        entity.setCreateTime(DateUtils.getNowDate());
        sakuraPublishWhitelistService.save(entity);
    }

    /**
     * 移除发布白名单
     *
     * @param request  移除请求
     * @param operator 操作人
     */
    @Override
    public void removeWhitelist(AdminWhitelistRemoveRequest request, String operator)
    {
        SakuraPublishWhitelist entity = sakuraPublishWhitelistService.getById(request.getId());
        if (Objects.isNull(entity))
        {
            log.info("移除白名单失败，记录不存在: id={}", request.getId());
            throw new ServiceException("记录不存在");
        }
        sakuraPublishWhitelistService.removeById(request.getId());
        log.info("发布白名单已移除: id={}, userId={}, operator={}", request.getId(), entity.getUserId(), operator);
    }

    /**
     * 按 昵称/邮箱/手机号 搜索用户（供白名单添加与权限设置）
     *
     * @param request 搜索请求
     * @return 用户列表（昵称按「昵称(ID)」展示）
     */
    @Override
    public List<SakuraPublishUserVo> searchUsers(AdminPublishUserSearchRequest request)
    {
        List<SakuraPublishUserVo> users = sakuraPublishWhitelistService.searchPublishUsers(request.getKeyword().trim());
        // 昵称统一格式化为「昵称(ID)」，便于同昵称用户区分
        for (SakuraPublishUserVo user : users)
        {
            String nickName = StrUtil.isBlank(user.getNickName()) ? "" : user.getNickName();
            user.setNickName(nickName + "(" + user.getUserId() + ")");
        }
        return users;
    }

    /**
     * 设置用户级发布权限
     *
     * @param request  权限设置请求
     * @param operator 操作人
     */
    @Override
    public void setUserPublishPermission(AdminPublishPermissionRequest request, String operator)
    {
        // 仅允许 0/1 两种取值
        if (!Objects.equals(USER_PUBLISH_ENABLED, request.getPublishEnabled())
                && !Objects.equals(USER_PUBLISH_DISABLED, request.getPublishEnabled()))
        {
            throw new ServiceException("权限取值非法");
        }
        SakuraUserProfile profile = sakuraUserProfileService.getByUserId(request.getUserId());
        if (Objects.isNull(profile))
        {
            log.info("设置发布权限失败，用户扩展信息不存在: userId={}", request.getUserId());
            throw new ServiceException("用户不存在");
        }
        // 查询字段精简说明：仅更新 publish_enabled 与审计字段
        LambdaUpdateWrapper<SakuraUserProfile> updateWrapper = Wrappers.lambdaUpdate();
        updateWrapper.eq(SakuraUserProfile::getId, profile.getId());
        updateWrapper.set(SakuraUserProfile::getPublishEnabled, request.getPublishEnabled());
        updateWrapper.set(SakuraUserProfile::getUpdateBy, operator);
        updateWrapper.set(SakuraUserProfile::getUpdateTime, DateUtils.getNowDate());
        sakuraUserProfileService.update(updateWrapper);
        log.info("用户发布权限已更新: userId={}, publishEnabled={}, operator={}",
                request.getUserId(), request.getPublishEnabled(), operator);
    }

    /**
     * 加载未删除项目，不存在时抛出业务异常
     *
     * @param projectId 项目ID
     * @return 项目实体
     */
    private SakuraComicProject loadProject(Long projectId)
    {
        SakuraComicProject project = sakuraComicProjectService.getOne(
                Wrappers.<SakuraComicProject>lambdaQuery()
                        .eq(SakuraComicProject::getId, projectId)
                        .eq(SakuraComicProject::getDelFlag, DEL_FLAG_NORMAL));
        if (Objects.isNull(project))
        {
            log.info("发布管理操作失败，项目不存在: projectId={}", projectId);
            throw new ServiceException("项目不存在");
        }
        return project;
    }
}
