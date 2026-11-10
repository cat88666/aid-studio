package com.sakura.sakura.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sakura.sakura.domain.SakuraInviteRebateRecord;
import com.sakura.sakura.domain.dto.InviteRebateRowDTO;
import com.sakura.sakura.domain.vo.SakuraInviteRebateRecordVo;

/**
 * 邀请返佣记录Mapper接口
 *
 * @author 视觉AID
 */
public interface SakuraInviteRebateRecordMapper extends BaseMapper<SakuraInviteRebateRecord>
{
    /**
     * 后台返佣记录列表（联表 sys_user 取邀请人/被邀请人昵称）。
     * 仅返回列表展示必要字段，分页由调用方 PageHelper.startPage 接管。
     *
     * @param query 查询条件（inviterUserId/inviteeUserId/orderNo/status）
     * @return 返佣记录聚合列表
     */
    List<SakuraInviteRebateRecordVo> selectRebateRecordVoList(SakuraInviteRebateRecordVo query);

    /**
     * C端「返佣明细」列表（联表 sys_user 取被邀请人昵称）。
     * 仅返回展示必要字段，分页由调用方 PageHelper.startPage 接管。
     *
     * @param inviterUserId 邀请人用户ID
     * @return 返佣明细行列表
     */
    List<InviteRebateRowDTO> selectRebateRows(@Param("inviterUserId") Long inviterUserId);
}
