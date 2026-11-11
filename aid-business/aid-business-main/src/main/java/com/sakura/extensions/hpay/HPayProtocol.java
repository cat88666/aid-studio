package com.sakura.extensions.hpay;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.StringJoiner;

/** Field order follows the supplied HPay PDFs; values are not URL encoded. */
public final class HPayProtocol {
    static final String[] REQUEST = {"merchantId", "merchantOrderId", "payAmount"};
    static final String[] CREATE_RESPONSE = {"merchantId", "merchantOrderId", "payOrderId", "payAmount"};
    static final String[] NOTIFY = {"merchantId", "merchantOrderId", "payOrderId", "payAmount", "paidAmount"};
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION);

    private HPayProtocol() { }

    static Map<String, Object> parse(String json) {
        try {
            if (json == null || json.length() > 65536) throw new IllegalArgumentException();
            return JSON.readValue(json, new TypeReference<Map<String, Object>>() { });
        } catch (Exception ignored) { throw new IllegalArgumentException("Invalid payment response"); }
    }

    static String json(Map<String, Object> value) {
        try { return JSON.writeValueAsString(value); }
        catch (Exception ignored) { throw new IllegalArgumentException("Invalid payment request"); }
    }

    static String sign(Map<String, Object> values, String secret, String... fields) {
        StringJoiner content = new StringJoiner("&");
        for (String field : fields) {
            Object value = values.get(field);
            if (value != null && !value.toString().isEmpty()) content.add(field + "=" + value);
        }
        content.add("key=" + secret);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("MD5")
                    .digest(content.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ignored) { throw new IllegalStateException("MD5 unavailable"); }
    }

    static void verify(Map<String, Object> values, String secret, String... fields) {
        String received = String.valueOf(values.get("sign"));
        String expected = sign(values, secret, fields);
        if (!received.matches("[0-9a-f]{32}") || !MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII), received.getBytes(StandardCharsets.US_ASCII))) {
            throw new IllegalArgumentException("Invalid payment signature");
        }
    }

    static BigDecimal amount(Object value) {
        try {
            BigDecimal amount = new BigDecimal(String.valueOf(value)).setScale(2, RoundingMode.UNNECESSARY);
            if (amount.signum() <= 0 || amount.precision() > 12) throw new IllegalArgumentException();
            return amount;
        } catch (RuntimeException ignored) { throw new IllegalArgumentException("Invalid payment amount"); }
    }

    static String id(Object value) {
        if (value == null || !value.toString().matches("[A-Za-z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("Invalid payment identifier");
        }
        return value.toString();
    }

    static void match(Map<String, Object> data, String merchant, String order, BigDecimal amount) {
        if (!merchant.equals(String.valueOf(data.get("merchantId")))
                || !order.equals(data.get("merchantOrderId"))
                || amount.compareTo(amount(data.get("payAmount"))) != 0) {
            throw new IllegalArgumentException("Payment order mismatch");
        }
        id(data.get("payOrderId"));
    }
}
