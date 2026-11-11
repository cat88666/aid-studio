package com.sakura.extensions.hpay;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.sakura.billing.service.IAccountUpdateService;
import com.sakura.sakura.domain.SakuraRechargePackage;
import com.sakura.sakura.service.ISakuraRechargePackageService;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.redisson.api.RLock;

class HPayServiceTest {
    HPayProperties config;
    HPayClient client;
    HPayOrderStore store;
    IAccountUpdateService accounts;
    ISakuraRechargePackageService packages;
    HPayService service;
    RLock lock;
    final BigDecimal amount = new BigDecimal("2.00");
    @BeforeEach void setup() throws Exception {
        config = new HPayProperties(); config.setEnabled(true); config.setBaseUrl("https://api.example");
        config.setMerchantId("123"); config.setSecret("test-secret"); config.setCheckoutHosts(List.of("cashier.example"));
        client = mock(HPayClient.class); store = mock(HPayOrderStore.class);
        accounts = mock(IAccountUpdateService.class); packages = mock(ISakuraRechargePackageService.class);
        RedissonClient redis = mock(RedissonClient.class); lock = mock(RLock.class);
        when(redis.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(0, TimeUnit.SECONDS)).thenReturn(true); when(lock.isHeldByCurrentThread()).thenReturn(true);
        service = new HPayService(config,client,store,packages,accounts,redis);
        when(store.find("HPU1")).thenReturn(order("pending"));
        when(client.query("HPU1",amount)).thenReturn(result("3","2.00","trade1"));
    }
    HPayOrderStore.Order order(String status) { return new HPayOrderStore.Order("HPU1",7,1,"pack",new BigDecimal("100"),amount,"123",status,"trade1",null,"now"); }
    Map<String,Object> result(String status,String paid,String trade) {
        return new LinkedHashMap<>(Map.of("merchantId",123,"merchantOrderId","HPU1","payAmount",amount,"paidAmount",new BigDecimal(paid),"orderStatus",status,"payOrderId",trade));
    }
    String notification(String status) {
        Map<String,Object> data = result(status,"2.00","trade1");
        data.put("sign",HPayProtocol.sign(data,config.getSecret(),HPayProtocol.NOTIFY)); return HPayProtocol.json(data);
    }
    @Test void creditsOnlyAfterQueryAndTradeReservation() {
        service.notifyPayment(notification("3"));
        var ordered = inOrder(client,store,accounts);
        ordered.verify(client).query("HPU1",amount);
        ordered.verify(store).bindTrade("HPU1","trade1");
        ordered.verify(accounts).recharge(eq(7L),eq(new BigDecimal("100")),eq("HPU1"),anyString(),anyString());
        ordered.verify(store).paid("HPU1","trade1");
    }
    @Test void forgedUnsignedSuccessNeverCredits() {
        when(client.query("HPU1",amount)).thenReturn(result("0","2.00","trade1"));
        assertThrows(RuntimeException.class, () -> service.notifyPayment(notification("3")));
        verifyNoInteractions(accounts);
    }
    @Test void queryFailureNeverAcknowledgesNotification() {
        when(client.query("HPU1",amount)).thenThrow(new IllegalStateException());
        assertThrows(RuntimeException.class, () -> service.notifyPayment(notification("3")));
        verifyNoInteractions(accounts); verify(store,never()).paid(anyString(),anyString());
    }
    @Test void underpaymentAndTradeMismatchNeverCredit() {
        when(client.query("HPU1",amount)).thenReturn(result("3","1.99","trade1"));
        assertThrows(RuntimeException.class, () -> service.reconcile("HPU1",null));
        when(client.query("HPU1",amount)).thenReturn(result("3","2.00","other"));
        assertThrows(RuntimeException.class, () -> service.reconcile("HPU1",null)); verifyNoInteractions(accounts);
    }
    @Test void alreadyPaidNotificationDoesNotCreditAgain() {
        when(store.find("HPU1")).thenReturn(order("paid")); service.notifyPayment(notification("3"));
        verifyNoInteractions(accounts,client);
    }
    @Test void ownershipCheckedBeforeProvider() {
        assertThrows(RuntimeException.class, () -> service.query(8,"HPU1")); verifyNoInteractions(accounts,client);
    }
    @Test void duplicateTradeRejectedBeforeAccountMutation() {
        doThrow(new IllegalStateException()).when(store).bindTrade(anyString(),anyString());
        assertThrows(RuntimeException.class, () -> service.reconcile("HPU1",null)); verifyNoInteractions(accounts);
    }
    @Test void failedCreditLeavesOrderPending() {
        doThrow(new IllegalStateException()).when(accounts).recharge(anyLong(),any(),anyString(),anyString(),anyString());
        assertThrows(RuntimeException.class, () -> service.reconcile("HPU1",null)); verify(store,never()).paid(anyString(),anyString());
    }
    @Test void retryAfterStatusFailureUsesSameIdempotencyKey() {
        doThrow(new IllegalStateException()).doNothing().when(store).paid("HPU1","trade1");
        assertThrows(RuntimeException.class, () -> service.reconcile("HPU1",null)); service.reconcile("HPU1",null);
        verify(accounts,times(2)).recharge(eq(7L),eq(new BigDecimal("100")),eq("HPU1"),anyString(),anyString());
    }
    @Test void defaultPricingAndExplicitOverride() {
        SakuraRechargePackage pack = new SakuraRechargePackage(); pack.setId(1L); pack.setPayPrice(new BigDecimal("10"));
        assertEquals(new BigDecimal("1.43"),service.price(pack));
        pack.setPayPrice(new BigDecimal("0.50")); assertEquals(new BigDecimal("1.00"),service.price(pack));
        config.setPrices(Map.of(1L,new BigDecimal("5.00"))); assertEquals(new BigDecimal("5.00"),service.price(pack));
    }
    @Test void duplicateCreateReturnsPendingOrderWithoutNewProviderOrder() {
        when(store.recentPending(7)).thenReturn(order("pending")); assertEquals("HPU1",service.create(7,1).orderNo());
        verifyNoInteractions(client,accounts); verify(store,never()).insert(any());
    }
    @Test void disabledModuleDoesNotTouchDatabaseOrProvider() {
        config.setEnabled(false); service.reconcilePending();
        assertThrows(RuntimeException.class, () -> service.create(7,1)); verifyNoInteractions(store,client,accounts);
    }
}
