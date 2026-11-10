package com.sakura.sakura.service;

import java.math.BigDecimal;
import java.util.List;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sakura.sakura.domain.SakuraInviteRelation;
import com.sakura.sakura.domain.dto.InvitedUserRowDTO;
import com.sakura.sakura.domain.vo.SakuraInviteRelationVo;

/**
 * 邀请关系Service接口
 *
 * @author 视觉AID
 */
public interface ISakuraInviteRelationService extends IService<SakuraInviteRelation>
{
    /**
     * 后台邀请关系列表（联表 sys_user）
     *
     * @param query 查询条件
     * @return 邀请关系聚合列表
     */
    List<SakuraInviteRelationVo> selectInviteRelationVoList(SakuraInviteRelationVo query);

    /**
     * 按被邀请人查询邀请关系（不区分状态，用于绑定前判重与返佣查找）
     *
     * @param inviteeUserId 被邀请人用户ID
     * @return 邀请关系（不存在返回 null）
     */
    SakuraInviteRelation getByInvitee(Long inviteeUserId);

    /**
     * C端「我邀请的用户」列表（联表 sys_user）
     *
     * @param inviterUserId 邀请人用户ID
     * @return 被邀请用户行列表
     */
    List<InvitedUserRowDTO> selectInvitedUserRows(Long inviterUserId);

    /**
     * 统计某邀请人的有效邀请人数
     *
     * @param inviterUserId 邀请人用户ID
     * @return 邀请人数
     */
    long countByInviter(Long inviterUserId);

    /**
     * 原子增减关系累计返佣积分
     *
     * @param id    关系ID
     * @param delta 返佣变动量（正加负减）
     * @return 受影响行数
     */
    int changeTotalRebate(Long id, BigDecimal delta);

    /**
     * 后台禁用/恢复邀请关系（风控处置：禁用后该关系不再产生返佣）
     *
     * @param id       关系ID
     * @param status   目标状态（0正常 1禁用）
     * @param remark   处置备注（可空）
     * @param updateBy 操作人
     */
    void changeStatus(Long id, String status, String remark, String updateBy);
}
