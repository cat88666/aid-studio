package com.sakura.extensions.hpay;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HPayClientTest {
    HPayProperties config() {
        HPayProperties p = new HPayProperties(); p.setEnabled(true); p.setBaseUrl("https://api.example");
        p.setMerchantId("123"); p.setSecret("test-secret"); p.setCheckoutHosts(List.of("cashier.example")); return p;
    }
    @Test void createUsesUsdtAndValidatesSignedResponse() {
        HPayProperties p = config();
        HPayClient client = new HPayClient(p) {
            @Override protected Map<String,Object> post(String path,Map<String,Object> body) {
                assertEquals("/hpay/dt/ct",path); assertEquals(1068,body.get("payType")); assertEquals(1,body.get("currencyConvert"));
                assertEquals(new BigDecimal("2.00"),body.get("payAmount"));
                HPayProtocol.verify(body,p.getSecret(),HPayProtocol.REQUEST);
                Map<String,Object> result = new LinkedHashMap<>(body); result.put("payOrderId","trade1");
                result.put("sign",HPayProtocol.sign(result,p.getSecret(),HPayProtocol.CREATE_RESPONSE)); return result;
            }
        };
        assertEquals("trade1",client.create("o1",new BigDecimal("2")).get("payOrderId"));
    }
    @Test void queryRejectsUnsignedResponse() {
        HPayClient client = new HPayClient(config()) {
            @Override protected Map<String,Object> post(String path,Map<String,Object> body) {
                assertEquals("/hpay/dt/vc/query",path); return Map.of("orderStatus",3);
            }
        };
        assertThrows(IllegalArgumentException.class, () -> client.query("o1",BigDecimal.ONE));
    }
    @Test void queryRejectsDifferentOrderEvenWithCorrectSignature() {
        HPayProperties p = config();
        HPayClient client = new HPayClient(p) {
            @Override protected Map<String,Object> post(String path,Map<String,Object> body) {
                Map<String,Object> result = new LinkedHashMap<>(body); result.put("merchantOrderId","other"); result.put("payOrderId","trade1");
                result.put("sign",HPayProtocol.sign(result,p.getSecret(),HPayProtocol.REQUEST)); return result;
            }
        };
        assertThrows(IllegalArgumentException.class, () -> client.query("o1",BigDecimal.ONE));
    }
}
