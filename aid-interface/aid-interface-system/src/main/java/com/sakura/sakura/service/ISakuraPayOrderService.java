package com.sakura.sakura.service;

import java.util.List;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sakura.sakura.domain.SakuraPayOrder;

/**
 * 支付订单CRUD Service接口
 * 用于后台管理的增删改查操作
 *
 * @author 视觉AID
 */
public interface ISakuraPayOrderService  extends IService<SakuraPayOrder>
{
    /**
     * 查询支付订单
     *
     * @param id 支付订单主键
     * @return 支付订单
     */
    SakuraPayOrder selectSakuraPayOrderById(Long id);

    /**
     * 查询支付订单列表
     *
     * @param sakuraPayOrder 支付订单
     * @return 支付订单集合
     */
    List<SakuraPayOrder> selectSakuraPayOrderList(SakuraPayOrder sakuraPayOrder);

    /**
     * 新增支付订单
     *
     * @param sakuraPayOrder 支付订单
     * @return 结果
     */
    int insertSakuraPayOrder(SakuraPayOrder sakuraPayOrder);

    /**
     * 修改支付订单
     *
     * @param sakuraPayOrder 支付订单
     * @return 结果
     */
    int updateSakuraPayOrder(SakuraPayOrder sakuraPayOrder);

    /**
     * 批量删除支付订单
     *
     * @param ids 需要删除的支付订单主键集合
     * @return 结果
     */
    int deleteSakuraPayOrderByIds(Long[] ids);

    /**
     * 删除支付订单信息
     *
     * @param id 支付订单主键
     * @return 结果
     */
    int deleteSakuraPayOrderById(Long id);

    /**
     * 按状态 + 过期时间下限扫描超时的待支付订单（QB1：Quartz 任务专用）。
     *
     * @param status       订单状态（例如 PayConstants.STATUS_PENDING）
     * @param createdBefore 只取 createTime 小于该时间的订单
     * @param limit        本轮最多返回条数
     * @return 订单列表（可能为空）
     */
    List<SakuraPayOrder> selectExpiredByStatus(String status, java.util.Date createdBefore, int limit);
}
