package com.course.platform.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.course.platform.common.exception.BusinessException;
import com.course.platform.domain.projectclient.ProjectClientTypes.*;
import com.course.platform.domain.servicecommerce.ServiceTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;

class ProjectClientTransactionTest extends ProjectClientTestSupport {
    @Test
    void sourcePoolAllowsMultipleCustomersForOneOwnerProjectWithoutAnUpstreamAccount() {
        var a = open("0");
        var b = open("0");
        assertNotEquals(a.id(), b.id());
        assertEquals(2, service.clients(keys.web(), 1L, 1, 20).getTotal());
        assertEquals(
                0,
                jdbc.queryForObject("SELECT COUNT(*) FROM service_project_account", Integer.class));
        wallet("100");
        assertEquals(0, ledgerCount());
    }

    @Test
    void initialFundingIsPreviewedThenLocallyDebitedExactlyOnce() {
        var op = quote(opening("10"));
        wallet("100");
        assertEquals("2.50", op.amount());
        assertEquals(0, service.clients(keys.web(), 1L, 1, 20).getTotal());
        assertEquals("APPLIED", confirm(op.id()).state());
        wallet("97.50");
        assertEquals(new BigDecimal("10.000000"), clients.selectById(op.clientId()).getBalance());
        assertEquals(1, ledgerCount());
        assertEquals("APPLIED", confirm(op.id()).state());
        wallet("97.50");
        assertEquals(1, ledgerCount());
    }

    @Test
    void requestIdentitySurvivesLostPreviewReplyWithoutCreatingADuplicateCustomer() {
        var form = opening("8");
        var first = quote(form);
        assertEquals(first.id(), quote(form).id());
        assertEquals(first.id(), service.byRequest(keys.web(), form.requestId()).id());
        confirm(first.id());
        assertEquals("APPLIED", quote(form).state());
        assertEquals(1, service.clients(keys.web(), 1L, 1, 20).getTotal());
        wallet("98");
    }

    @Test
    void reusingRequestIdWithDifferentCustomerAmountFails() {
        var form = opening("8");
        quote(form);
        var conflict =
                new QuoteForm(
                        form.requestId(), "OPEN", 1L, null, "Demo customer", BigDecimal.TEN, true);
        assertThrows(BusinessException.class, () -> quote(conflict));
        wallet("100");
    }

    @Test
    void insufficientPlatformBalanceCreatesNeitherCustomerNorLedger() {
        var op = quote(opening("1000"));
        assertThrows(BusinessException.class, () -> confirm(op.id()));
        assertNull(clients.selectById(op.clientId()));
        assertEquals("READY", service.operation(keys.web(), op.id()).state());
        wallet("100");
        assertEquals(0, ledgerCount());
    }

    @Test
    void frozenCustomerPriceIsUsedAfterProjectPriceChanges() {
        var c = open("10");
        jdbc.update("UPDATE service_project SET unit_price=1.50,version=version+1 WHERE id=1");
        var op = quote(adjust(c.id(), "TOP_UP", "4"));
        assertEquals("1.00", op.amount());
        confirm(op.id());
        wallet("96.50");
        assertEquals(new BigDecimal("0.250000"), clients.selectById(c.id()).getUnitPrice());
    }

    @Test
    void contractCostAboveFrozenPriceBlocksNewFundingButNotReturningOwnFunds() {
        var c = open("10");
        jdbc.update("UPDATE service_project SET unit_cost=0.50 WHERE id=1");
        assertThrows(BusinessException.class, () -> quote(adjust(c.id(), "TOP_UP", "1")));
        var op = quote(adjust(c.id(), "WITHDRAW", "10"));
        confirm(op.id());
        wallet("100");
    }

    @Test
    void localCustomerOwnerAndProjectMustMatchBeforeAnyFundsAreMoved() {
        var c = open("10");
        auth(8);
        assertThrows(BusinessException.class, () -> service.client(keys.web(), c.id()));
        assertThrows(BusinessException.class, () -> quote(adjust(c.id(), "TOP_UP", "1")));
        assertEquals(0, service.clients(keys.web(), 1L, 1, 20).getTotal());
        wallet("97.50");
    }

    @Test
    void quotePermissionsAndConsentAreRechecked() {
        var f = opening("10");
        assertThrows(
                BusinessException.class,
                () ->
                        quote(
                                new QuoteForm(
                                        f.requestId(),
                                        f.action(),
                                        f.projectId(),
                                        null,
                                        f.label(),
                                        f.units(),
                                        false)));
        var op = quote(f);
        assertThrows(
                BusinessException.class,
                () -> service.confirm(keys.web(), op.id(), new ConfirmForm(false)));
        wallet("100");
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "100000.000001", "1e999", "0.0000001"})
    void outOfRangeUnitsFailBeforeSavingQuote(String units) {
        assertThrows(BusinessException.class, () -> quote(opening(units)));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM project_client_operation", Integer.class));
        wallet("100");
    }

    @Test
    void concurrentQuotesWithSameRequestIdReturnOneDurableOperation() throws Exception {
        var f = opening("4");
        var start = new CountDownLatch(1);
        Callable<String> run =
                () -> {
                    auth(7);
                    start.await();
                    try {
                        return quote(f).id();
                    } finally {
                        SecurityContextHolder.clearContext();
                    }
                };
        var a = threads.submit(run);
        var b = threads.submit(run);
        start.countDown();
        assertEquals(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM project_client_operation", Integer.class));
        wallet("100");
    }

    @Test
    void concurrentConfirmationsDebitOnce() throws Exception {
        var op = quote(opening("12"));
        var start = new CountDownLatch(1);
        Callable<String> run =
                () -> {
                    auth(7);
                    start.await();
                    try {
                        return confirm(op.id()).state();
                    } finally {
                        SecurityContextHolder.clearContext();
                    }
                };
        var a = threads.submit(run);
        var b = threads.submit(run);
        start.countDown();
        assertEquals("APPLIED", a.get(10, TimeUnit.SECONDS));
        assertEquals("APPLIED", b.get(10, TimeUnit.SECONDS));
        wallet("97");
        assertEquals(1, ledgerCount());
    }

    @Test
    void ledgerInsertFailureRollsBackCustomerAndPlatformWallet() {
        var op = quote(opening("12"));
        jdbc.execute("ALTER TABLE account_ledger ADD CONSTRAINT fixture_no_ledger CHECK(amount<0)");
        assertThrows(RuntimeException.class, () -> confirm(op.id()));
        assertNull(clients.selectById(op.clientId()));
        wallet("100");
        assertEquals("READY", service.operation(keys.web(), op.id()).state());
    }

    @Test
    void customerWriteFailureRollsBackEarlierLedgerDebit() {
        var op = quote(opening("12"));
        jdbc.execute(
                "ALTER TABLE project_client ADD CONSTRAINT fixture_no_client CHECK(balance<0)");
        assertThrows(RuntimeException.class, () -> confirm(op.id()));
        wallet("100");
        assertEquals(0, ledgerCount());
        assertEquals("READY", service.operation(keys.web(), op.id()).state());
    }

    @Test
    void failedLocalWriteCanBeExplicitlyRetriedBySameOperationAfterRecovery() {
        var op = quote(opening("4"));
        jdbc.execute("ALTER TABLE project_client ADD CONSTRAINT fixture_retry CHECK(balance<0)");
        assertThrows(RuntimeException.class, () -> confirm(op.id()));
        jdbc.execute("ALTER TABLE project_client DROP CONSTRAINT fixture_retry");
        confirm(op.id());
        assertEquals(1, ledgerCount());
        wallet("99");
    }

    @Test
    void staleProjectOrCustomerQuoteNeverOverwritesNewState() {
        var c = open("4");
        var top = quote(adjust(c.id(), "TOP_UP", "1"));
        var another = quote(adjust(c.id(), "TOP_UP", "2"));
        confirm(another.id());
        assertEquals("STALE", confirm(top.id()).state());
        wallet("98.50");
    }

    @Test
    void changedProjectPriceBeforeOpeningInvalidatesPreviewWithoutDebit() {
        var op = quote(opening("4"));
        jdbc.update("UPDATE service_project SET unit_price=1,version=version+1 WHERE id=1");
        assertEquals("STALE", confirm(op.id()).state());
        wallet("100");
    }

    @Test
    void partialWithdrawIsCappedAndFinalReturnIncludesOnlyPaidRoundingResidual() {
        var c = open("0.01");
        wallet("99.99");
        var top = quote(adjust(c.id(), "TOP_UP", "0.01"));
        confirm(top.id());
        wallet("99.98");
        var all = quote(adjust(c.id(), "WITHDRAW", "0.02"));
        assertEquals("0.02", all.amount());
        confirm(all.id());
        wallet("100");
        assertEquals(0, clients.selectById(c.id()).getRefundBudget().signum());
    }

    @Test
    void normalPartialReturnAndFinalReturnBalanceNeverMintMoney() {
        var c = open("10");
        var partial = quote(adjust(c.id(), "WITHDRAW", "3"));
        assertEquals("0.75", partial.amount());
        confirm(partial.id());
        wallet("98.25");
        var finalReturn = quote(adjust(c.id(), "WITHDRAW", "7"));
        confirm(finalReturn.id());
        wallet("100");
        assertThrows(BusinessException.class, () -> quote(adjust(c.id(), "WITHDRAW", "1")));
        assertEquals(new BigDecimal("100.00"), users.selectById(7L).getTotalRecharge());
    }

    @Test
    void suspendedCustomerCannotTopUpButCanReturnItsLocalFunds() {
        var c = open("4");
        var suspended =
                service.status(keys.web(), c.id(), new StatusForm(c.version(), "SUSPENDED", true));
        assertThrows(BusinessException.class, () -> quote(adjust(c.id(), "TOP_UP", "1")));
        confirm(quote(adjust(c.id(), "WITHDRAW", "4")).id());
        wallet("100");
        assertEquals("SUSPENDED", suspended.status());
    }

    @Test
    void closingWithValueIsRejectedAndClosingEmptyClientInvalidatesKeys() {
        var c = open("4");
        var k = issue(c.id(), "READ_ONLY");
        assertThrows(
                BusinessException.class,
                () ->
                        service.status(
                                keys.web(), c.id(), new StatusForm(c.version(), "CLOSED", true)));
        confirm(quote(adjust(c.id(), "WITHDRAW", "4")).id());
        var current = service.client(keys.web(), c.id());
        var closed =
                service.status(
                        keys.web(), c.id(), new StatusForm(current.version(), "CLOSED", true));
        assertEquals("CLOSED", closed.status());
        assertThrows(BusinessException.class, () -> keys.authenticate(k.secret()));
    }

    @Test
    void expiredQuoteAndDisabledOwnerCannotConfirm() {
        var op = quote(opening("1"));
        jdbc.update(
                "UPDATE project_client_operation SET expires_at=? WHERE id=?",
                ServiceTime.now().minusSeconds(1),
                op.id());
        assertEquals("EXPIRED", confirm(op.id()).state());
        var pending = quote(opening("1"));
        jdbc.update("UPDATE sys_user SET status=0 WHERE id=7");
        assertThrows(BusinessException.class, () -> confirm(pending.id()));
        wallet("100");
    }

    @Test
    void nativeOffAllowsSafeWebRecoveryButNoFunding() {
        var c = open("4");
        ReflectionTestUtils.setField(service, "enabled", false);
        assertEquals(c.id(), service.client(keys.web(), c.id()).id());
        assertThrows(BusinessException.class, () -> quote(opening("0")));
        wallet("99");
    }

    @Test
    void sourceStatisticsReflectAppliedOperationsOnly() {
        var a = open("8");
        open("0");
        quote(opening("100"));
        confirm(quote(adjust(a.id(), "WITHDRAW", "3")).id());
        var s = service.stats(keys.web());
        assertEquals(2, s.customers());
        assertEquals(3, s.appliedOperations());
        assertEquals(0, new BigDecimal("2.00").compareTo(new BigDecimal(s.totalDebited())));
        assertEquals(0, new BigDecimal("0.75").compareTo(new BigDecimal(s.totalReturned())));
    }

    @Test
    void rateLimiterFailureDoesNotSaveOrApplyFinancialOperation() {
        when(limiter.check(any())).thenReturn(null);
        assertThrows(BusinessException.class, () -> quote(opening("1")));
        wallet("100");
        assertEquals(0, ledgerCount());
    }
}
