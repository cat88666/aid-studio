package com.sakura.extensions.hpay;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class HPayClient {
    private final HPayProperties config;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    public HPayClient(HPayProperties config) { this.config = config; }

    public Map<String, Object> create(String orderNo, BigDecimal amount) {
        Map<String, Object> body = request(orderNo, amount);
        body.put("payType", 1068);
        body.put("currencyConvert", 1);
        body.put("notifyUrl", config.getNotifyUrl());
        body.put("sendTime", System.currentTimeMillis());
        Map<String, Object> data = post("/hpay/dt/ct", body);
        HPayProtocol.verify(data, config.getSecret(), HPayProtocol.CREATE_RESPONSE);
        HPayProtocol.match(data, config.getMerchantId(), orderNo, amount);
        return data;
    }

    public Map<String, Object> query(String orderNo, BigDecimal amount) {
        Map<String, Object> data = post("/hpay/dt/vc/query", request(orderNo, amount));
        HPayProtocol.verify(data, config.getSecret(), HPayProtocol.REQUEST);
        HPayProtocol.match(data, config.getMerchantId(), orderNo, amount);
        return data;
    }

    private Map<String, Object> request(String orderNo, BigDecimal amount) {
        if (!config.ready()) throw new IllegalStateException("USDT payments unavailable");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("merchantId", Long.parseLong(config.getMerchantId()));
        body.put("merchantOrderId", HPayProtocol.id(orderNo));
        body.put("payAmount", HPayProtocol.amount(amount));
        body.put("sign", HPayProtocol.sign(body, config.getSecret(), HPayProtocol.REQUEST));
        return body;
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> post(String path, Map<String, Object> body) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(config.getBaseUrl().replaceAll("/$", "") + path))
                    .timeout(Duration.ofSeconds(12)).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(HPayProtocol.json(body))).build();
            HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            {
                byte[] bytes = response.body();
                if (response.statusCode() != 200 || bytes.length > 65536) throw new IllegalStateException();
                Map<String, Object> envelope = HPayProtocol.parse(new String(bytes, StandardCharsets.UTF_8));
                if (!"1".equals(String.valueOf(envelope.get("status"))) || !(envelope.get("data") instanceof Map)) {
                    throw new IllegalStateException();
                }
                return (Map<String, Object>) envelope.get("data");
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Payment provider request interrupted");
        } catch (Exception ignored) {
            // Provider bodies and exception URLs may contain sensitive details.
            throw new IllegalStateException("Payment provider temporarily unavailable");
        }
    }
}
