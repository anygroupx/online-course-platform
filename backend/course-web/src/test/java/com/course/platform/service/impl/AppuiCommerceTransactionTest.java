package com.course.platform.service.impl;

import com.course.platform.common.exception.BusinessException;
import com.course.platform.domain.exception.ProviderRequestException;
import com.course.platform.domain.servicecommerce.ServiceOrderFilter;
import com.course.platform.domain.servicecommerce.ServiceCommerceTypes.*;
import com.course.platform.infra.external.ApiHttpClient;
import com.course.platform.infra.http.ProviderUrlNormalizer;
import com.course.platform.infra.integration.PluginConnectorRegistry;
import com.course.platform.infra.servicecommerce.AppuiNativeServiceGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Actual service + AppUI adapter + H2/MyBatis ledger; HTTP is a deterministic, non-network fake. */
class AppuiCommerceTransactionTest {
    final ServiceCommerceTransactionTest f = new ServiceCommerceTransactionTest();
    final ObjectMapper json = new ObjectMapper();
    final AtomicInteger writes = new AtomicInteger();
    ApiHttpClient http;
    Long productId;
    ObjectNode remote;
    String cost = "0.10", uncertainAction, forcedRefund;
    CountDownLatch entered, release;
    Map<String,Object> lastWrite;

    @BeforeEach
    void setup() throws Exception {
        f.setup();
        f.provider.setProviderType("appui");
        http = mock(ApiHttpClient.class);
        var gateway = new AppuiNativeServiceGateway(http,new ProviderUrlNormalizer());
        ReflectionTestUtils.setField(f.service,"gateway",gateway);
        ReflectionTestUtils.setField(f.service,"catalogs",new PluginConnectorRegistry(List.of(gateway)));
        remote = json.createObjectNode().put("id",451).put("pid",1).put("user","student-001")
                .put("pass","private-password").put("total_day",10).put("residue_day",10)
                .put("status","进行中").put("address","当前地址").put("shangban_time","07:30").put("xiaban_time","18:10");
        remote.set("week", json.readTree("[1,2,3,4,5]")); remote.set("report",json.readTree("[1]"));
        when(http.postForString(eq(f.provider), anyString(), anyMap())).thenAnswer(inv -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            String url=inv.getArgument(1); Map<String,Object> body=inv.getArgument(2);
            String act=url.substring(url.indexOf("act=")+4).split("&")[0];
            switch (act) {
                case "getCourse": return "{\"code\":1,\"data\":[{\"pid\":1,\"name\":\"校友邦\",\"price\":\""+cost+"\",\"yes_school\":0}]}";
                case "query": return "{\"code\":1,\"userName\":\"已验证姓名\",\"address\":\"校验地址\"}";
                case "orders": return json.createObjectNode().put("code",1).set("data",json.createArrayNode().add(remote)).toString();
                case "add", "edit", "renew", "refund":
                    writes.incrementAndGet(); lastWrite = new LinkedHashMap<>(body);
                    if (entered != null) { entered.countDown(); assertTrue(release.await(5,TimeUnit.SECONDS)); }
                    if (act.equals(uncertainAction)) throw new ProviderRequestException(ProviderRequestException.Reason.TIMEOUT);
                    if ("add".equals(act)) return "{\"code\":1,\"oid\":451}";
                    if ("renew".equals(act)) {
                        int days=((Number)body.get("days1")).intValue();
                        remote.put("total_day",remote.path("total_day").asInt()+days);
                        remote.put("residue_day",remote.path("residue_day").asInt()+days);
                    }
                    if ("edit".equals(act)) remote.put("pass",body.get("form[pass]").toString());
                    if ("refund".equals(act)) {
                        int remaining=remote.path("residue_day").asInt(); remote.put("status","已退款").put("residue_day",0);
                        return forcedRefund != null ? forcedRefund : "{\"code\":1,\"days2\":"+remaining+"}";
                    }
                    return "{\"code\":1}";
                default: throw new AssertionError("Unexpected action "+act);
            }
        });
        f.auth(7,"api-provider:update");
        productId=f.service.saveProduct(null,new ProductCommand(9L,"1","1","实习天数","",new BigDecimal("0.25"),true,null)).id();
        f.auth(7,"ROLE_USER");
    }

    @AfterEach
    void cleanup() { if (release != null) release.countDown(); f.cleanup(); }

    Map<String,String> fields() { return Map.of("account","student-001","password","private-password",
            "address","已确认地址","startTime","07:30","endTime","18:10","weekdays","1,2,3,4,5","reports","1"); }
    QuoteView quote() { return f.service.quote(productId,new OrderForm(10,null,fields(),List.of(),true)); }
    QuoteView create() { return f.service.confirm(quote().id()); }
    int ledgerRows() { return f.jdbc.queryForObject("SELECT COUNT(*) FROM account_ledger",Integer.class); }

    @Test
    void creationUsesExactDayPriceAndOnlyExplicitConfirmationDebits() {
        var q=quote(); f.money("100"); assertEquals(0,writes.get());
        assertEquals("天",q.quantityUnit()); assertEquals("2.50",q.amount()); assertEquals("0.25000000",q.unitCharge());
        assertNull(q.distancePlan()); assertEquals("元/天", f.service.products(1,20,false,"appui").getRecords().get(0).priceUnit());
        var op=f.operations.selectById(q.id());
        assertFalse(op.getPayloadEncrypted().contains("private-password"));
        assertFalse(op.getScheduleJson().contains("student-001"));
        assertFalse(op.getScheduleJson().contains("private-password"));
        var done=f.service.confirm(q.id()); assertEquals("SUCCEEDED",done.state()); f.money("97.50"); assertEquals(1,writes.get());
        assertEquals("已验证姓名", lastWrite.get("form[userName]"));
        assertNull(f.operations.selectById(q.id()).getPayloadEncrypted());
        var order=f.service.orders(1,20,false,new ServiceOrderFilter(null,"appui",null,null,null,null,null)).getRecords().get(0);
        assertEquals("天",order.quantityUnit()); assertNull(order.distance()); assertNull(order.schedule());
        assertEquals(10,order.quantity());
        assertEquals("SUCCEEDED",f.service.confirm(q.id()).state()); assertEquals(1,ledgerRows()); assertEquals(1,writes.get());
    }

    @Test
    void concurrentConfirmDispatchesAndDebitsExactlyOnce() throws Exception {
        var q=quote(); entered=new CountDownLatch(1); release=new CountDownLatch(1);
        var first=f.threads.submit(() -> { f.auth(7,"ROLE_USER"); return f.service.confirm(q.id()); });
        assertTrue(entered.await(5,TimeUnit.SECONDS));
        var second=f.service.confirm(q.id()); assertEquals("DISPATCHING",second.state());
        release.countDown(); assertEquals("SUCCEEDED",first.get(5,TimeUnit.SECONDS).state());
        f.money("97.50"); assertEquals(1,writes.get()); assertEquals(1,ledgerRows());
    }

    @Test
    void lostCreateResponseKeepsReservationAndNeverReplaysOrCredits() {
        uncertainAction="add"; var q=quote();
        assertEquals("UNKNOWN",f.service.confirm(q.id()).state()); f.money("97.50");
        assertEquals("UNKNOWN",f.service.confirm(q.id()).state()); assertEquals(1,writes.get()); assertEquals(1,ledgerRows());
        assertNotNull(f.operations.selectById(q.id()).getPayloadEncrypted());
    }

    @Test
    void renewKeepsFrozenPriceAndEditHasNoDistanceOrExtraCharge() {
        var created=create();
        f.auth(7,"api-provider:update");
        f.service.saveProduct(productId,new ProductCommand(9L,"1","1","实习天数","",new BigDecimal("0.50"),true,0L));
        f.auth(7,"ROLE_USER");
        var renew=f.service.quoteAction(created.orderId(),new ActionForm("ADD_TIMES",4));
        assertEquals("1.00",renew.amount()); assertEquals("0.25000000",renew.unitCharge()); assertEquals("天",renew.quantityUnit());
        assertEquals("SUCCEEDED",f.service.confirm(renew.id()).state()); f.money("96.50");
        assertEquals(14,f.orders.selectById(created.orderId()).getQuantity());
        var edit=new LinkedHashMap<>(fields()); edit.remove("account"); edit.put("password","");
        var plan=f.service.quoteAction(created.orderId(),new ActionForm("EDIT_PLAN",0,edit));
        assertEquals("0.00",plan.amount()); assertNull(f.operations.selectById(plan.id()).getDistance());
        assertEquals("SUCCEEDED",f.service.confirm(plan.id()).state());
        assertEquals("private-password",lastWrite.get("form[pass]")); assertEquals(2,ledgerRows()); f.money("96.50");
        cost="0.30";
        assertThrows(BusinessException.class, () -> f.service.quoteAction(created.orderId(),new ActionForm("ADD_TIMES",1)));
        assertEquals(3,writes.get());
    }

    @Test
    void refundCreditsOnlyExplicitRemainingDaysAtFrozenPrice() {
        var created=create(); remote.put("residue_day",6);
        var preview=f.service.quoteAction(created.orderId(),new ActionForm("REFUND",0));
        assertEquals(6,preview.quantity()); assertEquals("1.50",preview.amount());
        remote.put("residue_day",5);
        var done=f.service.confirm(preview.id()); assertEquals("SUCCEEDED",done.state());
        assertEquals("1.25",done.amount()); assertEquals(5,done.quantity()); f.money("98.75");
        assertEquals("REFUNDED",f.orders.selectById(created.orderId()).getStatus());
        assertEquals(2,ledgerRows()); f.service.confirm(preview.id()); assertEquals(2,writes.get());
    }

    @Test
    void missingRefundReceiptOrExcessOverPreviewStaysUnknownWithoutCredit() {
        var created=create(); remote.put("residue_day",6);
        var q=f.service.quoteAction(created.orderId(),new ActionForm("REFUND",0));
        forcedRefund="{\"code\":1}";
        assertEquals("UNKNOWN",f.service.confirm(q.id()).state()); f.money("97.50"); assertEquals(1,ledgerRows());
        assertEquals("UNKNOWN",f.service.confirm(q.id()).state()); assertEquals(2,writes.get());
    }

    @Test
    void refundMoreThanPreviewIsNotSettledEvenIfUnderOriginalPurchasedQuota() {
        var created=create(); remote.put("residue_day",6);
        var q=f.service.quoteAction(created.orderId(),new ActionForm("REFUND",0));
        forcedRefund="{\"code\":1,\"days2\":7}";
        assertEquals("UNKNOWN",f.service.confirm(q.id()).state()); f.money("97.50"); assertEquals(1,ledgerRows());
    }

    @Test
    void refundAboveFreshRemainingQuotaIsUnknownEvenWhenWithinPreview() {
        var created=create(); remote.put("residue_day",6);
        var q=f.service.quoteAction(created.orderId(),new ActionForm("REFUND",0));
        remote.put("residue_day",4); forcedRefund="{\"code\":1,\"days2\":5}";
        assertEquals("UNKNOWN",f.service.confirm(q.id()).state());
        f.money("97.50"); assertEquals(1,ledgerRows());
        assertEquals("UNKNOWN",f.service.confirm(q.id()).state()); assertEquals(2,writes.get());
    }

    @Test
    void refundedStatusSyncPreservesConsumedCountAndDoesNotCreateCredit() {
        var created=create(); remote.put("residue_day",7);
        var first=f.service.sync(created.orderId()); assertEquals(3,first.completed());
        remote.put("status","已退款").put("residue_day",0);
        // Advance only test maintenance metadata past the manual-refresh cooldown.
        f.jdbc.update("UPDATE service_order SET status_check_after=NULL WHERE id=?",created.orderId());
        var state=f.service.sync(created.orderId());
        assertEquals("REFUND_REVIEW",state.status()); assertEquals(3,state.completed()); assertTrue(state.actions().isEmpty());
        f.money("97.50"); assertEquals(1,ledgerRows()); assertEquals(1,writes.get());
    }

    @Test
    void automaticStatusRefreshReadsAppuiOrdersWithoutWritingOrSettlingMoney() {
        var created=create(); remote.put("residue_day",7);
        ReflectionTestUtils.setField(f.service,"statusRefreshEnabled",true);
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        clearInvocations(http);
        assertEquals(1,f.service.refreshDueStatuses());
        assertEquals(3,f.orders.selectById(created.orderId()).getCompleted());
        assertNotNull(f.orders.selectById(created.orderId()).getStatusCheckedAt());
        verify(http,times(1)).postForString(eq(f.provider),endsWith("act=orders"),anyMap());
        verifyNoMoreInteractions(http);
        assertEquals(0,f.service.refreshDueStatuses());
        f.money("97.50"); assertEquals(1,ledgerRows()); assertEquals(1,writes.get());
    }

    @Test
    void ownershipAndConfigurationChangesBlockBeforeAnyWrite() {
        var q=quote();
        f.auth(8,"ROLE_USER");
        assertThrows(BusinessException.class,() -> f.service.confirm(q.id()));
        assertEquals(0,writes.get());
        f.auth(7,"ROLE_USER"); f.provider.setConfigVersion(3L);
        assertThrows(BusinessException.class,() -> f.service.confirm(q.id())); f.money("100");
        f.provider.setConfigVersion(2L); var created=f.service.confirm(q.id());
        f.auth(8,"ROLE_USER"); clearInvocations(http);
        assertThrows(BusinessException.class,() -> f.service.orderOptions(created.orderId()));
        assertThrows(BusinessException.class,() -> f.service.quoteAction(created.orderId(),new ActionForm("REFUND",0)));
        verifyNoInteractions(http);
    }

    @Test
    void exhaustedOrdersMayRenewOrRefundWithoutPretendingAttendanceWasCompleted() {
        var created=create(); remote.put("status","已完成").put("residue_day",2);
        var state=f.service.sync(created.orderId()); assertEquals("COMPLETED",state.status());
        assertTrue(state.actions().contains("ADD_TIMES")); assertTrue(state.actions().contains("REFUND"));
        var q=f.service.quoteAction(created.orderId(),new ActionForm("REFUND",0)); assertEquals("0.50",q.amount());
        assertEquals("SUCCEEDED",f.service.confirm(q.id()).state()); f.money("98.00");
    }
}
