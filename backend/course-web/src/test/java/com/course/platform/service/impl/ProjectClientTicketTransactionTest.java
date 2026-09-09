package com.course.platform.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.course.platform.common.exception.BusinessException;
import com.course.platform.config.RateLimitProperties;
import com.course.platform.domain.projectclient.*;
import com.course.platform.domain.projectclient.ProjectClientTypes.Caller;
import com.course.platform.domain.projectclient.ProjectClientTypes.StatusForm;
import com.course.platform.domain.projectclient.ProjectClientTicketTypes.*;
import com.course.platform.infra.persistence.mapper.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;

class ProjectClientTicketTransactionTest extends ProjectClientTestSupport {
    ProjectClientTicketServiceImpl tickets;
    String clientId;

    @BeforeEach
    void setupTickets() {
        tickets = new ProjectClientTicketServiceImpl(
                sql.getMapper(ProjectClientTicketMapper.class),
                sql.getMapper(ProjectClientTicketReplyMapper.class),
                sql.getMapper(ProjectClientTicketCommandMapper.class), clients, users, keys,
                validation.getValidator(), limiter, new RateLimitProperties(),
                new DataSourceTransactionManager(jdbc.getDataSource()));
        ReflectionTestUtils.setField(tickets, "enabled", true);
        clientId = open("0").id();
    }

    CreateForm form(String client, String kind) {
        return new CreateForm(UUID.randomUUID().toString(), client, kind, "本地售后申请",
                "请核实本地客户的问题，不包含任何凭据。", new BigDecimal("COMPENSATION".equals(kind) ? "1.25" : "0"), true);
    }

    TicketView create(String kind) {
        var receipt = tickets.create(keys.web(), form(clientId, kind));
        return tickets.ticket(keys.web(), receipt.ticketId());
    }

    ReplyForm reply(long version) {
        return new ReplyForm(UUID.randomUUID().toString(), version, "本次仅补充问题说明", true);
    }

    DecisionForm decision(long version, String action) {
        return new DecisionForm(UUID.randomUUID().toString(), version, action, "核实后的本地处理结论；不进行入账", true);
    }

    Caller customer(String id, String scope) { return keys.authenticate(issue(id, scope).secret()); }
    long count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class); }
    void noMoney() {
        wallet("100");
        assertEquals(0, ledgerCount());
        assertEquals(0, clients.selectById(clientId).getBalance().signum());
        assertEquals(0, count("service_project_account"));
    }

    @Test void ownerCanCreateZeroBalanceCustomerAfterSalesWithoutSupplierOrMoney() {
        var row = create("COMPENSATION");
        assertEquals("PENDING", row.reviewResult());
        assertEquals("OPEN", row.status());
        assertEquals("1.25", row.requestedAmount());
        assertEquals(clientId, row.clientId());
        assertEquals(0, row.version());
        noMoney();
    }

    @Test void readOnlyMasterCanReadButCannotCreateReplyOrReview() {
        var row = create("COMPENSATION");
        var caller = keys.authenticate(issue("OWNER", "READ_ONLY").secret());
        assertEquals(1, tickets.list(caller, null, null, null, 1, 20).getTotal());
        assertThrows(BusinessException.class, () -> tickets.create(caller, form(clientId, "BUG")));
        assertThrows(BusinessException.class, () -> tickets.reply(caller, row.id(), reply(0)));
        assertThrows(BusinessException.class, () -> tickets.decide(caller, row.id(), decision(0, "APPROVE")));
        noMoney();
    }

    @Test void explicitSupportCustomerCanSubmitAndReplyOnlyAsCustomer() {
        var caller = customer(clientId, "SUPPORT");
        var receipt = tickets.create(caller, form(clientId, "BUG"));
        tickets.reply(caller, receipt.ticketId(), reply(0));
        var row = tickets.ticket(caller, receipt.ticketId());
        assertEquals("IN_PROGRESS", row.status());
        assertEquals("CUSTOMER", tickets.replies(caller, row.id(), 1, 20).getRecords().get(0).author());
        assertEquals(1, tickets.list(caller, null, null, null, 1, 20).getTotal());
        assertThrows(BusinessException.class, () -> service.quote(caller, opening("1")));
        noMoney();
    }

    @Test void readOnlyCustomerCannotGainNewWritePrivilege() {
        var row = create("BUG");
        var caller = customer(clientId, "READ_ONLY");
        assertEquals(row.id(), tickets.ticket(caller, row.id()).id());
        assertThrows(BusinessException.class, () -> tickets.create(caller, form(clientId, "BUG")));
        assertThrows(BusinessException.class, () -> tickets.reply(caller, row.id(), reply(0)));
    }

    @Test void customerCannotReadSiblingSameProjectTicketsRepliesOrCreateForSibling() {
        var own = create("BUG");
        var other = open("0").id();
        var sibling = tickets.create(keys.web(), form(other, "BUG"));
        var caller = customer(clientId, "SUPPORT");
        assertEquals(List.of(own.id()), tickets.list(caller, null, null, null, 1, 20).getRecords().stream().map(TicketView::id).toList());
        assertThrows(BusinessException.class, () -> tickets.ticket(caller, sibling.ticketId()));
        assertThrows(BusinessException.class, () -> tickets.replies(caller, sibling.ticketId(), 1, 20));
        assertThrows(BusinessException.class, () -> tickets.list(caller, other, null, null, 1, 20));
        assertThrows(BusinessException.class, () -> tickets.create(caller, form(other, "BUG")));
        assertThrows(BusinessException.class, () -> tickets.reply(caller, sibling.ticketId(), reply(0)));
    }

    @Test void otherOwnerCannotInspectReadOrMutateTickets() {
        var row = create("BUG");
        auth(8);
        assertEquals(0, tickets.list(keys.web(), null, null, null, 1, 20).getTotal());
        assertThrows(BusinessException.class, () -> tickets.ticket(keys.web(), row.id()));
        assertThrows(BusinessException.class, () -> tickets.reply(keys.web(), row.id(), reply(0)));
        assertThrows(BusinessException.class, () -> tickets.create(keys.web(), form(clientId, "BUG")));
    }

    @Test void supportCustomerCannotReviewCompensationOrCloseEvenOwnTicket() {
        var row = create("COMPENSATION");
        var caller = customer(clientId, "SUPPORT");
        for (String action : List.of("APPROVE", "REJECT", "RESOLVE", "CLOSE"))
            assertThrows(BusinessException.class, () -> tickets.decide(caller, row.id(), decision(0, action)));
        assertEquals("PENDING", tickets.ticket(keys.web(), row.id()).reviewResult());
    }

    @Test void ownerAndCustomerHaveDifferentIdempotencyNamespacesWithoutCrossRequestDisclosure() {
        var f = form(clientId, "BUG");
        var receipt = tickets.create(keys.web(), f);
        var caller = customer(clientId, "SUPPORT");
        assertNull(tickets.byRequest(caller, f.requestId()));
        var second = tickets.create(caller, f);
        assertNotEquals(receipt.ticketId(), second.ticketId());
        assertEquals(receipt.ticketId(), tickets.byRequest(keys.web(), f.requestId()).ticketId());
        assertEquals(second.ticketId(), tickets.byRequest(caller, f.requestId()).ticketId());
    }

    @Test void sameCanonicalCreateAndRequestIdReturnsOriginalWithoutDuplicate() {
        var f = form(clientId, "BUG");
        var first = tickets.create(keys.web(), f);
        var equivalent = new CreateForm(f.requestId(), f.clientId(), f.kind(), " " + f.title() + " ", f.description(), f.requestedAmount(), true);
        assertEquals(first.ticketId(), tickets.create(keys.web(), equivalent).ticketId());
        assertEquals(1, count("project_client_ticket"));
        assertEquals(1, count("project_client_ticket_command"));
    }

    @Test void changedPayloadCannotReuseRequestIdentifier() {
        var f = form(clientId, "BUG");
        tickets.create(keys.web(), f);
        var changed = new CreateForm(f.requestId(), f.clientId(), f.kind(), "不同标题", f.description(), f.requestedAmount(), true);
        assertThrows(BusinessException.class, () -> tickets.create(keys.web(), changed));
        assertEquals(1, count("project_client_ticket"));
    }

    @Test void concurrentSameCreateCommitsExactlyOnce() throws Exception {
        var caller = keys.authenticate(issue("OWNER", "MANAGE").secret());
        var f = form(clientId, "BUG");
        var a = threads.submit(() -> tickets.create(caller, f));
        var b = threads.submit(() -> tickets.create(caller, f));
        assertEquals(a.get(10, TimeUnit.SECONDS).ticketId(), b.get(10, TimeUnit.SECONDS).ticketId());
        assertEquals(1, count("project_client_ticket"));
        noMoney();
    }

    @Test void replyIsIdempotentAndOrderedByTicketVersion() {
        var row = create("BUG");
        var f = reply(0);
        tickets.reply(keys.web(), row.id(), f);
        tickets.reply(keys.web(), row.id(), f);
        assertEquals(1, tickets.ticket(keys.web(), row.id()).version());
        assertEquals(1, tickets.replies(keys.web(), row.id(), 1, 20).getTotal());
        assertEquals("OWNER", tickets.replies(keys.web(), row.id(), 1, 20).getRecords().get(0).author());
    }

    @Test void changedReplyWithSameIdIsRejected() {
        var row = create("BUG");
        var f = reply(0);
        tickets.reply(keys.web(), row.id(), f);
        assertThrows(BusinessException.class, () -> tickets.reply(keys.web(), row.id(),
                new ReplyForm(f.requestId(), 0L, "另一条不同回复", true)));
    }

    @Test void staleReplyCannotOverwriteNewerContent() {
        var row = create("BUG");
        tickets.reply(keys.web(), row.id(), reply(0));
        assertThrows(BusinessException.class, () -> tickets.reply(keys.web(), row.id(), reply(0)));
        assertEquals(1, count("project_client_ticket_reply"));
    }

    @ParameterizedTest @ValueSource(strings = {"APPROVE", "REJECT"})
    void compensationDecisionIsFinalMetadataNotARefundOrCredit(String action) {
        var row = create("COMPENSATION");
        var f = decision(0, action);
        var receipt = tickets.decide(keys.web(), row.id(), f);
        var result = tickets.ticket(keys.web(), row.id());
        assertEquals("APPROVE".equals(action) ? "APPROVED" : "REJECTED", result.reviewResult());
        assertEquals("APPROVE".equals(action) ? "RESOLVED" : "CLOSED", result.status());
        assertNotNull(result.reviewedAt());
        assertTrue(receipt.notice().contains("不执行补偿"));
        assertEquals(receipt.ticketId(), tickets.decide(keys.web(), row.id(), f).ticketId());
        assertThrows(BusinessException.class, () -> tickets.reply(keys.web(), row.id(), reply(1)));
        assertThrows(BusinessException.class, () -> tickets.decide(keys.web(), row.id(), decision(1, action)));
        noMoney();
    }

    @ParameterizedTest @ValueSource(strings = {"RESOLVE", "CLOSE"})
    void nonCompensationTicketCanBeResolvedOrClosedWithPublicPlaintextNote(String action) {
        var row = create("SUGGESTION");
        tickets.decide(keys.web(), row.id(), decision(0, action));
        assertEquals("NONE", tickets.ticket(keys.web(), row.id()).reviewResult());
        assertEquals(1, tickets.replies(keys.web(), row.id(), 1, 20).getTotal());
        noMoney();
    }

    @Test void ordinaryTicketCannotApproveAndCompensationCannotBypassReview() {
        var bug = create("BUG");
        var claim = create("COMPENSATION");
        assertThrows(BusinessException.class, () -> tickets.decide(keys.web(), bug.id(), decision(0, "APPROVE")));
        assertThrows(BusinessException.class, () -> tickets.decide(keys.web(), claim.id(), decision(0, "CLOSE")));
    }

    @Test void simultaneousReplyAndReviewWithSameVersionCannotBothApply() throws Exception {
        var row = create("COMPENSATION");
        var caller = keys.authenticate(issue("OWNER", "MANAGE").secret());
        var a = threads.submit(() -> attempt(() -> tickets.reply(caller, row.id(), reply(0))));
        var b = threads.submit(() -> attempt(() -> tickets.decide(caller, row.id(), decision(0, "APPROVE"))));
        assertNotEquals(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
        assertEquals(1, tickets.ticket(keys.web(), row.id()).version());
        noMoney();
    }

    static boolean attempt(Runnable work) {
        try { work.run(); return true; } catch (BusinessException expected) { return false; }
    }

    @Test void failedCreateReceiptRollsBackTicketAndCanRetrySameIdExplicitly() {
        var mapper = sql.getMapper(ProjectClientTicketCommandMapper.class);
        var broken = spy(mapper);
        doReturn(0).when(broken).insert(any(ProjectClientTicketCommand.class));
        ReflectionTestUtils.setField(tickets, "commands", broken);
        var f = form(clientId, "BUG");
        assertThrows(BusinessException.class, () -> tickets.create(keys.web(), f));
        assertEquals(0, count("project_client_ticket"));
        assertNull(tickets.byRequest(keys.web(), f.requestId()));
        ReflectionTestUtils.setField(tickets, "commands", mapper);
        tickets.create(keys.web(), f);
        assertEquals(1, count("project_client_ticket"));
    }

    @Test void failedReplyReceiptRollsBackBothReplyAndTicketVersion() {
        var row = create("BUG");
        breakReceiptWrites();
        assertThrows(BusinessException.class, () -> tickets.reply(keys.web(), row.id(), reply(0)));
        assertEquals(0, count("project_client_ticket_reply"));
        assertEquals(0, tickets.ticket(keys.web(), row.id()).version());
    }

    @Test void failedReviewReceiptRollsBackDecisionWithoutTouchingMoney() {
        var row = create("COMPENSATION");
        breakReceiptWrites();
        assertThrows(BusinessException.class, () -> tickets.decide(keys.web(), row.id(), decision(0, "APPROVE")));
        assertEquals("PENDING", tickets.ticket(keys.web(), row.id()).reviewResult());
        assertNull(tickets.ticket(keys.web(), row.id()).reviewedAt());
        noMoney();
    }

    void breakReceiptWrites() {
        var broken = spy(sql.getMapper(ProjectClientTicketCommandMapper.class));
        doReturn(0).when(broken).insert(any(ProjectClientTicketCommand.class));
        ReflectionTestUtils.setField(tickets, "commands", broken);
    }

    @Test void rotatedKeyInvalidatesAlreadyResolvedCallerIncludingPendingWrites() {
        var caller = customer(clientId, "SUPPORT");
        issue(clientId, "READ_ONLY");
        assertThrows(BusinessException.class, () -> tickets.create(caller, form(clientId, "BUG")));
        assertThrows(BusinessException.class, () -> tickets.list(caller, null, null, null, 1, 20));
        assertEquals(0, count("project_client_ticket"));
    }

    @Test void suspendedCustomerKeyCannotReadOrWriteButOwnerCanHandleExistingAfterSales() {
        var row = create("BUG");
        var caller = customer(clientId, "SUPPORT");
        var client = service.client(keys.web(), clientId);
        service.status(keys.web(), clientId, new StatusForm(client.version(), "SUSPENDED", true));
        assertThrows(BusinessException.class, () -> tickets.ticket(caller, row.id()));
        assertThrows(BusinessException.class, () -> tickets.reply(caller, row.id(), reply(0)));
        tickets.reply(keys.web(), row.id(), reply(0));
        assertEquals(1, tickets.ticket(keys.web(), row.id()).version());
        assertThrows(BusinessException.class, () -> tickets.create(keys.web(), form(clientId, "BUG")));
    }

    @Test void closedCustomerStillHasOwnerVisibleAfterSalesWithoutReopeningBalance() {
        var row = create("COMPENSATION");
        var client = service.client(keys.web(), clientId);
        service.status(keys.web(), clientId, new StatusForm(client.version(), "CLOSED", true));
        tickets.decide(keys.web(), row.id(), decision(0, "APPROVE"));
        assertEquals("CLOSED", clients.selectById(clientId).getStatus());
        noMoney();
    }

    @Test void featureOffAllowsWebReadsAndRecoveryButNoTicketWrites() {
        var f = form(clientId, "BUG");
        var row = tickets.create(keys.web(), f);
        ReflectionTestUtils.setField(tickets, "enabled", false);
        assertEquals(row.ticketId(), tickets.byRequest(keys.web(), f.requestId()).ticketId());
        assertEquals(row.ticketId(), tickets.ticket(keys.web(), row.ticketId()).id());
        assertThrows(BusinessException.class, () -> tickets.create(keys.web(), form(clientId, "BUG")));
        assertThrows(BusinessException.class, () -> tickets.reply(keys.web(), row.ticketId(), reply(0)));
        assertThrows(BusinessException.class, () -> tickets.decide(keys.web(), row.ticketId(), decision(0, "RESOLVE")));
    }

    @Test void malformedIdFiltersAndPaginationAreRejectedRatherThanBroadeningScope() {
        var caller = keys.web();
        assertThrows(BusinessException.class, () -> tickets.ticket(caller, "1-1-1-1-1"));
        assertThrows(BusinessException.class, () -> tickets.byRequest(caller, "bad-id"));
        assertThrows(BusinessException.class, () -> tickets.list(caller, null, "ALL", null, 1, 20));
        assertThrows(BusinessException.class, () -> tickets.list(caller, null, null, "anything", 1, 20));
        assertThrows(BusinessException.class, () -> tickets.list(caller, null, null, null, 0, 20));
        assertThrows(BusinessException.class, () -> tickets.list(caller, null, null, null, 1, 101));
    }

    @Test void listsApplyClientKindStatusAndPageFilters() {
        create("BUG");
        create("COMPENSATION");
        tickets.create(keys.web(), form(open("0").id(), "BUG"));
        assertEquals(2, tickets.list(keys.web(), clientId, "OPEN", null, 1, 1).getTotal());
        assertEquals(1, tickets.list(keys.web(), clientId, null, "COMPENSATION", 1, 20).getTotal());
        assertEquals(1, tickets.list(keys.web(), clientId, null, null, 2, 1).getRecords().size());
    }

    @Test void textIsStoredAsPlainTextNeverMarkupExecutionAndFormsRedactToString() {
        var f = new CreateForm(UUID.randomUUID().toString(), clientId, "BUG", "<b>问题</b>", "<script>doNotExecute()</script>", BigDecimal.ZERO, true);
        var receipt = tickets.create(keys.web(), f);
        assertEquals(f.description(), tickets.ticket(keys.web(), receipt.ticketId()).description());
        assertFalse(f.toString().contains("doNotExecute"));
        assertThrows(BusinessException.class, () -> tickets.reply(keys.web(), receipt.ticketId(),
                new ReplyForm(UUID.randomUUID().toString(), 0L, "bad\u0000text", true)));
    }

    @Test void invalidAmountsOversizedTextAndMissingConsentNeverCreate() {
        for (String amount : List.of("-1", "0.001", "100000000", "1E+10000", "1E-10000")) {
            var f = new CreateForm(UUID.randomUUID().toString(), clientId, "COMPENSATION", "test", "test", new BigDecimal(amount), true);
            assertThrows(BusinessException.class, () -> tickets.create(keys.web(), f));
        }
        assertThrows(BusinessException.class, () -> tickets.create(keys.web(), new CreateForm(UUID.randomUUID().toString(), clientId, "BUG", "t", "d", BigDecimal.ONE, true)));
        assertThrows(BusinessException.class, () -> tickets.create(keys.web(), new CreateForm(UUID.randomUUID().toString(), clientId, "BUG", "t", "x".repeat(5001), BigDecimal.ZERO, true)));
        assertThrows(BusinessException.class, () -> tickets.create(keys.web(), new CreateForm(UUID.randomUUID().toString(), clientId, "BUG", "t", "d", BigDecimal.ZERO, false)));
        assertEquals(0, count("project_client_ticket"));
    }

    @Test void supportIsExplicitCustomerOnlyAndNeverMasterManagementScope() {
        assertThrows(BusinessException.class, () -> issue("OWNER", "SUPPORT"));
        assertThrows(BusinessException.class, () -> issue(clientId, "MANAGE"));
        var issued = issue(clientId, "SUPPORT");
        assertEquals("SUPPORT", issued.settings().access());
        assertFalse(keys.authenticate(issued.secret()).manage());
    }

    @Test void databasePreventsOwnerClientIdentityMismatchAndReviewedPendingState() {
        var row = create("COMPENSATION");
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update(
                "UPDATE project_client_ticket SET owner_id=8 WHERE id=?", row.id()));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update(
                "UPDATE project_client_ticket SET review_result='APPROVED' WHERE id=?", row.id()));
    }
}
