package com.course.platform.service.impl;

import com.course.platform.common.exception.BusinessException;
import com.course.platform.domain.servicecommerce.ServiceAccountFingerprint;
import com.course.platform.domain.servicecommerce.ServiceCommerceTypes.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** YYD goes through the real commerce service and H2 ledger, not a mocked balance. */
class JingyuSchoolCommerceTransactionTest {
    final JingyuCommerceTransactionTest fixture = new JingyuCommerceTransactionTest();
    ServiceCommerceTransactionTest f;

    @BeforeEach
    void setup() throws Exception {
        fixture.setup(); fixture.configureProject("yyd"); fixture.publish(); f = fixture.f;
        fixture.calls.clear();
    }
    @AfterEach void cleanup() { fixture.cleanup(); }

    @Test
    void quoteAndConfirmationFreezeTheScopedSchoolIdentityAndDebitExactlyOnce() throws Exception {
        var quote = fixture.quote();
        assertEquals("0.06", quote.amount()); assertEquals("0.02000000", quote.unitCharge());
        f.money("100"); assertEquals(0, fixture.ledgerRows());
        var snapshot = f.operations.selectById(quote.id());
        var binding = fixture.json.readValue(snapshot.getScheduleJson(), ServiceAccountFingerprint.class);
        assertFalse(binding.matches(fixture.account()));
        assertTrue(binding.matchesScoped("jingyu:yyd:v1", fixture.account(), fixture.fields().get("password"), "示例学院", "91", "示例跑区"));
        for (String value : new String[]{fixture.account(), fixture.fields().get("password"), "示例学院"}) {
            assertFalse(snapshot.getScheduleJson().contains(value)); assertFalse(snapshot.getPayloadEncrypted().contains(value));
        }
        var result = f.service.confirm(quote.id()); assertEquals("SUCCEEDED", result.state());
        assertEquals("SUCCEEDED", f.service.confirm(quote.id()).state());
        f.money("99.94"); assertEquals(1, fixture.writes.get()); assertEquals(1, fixture.ledgerRows());
        var order = f.orders.selectById(result.orderId());
        assertEquals("yyd-451", order.getExternalOrderNo()); assertEquals("17", order.getExternalSubOrderNo());
        assertNull(f.service.order(order.getId()).schedule());
    }

    @Test
    void aGenericAccountOnlyFingerprintCannotReplaceTheSchoolBinding() {
        var prepared = fixture.gateway.prepare(f.provider, f.products.selectById(fixture.productId), fixture.form());
        doReturn(new PreparedOrder(prepared.fields(), 3, new BigDecimal("1.5"), new BigDecimal("1.5"), "脱敏账号", null, null,
                ServiceAccountFingerprint.create(fixture.account()))).when(fixture.gateway).prepare(any(), any(), any());
        assertThrows(BusinessException.class, fixture::quote);
        f.money("100"); assertEquals(0, fixture.ledgerRows()); assertEquals(0, fixture.writes.get());
    }

    @Test
    void schoolSearchRequiresOwnershipOfASellableProductAndDoesNotCreateQuotesOrLedgerRows() {
        var result = f.service.schools(fixture.productId, 1, "示例");
        assertEquals("51", result.items().get(0).id()); assertEquals("示例学院", result.items().get(0).name());
        assertEquals(0, fixture.ledgerRows()); assertEquals(0, fixture.writes.get());
        var product = f.products.selectById(fixture.productId); product.setEnabled(false); f.products.updateById(product);
        clearInvocations(fixture.http);
        assertThrows(BusinessException.class, () -> f.service.schools(fixture.productId, 1, "示例"));
        verifyNoInteractions(fixture.http);
    }

    @Test
    void changedRuleCannotDispatchEvenAfterAQuoteWasAlreadyCreated() {
        var quote = fixture.quote();
        ((ObjectNode) fixture.student.path("run_rule_items").get(0).path("zone")).put("name", "变更后的跑区");
        // Keep the shared UNKNOWN contract; only an explicit audited rejection may return funds.
        var result = f.service.confirm(quote.id());
        assertEquals("UNKNOWN", result.state());
        assertEquals(0, fixture.writes.get()); f.money("99.94"); assertEquals(1, fixture.ledgerRows());
        fixture.admin();
        assertEquals("NOT_ACCEPTED", f.service.resolve(quote.id(), new ResolveForm("NOT_ACCEPTED", null, null,
                "已核实规则变更导致未提交且不存在对应执行订单", true)).state());
        f.money("100"); assertEquals(2, fixture.ledgerRows()); assertEquals(0, fixture.writes.get());
    }

    @Test
    void uncertainCreationKeepsTheReservationAndRequiresTheSameSchoolWhenRecovering() {
        var pending = fixture.unknownCreate();
        assertEquals("UNKNOWN", f.service.confirm(pending.id()).state()); f.money("99.94");
        fixture.admin(); fixture.remote.put("school_name", "另一所学院");
        assertThrows(BusinessException.class, () -> f.service.resolve(pending.id(), fixture.acceptedCreate()));
        assertEquals("UNKNOWN", f.operations.selectById(pending.id()).getState());
        assertEquals(1, fixture.writes.get()); assertEquals(1, fixture.ledgerRows());
        fixture.remote.put("school_name", "示例学院");
        assertEquals("SUCCEEDED", f.service.resolve(pending.id(), fixture.acceptedCreate()).state());
        assertEquals(1, fixture.writes.get()); assertEquals(1, fixture.ledgerRows()); f.money("99.94");
    }

    @Test
    void simultaneousConfirmationStillMakesOnlyOneSchoolOrderAndOneDebit() throws Exception {
        var quote = fixture.quote(); fixture.entered = new CountDownLatch(1); fixture.release = new CountDownLatch(1);
        var first = f.threads.submit(() -> { f.auth(7, "ROLE_USER"); return f.service.confirm(quote.id()); });
        assertTrue(fixture.entered.await(5, TimeUnit.SECONDS));
        assertEquals("DISPATCHING", f.service.confirm(quote.id()).state());
        fixture.release.countDown(); assertEquals("SUCCEEDED", first.get(5, TimeUnit.SECONDS).state());
        assertEquals(1, fixture.writes.get()); assertEquals(1, fixture.ledgerRows()); f.money("99.94");
    }

    @Test
    void schoolOrderRefundRemainsUncreditedUntilAnAuthorizedExactCountIsReconciled() {
        var created = fixture.create();
        var refund = f.service.confirm(fixture.action(created.orderId(), "REFUND").id());
        assertEquals("UNKNOWN", refund.state()); f.money("99.94"); assertEquals(1, fixture.ledgerRows());
        assertThrows(BusinessException.class, () -> f.service.resolve(refund.id(), fixture.acceptedAction(2)));
        fixture.admin();
        assertEquals("SUCCEEDED", f.service.resolve(refund.id(), fixture.acceptedAction(2)).state());
        f.money("99.98"); assertEquals(2, fixture.ledgerRows()); assertEquals(2, fixture.writes.get());
        assertThrows(BusinessException.class, () -> f.service.resolve(refund.id(), fixture.acceptedAction(2)));
        f.money("99.98");
    }

    @Test
    void taskTimeEditsUseTheExistingSchoolOrderAndNeverCreateANewOrderOrCharge() {
        var created = fixture.create();
        var quote = f.service.quoteAction(created.orderId(), new ActionForm("CHANGE_TIME", 0,
                Map.of("taskId", "task-1", "page", "1", "time", fixture.time(4))));
        assertEquals("0.00", quote.amount()); assertEquals("SUCCEEDED", f.service.confirm(quote.id()).state());
        assertEquals(2, fixture.writes.get()); assertEquals(1, fixture.ledgerRows()); f.money("99.94");
    }
}
