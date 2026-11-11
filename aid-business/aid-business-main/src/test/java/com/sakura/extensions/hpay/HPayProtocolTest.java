package com.sakura.extensions.hpay;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HPayProtocolTest {
    @Test void preservesDecimalTextAndSignsInDocumentOrder() {
        Map<String, Object> data = HPayProtocol.parse("{\"payAmount\":100.00,\"merchantOrderId\":\"order1\",\"merchantId\":123}");
        assertEquals("100.00", data.get("payAmount").toString());
        assertEquals("a234b3ba6667cdd98f34511b36b16dfb", HPayProtocol.sign(data, "test-secret", HPayProtocol.REQUEST));
        assertTrue(HPayProtocol.json(data).contains("100.00"));
    }
    @Test void rejectsTamperingAndMalformedSignature() {
        Map<String,Object> data = new LinkedHashMap<>(Map.of("merchantId",123,"merchantOrderId","o1","payAmount",new BigDecimal("2.00")));
        data.put("sign", HPayProtocol.sign(data, "test", HPayProtocol.REQUEST));
        HPayProtocol.verify(data, "test", HPayProtocol.REQUEST);
        data.put("payAmount", new BigDecimal("3.00"));
        assertThrows(IllegalArgumentException.class, () -> HPayProtocol.verify(data,"test",HPayProtocol.REQUEST));
        data.put("sign", "bad");
        assertThrows(IllegalArgumentException.class, () -> HPayProtocol.verify(data,"test",HPayProtocol.REQUEST));
    }
    @Test void rejectsDuplicateJsonKeysAndInvalidAmounts() {
        assertThrows(IllegalArgumentException.class, () -> HPayProtocol.parse("{\"merchantId\":1,\"merchantId\":2}"));
        for (String amount : List.of("0", "-1", "0.001", "NaN", "10000000000")) {
            assertThrows(IllegalArgumentException.class, () -> HPayProtocol.amount(amount));
        }
    }
    @Test void checksMerchantOrderAndExactAmount() {
        Map<String,Object> data = Map.of("merchantId",123,"merchantOrderId","o1","payAmount",10000,"payOrderId","t1");
        assertThrows(IllegalArgumentException.class, () -> HPayProtocol.match(data,"123","o1",new BigDecimal("100.00")));
        assertThrows(IllegalArgumentException.class, () -> HPayProtocol.match(data,"124","o1",new BigDecimal("10000")));
        assertThrows(IllegalArgumentException.class, () -> HPayProtocol.match(data,"123","o2",new BigDecimal("10000")));
    }
    @Test void checkoutRequiresHttpsAndExactAllowedHost() {
        HPayProperties p = new HPayProperties(); p.setCheckoutHosts(List.of("cashier.example"));
        assertEquals("https://cashier.example/pay/1",p.checkedCheckoutUrl("https://cashier.example/pay/1"));
        for (String value : List.of("javascript:alert(1)", "http://cashier.example", "https://cashier.example.evil.test", "https://user@cashier.example")) {
            assertThrows(IllegalStateException.class, () -> p.checkedCheckoutUrl(value));
        }
        assertFalse(p.ready());
    }
}
