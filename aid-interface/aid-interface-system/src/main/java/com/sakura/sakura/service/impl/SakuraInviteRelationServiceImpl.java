package com.sakura.sakura.service.impl;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sakura.sakura.domain.SakuraInviteRelation;
import com.sakura.sakura.domain.dto.InvitedUserRowDTO;
import com.sakura.sakura.domain.vo.SakuraInviteRelationVo;
import com.sakura.sakura.mapper.SakuraInviteRelationMapper;
import com.sakura.sakura.service.ISakuraInviteRelationService;
import com.sakura.common.exception.ServiceException;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * 邀请关系Service业务层处理
 *
 * @author 视觉AID
 */
@Slf4j
@Service
public class SakuraInviteRelationServiceImpl extends ServiceImpl<SakuraInviteRelationMapper, SakuraInviteRelation> implements ISakuraInviteRelationService
{
    @Autowired
    private SakuraInviteRelationMapper sakuraInviteRelationMapper;

    /**
     * 后台邀请关系列表（联表 sys_user），分页由 Controller 的 startPage 统一处理
     */
    @Override
    public List<SakuraInviteRelationVo> selectInviteRelationVoList(SakuraInviteRelationVo query)
    {
        return sakuraInviteRelationMapper.selectInviteRelationVoList(query);
    }

    /**
     * 按被邀请人查询邀请关系
     * 仅查询字段：id, inviterUserId, inviteeUserId, inviteCode, status, totalRebate（返佣与判重所需）
     */
    @Override
    public SakuraInviteRelation getByInvitee(Long inviteeUserId)
    {
        if (Objects.isNull(inviteeUserId))
        {
            return null;
        }
        return this.getOne(Wrappers.<SakuraInviteRelation>lambdaQuery()
                .select(SakuraInviteRelation::getId, SakuraInviteRelation::getInviterUserId,
                        SakuraInviteRelation::getInviteeUserId, SakuraInviteRelation::getInviteCode,
                        SakuraInviteRelation::getStatus, SakuraInviteRelation::getTotalRebate)
                .eq(SakuraInviteRelation::getInviteeUserId, inviteeUserId)
                .eq(SakuraInviteRelation::getDelFlag, "0")
                .last("LIMIT 1"));
    }

    /**
     * C端「我邀请的用户」列表，分页由调用方 PageHelper.startPage 接管
     */
    @Override
    public List<InvitedUserRowDTO> selectInvitedUserRows(Long inviterUserId)
    {
        return sakuraInviteRelationMapper.selectInvitedUserRows(inviterUserId);
    }

    /**
     * 统计某邀请人的有效邀请人数
     */
    @Override
    public long countByInviter(Long inviterUserId)
    {
        if (Objects.isNull(inviterUserId))
        {
            return 0L;
        }
        return this.count(Wrappers.<SakuraInviteRelation>lambdaQuery()
                .eq(SakuraInviteRelation::getInviterUserId, inviterUserId)
                .eq(SakuraInviteRelation::getDelFlag, "0"));
    }

    /**
     * 原子增减关系累计返佣积分（SQL delta 更新，避免读改写并发丢更新）
     */
    @Override
    public int changeTotalRebate(Long id, BigDecimal delta)
    {
        if (Objects.isNull(id) || Objects.isNull(delta))
        {
            return 0;
        }
        return sakuraInviteRelationMapper.changeTotalRebate(id, delta);
    }

    /**
     * 后台禁用/恢复邀请关系（仅更新 status/remark/更新人时间，其余字段不允许改动）
     */
    @Override
    public void changeStatus(Long id, String status, String remark, String updateBy)
    {
        // 关系ID必填
        if (Objects.isNull(id))
        {
            log.info("邀请关系状态变更缺少ID");
            throw new ServiceException("参数有误");
        }
        // 状态只允许 0正常 / 1禁用
        if (!Objects.equals("0", status) && !Objects.equals("1", status))
        {
            log.info("邀请关系状态值非法, id={}, status={}", id, status);
            throw new ServiceException("状态值有误");
        }
        // 关系必须存在且未删除
        SakuraInviteRelation relation = this.getOne(Wrappers.<SakuraInviteRelation>lambdaQuery()
                .select(SakuraInviteRelation::getId, SakuraInviteRelation::getStatus)
                .eq(SakuraInviteRelation::getId, id)
                .eq(SakuraInviteRelation::getDelFlag, "0")
                .last("LIMIT 1"));
        if (Objects.isNull(relation))
        {
            log.info("邀请关系不存在, id={}", id);
            throw new ServiceException("关系不存在");
        }
        // 更新操作必须填写更新时间和更新者
        boolean updated = this.update(Wrappers.<SakuraInviteRelation>lambdaUpdate()
                .eq(SakuraInviteRelation::getId, id)
                .eq(SakuraInviteRelation::getDelFlag, "0")
                .set(SakuraInviteRelation::getStatus, status)
                .set(StrUtil.isNotBlank(remark), SakuraInviteRelation::getRemark, remark)
                .set(SakuraInviteRelation::getUpdateBy, updateBy)
                .set(SakuraInviteRelation::getUpdateTime, new Date()));
        if (!updated)
        {
            log.error("邀请关系状态变更失败, id={}, status={}", id, status);
            throw new ServiceException("操作失败");
        }
        log.info("邀请关系状态变更成功, id={}, status={}, updateBy={}", id, status, updateBy);
    }
}
