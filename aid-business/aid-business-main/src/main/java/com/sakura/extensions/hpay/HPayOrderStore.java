package com.sakura.extensions.hpay;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class HPayOrderStore {
    public record Order(String orderNo, long userId, long packageId, String productName,
            BigDecimal credits, BigDecimal amount, String merchantId, String status,
            String tradeNo, String checkoutUrl, String createdAt) { }
    private final JdbcTemplate jdbc;
    private static final RowMapper<Order> MAPPER = (rs, row) -> new Order(
            rs.getString("order_no"), rs.getLong("user_id"), rs.getLong("package_id"),
            rs.getString("product_name"), rs.getBigDecimal("credits"), rs.getBigDecimal("amount_usdt"),
            rs.getString("merchant_id"), rs.getString("status"), rs.getString("trade_no"),
            rs.getString("checkout_url"), rs.getString("created_at"));
    public HPayOrderStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public void insert(Order o) {
        jdbc.update("INSERT INTO extension_hpay_order (order_no,user_id,package_id,product_name,credits,amount_usdt,merchant_id,status) VALUES (?,?,?,?,?,?,?,'pending')",
                o.orderNo(), o.userId(), o.packageId(), o.productName(), o.credits(), o.amount(), o.merchantId());
    }
    public Order find(String orderNo) {
        List<Order> result = jdbc.query("SELECT * FROM extension_hpay_order WHERE order_no=?", MAPPER, orderNo);
        return result.isEmpty() ? null : result.get(0);
    }
    public List<Order> list(long userId) {
        return jdbc.query("SELECT * FROM extension_hpay_order WHERE user_id=? ORDER BY created_at DESC,order_no DESC LIMIT 20", MAPPER, userId);
    }
    public Order recentPending(long userId) {
        List<Order> result = jdbc.query("SELECT * FROM extension_hpay_order WHERE user_id=? AND status='pending' AND created_at > DATE_SUB(NOW(), INTERVAL 30 MINUTE) ORDER BY created_at DESC LIMIT 1", MAPPER, userId);
        return result.isEmpty() ? null : result.get(0);
    }
    public void checkout(String orderNo, String tradeNo, String url) {
        int n = jdbc.update("UPDATE extension_hpay_order SET trade_no=?,checkout_url=?,updated_at=NOW() WHERE order_no=? AND (trade_no IS NULL OR trade_no=?)", tradeNo, url, orderNo, tradeNo);
        if (n != 1) throw new IllegalStateException("Payment trade mismatch");
    }
    public void bindTrade(String orderNo, String tradeNo) {
        int n = jdbc.update("UPDATE extension_hpay_order SET trade_no=? WHERE order_no=? AND (trade_no IS NULL OR trade_no=?)", tradeNo, orderNo, tradeNo);
        if (n != 1) throw new IllegalStateException("Payment trade mismatch");
    }
    public void paid(String orderNo, String tradeNo) {
        int n = jdbc.update("UPDATE extension_hpay_order SET status='paid',trade_no=?,paid_at=NOW(),updated_at=NOW() WHERE order_no=? AND status='pending' AND (trade_no IS NULL OR trade_no=?)", tradeNo, orderNo, tradeNo);
        if (n != 1) throw new IllegalStateException("Payment status update failed");
    }
    public void checked(String orderNo) { jdbc.update("UPDATE extension_hpay_order SET checked_at=NOW() WHERE order_no=?", orderNo); }
    public List<Order> pending() {
        return jdbc.query("SELECT * FROM extension_hpay_order WHERE status='pending' AND created_at > DATE_SUB(NOW(), INTERVAL 7 DAY) AND (checked_at IS NULL OR checked_at < DATE_SUB(NOW(), INTERVAL 60 SECOND)) ORDER BY COALESCE(checked_at,created_at),order_no LIMIT 20", MAPPER);
    }
}
