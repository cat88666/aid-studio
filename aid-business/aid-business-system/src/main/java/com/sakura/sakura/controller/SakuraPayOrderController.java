package com.sakura.sakura.controller;

import java.util.List;

import com.sakura.sakura.domain.SakuraPayOrder;
import com.sakura.sakura.service.ISakuraPayOrderService;
import com.sakura.pay.dto.RefundOrderRequest;
import com.sakura.pay.service.ISakuraPayOrderBussinessService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.sakura.common.annotation.Log;
import com.sakura.common.core.controller.BaseController;
import com.sakura.common.core.domain.AjaxResult;
import com.sakura.common.enums.BusinessType;
import com.sakura.common.utils.poi.ExcelUtil;
import com.sakura.common.core.page.TableDataInfo;

/**
 * 支付订单Controller
 *
 * @author 视觉AID
 */
@RestController
@RequestMapping("/sakura/payorder")
public class SakuraPayOrderController extends BaseController
{
    @Autowired
    private ISakuraPayOrderService sakuraPayOrderService;

    /** 业务侧支付订单服务（同步订单状态等运营动作走它，不走 admin 的 CRUD 服务） */
    @Autowired
    private ISakuraPayOrderBussinessService sakuraPayOrderBussinessService;


    /**
     * 查询支付订单列表
     */
    @PreAuthorize("@ss.hasPermi('sakura:payorder:list')")
    @GetMapping("/list")
    public TableDataInfo list(SakuraPayOrder sakuraPayOrder)
    {
        startPage();
        List<SakuraPayOrder> list = sakuraPayOrderService.selectSakuraPayOrderList(sakuraPayOrder);
        return getDataTable(list);
    }

    /**
     * 导出支付订单列表
     */
    @PreAuthorize("@ss.hasPermi('sakura:payorder:export')")
    @Log(title = "支付订单", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, SakuraPayOrder sakuraPayOrder)
    {
        List<SakuraPayOrder> list = sakuraPayOrderService.selectSakuraPayOrderList(sakuraPayOrder);
        ExcelUtil<SakuraPayOrder> util = new ExcelUtil<SakuraPayOrder>(SakuraPayOrder.class);
        util.exportExcel(response, list, "支付订单数据");
    }

    /**
     * 获取支付订单详细信息
     */
    @PreAuthorize("@ss.hasPermi('sakura:payorder:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return success(sakuraPayOrderService.selectSakuraPayOrderById(id));
    }

    /**
     * 新增支付订单
     * 资金类 CRUD 已下线，防止伪造订单绕过真实支付造成无流水充值。
     */
    @PreAuthorize("@ss.hasPermi('sakura:payorder:add')")
    @Log(title = "支付订单", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody SakuraPayOrder sakuraPayOrder)
    {
        return AjaxResult.error(403, "为保障资金安全，支付订单禁止手动新增");
    }

    /**
     * 修改支付订单
     * 资金类 CRUD 已下线，防止将 pending 直接切换为 paid。
     */
    @PreAuthorize("@ss.hasPermi('sakura:payorder:edit')")
    @Log(title = "支付订单", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody SakuraPayOrder sakuraPayOrder)
    {
        return AjaxResult.error(403, "为保障资金安全，支付订单禁止手动修改");
    }

    /**
     * 删除支付订单
     * 资金类 CRUD 已下线，订单是对账依据不应允许删除。
     */
    @PreAuthorize("@ss.hasPermi('sakura:payorder:remove')")
    @Log(title = "支付订单", businessType = BusinessType.DELETE)
	@DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return AjaxResult.error(403, "为保障资金安全，支付订单禁止手动删除");
    }

    /**
     * 同步订单状态（运营手动触发，按订单的支付渠道自动选择支付宝/微信支付查询接口）
     * 仅后台运营使用，受 {@code sakura:payorder:sync} 权限点约束。
     *
     * @param orderNo 订单号
     * @return 处理结果
     */
    @PreAuthorize("@ss.hasPermi('sakura:payorder:sync')")
    @Log(title = "支付订单", businessType = BusinessType.UPDATE)
    @PostMapping("/sync/{orderNo}")
    public AjaxResult syncOrderStatus(@PathVariable String orderNo)
    {
        return sakuraPayOrderBussinessService.syncOrderStatus(orderNo);
    }

    /**
     * 订单退款（后台运营操作）
     * 仅"已支付"订单可退，按订单支付渠道自动选择微信/支付宝退款接口全额退款，
     * 并扣回已发放积分。受 {@code sakura:payorder:refund} 权限点约束。
     *
     * @param request 退款请求（orderNo 订单号必填，refundReason 退款原因可选）
     * @return 处理结果
     */
    @PreAuthorize("@ss.hasPermi('sakura:payorder:refund')")
    @Log(title = "支付订单", businessType = BusinessType.UPDATE)
    @PostMapping("/refund")
    public AjaxResult refund(@Valid @RequestBody RefundOrderRequest request)
    {
        return sakuraPayOrderBussinessService.refundOrder(request.getOrderNo(), request.getRefundReason());
    }

}
