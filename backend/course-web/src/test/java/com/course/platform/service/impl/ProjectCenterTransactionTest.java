package com.course.platform.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.course.platform.common.exception.BusinessException;
import com.course.platform.domain.projectcenter.*;
import com.course.platform.domain.projectcenter.ProjectCenterTypes.*;
import com.course.platform.domain.servicecommerce.ServiceTime;
import com.course.platform.infra.persistence.mapper.*;
import com.course.platform.security.*;

import org.junit.jupiter.api.*;
import org.springframework.jdbc.datasource.*;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

class ProjectCenterTransactionTest extends ProjectCenterTestSupport {
    @Test
    void zeroBalanceOpeningCreatesEncryptedOwnerBoundCustomerOnlyAfterExplicitConfirmation()
            throws Exception {
        var q = quote("PROVISION", null);
        assertEquals("0.00", q.amount());
        assertEquals(0, createCalls.get());
        money("100");
        var done = service.confirm(q.id());
        assertEquals("SUCCEEDED", done.state());
        service.confirm(q.id());
        assertEquals(1, createCalls.get());
        money("100");
        var account = accounts.selectById(done.accountId());
        assertTrue(account.getCustomerKeyEncrypted().startsWith("ENC:"));
        assertFalse(account.toString().contains("private-customer-key"));
        var json =
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .findAndRegisterModules()
                        .writeValueAsString(service.projects(1, 20, false));
        assertFalse(json.contains("private-main-key"));
        assertFalse(json.contains("private-customer-key"));
        assertFalse(json.contains("remoteCustomerId"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM account_ledger", Integer.class));
    }

    @Test
    void platformDebitAndProjectCreditUseExactFrozenRateAndWithdrawalNeverCountsAsNewRecharge() {
        var id = open();
        assertEquals("SUCCEEDED", topup("8").state());
        money("98");
        assertEquals(0, new BigDecimal("8").compareTo(upstream));
        assertEquals("8", service.refresh(id).refundableUnits());
        var w = quote("WITHDRAW", "3");
        assertEquals("0.75", w.amount());
        assertEquals("SUCCEEDED", service.confirm(w.id()).state());
        money("98.75");
        assertEquals("5", service.refresh(id).remoteBalance());
        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(
                        jdbc.queryForObject(
                                "SELECT total_recharge FROM sys_user WHERE id=7",
                                BigDecimal.class)));
        service.confirm(w.id());
        money("98.75");
        verify(gateway, times(2)).adjust(any(), anyString(), anyString(), any(), anyString());
    }

    @Test
    void changingPublishedRatesCannotInflateAnExistingCustomersWithdrawalOrPrice() {
        var id = open();
        topup("8");
        auth(7, "api-provider:update");
        service.save(projectId, form("0.50", true, 0L));
        auth(7, "ROLE_USER");
        assertEquals("0.25", service.refresh(id).unitPrice());
        assertEquals("2.00", quote("WITHDRAW", "8").amount());
        assertEquals("1.00", quote("TOP_UP", "4").amount());
    }

    @Test
    void roundingNeverCreatesMoneyFromRepeatedMicroTopupsAndWithdrawals() {
        open();
        var top = topup("0.05");
        assertEquals("0.02", top.amount());
        money("99.98");
        var refund = quote("WITHDRAW", "0.05");
        assertEquals("0.01", refund.amount());
        service.confirm(refund.id());
        money("99.99");
        assertThrows(BusinessException.class, () -> quote("WITHDRAW", "0.001"));
    }

    @Test
    void externalGiftedBalanceCannotBeAutomaticallyExchangedForLocalCurrency() {
        open();
        topup("2");
        upstream = new BigDecimal("100");
        assertThrows(BusinessException.class, () -> quote("WITHDRAW", "3"));
        money("99.5");
    }

    @Test
    void insufficientLocalBalanceRollsBackReservationAndPreventsSupplierDispatch() {
        var id = open();
        var q = quote("TOP_UP", "8");
        jdbc.update("UPDATE sys_user SET balance=0 WHERE id=7");
        assertThrows(BusinessException.class, () -> service.confirm(q.id()));
        assertNull(accounts.selectById(id).getPendingOperationId());
        assertEquals("ACTIVE", accounts.selectById(id).getState());
        assertEquals("READY", operations.selectById(q.id()).getState());
        verify(gateway, never()).adjust(any(), anyString(), anyString(), any(), anyString());
    }

    @Test
    void failedSupplierWriteHoldsPreDebitAndAccountUntilDualPermissionManualNonacceptance() {
        var id = open();
        doThrow(new RuntimeException("private-provider-response"))
                .when(gateway)
                .adjust(any(), anyString(), anyString(), any(), anyString());
        var op = topup("4");
        assertEquals("UNKNOWN", op.state());
        money("99");
        assertEquals("UNKNOWN", accounts.selectById(id).getState());
        service.confirm(op.id());
        verify(gateway, times(1)).adjust(any(), anyString(), anyString(), any(), anyString());
        assertThrows(BusinessException.class, () -> quote("TOP_UP", "4"));
        assertThrows(
                BusinessException.class,
                () ->
                        service.resolve(
                                op.id(),
                                new ResolveForm("NOT_ACCEPTED", null, "联系上游人工核实本操作完全未受理", true)));
        auth(7, "api-provider:update");
        assertThrows(
                BusinessException.class,
                () ->
                        service.resolve(
                                op.id(),
                                new ResolveForm("NOT_ACCEPTED", null, "联系上游人工核实本操作完全未受理", true)));
        auth(7, "api-provider:update", "payment:reconcile");
        service.resolve(op.id(), new ResolveForm("NOT_ACCEPTED", null, "联系上游人工核实本操作完全未受理", true));
        money("100");
        service.resolve(op.id(), new ResolveForm("NOT_ACCEPTED", null, "联系上游人工核实本操作完全未受理", true));
        money("100");
        assertEquals("ACTIVE", accounts.selectById(id).getState());
    }

    @Test
    void lostWithdrawalReplyDoesNotCreditUntilManualAcceptanceAndNeverSendsASecondDebit() {
        var id = open();
        topup("4");
        var q = quote("WITHDRAW", "2");
        doAnswer(
                        a -> {
                            upstream = upstream.subtract(new BigDecimal("2"));
                            throw new RuntimeException("lost reply");
                        })
                .when(gateway)
                .adjust(any(), anyString(), anyString(), any(), anyString());
        assertEquals("UNKNOWN", service.confirm(q.id()).state());
        money("99");
        auth(7, "api-provider:update", "payment:reconcile");
        var result =
                service.resolve(
                        q.id(), new ResolveForm("ACCEPTED", null, "上游流水逐项核实，本次扣除两额度已成功", true));
        assertEquals("SUCCEEDED", result.state());
        money("99.5");
        service.resolve(q.id(), new ResolveForm("ACCEPTED", null, "上游流水逐项核实，本次扣除两额度已成功", true));
        money("99.5");
        assertEquals("2", service.refresh(id).refundableUnits());
    }

    @Test
    void unknownOpeningNeverCreatesAnotherCustomerAndRequiresVerifiedCustomerReceiptToBind() {
        doThrow(new RuntimeException("lost reply")).when(gateway).provision(any(), anyString());
        var q = quote("PROVISION", null);
        assertEquals("UNKNOWN", service.confirm(q.id()).state());
        assertThrows(BusinessException.class, () -> quote("PROVISION", null));
        service.confirm(q.id());
        verify(gateway, times(1)).provision(any(), anyString());
        auth(7, "api-provider:update", "payment:reconcile");
        assertThrows(
                BusinessException.class,
                () ->
                        service.resolve(
                                q.id(),
                                new ResolveForm("ACCEPTED", null, "已核实上游已开户，待填正确客户编号", true)));
        assertEquals(
                "SUCCEEDED",
                service.resolve(
                                q.id(),
                                new ResolveForm("ACCEPTED", "11", "已核实开户操作对应客户编号十一无重复", true))
                        .state());
        money("100");
    }

    @Test
    void ownershipAndBindingProtectAllPathsIncludingUsersWithAdminLookingRoles() {
        var id = open();
        var q = quote("TOP_UP", "4");
        auth(8, "ROLE_ADMIN");
        assertThrows(BusinessException.class, () -> service.refresh(id));
        assertThrows(BusinessException.class, () -> service.operation(q.id(), false));
        assertThrows(BusinessException.class, () -> service.confirm(q.id()));
        assertEquals(0, service.operations(1, 20, null, false).getRecords().size());
        assertNull(service.projects(1, 20, false).getRecords().get(0).account());
        auth(7, "ROLE_USER");
        provider.setApiKey("different-owner-key");
        assertThrows(BusinessException.class, () -> service.confirm(q.id()));
        assertThrows(BusinessException.class, () -> service.refresh(id));
        verify(gateway, never()).adjust(any(), anyString(), anyString(), any(), anyString());
        money("100");
    }

    @Test
    void expiryProviderVersionsAndPriceChangesInvalidateQuotesBeforeAnyDebit() {
        open();
        var q = quote("TOP_UP", "4");
        jdbc.update(
                "UPDATE service_project_operation SET expires_at=? WHERE id=?",
                ServiceTime.now().minusSeconds(1),
                q.id());
        assertEquals("EXPIRED", service.confirm(q.id()).state());
        var second = quote("TOP_UP", "4");
        provider.setConfigVersion(3L);
        assertThrows(BusinessException.class, () -> service.confirm(second.id()));
        provider.setConfigVersion(2L);
        auth(7, "api-provider:update");
        service.save(projectId, form("0.30", true, 0L));
        auth(7, "ROLE_USER");
        assertThrows(BusinessException.class, () -> service.confirm(second.id()));
        verify(gateway, never()).adjust(any(), anyString(), anyString(), any(), anyString());
        money("100");
    }

    @Test
    void concurrentConfirmationsDispatchExactlyOnceAfterReservationAndMoneyCommit()
            throws Exception {
        var id = open();
        var quote = quote("TOP_UP", "4");
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(
                        a -> {
                            noTransaction();
                            money("99");
                            assertEquals("BUSY", accounts.selectById(id).getState());
                            assertEquals(
                                    "DISPATCHING", operations.selectById(quote.id()).getState());
                            entered.countDown();
                            assertTrue(release.await(5, TimeUnit.SECONDS));
                            return new AdjustmentReceipt(
                                    new BigDecimal("4"), new BigDecimal("0.10"));
                        })
                .when(gateway)
                .adjust(any(), anyString(), anyString(), any(), anyString());
        var first =
                threads.submit(
                        () -> {
                            auth(7, "ROLE_USER");
                            return service.confirm(quote.id());
                        });
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertEquals("DISPATCHING", service.confirm(quote.id()).state());
        } finally {
            release.countDown();
        }
        assertEquals("SUCCEEDED", first.get(5, TimeUnit.SECONDS).state());
        verify(gateway, times(1)).adjust(any(), anyString(), anyString(), any(), anyString());
        money("99");
    }

    @Test
    void differentQuotesFromTheSameAccountVersionCannotBothMutateTheWallet() {
        open();
        var a = quote("TOP_UP", "4");
        var b = quote("TOP_UP", "4");
        assertEquals("SUCCEEDED", service.confirm(a.id()).state());
        assertThrows(BusinessException.class, () -> service.confirm(b.id()));
        money("99");
        verify(gateway, times(1)).adjust(any(), anyString(), anyString(), any(), anyString());
    }

    @Test
    void duplicateOpeningQuotesCreateOnlyOneRemoteCustomer() {
        var a = quote("PROVISION", null);
        var b = quote("PROVISION", null);
        service.confirm(a.id());
        assertThrows(BusinessException.class, () -> service.confirm(b.id()));
        verify(gateway, times(1)).provision(any(), anyString());
    }

    @Test
    void accountRefreshCannotOverwriteAConcurrentFundsOperation() {
        var id = open();
        doAnswer(
                        a -> {
                            noTransaction();
                            jdbc.update(
                                    "UPDATE service_project_account SET version=version+1 WHERE"
                                            + " id=?",
                                    id);
                            return receipt("11");
                        })
                .when(gateway)
                .customer(any(), anyString(), anyString());
        assertThrows(BusinessException.class, () -> service.refresh(id));
        money("100");
    }

    @Test
    void missingConsentInvalidInitialFundsAndExpiredCostEvidenceFailBeforeSupplierWrites() {
        assertThrows(
                BusinessException.class,
                () -> service.quote(projectId, new QuoteForm("PROVISION", BigDecimal.ONE.negate(), true)));
        assertThrows(
                BusinessException.class,
                () -> service.quote(projectId, new QuoteForm("PROVISION", null, false)));
        jdbc.update(
                "UPDATE service_project SET valid_until=? WHERE id=?",
                ServiceTime.now().toLocalDate().minusDays(1),
                projectId);
        assertThrows(BusinessException.class, () -> quote("PROVISION", null));
        verifyNoInteractions(gateway);
        money("100");
    }

    @Test
    void costIncreasesBeyondSnapshotRequireManualReviewInsteadOfSilentSuccessOrRefund() {
        open();
        doReturn(new AdjustmentReceipt(new BigDecimal("4"), new BigDecimal("0.15")))
                .when(gateway)
                .adjust(any(), anyString(), anyString(), any(), anyString());
        var op = topup("4");
        assertEquals("UNKNOWN", op.state());
        money("99");
        assertThrows(BusinessException.class, () -> quote("WITHDRAW", "1"));
    }

    @Test
    void rateLimiterFailsClosedAndFeatureOffMakesNoSupplierCalls() {
        when(limiter.check(any())).thenReturn(RateLimitDecision.denied(60));
        assertThrows(RateLimitExceededException.class, () -> quote("PROVISION", null));
        verifyNoInteractions(gateway);
        ReflectionTestUtils.setField(service, "enabled", false);
        assertThrows(BusinessException.class, () -> service.projects(1, 20, false));
        assertThrows(BusinessException.class, () -> quote("PROVISION", null));
    }

    @Test
    void abandonedDispatchBecomesUnknownWithoutReplayingSupplier() {
        open();
        doThrow(new RuntimeException("lost"))
                .when(gateway)
                .adjust(any(), anyString(), anyString(), any(), anyString());
        var op = topup("4");
        jdbc.update(
                "UPDATE service_project_operation SET state='DISPATCHING',update_time=? WHERE id=?",
                ServiceTime.now().minusMinutes(3),
                op.id());
        assertEquals("UNKNOWN", service.operation(op.id(), false).state());
        verify(gateway, times(1)).adjust(any(), anyString(), anyString(), any(), anyString());
        money("99");
    }

    @Test
    void frozenRateBelowNewCostBlocksNewTopupsWithoutMutatingOldAccount() {
        var id = open();
        jdbc.update(
                "UPDATE service_project SET unit_cost=0.50,version=version+1 WHERE id=?",
                projectId);
        assertThrows(BusinessException.class, () -> quote("TOP_UP", "4"));
        assertEquals("0.25", service.refresh(id).unitPrice());
        money("100");
    }

    @Test
    void localSettlementFailurePreservesPreDebitAndUnknownSagaWithoutReplayingHttp() {
        var id = open();
        jdbc.execute(
                "ALTER TABLE service_project_account ADD CONSTRAINT simulated_write_failure CHECK"
                        + " (refundable_units < 4)");
        var q = quote("TOP_UP", "4");
        assertEquals("UNKNOWN", service.confirm(q.id()).state());
        money("99");
        assertEquals(0, BigDecimal.ZERO.compareTo(accounts.selectById(id).getRefundableUnits()));
        assertEquals("UNKNOWN", service.confirm(q.id()).state());
        verify(gateway, times(1)).adjust(any(), anyString(), anyString(), any(), anyString());
        jdbc.execute("ALTER TABLE service_project_account DROP CONSTRAINT simulated_write_failure");
        auth(7, "api-provider:update", "payment:reconcile");
        assertEquals(
                "SUCCEEDED",
                service.resolve(
                                q.id(),
                                new ResolveForm("ACCEPTED", null, "核实上游已充值且原本地记账因数据库错误失败", true))
                        .state());
        money("99");
        assertEquals(
                0, new BigDecimal("4").compareTo(accounts.selectById(id).getRefundableUnits()));
    }

    @Test
    void cachedDisabledCustomerCanBeExplicitlyRecheckedButNeverFundedBlindly() {
        var id = open();
        doReturn(new CustomerReceipt("11", "5", "private-customer-key", ZERO(), false))
                .when(gateway)
                .customer(any(), anyString(), anyString());
        assertEquals("DISABLED", service.refresh(id).state());
        assertThrows(BusinessException.class, () -> quote("TOP_UP", "4"));
        doReturn(receipt("11")).when(gateway).customer(any(), anyString(), anyString());
        assertEquals("ACTIVE", service.refresh(id).state());
    }

    @Test
    void duplicateRemoteCustomerCannotBeBoundToAnotherLocalOwner() {
        var original = open();
        auth(8, "ROLE_USER");
        doAnswer(
                        a -> {
                            noTransaction();
                            return receipt("11");
                        })
                .when(gateway)
                .provision(any(), anyString());
        var q = quote("PROVISION", null);
        assertEquals("UNKNOWN", service.confirm(q.id()).state());
        assertEquals("ACTIVE", accounts.selectById(original).getState());
        assertNull(accounts.selectById(q.accountId()).getRemoteCustomerId());
        assertEquals("UNKNOWN", accounts.selectById(q.accountId()).getState());
        auth(8, "api-provider:update", "payment:reconcile");
        assertThrows(
                RuntimeException.class,
                () ->
                        service.resolve(
                                q.id(),
                                new ResolveForm("ACCEPTED", "11", "上游客户编号经核实，但禁止与既有账户重复绑定", true)));
        assertEquals("UNKNOWN", operations.selectById(q.id()).getState());
        money("100");
    }

    @Test
    void publishingChangesDuringHttpNeverRepricesTheReservedOperation() {
        var account = open();
        var q = quote("TOP_UP", "4");
        doAnswer(
                        a -> {
                            noTransaction();
                            auth(7, "api-provider:update");
                            service.save(projectId, form("0.80", true, 0L));
                            auth(7, "ROLE_USER");
                            return new AdjustmentReceipt(
                                    new BigDecimal("4"), new BigDecimal("0.10"));
                        })
                .when(gateway)
                .adjust(any(), anyString(), anyString(), any(), anyString());
        var result = service.confirm(q.id());
        assertEquals("SUCCEEDED", result.state());
        assertEquals("1.00", result.amount());
        assertEquals("0.25", service.refresh(account).unitPrice());
        assertEquals("0.8", service.projects(1, 20, false).getRecords().get(0).unitPrice());
        money("99");
    }

    @Test
    void disablingProjectPreservesExistingOwnersVisibilityAndPaidWithdrawal() {
        open();
        topup("4");
        auth(7, "api-provider:update");
        service.save(projectId, form("0.25", false, 0L));
        auth(7, "ROLE_USER");
        assertEquals(1, service.projects(1, 20, false).getTotal());
        assertFalse(service.projects(1, 20, false).getRecords().get(0).available());
        assertThrows(BusinessException.class, () -> quote("TOP_UP", "1"));
        assertEquals("SUCCEEDED", service.confirm(quote("WITHDRAW", "4").id()).state());
        money("100");
        auth(8, "ROLE_USER");
        assertEquals(0, service.projects(1, 20, false).getTotal());
    }

    @Test
    void concurrentManualAcceptancesCreditOnlyOnceAndNeverRepeatTheRemoteDebit() throws Exception {
        open();
        topup("4");
        var q = quote("WITHDRAW", "2");
        doThrow(new RuntimeException("lost withdrawal response"))
                .when(gateway)
                .adjust(any(), anyString(), anyString(), any(), anyString());
        assertEquals("UNKNOWN", service.confirm(q.id()).state());
        clearInvocations(gateway);
        var entered = new CountDownLatch(2);
        doAnswer(
                        a -> {
                            noTransaction();
                            entered.countDown();
                            assertTrue(entered.await(5, TimeUnit.SECONDS));
                            return receipt("11");
                        })
                .when(gateway)
                .customer(any(), anyString(), anyString());
        Callable<OperationView> resolve =
                () -> {
                    auth(7, "api-provider:update", "payment:reconcile");
                    return service.resolve(
                            q.id(),
                            new ResolveForm("ACCEPTED", null, "已逐项核对相同原始流水，确认本次扣除受理成功", true));
                };
        var first = threads.submit(resolve);
        var second = threads.submit(resolve);
        assertEquals("SUCCEEDED", first.get(8, TimeUnit.SECONDS).state());
        assertEquals("SUCCEEDED", second.get(8, TimeUnit.SECONDS).state());
        money("99.50");
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM account_ledger WHERE biz_no=? AND direction=1",
                        Integer.class,
                        "SYY:" + q.id()));
        verify(gateway, never()).adjust(any(), anyString(), anyString(), any(), anyString());
    }

    @Test
    void manualSettlementPreservesAnExplicitDisabledCustomerReceipt() {
        String account = open();
        topup("4");
        var q = quote("WITHDRAW", "2");
        doThrow(new RuntimeException("lost withdrawal result"))
                .when(gateway)
                .adjust(any(), anyString(), anyString(), any(), anyString());
        assertEquals("UNKNOWN", service.confirm(q.id()).state());
        when(gateway.customer(any(), anyString(), anyString()))
                .thenReturn(
                        new CustomerReceipt(
                                "11", "5", "private-customer-key", new BigDecimal("2"), false));
        auth(7, "api-provider:update", "payment:reconcile");
        service.resolve(q.id(), new ResolveForm("ACCEPTED", null, "已核实原扣款成功，且上游随后停用了该账户", true));
        assertEquals("DISABLED", accounts.selectById(account).getState());
        money("99.50");
        assertThrows(BusinessException.class, () -> quote("TOP_UP", "1"));
    }

    @Test
    void duplicateProviderConfigurationsCannotBindOneSourceCustomerToTwoUsers() {
        var first = open();
        var alias = aliasProject();
        auth(8, "ROLE_USER");
        doAnswer(
                        a -> {
                            noTransaction();
                            return receipt("11");
                        })
                .when(gateway)
                .provision(any(), anyString());
        var q = service.quote(alias, new QuoteForm("PROVISION", null, true));
        assertEquals("UNKNOWN", service.confirm(q.id()).state());
        assertNull(accounts.selectById(q.accountId()).getRemoteCustomerId());
        assertEquals("ACTIVE", accounts.selectById(first).getState());
        assertEquals(
                accounts.selectById(first).getProviderIdentity(),
                accounts.selectById(q.accountId()).getProviderIdentity());
        assertNotEquals(
                accounts.selectById(first).getProviderId(),
                accounts.selectById(q.accountId()).getProviderId());
        money("100");
    }

    private BigDecimal ZERO() {
        return BigDecimal.ZERO;
    }

    @Test
    void anotherSourceCustomerOrKeyCannotOverwriteOwnedBalance() {
        var id = open();
        for (CustomerReceipt receipt :
                List.of(
                        new CustomerReceipt(
                                "12", "5", "private-customer-key", BigDecimal.TEN, true),
                        new CustomerReceipt(
                                "11", "6", "private-customer-key", BigDecimal.TEN, true),
                        new CustomerReceipt("11", "5", "replaced-key", BigDecimal.TEN, true))) {
            doReturn(receipt).when(gateway).customer(any(), anyString(), anyString());
            assertThrows(BusinessException.class, () -> service.refresh(id));
        }
        assertEquals(0, BigDecimal.ZERO.compareTo(accounts.selectById(id).getRemoteBalance()));
        money("100");
    }
}
