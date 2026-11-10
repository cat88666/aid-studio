package com.sakura.sakura.service.impl;

import java.util.Arrays;
import java.util.List;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.sakura.sakura.domain.SakuraPayOrder;
import com.sakura.sakura.mapper.SakuraPayOrderMapper;
import com.sakura.sakura.service.ISakuraPayOrderService;
import com.sakura.common.utils.DateUtils;

/**
 * 支付订单CRUD Service业务层处理
 * 用于后台管理的增删改查操作
 *
 * @author 视觉AID
 */
@Service
public class SakuraPayOrderServiceImpl extends ServiceImpl<SakuraPayOrderMapper, SakuraPayOrder>  implements ISakuraPayOrderService
{
    @Autowired
    private SakuraPayOrderMapper sakuraPayOrderMapper;

    /**
     * 查询支付订单
     *
     * @param id 支付订单主键
     * @return 支付订单
     */
    @Override
    public SakuraPayOrder selectSakuraPayOrderById(Long id)
    {
        return sakuraPayOrderMapper.selectById(id);
    }

    /**
     * 查询支付订单列表
     *
     * @param sakuraPayOrder 支付订单
     * @return 支付订单
     */
    @Override
    public List<SakuraPayOrder> selectSakuraPayOrderList(SakuraPayOrder sakuraPayOrder)
    {
        LambdaQueryWrapper<SakuraPayOrder> wrapper = Wrappers.lambdaQuery();
        if (sakuraPayOrder != null)
        {
            if (StrUtil.isNotBlank(sakuraPayOrder.getOrderNo()))
            {
                wrapper.like(SakuraPayOrder::getOrderNo, sakuraPayOrder.getOrderNo());
            }
            if (StrUtil.isNotBlank(sakuraPayOrder.getTradeNo()))
            {
                wrapper.like(SakuraPayOrder::getTradeNo, sakuraPayOrder.getTradeNo());
            }
            if (sakuraPayOrder.getUserId() != null)
            {
                wrapper.eq(SakuraPayOrder::getUserId, sakuraPayOrder.getUserId());
            }
            if (sakuraPayOrder.getPackageId() != null)
            {
                wrapper.eq(SakuraPayOrder::getPackageId, sakuraPayOrder.getPackageId());
            }
            if (StrUtil.isNotBlank(sakuraPayOrder.getProductName()))
            {
                wrapper.like(SakuraPayOrder::getProductName, sakuraPayOrder.getProductName());
            }
            if (StrUtil.isNotBlank(sakuraPayOrder.getPayChannel()))
            {
                wrapper.eq(SakuraPayOrder::getPayChannel, sakuraPayOrder.getPayChannel());
            }
            if (StrUtil.isNotBlank(sakuraPayOrder.getPayStatus()))
            {
                wrapper.eq(SakuraPayOrder::getPayStatus, sakuraPayOrder.getPayStatus());
            }
        }
        wrapper.orderByDesc(SakuraPayOrder::getId);
        return sakuraPayOrderMapper.selectList(wrapper);
    }

    /**
     * 新增支付订单
     *
     * @param sakuraPayOrder 支付订单
     * @return 结果
     */
    @Override
    public int insertSakuraPayOrder(SakuraPayOrder sakuraPayOrder)
    {
        sakuraPayOrder.setCreateTime(DateUtils.getNowDate());
        return sakuraPayOrderMapper.insert(sakuraPayOrder) > 0 ? 1 : 0;
    }

    /**
     * 修改支付订单
     *
     * @param sakuraPayOrder 支付订单
     * @return 结果
     */
    @Override
    public int updateSakuraPayOrder(SakuraPayOrder sakuraPayOrder)
    {
        sakuraPayOrder.setUpdateTime(DateUtils.getNowDate());
        return sakuraPayOrderMapper.updateById(sakuraPayOrder) > 0 ? 1 : 0;
    }

    /**
     * 批量删除支付订单
     *
     * @param ids 需要删除的支付订单主键
     * @return 结果
     */
    @Override
    public int deleteSakuraPayOrderByIds(Long[] ids)
    {
        if (ids == null || ids.length == 0)
        {
            return 0;
        }
        return sakuraPayOrderMapper.deleteBatchIds(Arrays.asList(ids));
    }

    /**
     * 删除支付订单信息
     *
     * @param id 支付订单主键
     * @return 结果
     */
    @Override
    public int deleteSakuraPayOrderById(Long id)
    {
        if (id == null)
        {
            return 0;
        }
        return sakuraPayOrderMapper.deleteById(id);
    }

    /**
     * 按状态 + 过期时间下限扫描超时的待支付订单（QB1：Quartz 任务专用）
     */
    @Override
    public List<SakuraPayOrder> selectExpiredByStatus(String status, java.util.Date createdBefore, int limit)
    {
        // 兜底：limit 落在 1..1000 范围，避免上游传 0/Integer.MAX_VALUE 造成 OOM/无效查询
        int safeLimit = Math.max(1, Math.min(limit, 1000));
        LambdaQueryWrapper<SakuraPayOrder> queryWrapper = Wrappers.lambdaQuery();
        queryWrapper.eq(SakuraPayOrder::getPayStatus, status)
                .lt(SakuraPayOrder::getCreateTime, createdBefore)
                .orderByAsc(SakuraPayOrder::getCreateTime)
                .last("LIMIT " + safeLimit);
        return sakuraPayOrderMapper.selectList(queryWrapper);
    }
}
