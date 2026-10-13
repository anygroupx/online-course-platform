package com.course.platform.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.config.MyMetaObjectHandler;
import com.course.platform.domain.dto.CourseWalletTransferRequest;
import com.course.platform.domain.entity.*;
import com.course.platform.infra.persistence.mapper.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 真数据库行锁与事务测试，不用内存余额替代实际资金一致性。 */
class CourseReservedWalletTransactionTest {
    JdbcTemplate jdbc;
    TransactionTemplate tx;
    CourseReservedWalletService service;
    CoursePrivateContractMapper contracts;
    CourseReservedWalletMapper wallets;
    CourseReservedWalletLedgerMapper ledgers;
    UserMapper users;
    ExecutorService threads;
    Clock clock = Clock.fixed(Instant.parse("2026-10-13T12:00:00Z"), ZoneId.of("Asia/Shanghai"));

    @BeforeEach void setup() throws Exception {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:coursewallet_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000", "sa", "");
        jdbc = new JdbcTemplate(ds); tx = new TransactionTemplate(new DataSourceTransactionManager(ds)); threads = Executors.newFixedThreadPool(2);
        jdbc.execute("CREATE TABLE sys_user(id BIGINT PRIMARY KEY,status INT DEFAULT 1,balance DECIMAL(12,2),total_recharge DECIMAL(12,2),update_time TIMESTAMP)");
        jdbc.execute("CREATE TABLE account_ledger(id BIGINT AUTO_INCREMENT PRIMARY KEY,user_id BIGINT,biz_type VARCHAR(32),biz_no VARCHAR(64),direction INT,amount DECIMAL(12,2),balance_before DECIMAL(12,2),balance_after DECIMAL(12,2),remark VARCHAR(255),create_time TIMESTAMP,UNIQUE(user_id,biz_type,biz_no,direction))");
        jdbc.execute("CREATE TABLE course_private_contract(id BIGINT AUTO_INCREMENT PRIMARY KEY,user_id BIGINT,platform_id BIGINT,fixed_price DECIMAL(12,2),min_initial_deposit DECIMAL(12,2),status VARCHAR(32),activated_at TIMESTAMP,valid_from TIMESTAMP,valid_until TIMESTAMP,version INT,created_by BIGINT,updated_by BIGINT,create_time TIMESTAMP,update_time TIMESTAMP,UNIQUE(user_id,platform_id))");
        jdbc.execute("CREATE TABLE course_reserved_wallet(id BIGINT AUTO_INCREMENT PRIMARY KEY,contract_id BIGINT,user_id BIGINT,platform_id BIGINT,balance DECIMAL(12,2) CHECK(balance>=0),version INT,create_time TIMESTAMP,update_time TIMESTAMP,UNIQUE(user_id,platform_id))");
        jdbc.execute("CREATE TABLE course_reserved_wallet_ledger(id BIGINT AUTO_INCREMENT PRIMARY KEY,wallet_id BIGINT,user_id BIGINT,biz_type VARCHAR(32),biz_no VARCHAR(64),direction INT,amount DECIMAL(12,2),balance_before DECIMAL(12,2),balance_after DECIMAL(12,2),related_order_no VARCHAR(64),operator_id BIGINT,remark VARCHAR(255),create_time TIMESTAMP,UNIQUE(wallet_id,biz_type,biz_no,direction))");
        jdbc.execute("CREATE TABLE course_reserved_transfer(id BIGINT AUTO_INCREMENT PRIMARY KEY,wallet_id BIGINT,user_id BIGINT,amount DECIMAL(12,2),type VARCHAR(32),request_key VARCHAR(64),biz_no VARCHAR(64),status VARCHAR(32),operator_id BIGINT,approved_by BIGINT,reason VARCHAR(255),create_time TIMESTAMP,completed_at TIMESTAMP,UNIQUE(user_id,request_key))");
        jdbc.execute("CREATE TABLE course_private_contract_history(id BIGINT AUTO_INCREMENT PRIMARY KEY,contract_id BIGINT,operator_id BIGINT,reason VARCHAR(255),before_json TEXT,after_json TEXT,create_time TIMESTAMP)");
        jdbc.update("INSERT INTO sys_user VALUES(7,1,500,500,NOW()),(8,1,500,500,NOW())");
        jdbc.update("INSERT INTO course_private_contract(id,user_id,platform_id,fixed_price,min_initial_deposit,status,version) VALUES(3,7,9,7.20,100,'PENDING_ACTIVATION',0),(4,7,10,5,0,'ACTIVE',0),(6,8,9,8,0,'ACTIVE',0)");
        jdbc.update("INSERT INTO course_reserved_wallet(id,contract_id,user_id,platform_id,balance,version) VALUES(5,3,7,9,0,0),(6,4,7,10,0,0),(7,6,8,9,0,0)");
        var config = new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true);
        for (var mapper : List.of(UserMapper.class,AccountLedgerMapper.class,CoursePrivateContractMapper.class,CourseReservedWalletMapper.class,CourseReservedWalletLedgerMapper.class,CourseReservedTransferMapper.class,CoursePrivateContractHistoryMapper.class)) config.addMapper(mapper);
        var factory = new MybatisSqlSessionFactoryBean(); factory.setDataSource(ds); factory.setConfiguration(config); factory.setGlobalConfig(new GlobalConfig().setMetaObjectHandler(new MyMetaObjectHandler()));
        var session = new SqlSessionTemplate(factory.getObject());
        // BaseMapper查询完整实体字段，资金夹具也需保留其他可空列。
        for (var field : com.baomidou.mybatisplus.core.metadata.TableInfoHelper.getTableInfo(User.class).getFieldList()) {
            String type=field.getPropertyType()==Integer.class?"INT":field.getPropertyType()==Long.class?"BIGINT":field.getPropertyType()==BigDecimal.class?"DECIMAL(12,2)":field.getPropertyType()==LocalDateTime.class?"TIMESTAMP":"VARCHAR(2048)";
            jdbc.execute("ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS " + field.getColumn() + " " + type);
        }

        users=session.getMapper(UserMapper.class); contracts=session.getMapper(CoursePrivateContractMapper.class); wallets=session.getMapper(CourseReservedWalletMapper.class); ledgers=session.getMapper(CourseReservedWalletLedgerMapper.class);
        CoursePlatformMapper products=mock(CoursePlatformMapper.class); CoursePlatform p=new CoursePlatform();p.setId(9L);p.setStatus(1);when(products.selectById(9L)).thenReturn(p);
        service=new CourseReservedWalletService(users,products,contracts,wallets,ledgers,session.getMapper(CourseReservedTransferMapper.class),session.getMapper(CoursePrivateContractHistoryMapper.class),new AccountLedgerServiceImpl(users,session.getMapper(AccountLedgerMapper.class)),new ObjectMapper().findAndRegisterModules(),clock);
    }
    @AfterEach void cleanup(){threads.shutdownNow();org.springframework.security.core.context.SecurityContextHolder.clearContext();}
    CourseWalletTransferRequest request(String amount,String key){var r=new CourseWalletTransferRequest();r.setAmount(new BigDecimal(amount));r.setRequestKey(key);r.setReason("已核查资金归属");return r;}
    BigDecimal balance(String table,long id){return jdbc.queryForObject("SELECT balance FROM "+table+" WHERE id=?",BigDecimal.class,id);}
    void money(String expected,BigDecimal actual){assertEquals(0,new BigDecimal(expected).compareTo(actual));}
    void deposit(String amount,String key){tx.executeWithoutResult(s->service.transferIn(5L,7L,request(amount,key)));}
    CourseOrder order(String number){var o=new CourseOrder();o.setUserId(7L);o.setPlatformId(9L);o.setWalletId(5L);o.setPrivateContractId(3L);o.setOrderNo(number);o.setAmount(new BigDecimal("7.20"));o.setPaymentSource("COURSE_WALLET");o.setContractVersion(contracts.selectById(3).getVersion());return o;}
    void charge(String number){tx.executeWithoutResult(s->service.charge(order(number)));}
    void refund(String number){tx.executeWithoutResult(s->service.refund(order(number),7L));}
    @Test void transferConservesMoneyAndDoesNotCountRecharge(){deposit("200","in-1");money("300",balance("sys_user",7));money("200",balance("course_reserved_wallet",5));money("500",jdbc.queryForObject("SELECT total_recharge FROM sys_user WHERE id=7",BigDecimal.class));assertEquals("ACTIVE",contracts.selectById(3).getStatus());assertNotNull(contracts.selectById(3).getActivatedAt());}
    @Test void failedDebitLeavesBothSidesUnchanged(){assertThrows(BusinessException.class,()->deposit("501","in-1"));money("500",balance("sys_user",7));money("0",balance("course_reserved_wallet",5));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM course_reserved_transfer",Integer.class));}
    @Test void failedWalletLedgerRollsBackGeneralDebit(){jdbc.execute("ALTER TABLE course_reserved_wallet_ledger ADD CONSTRAINT forced_failure CHECK(amount<100)");assertThrows(RuntimeException.class,()->deposit("200","in-1"));money("500",balance("sys_user",7));money("0",balance("course_reserved_wallet",5));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM account_ledger",Integer.class));}
    @Test void retryIsExactlyOnceAndChangedAmountConflicts(){deposit("200","in-1");deposit("200","in-1");money("300",balance("sys_user",7));assertThrows(BusinessException.class,()->deposit("201","in-1"));assertThrows(BusinessException.class,()->tx.executeWithoutResult(s->service.transferIn(6L,7L,request("200","in-1"))));}
    @ParameterizedTest @ValueSource(strings={"0","-1","0.001","10000000000.00"}) void badAmountsCannotMoveMoney(String amount){assertThrows(BusinessException.class,()->deposit(amount,"bad"));money("500",balance("sys_user",7));}
    @Test void anotherAgentCannotMoveWalletMoney(){assertThrows(BusinessException.class,()->tx.executeWithoutResult(s->service.transferIn(5L,8L,request("10","bad"))));money("500",balance("sys_user",8));}
    @Test void activationThresholdAccumulatesSuccessfulDepositsOnly(){deposit("40","one");assertEquals("PENDING_ACTIVATION",contracts.selectById(3).getStatus());deposit("60","two");assertEquals("ACTIVE",contracts.selectById(3).getStatus());charge("o-1");assertEquals("ACTIVE",contracts.selectById(3).getStatus());money("92.80",balance("course_reserved_wallet",5));}
    @Test void differentProductCannotSpendThisWallet(){deposit("200","in");CourseOrder wrong=order("bad");wrong.setPlatformId(10L);assertThrows(BusinessException.class,()->tx.executeWithoutResult(s->service.charge(wrong)));money("200",balance("course_reserved_wallet",5));}
    @Test void sameOrderChargeAndRefundAreIdempotent(){deposit("200","in");charge("o-1");charge("o-1");money("192.80",balance("course_reserved_wallet",5));refund("o-1");refund("o-1");money("200",balance("course_reserved_wallet",5));money("300",balance("sys_user",7));}
    @Test void refundNeedsOriginalChargeAndUsesSnapshotAfterPriceChange(){deposit("200","in");assertThrows(BusinessException.class,()->refund("unknown"));charge("o-1");jdbc.update("UPDATE course_private_contract SET fixed_price=99,status='TERMINATED' WHERE id=3");refund("o-1");money("200",balance("course_reserved_wallet",5));}
    @Test void twoConsumersOfLastBalanceOnlyOneSucceeds() throws Exception {deposit("100","in");jdbc.update("UPDATE course_reserved_wallet SET balance=7.20 WHERE id=5");var result=concurrent(()->charge("one"),()->charge("two"));assertEquals(1,result.stream().filter(RuntimeException.class::isInstance).count());money("0",balance("course_reserved_wallet",5));}
    @Test void concurrentDuplicateTransferOnlyMovesOnce() throws Exception{var result=concurrent(()->deposit("200","same"),()->deposit("200","same"));assertEquals(0,result.stream().filter(RuntimeException.class::isInstance).count());money("300",balance("sys_user",7));money("200",balance("course_reserved_wallet",5));}
    @Test void transferAndChargeUseSameLockOrder() throws Exception{deposit("100","first");var result=concurrent(()->deposit("20","second"),()->charge("one"));assertEquals(0,result.stream().filter(RuntimeException.class::isInstance).count());money("112.80",balance("course_reserved_wallet",5));money("380",balance("sys_user",7));}
    @Test void cumulativeWalletFiguresIncludeAllLedgerHistoryWithoutDoubleCounting(){
        deposit("200","in");deposit("200","in");charge("one");refund("one");
        var totals=ledgers.sumByWallet(5L);
        for(var expected:Map.of("totalTransferIn","200.00","totalConsumed","7.20","totalRefunded","7.20","totalTransferredOut","0.00").entrySet()) {
            Object value=totals.entrySet().stream().filter(e->e.getKey().equalsIgnoreCase(expected.getKey())).findFirst().orElseThrow().getValue();money(expected.getValue(),new BigDecimal(value.toString()));
        }
    }
    @Test void disablingNewDepositsStillReturnsAnAlreadyCommittedRetry(){
        deposit("200","existing");org.springframework.test.util.ReflectionTestUtils.setField(service,"enabled",false);
        deposit("200","existing");assertThrows(BusinessException.class,()->deposit("10","new"));money("300",balance("sys_user",7));money("200",balance("course_reserved_wallet",5));
    }
    void finance(long operator,boolean authorized) {
        var authority=authorized?List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("course-wallet:release")):List.<org.springframework.security.core.authority.SimpleGrantedAuthority>of();
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(operator,null,authority));
    }
    @Test void approvedReleaseConservesFundsAndIsIdempotent(){
        deposit("200","in");finance(8,true);
        tx.executeWithoutResult(s->service.release(5L,8L,request("50","release")));
        tx.executeWithoutResult(s->service.release(5L,8L,request("50","release")));
        money("350",balance("sys_user",7));money("150",balance("course_reserved_wallet",5));money("500",balance("sys_user",8));
        assertEquals(8L,jdbc.queryForObject("SELECT approved_by FROM course_reserved_transfer WHERE type='TRANSFER_OUT'",Long.class));
        money("500",jdbc.queryForObject("SELECT total_recharge FROM sys_user WHERE id=7",BigDecimal.class));
    }
    @Test void releaseCannotBeRequestedByUserOrDisabledOperator(){
        deposit("200","in");finance(7,false);assertThrows(BusinessException.class,()->tx.executeWithoutResult(s->service.release(5L,7L,request("50","out"))));
        finance(8,true);jdbc.update("UPDATE sys_user SET status=0 WHERE id=8");assertThrows(BusinessException.class,()->tx.executeWithoutResult(s->service.release(5L,8L,request("50","out"))));money("200",balance("course_reserved_wallet",5));
    }
    @Test void releaseFailureRollsBackWalletDebitAndLedgers(){
        deposit("200","in");finance(8,true);jdbc.update("UPDATE sys_user SET balance=9999999999.99 WHERE id=7");
        assertThrows(RuntimeException.class,()->tx.executeWithoutResult(s->service.release(5L,8L,request("50","out"))));money("200",balance("course_reserved_wallet",5));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM course_reserved_transfer",Integer.class));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM course_reserved_wallet_ledger",Integer.class));
    }
    List<Object> concurrent(Runnable a,Runnable b) throws Exception{var start=new CountDownLatch(1);var futures=new ArrayList<Future<Object>>();for(var task:List.of(a,b))futures.add(threads.submit(()->{start.await();try{task.run();return Boolean.TRUE;}catch(RuntimeException e){return e;}}));start.countDown();return List.of(futures.get(0).get(10,TimeUnit.SECONDS),futures.get(1).get(10,TimeUnit.SECONDS));}
}
