package com.sakura.extensions.hpay;

import java.math.BigDecimal;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "extensions.hpay")
public class HPayProperties {
    private boolean enabled;
    private String baseUrl = "";
    private String merchantId = "";
    private String secret = "";
    private String notifyUrl = "https://thdrhdf.site/aid/pay/notify/hpay";
    private List<String> checkoutHosts = List.of();
    private BigDecimal pricingDivisor = new BigDecimal("7");
    private Map<Long, BigDecimal> prices = new HashMap<>();

    public boolean ready() {
        return enabled && merchantId.matches("[1-9][0-9]{0,9}") && !secret.isBlank()
                && secureUrl(baseUrl) && secureUrl(notifyUrl) && !checkoutHosts.isEmpty()
                && pricingDivisor != null && pricingDivisor.signum() > 0;
    }

    static boolean secureUrl(String value) {
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                    && uri.getUserInfo() == null && uri.getFragment() == null;
        } catch (RuntimeException ignored) { return false; }
    }

    public String checkedCheckoutUrl(Object value) {
        if (!(value instanceof String url) || !secureUrl(url)
                || checkoutHosts.stream().noneMatch(host -> host.equalsIgnoreCase(URI.create(url).getHost()))) {
            throw new IllegalStateException("Payment checkout URL rejected");
        }
        return url;
    }
}
