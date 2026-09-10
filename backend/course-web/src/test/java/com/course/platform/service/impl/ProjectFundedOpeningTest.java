package com.course.platform.service.impl;

import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.security.SecretCrypto;
import com.course.platform.domain.projectcenter.ProjectCenterTypes.*;
import com.course.platform.domain.projectcenter.ServiceProjectAccount;
import com.course.platform.domain.servicecommerce.ServiceTime;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProjectFundedOpeningTest extends ProjectCenterTestSupport {
    static final String EVIDENCE = "已核实此操作的开户回执、初始额度、实际扣款及本人客户归属";

    void fundedGateway(String balanceBeforeSettlement) {
        doAnswer(a -> {
            noTransaction();
            assertEquals("DISPATCHING", jdbc.queryForObject("SELECT state FROM service_project_operation WHERE state='DISPATCHING'", String.class));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM account_ledger WHERE direction=-1", Integer.class));
            upstream = new BigDecimal(balanceBeforeSettlement);
            return receipt("11");
        }).when(gateway).provision(any(), anyString(), any(BigDecimal.class));
    }

    @Test void fundedQuoteDoesNotCreateAccountDebitOrCallSupplier() {
        var q = quote("PROVISION", "8");
        assertEquals("8", q.units()); assertEquals("2.00", q.amount()); assertEquals("READY", q.state());
        assertTrue(q.warnings().stream().anyMatch(w -> w.contains("同时开通账户并充值 8")));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM service_project_account", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM account_ledger", Integer.class));
        money("100"); verifyNoInteractions(gateway);
    }

    @Test void fundedOpeningReservesOnceThenBindsReceiptAndCreatesOnlyPaidRefundBudget() {
        fundedGateway("8"); var q = quote("PROVISION", "8");
        var done = service.confirm(q.id()); assertEquals("SUCCEEDED", done.state());
        assertEquals("SUCCEEDED", service.confirm(q.id()).state()); money("98");
        var a = accounts.selectById(q.accountId());
        assertEquals(0, new BigDecimal("8").compareTo(a.getRefundableUnits()));
        assertEquals(0, new BigDecimal("2").compareTo(a.getRefundBudget()));
        assertEquals("private-customer-key", SecretCrypto.decrypt(a.getCustomerKeyEncrypted(), "test-project-master-key"));
        assertNull(a.getPendingOperationId()); assertEquals("ACTIVE", a.getState());
        verify(gateway, times(1)).provision(any(), eq("5"), argThat(v -> v != null && v.compareTo(new BigDecimal("8")) == 0));
        verify(gateway, never()).provision(any(), anyString());
        verify(gateway, never()).adjust(any(), anyString(), anyString(), any(), anyString());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM account_ledger", Integer.class));
    }

    @Test void initialFundingCanBeWithdrawnAtFrozenRateButGiftCannotBePaidOut() {
        fundedGateway("8"); var done = service.confirm(quote("PROVISION", "8").id());
        assertEquals("SUCCEEDED", done.state()); money("98");
        upstream = new BigDecimal("13"); // Five gift units are not five newly paid units.
        assertThrows(BusinessException.class, () -> quote("WITHDRAW", "9"));
        var withdrawal = service.confirm(quote("WITHDRAW", "3").id());
        assertEquals("SUCCEEDED", withdrawal.state()); money("98.75");
        var a = accounts.selectById(done.accountId());
        assertEquals(0, new BigDecimal("5").compareTo(a.getRefundableUnits()));
        assertEquals(0, new BigDecimal("1.25").compareTo(a.getRefundBudget()));
        assertEquals(BigDecimal.ZERO, jdbc.queryForObject("SELECT total_recharge FROM sys_user WHERE id=7", BigDecimal.class).stripTrailingZeros());
    }

    @Test void fundedOpeningRoundsUpAtACentAndWithdrawalRoundsDown() {
        fundedGateway("0.05"); var q = quote("PROVISION", "0.05");
        assertEquals("0.02", q.amount()); assertEquals("SUCCEEDED", service.confirm(q.id()).state()); money("99.98");
        var back = quote("WITHDRAW", "0.05"); assertEquals("0.01", back.amount());
        assertEquals("SUCCEEDED", service.confirm(back.id()).state()); money("99.99");
    }

    @ParameterizedTest @ValueSource(strings = {"-1", "0.0000001", "100001", "1e-1000000"})
    void invalidInitialCreditIsRejectedBeforeDraftOrSupplier(String units) {
        assertThrows(BusinessException.class, () -> quote("PROVISION", units)); money("100");
        verifyNoInteractions(gateway);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM service_project_operation", Integer.class));
    }

    @Test void zeroAndAbsentInitialUnitsRetainFreeOpening() {
        for (String initial : Arrays.asList(null, "0.000000")) {
            var q = quote("PROVISION", initial);
            assertEquals("0.00", q.amount()); assertEquals("0", q.units());
        }
        var q = quote("PROVISION", "0"); assertEquals("SUCCEEDED", service.confirm(q.id()).state());
        money("100"); assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM account_ledger", Integer.class));
        verify(gateway, never()).provision(any(), anyString(), any());
    }

    @Test void insufficientWalletRollsBackAccountAndDispatchSoNoRemoteAccountIsCreated() {
        var q = quote("PROVISION", "1000");
        assertThrows(BusinessException.class, () -> service.confirm(q.id())); money("100");
        assertEquals("READY", operations.selectById(q.id()).getState());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM service_project_account", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM account_ledger", Integer.class));
        verifyNoInteractions(gateway);
    }

    @Test void otherUserAndChangedContractCannotConfirmFundedOpening() {
        var q = quote("PROVISION", "8"); auth(8, "ROLE_USER");
        assertThrows(BusinessException.class, () -> service.confirm(q.id())); auth(7, "ROLE_USER");
        provider.setConfigVersion(provider.getConfigVersion() + 1);
        assertThrows(BusinessException.class, () -> service.confirm(q.id())); money("100"); verifyNoInteractions(gateway);
    }

    @Test void expiredFundedPreviewDoesNotReserveMoney() {
        var q = quote("PROVISION", "8");
        jdbc.update("UPDATE service_project_operation SET expires_at=? WHERE id=?", ServiceTime.now().minusSeconds(1), q.id());
        assertEquals("EXPIRED", service.confirm(q.id()).state()); money("100"); verifyNoInteractions(gateway);
    }

    @Test void mismatchedInitialBalanceDoesNotBindCustomerOrReleaseDebit() {
        fundedGateway("7"); var q = quote("PROVISION", "8");
        assertEquals("UNKNOWN", service.confirm(q.id()).state()); money("98");
        var a = accounts.selectById(q.accountId()); assertNull(a.getRemoteCustomerId());
        assertEquals(0, a.getRefundableUnits().signum()); assertEquals(0, a.getRefundBudget().signum());
        assertEquals("UNKNOWN", service.confirm(q.id()).state());
        verify(gateway, times(1)).provision(any(), anyString(), any());
    }

    @Test void lostCreationResponseStaysUnknownAndCannotTriggerAnotherOpening() {
        doThrow(new IllegalStateException("simulated response loss")).when(gateway).provision(any(), anyString(), any());
        var q = quote("PROVISION", "8");
        assertEquals("UNKNOWN", service.confirm(q.id()).state()); money("98");
        assertEquals("UNKNOWN", service.operation(q.id(), false).state());
        assertEquals("UNKNOWN", service.confirm(q.id()).state());
        assertThrows(BusinessException.class, () -> quote("PROVISION", "8"));
        assertThrows(BusinessException.class, () -> quote("TOP_UP", "8"));
        verify(gateway, times(1)).provision(any(), anyString(), any());
    }

    @Test void localSettlementFailureKeepsPreDebitAndDoesNotReplayRemoteCall() {
        var failing = spy(accounts);
        doAnswer(a -> { if ("ACTIVE".equals(((ServiceProjectAccount) a.getArgument(0)).getState())) throw new IllegalStateException("commit failure"); return a.callRealMethod(); })
                .when(failing).updateById(any(ServiceProjectAccount.class));
        ReflectionTestUtils.setField(service, "accounts", failing);
        fundedGateway("8"); var q = quote("PROVISION", "8");
        assertEquals("UNKNOWN", service.confirm(q.id()).state()); money("98");
        assertNull(accounts.selectById(q.accountId()).getRemoteCustomerId());
        assertEquals(0, accounts.selectById(q.accountId()).getRefundBudget().signum());
        verify(gateway, times(1)).provision(any(), anyString(), any());
    }

    @Test void verifiedNotAcceptedReturnsOnlyTheOriginalInitialDebitExactlyOnce() {
        doThrow(new IllegalStateException()).when(gateway).provision(any(), anyString(), any());
        var q = quote("PROVISION", "8"); service.confirm(q.id()); money("98");
        var proof = new ResolveForm("NOT_ACCEPTED", null, EVIDENCE, true);
        auth(7, "api-provider:update"); assertThrows(BusinessException.class, () -> service.resolve(q.id(), proof));
        auth(7, "api-provider:update", "payment:reconcile");
        assertEquals("NOT_ACCEPTED", service.resolve(q.id(), proof).state());
        assertEquals("NOT_ACCEPTED", service.resolve(q.id(), proof).state()); money("100");
        assertEquals("NEW", accounts.selectById(q.accountId()).getState());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM account_ledger", Integer.class));
        verify(gateway, times(1)).provision(any(), anyString(), any());
    }

    @Test void verifiedAcceptanceUsesFrozenPaidBudgetNotCurrentBalanceToInferFunding() {
        doThrow(new IllegalStateException()).when(gateway).provision(any(), anyString(), any());
        var q = quote("PROVISION", "8"); service.confirm(q.id());
        upstream = new BigDecimal("5"); // Authoritative receipt is now lower after actual usage.
        auth(7, "api-provider:update", "payment:reconcile");
        assertThrows(BusinessException.class, () -> service.resolve(q.id(), new ResolveForm("ACCEPTED", "11", EVIDENCE, false)));
        assertEquals("SUCCEEDED", service.resolve(q.id(), new ResolveForm("ACCEPTED", "11", EVIDENCE, true)).state());
        money("98"); var a = accounts.selectById(q.accountId());
        assertEquals(0, new BigDecimal("8").compareTo(a.getRefundableUnits()));
        assertEquals(0, new BigDecimal("2").compareTo(a.getRefundBudget()));
        assertEquals(0, new BigDecimal("5").compareTo(a.getRemoteBalance()));
        assertThrows(BusinessException.class, () -> quote("WITHDRAW", "6"));
        verify(gateway, times(1)).provision(any(), anyString(), any());
        verify(gateway, never()).adjust(any(), anyString(), anyString(), any(), anyString());
    }

    @Test void concurrentConfirmationsReserveAndProvisionOnlyOnce() throws Exception {
        fundedGateway("8"); var q = quote("PROVISION", "8");
        CountDownLatch start = new CountDownLatch(1); var tasks = new ArrayList<Future<OperationView>>();
        for (int i = 0; i < 2; i++) tasks.add(threads.submit(() -> { auth(7, "ROLE_USER"); start.await(); try { return service.confirm(q.id()); } finally { SecurityContextHolder.clearContext(); } }));
        start.countDown(); for (var t : tasks) assertTrue(Set.of("SUCCEEDED", "DISPATCHING").contains(t.get(20, TimeUnit.SECONDS).state()));
        money("98"); verify(gateway, times(1)).provision(any(), anyString(), any());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM account_ledger", Integer.class));
    }
}
