package com.course.platform.service.impl;

import com.course.platform.common.exception.BusinessException;
import com.course.platform.domain.projectcenter.ProjectReportTypes.*;
import com.course.platform.domain.projectclient.ProjectClientTypes.*;
import com.course.platform.domain.servicecommerce.ServiceTime;
import com.course.platform.infra.persistence.mapper.ProjectReportMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectReportingTest extends ProjectClientTestSupport {
    ProjectReportingServiceImpl reports;
    ProjectReportMapper mapper;
    final LocalDateTime end = LocalDateTime.of(2026, 9, 9, 22, 0);

    @BeforeEach void reportsSetup() {
        sql.getConfiguration().addMapper(ProjectReportMapper.class);
        mapper = spy(sql.getMapper(ProjectReportMapper.class));
        reports = new ProjectReportingServiceImpl(mapper, keys, new DataSourceTransactionManager(jdbc.getDataSource()));
    }

    @Test void emptyOwnerUsesZeroStringsAndDoesNotWriteAnything() {
        var report = reports.owner(keys.web());
        assertEquals(0, report.calls().total());
        assertEquals(0, report.clients().total());
        assertEquals(0, report.tickets().total());
        assertEquals("0.00", report.localFunding().debited());
        assertEquals("0.00", report.localFunding().netDebited());
        assertEquals(report.window().through().minusHours(24), report.window().from());
        assertEquals("Asia/Shanghai", report.window().timezone());
        assertTrue(reports.projects(keys.web(), 1, 20).getRecords().isEmpty());
        assertEquals(0, ledgerCount());
        wallet("100");
    }

    @Test void rollingWindowIncludesBoundaryExcludesFutureAndNeverSeesAnotherOwner() {
        try (var time = mockStatic(ServiceTime.class)) {
            time.when(ServiceTime::now).thenReturn(end);
            var owner = issue("OWNER", "READ_ONLY");
            var c = keys.authenticate(owner.secret());
            call(c, "SELF", "OK", end.minusHours(24));
            call(c, "SELF", "FAILED", end.minusHours(25));
            call(c, "TICKETS", "FAILED", end);
            call(c, "FUTURE", "OK", end.plusSeconds(1));
            auth(8);
            var other = keys.authenticate(issue("OWNER", "READ_ONLY").secret());
            call(other, "OTHER_OWNER", "FAILED", end);
            auth(7);
            var report = reports.owner(c);
            assertEquals(new Calls(3, 2, 2, 1, 2), report.calls());
            assertEquals(List.of(new ActionUsage("SELF", 2, 1, 1, 0), new ActionUsage("TICKETS", 1, 1, 1, 1)), report.actions());
            assertFalse(report.moreActions());
            assertEquals(end.minusHours(24), report.window().from());
            assertEquals(end, report.window().through());
            assertEquals(5, jdbc.queryForObject("SELECT COUNT(*) FROM project_api_call", Integer.class));
        }
    }

    @Test void snapshotsReallyRunInReadOnlyRepeatableReadTransaction() {
        doAnswer(invocation -> {
            assertTrue(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
            assertEquals(TransactionDefinition.ISOLATION_REPEATABLE_READ,
                    TransactionSynchronizationManager.getCurrentTransactionIsolationLevel());
            return invocation.callRealMethod();
        }).when(mapper).clients(7L);
        reports.owner(keys.web());
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
    }

    @Test void actionGroupsAreBoundedWithoutLyingAboutTotal() {
        var c = keys.authenticate(issue("OWNER", "READ_ONLY").secret());
        for (int i = 0; i < 51; i++)
            call(c, "ACTION_" + (char) ('A' + i / 26) + (char) ('A' + i % 26), "OK", ServiceTime.now().minusMinutes(1));
        var report = reports.owner(c);
        assertEquals(51, report.calls().total());
        assertEquals(51, report.calls().actionKinds());
        assertEquals(50, report.actions().size());
        assertTrue(report.moreActions());
        assertEquals("ACTION_AA", report.actions().get(0).action());
    }

    @Test void projectUnitsStaySeparateAndDecimalsNeverPassThroughFloatingPoint() {
        var first = open("4");
        project(2, "Distinct units");
        var second = open("8");
        jdbc.update("UPDATE project_client SET project_id=2,status='SUSPENDED',balance=12345678901234.123456 WHERE id=?", second.id());
        auth(8);
        open("40");
        auth(7);
        var page = reports.projects(keys.web(), 1, 1);
        assertEquals(2, page.getTotal());
        assertEquals(1L, page.getRecords().get(0).projectId());
        assertEquals("4", page.getRecords().get(0).activeUnits());
        assertEquals("0", page.getRecords().get(0).suspendedUnits());
        assertEquals("1.00", page.getRecords().get(0).refundBudget());
        var next = reports.projects(keys.web(), 2, 1).getRecords().get(0);
        assertEquals(2L, next.projectId());
        assertEquals("0", next.activeUnits());
        assertEquals("12345678901234.123456", next.suspendedUnits());
        assertEquals("2.00", next.refundBudget());
        assertEquals(2, reports.owner(keys.web()).clients().total());
        assertEquals(1, reports.owner(keys.web()).clients().active());
        assertEquals(first.id(), service.client(keys.web(), first.id()).id());
    }

    @Test void paginationIsBoundedAndDoesNotLeakFullListOnInvalidInput() {
        for (int[] pair : new int[][]{{0,20},{10001,20},{1,0},{1,51},{Integer.MAX_VALUE,50}})
            assertThrows(BusinessException.class, () -> reports.projects(keys.web(), pair[0], pair[1]));
        verifyNoInteractions(mapper);
    }

    @Test void onlyOwnerCredentialsMayReadOwnerWideUsageEvenWhenCustomerHasSupport() {
        var client = open("4");
        for (String access : List.of("READ_ONLY", "SUPPORT")) {
            var c = keys.authenticate(issue(client.id(), access).secret());
            assertThrows(BusinessException.class, () -> reports.owner(c));
            assertThrows(BusinessException.class, () -> reports.projects(c, 1, 20));
        }
        var owner = keys.authenticate(issue("OWNER", "READ_ONLY").secret());
        assertEquals(1, reports.owner(owner).clients().total());
        assertThrows(BusinessException.class, () -> reports.owner(new Caller(8L, owner.credentialId(), owner.credentialVersion(), null, false)));
        jdbc.update("UPDATE project_api_credential SET key_hash=NULL WHERE id=?", owner.credentialId());
        assertThrows(BusinessException.class, () -> reports.owner(owner));
    }

    @Test void expiredOwnerKeysAndDisabledOwnersFailClosed() {
        var owner = keys.authenticate(issue("OWNER", "READ_ONLY").secret());
        jdbc.update("UPDATE project_api_credential SET expires_at=? WHERE id=?", ServiceTime.now().minusMinutes(1), owner.credentialId());
        assertThrows(BusinessException.class, () -> reports.owner(owner));
        jdbc.update("UPDATE sys_user SET status=0 WHERE id=7");
        assertThrows(BusinessException.class, () -> reports.owner(new Caller(7L, null, null, null, true)));
        verifyNoInteractions(mapper);
    }

    @Test void localFundingUsesOnlyAppliedOperationsAndTicketCountsNeverCreditMoney() {
        var client = open("4");
        confirm(quote(adjust(client.id(), "WITHDRAW", "2")).id());
        var pending = quote(adjust(client.id(), "TOP_UP", "12"));
        jdbc.update("UPDATE project_client_operation SET state='EXPIRED' WHERE id=?", pending.id());
        quote(adjust(client.id(), "TOP_UP", "20"));
        ticket(client.id(), "COMPENSATION", "OPEN", "PENDING");
        ticket(client.id(), "COMPENSATION", "RESOLVED", "APPROVED");
        ticket(client.id(), "BUG", "IN_PROGRESS", "NONE");
        long before = ledgerCount();
        var report = reports.owner(keys.web());
        assertEquals(new Funding(2, "1.00", "0.50", "0.50", 0), report.localFunding());
        assertEquals(new Tickets(3,1,1,1,0,1), report.tickets());
        assertEquals(before, ledgerCount());
        wallet("99.50");
    }

    @Test void systemOverviewRequiresBothRealPermissionsNotRoleNames() {
        for (List<String> authorities : List.of(List.of("ROLE_SUPER_ADMIN"), List.of("api-provider:update"), List.of("payment:reconcile"))) {
            authAs(authorities);
            assertThrows(BusinessException.class, () -> reports.system());
        }
        verifyNoInteractions(mapper);
    }

    @Test void systemSeparatesLocalAndSupplierCnyAndExcludesUnknownFromSettledMoney() throws Exception {
        project(2, "Other project");
        open("4");
        auth(8); open("8"); auth(7);
        binding(7, 1, "ACTIVE", "11");
        binding(7, 2, "ACTIVE", "12");
        binding(8, 1, "DISABLED", "21");
        binding(8, 2, "ACTIVE", null);
        upstream("TOP_UP", "SUCCEEDED", "10.11");
        upstream("WITHDRAW", "SUCCEEDED", "1.01");
        upstream("PROVISION", "SUCCEEDED", "0.00");
        upstream("TOP_UP", "UNKNOWN", "90.99");
        upstream("WITHDRAW", "DISPATCHING", "80.88");
        upstream("TOP_UP", "NOT_ACCEPTED", "70.77");
        upstream("TOP_UP", "READY", "60.66");
        authAs(List.of("api-provider:update", "payment:reconcile"));
        long before = ledgerCount();
        var result = reports.system();
        assertEquals(2, result.publishedProjects());
        assertEquals(2, result.activeUpstreamBindings());
        assertEquals(1, result.activeUpstreamOwners());
        assertEquals(2, result.activeLocalOwners());
        assertEquals(new Funding(3,"10.11","1.01","9.10",2), result.upstreamFunding());
        assertEquals(new Funding(2,"3.00","0.00","3.00",0), result.localFunding());
        assertEquals(before, ledgerCount());
        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(result);
        for (String forbidden : List.of("secret-upstream", "credentialId", "remoteCustomer", "remoteBalance", "apiKey", "resolutionEvidence"))
            assertFalse(json.contains(forbidden), forbidden);
    }

    private void call(Caller c, String action, String outcome, LocalDateTime at) {
        jdbc.update("INSERT INTO project_api_call(id,owner_id,credential_id,action,outcome,create_time) VALUES(?,?,?,?,?,?)",
                UUID.randomUUID().toString(), c.ownerId(), c.credentialId(), action, outcome, at);
    }

    private void project(long id, String title) {
        jdbc.update("INSERT INTO service_project(id,provider_id,remote_project_id,title,base_price,unit_price,unit_cost,valid_until,price_evidence,reviewed_by,reviewed_at,provider_identity,enabled,version,create_time,update_time)"
                + " SELECT ?,provider_id,?,?,base_price,unit_price,unit_cost,valid_until,price_evidence,reviewed_by,reviewed_at,provider_identity,enabled,version,create_time,update_time FROM service_project WHERE id=1", id, String.valueOf(id + 100), title);
    }

    private void ticket(String client, String kind, String status, String review) {
        jdbc.update("INSERT INTO project_client_ticket(id,owner_id,client_id,project_id,project_title,kind,title,description,requested_amount,status,version,review_result,create_time,update_time)"
                + " SELECT ?,owner_id,id,project_id,project_title,?,'private title','encrypted private content',?,?,0,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP FROM project_client WHERE id=?",
                UUID.randomUUID().toString(), kind, "COMPENSATION".equals(kind) ? BigDecimal.ONE : BigDecimal.ZERO, status, review, client);
    }

    private void binding(long owner, long project, String state, String customer) {
        jdbc.update("INSERT INTO service_project_account(id,user_id,project_id,provider_id,provider_identity,remote_project_id,remote_customer_id,customer_key_encrypted,state,unit_price,create_time,update_time)"
                + " VALUES(?,?,?,9,?,'5',?,'secret-upstream-do-not-leak',?,0.25,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                UUID.randomUUID().toString(),owner,project,"a".repeat(64),customer,state);
    }

    private void upstream(String action, String state, String amount) {
        jdbc.update("INSERT INTO service_project_operation(id,account_id,user_id,project_id,project_title,project_version,provider_version,action,state,units,unit_price,unit_cost,amount,expires_at,create_time,update_time)"
                + " VALUES(?,?,7,1,'project',1,1,?,?,1,0.25,0.10,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                UUID.randomUUID().toString(),UUID.randomUUID().toString(),action,state,new BigDecimal(amount));
    }

    private void authAs(List<String> permissions) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(7L, null,
                permissions.stream().map(SimpleGrantedAuthority::new).toList()));
    }
}
