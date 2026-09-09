package com.course.platform.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.course.platform.application.service.projectcenter.ProjectTicketGateway;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.domain.projectcenter.*;
import com.course.platform.domain.projectcenter.ProjectTicketTypes.*;
import com.course.platform.domain.servicecommerce.ServiceTime;
import com.course.platform.infra.persistence.mapper.*;

import org.junit.jupiter.api.*;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;

class ProjectTicketTransactionTest extends ProjectCenterTestSupport {
    ProjectTicketServiceImpl ticketService;
    ProjectTicketGateway ticketGateway;
    ServiceProjectTicketMapper ticketMapper;
    ServiceProjectTicketOperationMapper ticketOps;
    String accountId;
    Receipt remote;
    SubmitForm form;

    @BeforeEach
    void ticketsSetup() {
        accountId = open();
        ticketMapper = sql.getMapper(ServiceProjectTicketMapper.class);
        ticketOps = sql.getMapper(ServiceProjectTicketOperationMapper.class);
        ticketGateway = mock(ProjectTicketGateway.class);
        ticketService =
                new ProjectTicketServiceImpl(
                        ticketMapper,
                        ticketOps,
                        service,
                        ticketGateway,
                        limiter,
                        new com.course.platform.config.RateLimitProperties(),
                        new DataSourceTransactionManager(ds));
        ReflectionTestUtils.setField(ticketService, "enabled", true);
        ReflectionTestUtils.setField(ticketService, "cryptoSecret", "test-project-master-key");
        form =
                new SubmitForm(
                        "compensation",
                        "本人服务异常",
                        "服务未按约定完成，请核实执行记录。",
                        new BigDecimal("2.25"),
                        true);
        remote = receipt("31", "pending", "", "", List.of());
        when(ticketGateway.submitTicket(any(), anyString(), anyString(), any()))
                .thenAnswer(
                        a -> {
                            noTransaction();
                            return remote;
                        });
        when(ticketGateway.ticket(any(), anyString(), anyString(), anyString()))
                .thenAnswer(
                        a -> {
                            noTransaction();
                            return remote;
                        });
        when(ticketGateway.replyTicket(any(), anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(
                        a -> {
                            noTransaction();
                            remote =
                                    receipt(
                                            "31",
                                            "processing",
                                            "",
                                            "",
                                            List.of(
                                                    new Reply(
                                                            "45",
                                                            "customer",
                                                            a.getArgument(4),
                                                            "2026-09-08 12:00:00",
                                                            false)));
                            return remote;
                        });
        when(ticketGateway.reviewTicket(any(), anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(
                        a -> {
                            noTransaction();
                            String result = a.getArgument(3);
                            remote =
                                    receipt(
                                            "31",
                                            "approved".equals(result) ? "resolved" : "closed",
                                            result,
                                            a.getArgument(4),
                                            remote.replies());
                            return remote;
                        });
    }

    Receipt receipt(String id, String status, String result, String note, List<Reply> replies) {
        return new Receipt(
                id,
                "5",
                form.type(),
                form.title(),
                form.description(),
                form.compensationAmount(),
                status,
                result,
                note,
                "2026-09-08 11:00:00",
                "2026-09-08 12:00:00",
                false,
                replies);
    }

    OperationView draft() {
        return ticketService.submit(accountId, form);
    }

    TicketView submit() {
        var draft = draft();
        assertEquals("SUCCEEDED", ticketService.confirm(draft.id(), false).state());
        return ticketService.ticket(draft.ticketId(), false);
    }

    void support() {
        auth(7, "api-provider:update", "payment:reconcile");
    }

    @Test
    void draftIsLocalEncryptedAndOnlyExplicitConfirmationSubmitsOnce() {
        var op = draft();
        assertEquals("READY", op.state());
        verifyNoInteractions(ticketGateway);
        money("100");
        assertFalse(
                ticketMapper
                        .selectById(op.ticketId())
                        .getRequestEncrypted()
                        .contains(form.description()));
        var result = ticketService.confirm(op.id(), false);
        assertEquals("SUCCEEDED", result.state());
        ticketService.confirm(op.id(), false);
        verify(ticketGateway, times(1))
                .submitTicket(eq(provider), eq("5"), eq("private-customer-key"), any());
        var view = ticketService.ticket(op.ticketId(), false);
        assertEquals(form.description(), view.description());
        assertNull(view.userId());
        assertFalse(view.toString().contains("private-customer-key"));
        assertEquals("ACTIVE", view.state());
        money("100");
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM account_ledger", Integer.class));
    }

    @Test
    void listsAndDetailNeverCallSupplierWideListOrExposeOtherLocalOwners() {
        var t = submit();
        clearInvocations(ticketGateway);
        assertEquals(1, ticketService.tickets(1, 20, null, false).getTotal());
        assertNull(ticketService.tickets(1, 20, null, false).getRecords().get(0).description());
        ticketService.ticket(t.id(), false);
        verifyNoInteractions(ticketGateway);
        auth(8, "ROLE_USER");
        assertEquals(0, ticketService.tickets(1, 20, accountId, false).getTotal());
        assertThrows(BusinessException.class, () -> ticketService.ticket(t.id(), false));
        assertThrows(BusinessException.class, () -> ticketService.refresh(t.id(), false));
        assertThrows(BusinessException.class, () -> ticketService.submit(accountId, form));
        assertThrows(
                BusinessException.class,
                () -> ticketService.reply(t.id(), new ReplyForm("越权回复", t.version(), true)));
        verifyNoInteractions(ticketGateway);
    }

    @Test
    void onlyExplicitRefreshFetchesExactlyTheBoundTicket() {
        var t = submit();
        clearInvocations(ticketGateway);
        var refreshed = ticketService.refresh(t.id(), false);
        assertTrue(refreshed.version() > t.version());
        verify(ticketGateway).ticket(eq(provider), eq("5"), eq("private-customer-key"), eq("31"));
        assertEquals(form.description(), refreshed.description());
    }

    @Test
    void mismatchedRemoteIdProjectOrOriginalContentCannotOverwriteBinding() {
        var t = submit();
        String cached = ticketMapper.selectById(t.id()).getSnapshotEncrypted();
        remote = receipt("32", "pending", "", "", List.of());
        assertThrows(BusinessException.class, () -> ticketService.refresh(t.id(), false));
        remote =
                new Receipt(
                        "31",
                        "6",
                        form.type(),
                        form.title(),
                        form.description(),
                        form.compensationAmount(),
                        "pending",
                        "",
                        "",
                        "",
                        "",
                        false,
                        List.of());
        assertThrows(BusinessException.class, () -> ticketService.refresh(t.id(), false));
        remote =
                new Receipt(
                        "31",
                        "5",
                        form.type(),
                        "他人的工单",
                        form.description(),
                        form.compensationAmount(),
                        "pending",
                        "",
                        "",
                        "",
                        "",
                        false,
                        List.of());
        assertThrows(BusinessException.class, () -> ticketService.refresh(t.id(), false));
        assertEquals(cached, ticketMapper.selectById(t.id()).getSnapshotEncrypted());
    }

    @Test
    void lostSubmitRemainsUnknownWithoutGuessingOrAcceptingAnArbitraryRemoteId() {
        var op = draft();
        doThrow(new RuntimeException("lost request result private-customer-key"))
                .when(ticketGateway)
                .submitTicket(any(), anyString(), anyString(), any());
        assertEquals("UNKNOWN", ticketService.confirm(op.id(), false).state());
        ticketService.confirm(op.id(), false);
        assertEquals("UNKNOWN", ticketService.operation(op.id(), false).state());
        assertNull(ticketMapper.selectById(op.ticketId()).getRemoteTicketId());
        support();
        assertThrows(
                BusinessException.class,
                () ->
                        ticketService.resolve(
                                op.id(),
                                new ResolveForm("ACCEPTED", "已核实可能的工单编号，但不允许跨用户认领", true)));
        verify(ticketGateway, never()).ticket(any(), anyString(), anyString(), anyString());
        verify(ticketGateway, times(1)).submitTicket(any(), anyString(), anyString(), any());
        money("100");
    }

    @Test
    void verifiedNonacceptanceClosesUnknownDraftWithoutRefundOrSendingAgain() {
        var op = draft();
        doThrow(new RuntimeException("lost"))
                .when(ticketGateway)
                .submitTicket(any(), anyString(), anyString(), any());
        ticketService.confirm(op.id(), false);
        support();
        var f = new ResolveForm("NOT_ACCEPTED", "上游已核实这次请求完全未受理且未创建工单", true);
        assertEquals("NOT_ACCEPTED", ticketService.resolve(op.id(), f).state());
        ticketService.resolve(op.id(), f);
        assertEquals("CANCELLED", ticketService.ticket(op.ticketId(), true).state());
        money("100");
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM account_ledger", Integer.class));
    }

    @Test
    void replyPreviewHasNoSideEffectAndStaleVersionsOrOtherActorsCannotDispatch() {
        var t = submit();
        clearInvocations(ticketGateway);
        assertThrows(
                BusinessException.class,
                () -> ticketService.reply(t.id(), new ReplyForm("补充情况", t.version() - 1, true)));
        var op = ticketService.reply(t.id(), new ReplyForm("补充情况：已核实执行日志。", t.version(), true));
        verifyNoInteractions(ticketGateway);
        auth(8, "ROLE_USER");
        assertThrows(BusinessException.class, () -> ticketService.confirm(op.id(), false));
        auth(7, "ROLE_USER");
        assertEquals("SUCCEEDED", ticketService.confirm(op.id(), false).state());
        ticketService.confirm(op.id(), false);
        assertEquals(
                "补充情况：已核实执行日志。", ticketService.ticket(t.id(), false).replies().get(0).content());
        verify(ticketGateway, times(1))
                .replyTicket(any(), anyString(), anyString(), eq("31"), anyString());
        money("100");
    }

    @Test
    void refreshCannotTurnLostReplyIntoSuccessMerelyBecauseTextAppears() {
        var t = submit();
        var op = ticketService.reply(t.id(), new ReplyForm("原回复，已在上游出现", t.version(), true));
        doAnswer(
                        a -> {
                            remote =
                                    receipt(
                                            "31",
                                            "processing",
                                            "",
                                            "",
                                            List.of(
                                                    new Reply(
                                                            "45",
                                                            "customer",
                                                            "原回复，已在上游出现",
                                                            "2026-09-08",
                                                            false)));
                            throw new RuntimeException("lost");
                        })
                .when(ticketGateway)
                .replyTicket(any(), anyString(), anyString(), anyString(), anyString());
        assertEquals("UNKNOWN", ticketService.confirm(op.id(), false).state());
        ticketService.refresh(t.id(), false);
        assertEquals("UNKNOWN", ticketService.operation(op.id(), false).state());
        assertNotNull(ticketService.ticket(t.id(), false).pendingOperationId());
        support();
        assertEquals(
                "SUCCEEDED",
                ticketService
                        .resolve(op.id(), new ResolveForm("ACCEPTED", "已逐项核实该回复确实由原请求写入上游工单", true))
                        .state());
        verify(ticketGateway, times(1))
                .replyTicket(any(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void approvedCompensationIsReviewOnlyAndNeverCreditsAnyWallet() {
        var t = submit();
        support();
        var op =
                ticketService.review(
                        t.id(),
                        new ReviewForm("approved", "已核实服务异常，同意补偿申请，付款须另行核实", t.version(), true));
        verify(ticketGateway, never())
                .reviewTicket(any(), anyString(), anyString(), anyString(), anyString());
        assertEquals("SUCCEEDED", ticketService.confirm(op.id(), true).state());
        ticketService.confirm(op.id(), true);
        assertEquals("approved", ticketService.ticket(t.id(), true).reviewResult());
        money("100");
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM account_ledger", Integer.class));
        assertEquals("0", service.refresh(accountId).refundableUnits());
        verify(ticketGateway, times(1))
                .reviewTicket(any(), eq("5"), eq("31"), eq("approved"), anyString());
        assertThrows(
                BusinessException.class,
                () ->
                        ticketService.review(
                                t.id(),
                                new ReviewForm(
                                        "rejected",
                                        "禁止重复审核并覆盖原来已经确定的审核结论",
                                        ticketService.ticket(t.id(), true).version(),
                                        true)));
    }

    @Test
    void reviewRequiresBothAuthoritiesAndPrivatePreviewIsHiddenFromCustomer() {
        var t = submit();
        for (String permission :
                List.of("ROLE_ADMIN", "api-provider:update", "payment:reconcile")) {
            auth(7, permission);
            assertThrows(
                    RuntimeException.class,
                    () ->
                            ticketService.review(
                                    t.id(),
                                    new ReviewForm(
                                            "approved", "已经核实，等待确认的内部审核说明", t.version(), true)));
        }
        support();
        var op =
                ticketService.review(
                        t.id(), new ReviewForm("approved", "已经核实，等待确认的内部审核说明", t.version(), true));
        auth(7, "ROLE_USER");
        assertEquals("", ticketService.operation(op.id(), false).content());
        assertNull(ticketService.operation(op.id(), false).reviewResult());
        assertThrows(BusinessException.class, () -> ticketService.confirm(op.id(), false));
        auth(8, "api-provider:update", "payment:reconcile");
        assertThrows(BusinessException.class, () -> ticketService.confirm(op.id(), true));
    }

    @Test
    void concurrentConfirmationsDispatchOnlyOnceOutsideSqlLocks() throws Exception {
        var op = draft();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(
                        a -> {
                            noTransaction();
                            assertEquals("DISPATCHING", ticketOps.selectById(op.id()).getState());
                            entered.countDown();
                            assertTrue(release.await(5, TimeUnit.SECONDS));
                            return remote;
                        })
                .when(ticketGateway)
                .submitTicket(any(), anyString(), anyString(), any());
        var future =
                threads.submit(
                        () -> {
                            auth(7, "ROLE_USER");
                            return ticketService.confirm(op.id(), false);
                        });
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertEquals("DISPATCHING", ticketService.confirm(op.id(), false).state());
        } finally {
            release.countDown();
        }
        assertEquals("SUCCEEDED", future.get(5, TimeUnit.SECONDS).state());
        verify(ticketGateway, times(1)).submitTicket(any(), anyString(), anyString(), any());
    }

    @Test
    void quoteExpiryAndProviderChangeStopBeforeSupplierDispatch() {
        var op = draft();
        jdbc.update(
                "UPDATE service_project_ticket_operation SET expires_at=? WHERE id=?",
                ServiceTime.now().minusSeconds(1),
                op.id());
        assertEquals("EXPIRED", ticketService.confirm(op.id(), false).state());
        assertNull(ticketService.ticket(op.ticketId(), false).pendingOperationId());
        var next = draft();
        provider.setConfigVersion(3L);
        assertThrows(BusinessException.class, () -> ticketService.confirm(next.id(), false));
        verifyNoInteractions(ticketGateway);
    }

    @Test
    void localCommitFailureAndDuplicateRemoteBindingStayUnknown() {
        var t = submit();
        var second = draft();
        assertEquals("UNKNOWN", ticketService.confirm(second.id(), false).state());
        assertEquals("ACTIVE", ticketService.ticket(t.id(), false).state());
        assertNull(ticketMapper.selectById(second.ticketId()).getRemoteTicketId());
        money("100");
    }

    @Test
    void staleRefreshCannotOverwriteTheReceiptFromAConcurrentReply() {
        var t = submit();
        var op = ticketService.reply(t.id(), new ReplyForm("有效的新回复", t.version(), true));
        doAnswer(
                        a -> {
                            ticketService.confirm(op.id(), false);
                            return receipt("31", "pending", "", "", List.of());
                        })
                .when(ticketGateway)
                .ticket(any(), anyString(), anyString(), anyString());
        assertThrows(BusinessException.class, () -> ticketService.refresh(t.id(), false));
        assertEquals("有效的新回复", ticketService.ticket(t.id(), false).replies().get(0).content());
    }

    @Test
    void localReplyCommitFailureRollsBackReceiptAndCanBeResolvedWithoutASecondSend() {
        var t = submit();
        var op = ticketService.reply(t.id(), new ReplyForm("请核实本次回复的原始请求", t.version(), true));
        jdbc.execute(
                "ALTER TABLE service_project_ticket ADD CONSTRAINT test_ticket_settlement"
                        + " CHECK(state <> 'ACTIVE' OR pending_operation_id IS NOT NULL)");
        assertEquals("UNKNOWN", ticketService.confirm(op.id(), false).state());
        assertTrue(ticketService.ticket(t.id(), false).replies().isEmpty());
        jdbc.execute("ALTER TABLE service_project_ticket DROP CONSTRAINT test_ticket_settlement");
        support();
        assertEquals(
                "SUCCEEDED",
                ticketService
                        .resolve(op.id(), new ResolveForm("ACCEPTED", "已核实该回复属于原请求且上游已成功写入", true))
                        .state());
        assertEquals(1, ticketService.ticket(t.id(), true).replies().size());
        verify(ticketGateway, times(1))
                .replyTicket(any(), anyString(), anyString(), anyString(), anyString());
        money("100");
    }

    @Test
    void refreshedSnapshotInvalidatesUnsentReviewOrReplyButDoesNotReopenAnotherWrite() {
        var t = submit();
        var op = ticketService.reply(t.id(), new ReplyForm("原快照对应的待发送回复", t.version(), true));
        ticketService.refresh(t.id(), false);
        assertThrows(BusinessException.class, () -> ticketService.confirm(op.id(), false));
        verify(ticketGateway, never())
                .replyTicket(any(), anyString(), anyString(), anyString(), anyString());
        jdbc.update(
                "UPDATE service_project_ticket_operation SET expires_at=? WHERE id=?",
                ServiceTime.now().minusSeconds(1),
                op.id());
        assertEquals("EXPIRED", ticketService.operation(op.id(), false).state());
        assertNull(ticketService.ticket(t.id(), false).pendingOperationId());
    }

    @Test
    void plainSecretsAndInvalidCompensationNeverBecomeDrafts() {
        assertThrows(
                BusinessException.class,
                () ->
                        ticketService.submit(
                                accountId,
                                new SubmitForm(
                                        "bug",
                                        "标题",
                                        "private-customer-key",
                                        BigDecimal.ZERO,
                                        true)));
        assertThrows(
                BusinessException.class,
                () ->
                        ticketService.submit(
                                accountId,
                                new SubmitForm("bug", "标题", "非补偿工单不能索要资金", BigDecimal.ONE, true)));
        assertThrows(
                BusinessException.class,
                () ->
                        ticketService.submit(
                                accountId,
                                new SubmitForm(
                                        "compensation", "标题", "补偿必须声明额度", BigDecimal.ZERO, true)));
        assertThrows(
                BusinessException.class,
                () ->
                        ticketService.submit(
                                accountId,
                                new SubmitForm("bug", "标题", "没有同意提交", BigDecimal.ZERO, false)));
        assertEquals(0, ticketService.tickets(1, 20, null, false).getTotal());
        verifyNoInteractions(ticketGateway);
    }

    @Test
    void supplierReplySecretsAreRedactedAndAttachmentsNeverBecomeUrls() {
        var t = submit();
        remote =
                receipt(
                        "31",
                        "processing",
                        "",
                        "",
                        List.of(
                                new Reply(
                                        "45",
                                        "admin",
                                        "private-customer-key / "
                                                + provider.getApiKey()
                                                + " <script>bad</script>",
                                        "2026-09-08",
                                        true)));
        var refreshed = ticketService.refresh(t.id(), false);
        assertTrue(refreshed.replies().get(0).hasAttachment());
        assertTrue(refreshed.replies().get(0).content().contains("[REDACTED]"));
        assertFalse(refreshed.toString().contains("private-customer-key"));
    }

    @Test
    void abandonedDispatchIsQueryOnlyAndFeatureOffPreventsTicketAccess() {
        var op = draft();
        jdbc.update(
                "UPDATE service_project_ticket_operation SET state='DISPATCHING',update_time=?"
                        + " WHERE id=?",
                ServiceTime.now().minusMinutes(3),
                op.id());
        assertEquals("UNKNOWN", ticketService.operation(op.id(), false).state());
        verifyNoInteractions(ticketGateway);
        ReflectionTestUtils.setField(ticketService, "enabled", false);
        assertThrows(BusinessException.class, () -> ticketService.ticket(op.ticketId(), false));
        assertThrows(BusinessException.class, () -> ticketService.confirm(op.id(), false));
    }

    @Test
    void duplicateProviderConfigurationsCannotRebindAnotherCustomersSourceTicket() {
        var first = submit();
        var alias = aliasProject();
        auth(8, "ROLE_USER");
        doAnswer(
                        a -> {
                            noTransaction();
                            return new ProjectCenterTypes.CustomerReceipt(
                                    "12", "5", "second-customer-key", BigDecimal.ZERO, true);
                        })
                .when(gateway)
                .provision(any(), anyString());
        var account =
                service.confirm(
                        service.quote(
                                        alias,
                                        new ProjectCenterTypes.QuoteForm("PROVISION", null, true))
                                .id());
        assertEquals("SUCCEEDED", account.state());
        var op = ticketService.submit(account.accountId(), form);
        assertEquals("UNKNOWN", ticketService.confirm(op.id(), false).state());
        assertNull(ticketMapper.selectById(op.ticketId()).getRemoteTicketId());
        support();
        assertEquals("ACTIVE", ticketService.ticket(first.id(), true).state());
        assertEquals(9L, ticketMapper.selectById(first.id()).getProviderId());
        assertEquals(10L, ticketMapper.selectById(op.ticketId()).getProviderId());
        money("100");
    }
}
