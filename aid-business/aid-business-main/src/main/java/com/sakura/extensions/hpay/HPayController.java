package com.sakura.extensions.hpay;

import com.sakura.common.annotation.Anonymous;
import com.sakura.common.core.domain.AjaxResult;
import com.sakura.common.sakura.crypto.annotation.CryptoIgnore;
import com.sakura.common.utils.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;

@RestController
public class HPayController {
    private final HPayService service;
    private final HPayProperties config;
    public record Create(@NotNull @Positive Long packageId) { }
    public record Query(@NotBlank @Size(max = 64) String orderNo) { }
    public HPayController(HPayService service, HPayProperties config) { this.service = service; this.config = config; }
    @PostMapping("/api/user/extensions/hpay/catalog")
    public AjaxResult catalog() {
        SecurityUtils.getUserId();
        return AjaxResult.success(Map.of("enabled", config.ready(), "currency", "USDT", "packages", config.ready() ? service.packs() : java.util.List.of()));
    }
    @PostMapping("/api/user/extensions/hpay/create")
    public AjaxResult create(@Valid @RequestBody Create input) { return AjaxResult.success(service.create(SecurityUtils.getUserId(), input.packageId())); }
    @PostMapping("/api/user/extensions/hpay/orders")
    public AjaxResult orders() { return AjaxResult.success(service.list(SecurityUtils.getUserId())); }
    @PostMapping("/api/user/extensions/hpay/query")
    public AjaxResult query(@Valid @RequestBody Query input) { return AjaxResult.success(service.query(SecurityUtils.getUserId(), input.orderNo())); }
    @Anonymous
    @CryptoIgnore
    @PostMapping(value = "/pay/notify/hpay", consumes = "application/json", produces = "text/plain")
    public ResponseEntity<String> notifyPayment(@RequestBody String body) {
        try { service.notifyPayment(body); return ResponseEntity.ok("success"); }
        catch (RuntimeException ignored) { return ResponseEntity.status(503).body("fail"); }
    }
}
