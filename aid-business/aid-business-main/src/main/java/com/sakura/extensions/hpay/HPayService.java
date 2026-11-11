package com.sakura.extensions.hpay;

import com.sakura.billing.service.IAccountUpdateService;
import com.sakura.common.constant.PayConstants;
import com.sakura.common.exception.ServiceException;
import com.sakura.sakura.domain.SakuraRechargePackage;
import com.sakura.sakura.service.ISakuraRechargePackageService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.redisson.api.RedissonClient;
import org.redisson.api.RLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class HPayService {
    private static final Logger LOG = LoggerFactory.getLogger(HPayService.class);
    private final HPayProperties config;
    private final HPayClient client;
    private final HPayOrderStore orders;
    private final ISakuraRechargePackageService packages;
    private final IAccountUpdateService accounts;
    private final RedissonClient redis;
    public record Pack(long id, String name, BigDecimal credits, BigDecimal amount, String currency) { }
    public record View(String orderNo, String productName, BigDecimal credits, BigDecimal amount,
            String currency, String status, String checkoutUrl, String createdAt) { }
    public HPayService(HPayProperties config, HPayClient client, HPayOrderStore orders,
            ISakuraRechargePackageService packages, IAccountUpdateService accounts, RedissonClient redis) {
        this.config = config; this.client = client; this.orders = orders;
        this.packages = packages; this.accounts = accounts; this.redis = redis;
    }
    public List<Pack> packs() {
        requireReady();
        return packages.list(new LambdaQueryWrapper<SakuraRechargePackage>()
                .eq(SakuraRechargePackage::getStatus, "0").eq(SakuraRechargePackage::getDelFlag, "0")
                .orderByAsc(SakuraRechargePackage::getSortOrder)).stream()
                .map(p -> new Pack(p.getId(), p.getPackageName(), p.getCredits(), price(p), "USDT")).toList();
    }
    BigDecimal price(SakuraRechargePackage pack) {
        BigDecimal override = config.getPrices().get(pack.getId());
        if (override != null) return HPayProtocol.amount(override);
        if (pack.getPayPrice() == null || pack.getPayPrice().signum() <= 0) throw new ServiceException("Invalid credit pack price");
        return HPayProtocol.amount(pack.getPayPrice().divide(config.getPricingDivisor(), 2, RoundingMode.CEILING).max(BigDecimal.ONE));
    }
    public View create(long userId, long packageId) {
        requireReady();
        return locked("create:" + userId, () -> {
            HPayOrderStore.Order pending = orders.recentPending(userId);
            if (pending != null) return view(pending);
            SakuraRechargePackage pack = packages.getById(packageId);
            if (pack == null || !"0".equals(pack.getStatus()) || !"0".equals(pack.getDelFlag())
                    || pack.getCredits() == null || pack.getCredits().signum() <= 0) throw new ServiceException("Credit pack unavailable");
            String orderNo = "HPU" + UUID.randomUUID().toString().replace("-", "");
            HPayOrderStore.Order order = new HPayOrderStore.Order(orderNo, userId, packageId,
                    pack.getPackageName(), pack.getCredits(), price(pack), config.getMerchantId(), "pending", null, null, null);
            // Persist before contacting provider; an unknown outcome remains reconcilable.
            orders.insert(order);
            try {
                Map<String, Object> result = client.create(orderNo, order.amount());
                String url = config.checkedCheckoutUrl(result.get("vcPayPageUrl"));
                orders.checkout(orderNo, HPayProtocol.id(result.get("payOrderId")), url);
            } catch (RuntimeException ignored) { LOG.warn("HPay create requires reconciliation: orderNo={}", orderNo); }
            return view(orders.find(orderNo));
        });
    }
    public List<View> list(long userId) { requireReady(); return orders.list(userId).stream().map(this::view).toList(); }
    public View query(long userId, String orderNo) {
        requireReady();
        HPayOrderStore.Order order = owned(userId, orderNo);
        reconcile(order.orderNo(), null);
        return view(orders.find(orderNo));
    }
    public void notifyPayment(String body) {
        requireReady();
        Map<String, Object> notification = HPayProtocol.parse(body);
        HPayProtocol.verify(notification, config.getSecret(), HPayProtocol.NOTIFY);
        String orderNo = HPayProtocol.id(notification.get("merchantOrderId"));
        HPayOrderStore.Order order = orders.find(orderNo);
        if (order == null) throw new ServiceException("Order not found");
        HPayProtocol.match(notification, order.merchantId(), orderNo, order.amount());
        // orderStatus is not signed: callback alone NEVER grants credits.
        reconcile(orderNo, HPayProtocol.id(notification.get("payOrderId")));
    }
    void reconcile(String orderNo, String notifiedTrade) {
        locked("order:" + orderNo, () -> {
            HPayOrderStore.Order order = orders.find(orderNo);
            if (order == null || !Objects.equals(order.merchantId(), config.getMerchantId())) throw new ServiceException("Payment merchant mismatch");
            if (notifiedTrade != null && order.tradeNo() != null && !notifiedTrade.equals(order.tradeNo())) throw new ServiceException("Payment trade mismatch");
            if ("paid".equals(order.status())) return null;
            if (!"pending".equals(order.status())) throw new ServiceException("Payment status unavailable");
            orders.checked(orderNo);
            Map<String, Object> result = client.query(orderNo, order.amount());
            String trade = HPayProtocol.id(result.get("payOrderId"));
            if ((order.tradeNo() != null && !order.tradeNo().equals(trade)) || (notifiedTrade != null && !notifiedTrade.equals(trade))) throw new ServiceException("Payment trade mismatch");
            String status = String.valueOf(result.get("orderStatus"));
            if (!List.of("0", "1", "3", "8").contains(status)) throw new ServiceException("Unknown payment status");
            if (!"3".equals(status)) {
                // Keep reconcilable for late provider transitions; do not discard received money.
                if (notifiedTrade != null) throw new ServiceException("Payment not confirmed");
                return null;
            }
            if (order.amount().compareTo(HPayProtocol.amount(result.get("paidAmount"))) != 0) throw new ServiceException("Actual USDT amount mismatch; manual reconciliation required");
            if (order.credits() == null || order.credits().signum() <= 0) throw new ServiceException("Invalid credit snapshot");
            // Existing account service: independent transaction and traceId idempotency.
            // If status update fails, retry repairs it without crediting twice.
            orders.bindTrade(orderNo, trade); // Unique provider trade is reserved BEFORE any credit.
            accounts.recharge(order.userId(), order.credits(), orderNo, PayConstants.BALANCE_BIZ_TYPE_RECHARGE, "USDT credit purchase");
            orders.paid(orderNo, trade);
            return null;
        });
    }
    @Scheduled(fixedDelayString = "${extensions.hpay.reconcile-delay-ms:60000}")
    public void reconcilePending() {
        if (!config.ready()) return;
        try {
            for (HPayOrderStore.Order order : orders.pending()) {
                try { reconcile(order.orderNo(), null); }
                catch (RuntimeException ignored) { LOG.warn("HPay reconciliation pending: orderNo={}", order.orderNo()); }
            }
        } catch (RuntimeException ignored) { LOG.warn("HPay reconciliation unavailable; check module configuration/schema"); }
    }
    private HPayOrderStore.Order owned(long userId, String orderNo) {
        HPayOrderStore.Order order = orders.find(HPayProtocol.id(orderNo));
        if (order == null || order.userId() != userId) throw new ServiceException("Order not found");
        return order;
    }
    private View view(HPayOrderStore.Order o) {
        return new View(o.orderNo(), o.productName(), o.credits(), o.amount(), "USDT", o.status(), o.checkoutUrl(), o.createdAt());
    }
    private void requireReady() { if (!config.ready()) throw new ServiceException("USDT payments unavailable"); }
    private <T> T locked(String key, Supplier<T> action) {
        RLock lock = redis.getLock("extension:hpay:" + key);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(0, TimeUnit.SECONDS); // watchdog renews during HTTP/account operations
            if (!acquired) throw new ServiceException("Payment processing; retry shortly");
            return action.get();
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt(); throw new ServiceException("Payment interrupted");
        } finally { if (acquired && lock.isHeldByCurrentThread()) lock.unlock(); }
    }
}
