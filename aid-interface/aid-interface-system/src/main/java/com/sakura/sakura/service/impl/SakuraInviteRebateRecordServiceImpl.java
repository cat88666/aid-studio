package com.sakura.sakura.service.impl;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sakura.sakura.domain.SakuraInviteRebateRecord;
import com.sakura.sakura.domain.dto.InviteRebateRowDTO;
import com.sakura.sakura.domain.vo.SakuraInviteRebateRecordVo;
import com.sakura.sakura.mapper.SakuraInviteRebateRecordMapper;
import com.sakura.sakura.service.ISakuraInviteRebateRecordService;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;

/**
 * 邀请返佣记录Service业务层处理
 *
 * @author 视觉AID
 */
@Service
public class SakuraInviteRebateRecordServiceImpl extends ServiceImpl<SakuraInviteRebateRecordMapper, SakuraInviteRebateRecord> implements ISakuraInviteRebateRecordService
{
    @Autowired
    private SakuraInviteRebateRecordMapper sakuraInviteRebateRecordMapper;

    /**
     * 后台返佣记录列表（联表 sys_user），分页由 Controller 的 startPage 统一处理
     */
    @Override
    public List<SakuraInviteRebateRecordVo> selectRebateRecordVoList(SakuraInviteRebateRecordVo query)
    {
        return sakuraInviteRebateRecordMapper.selectRebateRecordVoList(query);
    }

    /**
     * 按订单号查询返佣记录
     * 仅查询字段：id, inviterUserId, inviteeUserId, orderNo, rebateAmount, status（幂等判断与退款扣回所需）
     */
    @Override
    public SakuraInviteRebateRecord getByOrderNo(String orderNo)
    {
        if (StrUtil.isBlank(orderNo))
        {
            return null;
        }
        return this.getOne(Wrappers.<SakuraInviteRebateRecord>lambdaQuery()
                .select(SakuraInviteRebateRecord::getId, SakuraInviteRebateRecord::getInviterUserId,
                        SakuraInviteRebateRecord::getInviteeUserId, SakuraInviteRebateRecord::getOrderNo,
                        SakuraInviteRebateRecord::getRebateAmount, SakuraInviteRebateRecord::getStatus)
                .eq(SakuraInviteRebateRecord::getOrderNo, orderNo)
                .eq(SakuraInviteRebateRecord::getDelFlag, "0")
                .last("LIMIT 1"));
    }

    /**
     * C端「返佣明细」列表，分页由调用方 PageHelper.startPage 接管
     */
    @Override
    public List<InviteRebateRowDTO> selectRebateRows(Long inviterUserId)
    {
        return sakuraInviteRebateRecordMapper.selectRebateRows(inviterUserId);
    }

    /**
     * 统计某邀请人累计已发放返佣积分（status=granted）
     * 仅查询字段：rebateAmount（求和统计）
     */
    @Override
    public BigDecimal sumGrantedRebate(Long inviterUserId)
    {
        if (Objects.isNull(inviterUserId))
        {
            return BigDecimal.ZERO;
        }
        List<SakuraInviteRebateRecord> records = this.list(Wrappers.<SakuraInviteRebateRecord>lambdaQuery()
                .select(SakuraInviteRebateRecord::getRebateAmount)
                .eq(SakuraInviteRebateRecord::getInviterUserId, inviterUserId)
                .eq(SakuraInviteRebateRecord::getStatus, "granted")
                .eq(SakuraInviteRebateRecord::getDelFlag, "0"));
        if (CollectionUtil.isEmpty(records))
        {
            return BigDecimal.ZERO;
        }
        // 逐条累加已发放返佣，null 金额按 0 处理
        BigDecimal total = BigDecimal.ZERO;
        for (SakuraInviteRebateRecord record : records)
        {
            if (Objects.nonNull(record.getRebateAmount()))
            {
                total = total.add(record.getRebateAmount());
            }
        }
        return total;
    }
}
