package com.course.platform.service.impl;

import com.course.platform.common.exception.BusinessException;
import com.course.platform.domain.projectcenter.ProjectRecordTypes.*;
import com.course.platform.domain.projectclient.ProjectClientTypes.Caller;
import com.course.platform.infra.persistence.mapper.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProjectRecordsTest extends ProjectClientTestSupport {
    ProjectRecordsServiceImpl records;
    ProjectRecordsMapper mapper;
    static final LocalDateTime AT = LocalDateTime.of(2026, 9, 10, 10, 0);
    final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @BeforeEach void setupRecords() {
        sql.getConfiguration().addMapper(ProjectRecordsMapper.class);
        sql.getConfiguration().addMapper(ProjectReportMapper.class);
        mapper = spy(sql.getMapper(ProjectRecordsMapper.class));
        records = new ProjectRecordsServiceImpl(mapper, sql.getMapper(ProjectReportMapper.class), keys,
                new DataSourceTransactionManager(jdbc.getDataSource()));
    }

    @Test void paidOpeningAppearsInEveryAccountDebitAggregateAndZeroOpeningStaysZero() {
        String paid = upstream(7,1,"PROVISION","SUCCEEDED","2.00","8",AT);
        String zero = upstream(7,1,"PROVISION","SUCCEEDED","0.00","0",AT);
        upstream(7,1,"PROVISION","UNKNOWN","9.00","36",AT);
        upstream(7,1,"PROVISION","NOT_ACCEPTED","5.00","20",AT);
        upstream(8,1,"PROVISION","SUCCEEDED","3.00","12",AT);
        String id = account(7,1,"ACTIVE",new BigDecimal("8"));
        jdbc.update("UPDATE service_project_operation SET account_id=? WHERE id IN (?,?)",id,paid,zero);
        admin();
        assertEquals("2.00", records.owners("7",1,20).getRecords().get(0).accountDebited());
        assertEquals("2.00", records.owner(7).accountFunding().debited());
        assertEquals("2.00", records.accounts(7,1,20).getRecords().get(0).debited());
        var ledger = records.adminLedger(new LedgerFilter(7L,null,null,"PROJECT_ACCOUNT",null,null,null,null),1,20);
        assertEquals(2,ledger.total()); assertEquals("2.00",ledger.totals().get(0).debited());
        assertEquals(0,new BigDecimal("5.00").compareTo(sql.getMapper(ProjectReportMapper.class).upstreamFunding().debited()));
        assertEquals(0,ledgerCount()); wallet("100");
    }

    @Test void ownersAreHistoricalDistinctAndSqlPaginatedIncludingMissingIdentity() {
        open("4"); auth(8); open("2"); auth(7);
        upstream(8,1,"TOP_UP","SUCCEEDED","1.00","4",AT);
        upstream(19,1,"TOP_UP","SUCCEEDED","2.00","8",AT);
        jdbc.update("UPDATE sys_user SET status=0 WHERE id=8");
        admin();
        var first = records.owners(null,1,2);
        assertEquals(3, first.getTotal());
        assertEquals(List.of(7L,8L), first.getRecords().stream().map(Owner::id).toList());
        assertEquals("DISABLED",first.getRecords().get(1).status());
        var missing=records.owners(null,2,2).getRecords().get(0);
        assertEquals(19L,missing.id()); assertNull(missing.username()); assertEquals("MISSING",missing.status());
        assertEquals("2.00",missing.accountDebited());
    }

    @Test void usersWithNoProjectHistoryAreNotAnUnboundedUserDirectory() {
        admin(); assertEquals(0,records.owners(null,1,20).getTotal());
        assertThrows(BusinessException.class,()->records.owner(8));
        assertEquals(0,records.adminLedger(all(),1,20).total());
        verifyNoMoreInteractionsAfterEmptyLedger();
    }

    private void verifyNoMoreInteractionsAfterEmptyLedger() {
        assertEquals(0,ledgerCount()); wallet("100");
    }

    @Test void ownerSearchEscapesPercentUnderscoreBangAndQuotes() {
        open("0"); auth(8); open("0"); auth(7);
        jdbc.update("UPDATE sys_user SET username=? WHERE id=7","user%_!literal'quote");
        jdbc.update("UPDATE sys_user SET username=? WHERE id=8","userABordinary");
        admin();
        for(String word:List.of("%","_","!","'quote","user%_!")) {
            var rows=records.owners(word,1,20); assertEquals(1,rows.getTotal(),word); assertEquals(7L,rows.getRecords().get(0).id());
        }
        assertEquals(0,records.owners("' OR 1=1 --",1,20).getTotal());
    }

    @Test void numericOwnerSearchIsExactAndTooLargeNumericStringsAreLiteralNotOverflow() {
        open("0"); upstream(70,1,"PROVISION","SUCCEEDED","0","0",AT); admin();
        assertEquals(List.of(7L),records.owners("7",1,20).getRecords().stream().map(Owner::id).toList());
        assertTrue(records.owners("9999999999999999999",1,20).getRecords().isEmpty());
    }

    @Test void ownerDetailCanInspectDisabledOrMissingOwnersWithoutImpersonatingThem() throws Exception {
        auth(8); open("4"); auth(7); jdbc.update("UPDATE sys_user SET status=0,api_key='never-public-user-key' WHERE id=8");
        upstream(8,1,"TOP_UP","UNKNOWN","8.25","33",AT);
        upstream(19,1,"TOP_UP","SUCCEEDED","1.01","4.04",AT);
        admin();
        var detail=records.owner(8);
        assertEquals("DISABLED",detail.owner().status());
        assertEquals(1,detail.usage().clients().total());
        assertEquals("1.00",detail.usage().localFunding().debited());
        assertEquals(1,detail.accountFunding().unresolvedOperations());
        assertEquals("0.00",detail.accountFunding().debited());
        assertEquals("MISSING",records.owner(19).owner().status());
        assertFalse(json.writeValueAsString(detail).contains("never-public"));
        assertEquals(7L,keys.web().ownerId());
    }

    @Test void accountRowsArePagedExactCachedObservationsWithoutCredentialsOrRemoteIds() throws Exception {
        project(2);
        String id=account(7,1,"ACTIVE",new BigDecimal("12345678901234.123456"));
        account(7,2,"UNKNOWN",null); account(8,1,"ACTIVE",BigDecimal.TEN);
        String op=upstream(7,1,"TOP_UP","SUCCEEDED","0.01","0.04",AT);
        jdbc.update("UPDATE service_project_operation SET account_id=? WHERE id=?",id,op);
        String unknown=upstream(7,1,"TOP_UP","UNKNOWN","88.88","4",AT);
        jdbc.update("UPDATE service_project_operation SET account_id=? WHERE id=?",id,unknown);
        admin();
        var first=records.accounts(7,1,1);assertEquals(2,first.getTotal());
        var row=first.getRecords().get(0);assertEquals("12345678901234.123456",row.cachedBalance());
        assertEquals("0.01",row.debited());assertEquals(1,row.unresolvedOperations());
        assertNull(records.accounts(7,2,1).getRecords().get(0).cachedBalance());
        String encoded=json.writeValueAsString(first);
        for(String forbidden:List.of("secret-private-customer-key","remoteCustomerId","providerIdentity","providerId"))assertFalse(encoded.contains(forbidden));
    }

    @Test void clientRowsFilterExactOwnerProjectAndStateAndShowActualSettledFundingOnly() throws Exception {
        var one=open("4");var two=open("8");project(2);
        jdbc.update("UPDATE project_client SET project_id=2,status='SUSPENDED' WHERE id=?",two.id());
        auth(8);open("100");auth(7); quote(adjust(one.id(),"TOP_UP","10"));admin();
        var all=records.customers(7,null,null,1,20);assertEquals(2,all.getTotal());
        assertEquals("1.00",all.getRecords().stream().filter(r->r.id().equals(one.id())).findFirst().orElseThrow().debited());
        assertEquals(two.id(),records.customers(7,2L,"SUSPENDED",1,20).getRecords().get(0).id());
        assertTrue(records.customers(7,1L,"SUSPENDED",1,20).getRecords().isEmpty());
        assertFalse(json.writeValueAsString(all).contains("keyHash"));
    }

    @Test void ledgerSummariesCoverAllMatchingPagesNotOnlyVisibleRowsAndKeepBooksSeparate() {
        open("4");upstream(7,1,"TOP_UP","SUCCEEDED","10.11","40.44",AT);
        upstream(7,1,"WITHDRAW","SUCCEEDED","1.01","4.04",AT);
        upstream(7,1,"TOP_UP","UNKNOWN","1000.00","4000",AT);
        upstream(7,1,"WITHDRAW","DISPATCHING","2000.00","8000",AT);
        admin(); var page=records.adminLedger(all(),1,1);
        assertEquals(3,page.total());assertEquals(1,page.records().size());assertEquals(2,page.totals().size());
        var project=page.totals().stream().filter(r->r.book().equals("PROJECT_ACCOUNT")).findFirst().orElseThrow();
        assertEquals(new BookTotals("PROJECT_ACCOUNT",2,"10.11","1.01","9.10"),project);
        var customer=page.totals().stream().filter(r->r.book().equals("CUSTOMER_CREDIT")).findFirst().orElseThrow();
        assertEquals("1.00",customer.debited());assertEquals(3,page.totals().stream().mapToLong(BookTotals::operations).sum());
        assertEquals(page.totals(),records.adminLedger(all(),2,1).totals());
    }

    @Test void zeroProvisionIncludedAsSettledActionButNoPhantomCharge() {
        upstream(7,1,"PROVISION","SUCCEEDED","0","0",AT);
        upstream(7,1,"TOP_UP","NOT_ACCEPTED","10","40",AT);
        upstream(7,1,"TOP_UP","EXPIRED","20","80",AT);
        admin();var page=records.adminLedger(all(),1,20);
        assertEquals(1,page.total()); assertEquals("0.00",page.records().get(0).amount());
        assertEquals("0.00",page.totals().get(0).debited());assertEquals("0",page.records().get(0).units());
    }

    @Test void ledgerTiesUseBookThenOperationIdAndDoNotDropSameIdAcrossBooks() {
        var client=open("4");var op=jdbc.queryForObject("SELECT id FROM project_client_operation WHERE client_id=?",String.class,client.id());
        String id=upstream(7,1,"TOP_UP","SUCCEEDED","2","8",AT);
        jdbc.update("UPDATE service_project_operation SET id=? WHERE id=?",op,id);
        jdbc.update("UPDATE project_client_operation SET create_time=?,update_time=?",AT.minusDays(1),AT);
        admin();var first=records.adminLedger(all(),1,1);var second=records.adminLedger(all(),2,1);
        assertEquals(2,first.total());assertEquals("CUSTOMER_CREDIT",first.records().get(0).book());
        assertEquals("PROJECT_ACCOUNT",second.records().get(0).book());assertEquals(first.records().get(0).id(),second.records().get(0).id());
    }

    @Test void ledgerDateFiltersUseSettlementTimeAndIncludeWholeBeijingEndDay() {
        var date=LocalDate.of(2026,9,10);
        upstream(7,1,"TOP_UP","SUCCEEDED","1","4",date.atStartOfDay());
        upstream(7,1,"TOP_UP","SUCCEEDED","2","8",date.atTime(23,59,59));
        upstream(7,1,"TOP_UP","SUCCEEDED","3","12",date.plusDays(1).atStartOfDay());
        upstream(7,1,"TOP_UP","SUCCEEDED","4","16",date.minusDays(1).atTime(23,59,59));
        admin();var result=records.adminLedger(new LedgerFilter(null,null,null,null,null,date,date,null),1,20);
        assertEquals(2,result.total());assertEquals("3.00",result.totals().get(0).debited());assertEquals("Asia/Shanghai",result.timezone());
        assertTrue(result.records().stream().allMatch(r->r.requestedAt().isBefore(date.atStartOfDay())));
    }

    @Test void ledgerSupportsIndependentDatesAndReadOnlyExactDirectionAndBookFilters() {
        var c=open("4");confirm(quote(adjust(c.id(),"WITHDRAW","2")).id());
        upstream(8,1,"WITHDRAW","SUCCEEDED","10","40",AT);admin();
        var credits=records.adminLedger(new LedgerFilter(7L,1L,c.id(),"CUSTOMER_CREDIT","CREDIT",null,null,null),1,20);
        assertEquals(1,credits.total());assertEquals("0.50",credits.totals().get(0).returned());
        assertEquals("-0.50",credits.totals().get(0).netDebited());
        assertEquals("99.50",credits.records().get(0).walletBalanceAfter());
        assertTrue(records.adminLedger(new LedgerFilter(8L,1L,c.id(),"ALL",null,null,null,null),1,20).records().isEmpty());
    }

    @Test void ledgerLiteralKeywordEscapesWildcardsWithoutSearchingPrivateEvidence() {
        String id=upstream(7,1,"TOP_UP","SUCCEEDED","1","4",AT);
        jdbc.update("UPDATE service_project_operation SET project_title=?,resolution_evidence=? WHERE id=?","Exact%_!project","private-evidence-marker",id);
        upstream(7,1,"TOP_UP","SUCCEEDED","2","8",AT);admin();
        for(String word:List.of("%","_","!","Exact%_!","' OR 1=1 --")) {
            var page=records.adminLedger(new LedgerFilter(null,null,null,null,null,null,null,word),1,20);
            assertEquals(word.startsWith("'")?0:1,page.total(),word);
        }
        assertEquals(0,records.adminLedger(new LedgerFilter(null,null,null,null,null,null,null,"private-evidence-marker"),1,20).total());
    }

    @Test void decimalOutputsNeverLosePrecisionOrInventUnknownBalances() throws Exception {
        String id=upstream(7,1,"TOP_UP","SUCCEEDED","999999999999.99","12345678901234.123456",AT);admin();
        var row=records.adminLedger(all(),1,20).records().get(0);
        assertEquals("999999999999.99",row.amount());assertEquals("12345678901234.123456",row.units());
        assertNull(row.subjectBalanceAfter());assertNull(row.walletBalanceAfter());
        String text=json.writeValueAsString(row);assertFalse(text.contains("resolutionEvidence"));assertFalse(text.contains("provider"));assertTrue(text.contains(id));
    }

    @Test void ownLedgerDerivesOwnerAndRejectsOtherOwnersCustomerKeysOrRevokedKeys() {
        var client=open("4");auth(8);open("8");auth(7);
        var caller=keys.web();assertEquals(1,records.ownLedger(caller,all(),1,20).total());
        assertThrows(BusinessException.class,()->records.ownLedger(caller,new LedgerFilter(8L,null,null,null,null,null,null,null),1,20));
        var customer=keys.authenticate(issue(client.id(),"SUPPORT").secret());
        assertThrows(BusinessException.class,()->records.ownLedger(customer,all(),1,20));
        var key=keys.authenticate(issue("OWNER","READ_ONLY").secret());assertEquals(1,records.ownLedger(key,all(),1,20).total());
        jdbc.update("UPDATE project_api_credential SET key_hash=NULL WHERE id=?",key.credentialId());
        assertThrows(BusinessException.class,()->records.ownLedger(key,all(),1,20));
    }

    @Test void administrativeReadingNeedsBothActualPermissionsAndAnActiveAuthenticatedActor() {
        for(String permission:List.of("ROLE_SUPER_ADMIN","api-provider:update","payment:reconcile")) {
            authorities(permission);
            assertThrows(BusinessException.class,()->records.owners(null,1,20));
            assertThrows(BusinessException.class,()->records.owner(7));
            assertThrows(BusinessException.class,()->records.accounts(7,1,20));
            assertThrows(BusinessException.class,()->records.customers(7,null,null,1,20));
            assertThrows(BusinessException.class,()->records.adminLedger(all(),1,20));
        }
        admin();jdbc.update("UPDATE sys_user SET status=0 WHERE id=7");
        assertThrows(BusinessException.class,()->records.adminLedger(all(),1,20));verifyNoInteractions(mapper);
    }

    @Test void invalidPagesIdentifiersEnumsDatesAndLongFiltersAreRejectedBeforeSql() {
        admin();
        for(int[] q:new int[][]{{0,20},{10001,20},{1,0},{1,51},{Integer.MAX_VALUE,50}})
            assertThrows(BusinessException.class,()->records.owners(null,q[0],q[1]));
        for(String word:List.of("x".repeat(101),"hidden\nquery"))assertThrows(BusinessException.class,()->records.owners(word,1,20));
        assertThrows(BusinessException.class,()->records.owner(-1));
        assertThrows(BusinessException.class,()->records.customers(7,null,"invalid",1,20));
        for(var f:List.of(new LedgerFilter(0L,null,null,null,null,null,null,null),new LedgerFilter(null,-1L,null,null,null,null,null,null),
                new LedgerFilter(null,null,"../secret",null,null,null,null,null),new LedgerFilter(null,null,null,"rawSql",null,null,null,null),
                new LedgerFilter(null,null,null,null,"REFUND",null,null,null),new LedgerFilter(null,null,null,null,null,LocalDate.of(2026,9,11),LocalDate.of(2026,9,10),null),
                new LedgerFilter(null,null,null,null,null,null,LocalDate.of(9999,12,31),null),new LedgerFilter(null,null,UUID.randomUUID().toString(),"PROJECT_ACCOUNT",null,null,null,null)))
            assertThrows(BusinessException.class,()->records.adminLedger(f,1,20));
        verifyNoInteractions(mapper);
    }

    @Test void countRowsAndSummaryShareReadOnlyRepeatableReadAndDoNotTouchLedger() {
        open("4");admin();long before=ledgerCount();
        doAnswer(i->{assertTrue(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
            assertEquals(TransactionDefinition.ISOLATION_REPEATABLE_READ,TransactionSynchronizationManager.getCurrentTransactionIsolationLevel());
            return i.callRealMethod();}).when(mapper).ledgerTotals(any());
        records.adminLedger(all(),1,20);assertEquals(before,ledgerCount());wallet("99");
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
    }

    private LedgerFilter all(){return new LedgerFilter(null,null,null,null,null,null,null,null);}
    private void admin(){authorities("api-provider:update","payment:reconcile");}
    private void authorities(String... values){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(7L,null,Arrays.stream(values).map(SimpleGrantedAuthority::new).toList()));}
    private void project(long id){jdbc.update("INSERT INTO service_project(id,provider_id,remote_project_id,title,base_price,unit_price,unit_cost,valid_until,price_evidence,reviewed_by,reviewed_at,provider_identity,enabled,version,create_time,update_time) SELECT ?,provider_id,?,'Second project',base_price,unit_price,unit_cost,valid_until,price_evidence,reviewed_by,reviewed_at,provider_identity,enabled,version,create_time,update_time FROM service_project WHERE id=1",id,String.valueOf(id+100));}
    private String account(long owner,long project,String state,BigDecimal balance){String id=UUID.randomUUID().toString();jdbc.update("INSERT INTO service_project_account(id,user_id,project_id,provider_id,provider_identity,remote_project_id,remote_customer_id,customer_key_encrypted,state,unit_price,remote_balance,balance_checked_at,create_time,update_time) VALUES(?,?,?,9,?,'5',?,'secret-private-customer-key',?,0.25,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",id,owner,project,"a".repeat(64),owner+""+project,state,balance);return id;}
    private String upstream(long owner,long project,String action,String state,String amount,String units,LocalDateTime at){String id=UUID.randomUUID().toString();jdbc.update("INSERT INTO service_project_operation(id,account_id,user_id,project_id,project_title,project_version,provider_version,action,state,units,unit_price,unit_cost,amount,expires_at,create_time,update_time) VALUES(?,?,?,?,'Project snapshot',1,1,?,?,?,0.25,0.10,?,?,?,?)",id,UUID.randomUUID().toString(),owner,project,action,state,new BigDecimal(units),new BigDecimal(amount),at.plusHours(1),at.minusDays(2),at);return id;}
}
