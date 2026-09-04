package com.course.platform.service.impl;

import com.alipay.api.AlipayClient;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayTradeRefundResponse;
import com.course.platform.application.service.security.SecurityAuditService;
import com.course.platform.application.service.support.OperationLogService;
import com.course.platform.application.service.system.SystemConfigService;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.security.TokenHashUtil;
import com.course.platform.config.AlipayClientFactory;
import com.course.platform.domain.dto.CardRechargeRequest;
import com.course.platform.domain.entity.AccountLedger;
import com.course.platform.domain.entity.PaymentEvent;
import com.course.platform.domain.entity.PaymentOrder;
import com.course.platform.domain.entity.RechargeCard;
import com.course.platform.domain.entity.User;
import com.course.platform.infra.persistence.mapper.AccountLedgerMapper;
import com.course.platform.infra.persistence.mapper.PaymentEventMapper;
import com.course.platform.infra.persistence.mapper.PaymentNotifyLogMapper;
import com.course.platform.infra.persistence.mapper.PaymentOrderMapper;
import com.course.platform.infra.persistence.mapper.RechargeCardMapper;
import com.course.platform.infra.persistence.mapper.UserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Uses real concurrent threads, row locks, transactions and unique constraints.
 * H2 runs in MySQL compatibility mode so this test remains deterministic in CI.
 */
class FinancialIntegrityConcurrencyTest {

    private DataSource dataSource;
    private JdbcTemplate jdbc;
    private TransactionTemplate transaction;
    private ExecutorService executor;
    private UserMapper userMapper;
    private AccountLedgerMapper ledgerMapper;
    private AccountLedgerServiceImpl ledgerService;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:financial_" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
        ds.setUsername("sa");
        ds.setPassword("");
        dataSource = ds;
        jdbc = new JdbcTemplate(dataSource);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        executor = Executors.newFixedThreadPool(2);

        createSchema();
        userMapper = jdbcBackedUserMapper();
        ledgerMapper = jdbcBackedLedgerMapper();
        ledgerService = new AccountLedgerServiceImpl(userMapper, ledgerMapper);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void samePaymentCallbackCreditsExactlyOnce() throws Exception {
        insertUser(1L, "0.00");
        jdbc.update("INSERT INTO payment_order(id, order_no, user_id, amount, status) VALUES (1,'PAY-1',1,50.00,'PENDING')");

        PaymentOrderMapper orderMapper = jdbcBackedPaymentOrderMapper();
        PaymentEventMapper eventMapper = jdbcBackedPaymentEventMapper();
        AlipayServiceImpl service = paymentService(orderMapper, eventMapper, null);

        List<Object> results = runConcurrently(() -> transaction.execute(status ->
                service.processVerifiedPayment("PAY-1", "TRADE-1", new BigDecimal("50.00"), null, null)));

        assertEquals(2, results.size());
        assertTrue(results.contains(Boolean.TRUE));
        assertTrue(results.contains(Boolean.FALSE));
        assertMoney("50.00", balance(1L));
        assertEquals(1, count("account_ledger"));
        assertEquals(1, count("payment_event"));
        assertEquals("PAID", jdbc.queryForObject("SELECT status FROM payment_order WHERE id=1", String.class));
    }

    @Test
    void twoDebitsOfEightyFromOneHundredAllowOnlyOne() throws Exception {
        insertUser(1L, "100.00");

        List<Object> results = runConcurrentlyCapturingFailure(new String[]{"ORDER-A", "ORDER-B"}, bizNo ->
                transaction.executeWithoutResult(status -> ledgerService.debit(
                        1L, new BigDecimal("80.00"), AccountLedgerServiceImpl.BIZ_ORDER, bizNo, "test")));

        long failures = results.stream().filter(BusinessException.class::isInstance).count();
        assertEquals(1, failures);
        assertMoney("20.00", balance(1L));
        assertEquals(1, count("account_ledger"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE balance < 0", Integer.class));
    }

    @Test
    void sameRechargeCardCanBeClaimedOnlyOnce() throws Exception {
        insertUser(1L, "0.00");
        insertUser(2L, "0.00");
        String secret = "0123456789abcdef0123456789abcdef";
        jdbc.update("INSERT INTO recharge_card(id,card_no,password_hash,amount,status) VALUES (1,'1234567890123456',?,30.00,0)",
                TokenHashUtil.sha256(secret));

        RechargeCardMapper cardMapper = jdbcBackedRechargeCardMapper();
        RechargeCardServiceImpl service = new RechargeCardServiceImpl(
                cardMapper, userMapper, mock(OperationLogService.class), ledgerService);

        List<Object> results = runConcurrentlyCapturingFailure(new Long[]{1L, 2L}, userId -> {
            CardRechargeRequest request = new CardRechargeRequest();
            request.setCardNo("1234567890123456");
            request.setCardPassword(secret);
            transaction.executeWithoutResult(status -> service.rechargeByCard(request, userId));
        });

        assertEquals(1, results.stream().filter(BusinessException.class::isInstance).count(), results.toString());
        assertMoney("30.00", balance(1L).add(balance(2L)));
        assertEquals(1, count("account_ledger"));
        assertEquals(1, jdbc.queryForObject("SELECT status FROM recharge_card WHERE id=1", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM recharge_card WHERE used_by IN (1,2)", Integer.class));
    }

    @Test
    void concurrentRefundCallsProviderAndDebitsOnlyOnce() throws Exception {
        insertUser(1L, "100.00");
        jdbc.update("INSERT INTO payment_order(id,order_no,user_id,amount,status,alipay_trade_no) "
                + "VALUES (1,'PAY-REFUND',1,80.00,'PAID','TRADE-R')");

        PaymentOrderMapper orderMapper = jdbcBackedPaymentOrderMapper();
        PaymentEventMapper eventMapper = jdbcBackedPaymentEventMapper();
        AlipayClient client = mock(AlipayClient.class);
        AlipayTradeRefundResponse response = mock(AlipayTradeRefundResponse.class);
        when(response.isSuccess()).thenReturn(true);
        when(client.execute(any(AlipayTradeRefundRequest.class))).thenReturn(response);
        AlipayServiceImpl service = paymentService(orderMapper, eventMapper, client);

        List<Object> results = runConcurrently(() -> transaction.execute(status ->
                service.refund("PAY-REFUND", "test refund")));

        assertEquals(List.of(Boolean.TRUE, Boolean.TRUE), results.stream().sorted().toList());
        verify(client, times(1)).execute(any(AlipayTradeRefundRequest.class));
        assertMoney("20.00", balance(1L));
        assertEquals(1, count("account_ledger"));
        assertEquals(1, count("payment_event"));
        assertEquals("REFUNDED", jdbc.queryForObject("SELECT status FROM payment_order WHERE id=1", String.class));
    }

    @Test
    void failedProviderRefundRollsBackClaimBalanceAndLedger() throws Exception {
        insertUser(1L, "100.00");
        jdbc.update("INSERT INTO payment_order(id,order_no,user_id,amount,status,alipay_trade_no) "
                + "VALUES (1,'PAY-FAIL',1,80.00,'PAID','TRADE-F')");

        PaymentOrderMapper orderMapper = jdbcBackedPaymentOrderMapper();
        AlipayClient client = mock(AlipayClient.class);
        AlipayTradeRefundResponse response = mock(AlipayTradeRefundResponse.class);
        when(response.isSuccess()).thenReturn(false);
        when(response.getSubMsg()).thenReturn("declined");
        when(client.execute(any(AlipayTradeRefundRequest.class))).thenReturn(response);
        AlipayServiceImpl service = paymentService(orderMapper, jdbcBackedPaymentEventMapper(), client);

        assertThrows(BusinessException.class, () -> transaction.execute(status ->
                service.refund("PAY-FAIL", "test refund")));

        assertMoney("100.00", balance(1L));
        assertEquals(0, count("account_ledger"));
        assertEquals(0, count("payment_event"));
        assertEquals("PAID", jdbc.queryForObject("SELECT status FROM payment_order WHERE id=1", String.class));
    }

    private AlipayServiceImpl paymentService(PaymentOrderMapper orderMapper,
                                             PaymentEventMapper eventMapper,
                                             AlipayClient client) {
        AlipayClientFactory factory = mock(AlipayClientFactory.class);
        when(factory.getClient()).thenReturn(client);
        AlipayServiceImpl service = new AlipayServiceImpl();
        ReflectionTestUtils.setField(service, "alipayClientFactory", factory);
        ReflectionTestUtils.setField(service, "paymentOrderMapper", orderMapper);
        ReflectionTestUtils.setField(service, "paymentNotifyLogMapper", mock(PaymentNotifyLogMapper.class));
        ReflectionTestUtils.setField(service, "systemConfigService", mock(SystemConfigService.class));
        ReflectionTestUtils.setField(service, "accountLedgerService", ledgerService);
        ReflectionTestUtils.setField(service, "paymentEventMapper", eventMapper);
        ReflectionTestUtils.setField(service, "securityAuditService", mock(SecurityAuditService.class));
        ReflectionTestUtils.setField(service, "transactionManager", new DataSourceTransactionManager(dataSource));
        return service;
    }

    private void createSchema() {
        jdbc.execute("CREATE TABLE sys_user (id BIGINT PRIMARY KEY, balance DECIMAL(12,2) NOT NULL, total_recharge DECIMAL(12,2) NOT NULL)");
        jdbc.execute("CREATE TABLE account_ledger (id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, "
                + "biz_type VARCHAR(32) NOT NULL, biz_no VARCHAR(64) NOT NULL, direction TINYINT NOT NULL, "
                + "amount DECIMAL(12,2) NOT NULL, balance_before DECIMAL(12,2) NOT NULL, balance_after DECIMAL(12,2) NOT NULL, "
                + "remark VARCHAR(255), UNIQUE(biz_type,biz_no,direction,user_id))");
        jdbc.execute("CREATE TABLE recharge_card (id BIGINT PRIMARY KEY, card_no VARCHAR(32) UNIQUE NOT NULL, "
                + "card_password VARCHAR(32), password_hash CHAR(64) NOT NULL, amount DECIMAL(10,2) NOT NULL, "
                + "status TINYINT NOT NULL, used_by BIGINT, used_time TIMESTAMP, update_time TIMESTAMP)");
        jdbc.execute("CREATE TABLE payment_order (id BIGINT PRIMARY KEY, order_no VARCHAR(64) UNIQUE NOT NULL, "
                + "user_id BIGINT NOT NULL, amount DECIMAL(10,2) NOT NULL, status VARCHAR(20) NOT NULL, "
                + "alipay_trade_no VARCHAR(64) UNIQUE, buyer_logon_id VARCHAR(100), buyer_user_id VARCHAR(32), "
                + "paid_time TIMESTAMP, refund_amount DECIMAL(10,2), refund_reason VARCHAR(255), refund_time TIMESTAMP, update_time TIMESTAMP)");
        jdbc.execute("CREATE TABLE payment_event (id BIGINT AUTO_INCREMENT PRIMARY KEY, order_no VARCHAR(64) NOT NULL, "
                + "event_type VARCHAR(32) NOT NULL, provider_event_id VARCHAR(128), UNIQUE(order_no,event_type), UNIQUE(provider_event_id))");
    }

    private UserMapper jdbcBackedUserMapper() {
        UserMapper mapper = mock(UserMapper.class);
        when(mapper.selectByIdForUpdate(anyLong())).thenAnswer(invocation -> selectUser(invocation.getArgument(0), true));
        when(mapper.selectById(any())).thenAnswer(invocation -> selectUser(((Number) invocation.getArgument(0)).longValue(), false));
        when(mapper.increaseBalance(anyLong(), any(BigDecimal.class), anyInt())).thenAnswer(invocation -> jdbc.update(
                "UPDATE sys_user SET balance=balance+?, total_recharge=CASE WHEN ?=1 THEN total_recharge+? ELSE total_recharge END WHERE id=?",
                new Object[]{invocation.getArgument(1), invocation.getArgument(2), invocation.getArgument(1), invocation.getArgument(0)}));
        when(mapper.decreaseBalance(anyLong(), any(BigDecimal.class))).thenAnswer(invocation -> jdbc.update(
                "UPDATE sys_user SET balance=balance-? WHERE id=? AND balance>=?",
                new Object[]{invocation.getArgument(1), invocation.getArgument(0), invocation.getArgument(1)}));
        return mapper;
    }

    private AccountLedgerMapper jdbcBackedLedgerMapper() {
        AccountLedgerMapper mapper = mock(AccountLedgerMapper.class);
        when(mapper.selectByBizKey(anyLong(), anyString(), anyString(), anyInt())).thenAnswer(invocation -> {
            List<AccountLedger> rows = jdbc.query("SELECT * FROM account_ledger WHERE user_id=? AND biz_type=? AND biz_no=? AND direction=? FOR UPDATE",
                    (rs, row) -> ledger(rs), new Object[]{invocation.getArgument(0), invocation.getArgument(1),
                            invocation.getArgument(2), invocation.getArgument(3)});
            return rows.isEmpty() ? null : rows.get(0);
        });
        when(mapper.insert(any(AccountLedger.class))).thenAnswer(invocation -> {
            AccountLedger value = invocation.getArgument(0);
            return jdbc.update("INSERT INTO account_ledger(user_id,biz_type,biz_no,direction,amount,balance_before,balance_after,remark) VALUES (?,?,?,?,?,?,?,?)",
                    new Object[]{value.getUserId(), value.getBizType(), value.getBizNo(), value.getDirection(), value.getAmount(),
                            value.getBalanceBefore(), value.getBalanceAfter(), value.getRemark()});
        });
        return mapper;
    }

    private RechargeCardMapper jdbcBackedRechargeCardMapper() {
        RechargeCardMapper mapper = mock(RechargeCardMapper.class);
        when(mapper.selectByCardNo(anyString())).thenAnswer(invocation -> {
            List<RechargeCard> rows = jdbc.query("SELECT * FROM recharge_card WHERE card_no=?",
                    (rs, row) -> card(rs), new Object[]{invocation.getArgument(0)});
            return rows.isEmpty() ? null : rows.get(0);
        });
        when(mapper.claimUnusedCard(anyLong(), anyLong(), any(LocalDateTime.class), anyInt(), anyInt()))
                .thenAnswer(invocation -> jdbc.update(
                        "UPDATE recharge_card SET status=?,used_by=?,used_time=? WHERE id=? AND status=?",
                        new Object[]{invocation.getArgument(4), invocation.getArgument(1), invocation.getArgument(2),
                                invocation.getArgument(0), invocation.getArgument(3)}));
        return mapper;
    }

    private PaymentOrderMapper jdbcBackedPaymentOrderMapper() {
        PaymentOrderMapper mapper = mock(PaymentOrderMapper.class);
        when(mapper.selectByOrderNo(anyString())).thenAnswer(invocation -> selectPayment(invocation.getArgument(0), false));
        when(mapper.selectByOrderNoForUpdate(anyString())).thenAnswer(invocation -> selectPayment(invocation.getArgument(0), true));
        when(mapper.markPaidIfPending(anyString(), anyString(), nullable(String.class), nullable(String.class), any(LocalDateTime.class)))
                .thenAnswer(invocation -> jdbc.update("UPDATE payment_order SET status='PAID',alipay_trade_no=?,buyer_logon_id=?,buyer_user_id=?,paid_time=? WHERE order_no=? AND status='PENDING'",
                        new Object[]{invocation.getArgument(1), invocation.getArgument(2), invocation.getArgument(3), invocation.getArgument(4), invocation.getArgument(0)}));
        when(mapper.markRefundingIfPaid(anyString(), anyString())).thenAnswer(invocation -> jdbc.update(
                "UPDATE payment_order SET status='REFUNDING',refund_reason=? WHERE order_no=? AND status='PAID'",
                new Object[]{invocation.getArgument(1), invocation.getArgument(0)}));
        when(mapper.markRefundedIfRefunding(anyString(), anyString(), any(LocalDateTime.class))).thenAnswer(invocation -> jdbc.update(
                "UPDATE payment_order SET status='REFUNDED',refund_amount=amount,refund_reason=?,refund_time=? WHERE order_no=? AND status='REFUNDING'",
                new Object[]{invocation.getArgument(1), invocation.getArgument(2), invocation.getArgument(0)}));
        return mapper;
    }

    private PaymentEventMapper jdbcBackedPaymentEventMapper() {
        PaymentEventMapper mapper = mock(PaymentEventMapper.class);
        when(mapper.insert(any(PaymentEvent.class))).thenAnswer(invocation -> {
            PaymentEvent event = invocation.getArgument(0);
            return jdbc.update("INSERT INTO payment_event(order_no,event_type,provider_event_id) VALUES (?,?,?)",
                    new Object[]{event.getOrderNo(), event.getEventType(), event.getProviderEventId()});
        });
        return mapper;
    }

    private User selectUser(long id, boolean forUpdate) {
        List<User> rows = jdbc.query("SELECT * FROM sys_user WHERE id=?" + (forUpdate ? " FOR UPDATE" : ""),
                (rs, row) -> {
                    User user = new User();
                    user.setId(rs.getLong("id"));
                    user.setBalance(rs.getBigDecimal("balance"));
                    user.setTotalRecharge(rs.getBigDecimal("total_recharge"));
                    return user;
                }, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private PaymentOrder selectPayment(String orderNo, boolean forUpdate) {
        List<PaymentOrder> rows = jdbc.query("SELECT * FROM payment_order WHERE order_no=?" + (forUpdate ? " FOR UPDATE" : ""),
                (rs, row) -> {
                    PaymentOrder order = new PaymentOrder();
                    order.setId(rs.getLong("id"));
                    order.setOrderNo(rs.getString("order_no"));
                    order.setUserId(rs.getLong("user_id"));
                    order.setAmount(rs.getBigDecimal("amount"));
                    order.setStatus(rs.getString("status"));
                    order.setAlipayTradeNo(rs.getString("alipay_trade_no"));
                    return order;
                }, orderNo);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private AccountLedger ledger(ResultSet rs) throws java.sql.SQLException {
        AccountLedger value = new AccountLedger();
        value.setAmount(rs.getBigDecimal("amount"));
        return value;
    }

    private RechargeCard card(ResultSet rs) throws java.sql.SQLException {
        RechargeCard value = new RechargeCard();
        value.setId(rs.getLong("id"));
        value.setCardNo(rs.getString("card_no"));
        value.setCardPassword(rs.getString("card_password"));
        value.setPasswordHash(rs.getString("password_hash"));
        value.setAmount(rs.getBigDecimal("amount"));
        value.setStatus(rs.getInt("status"));
        return value;
    }

    private void insertUser(long id, String balance) {
        jdbc.update("INSERT INTO sys_user(id,balance,total_recharge) VALUES (?,?,0)", id, new BigDecimal(balance));
    }

    private BigDecimal balance(long id) {
        return jdbc.queryForObject("SELECT balance FROM sys_user WHERE id=?", BigDecimal.class, id);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }

    private List<Object> runConcurrently(ConcurrentSupplier operation) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                return operation.get();
            }));
        }
        ready.await();
        start.countDown();
        List<Object> values = new ArrayList<>();
        for (Future<Object> future : futures) {
            values.add(future.get());
        }
        return values;
    }

    private <T> List<Object> runConcurrentlyCapturingFailure(T[] inputs, ConcurrentConsumer<T> operation) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        for (T input : inputs) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    operation.accept(input);
                    return Boolean.TRUE;
                } catch (RuntimeException error) {
                    return error;
                }
            }));
        }
        ready.await();
        start.countDown();
        List<Object> values = new ArrayList<>();
        for (Future<Object> future : futures) {
            values.add(future.get());
        }
        return values;
    }

    @FunctionalInterface
    private interface ConcurrentSupplier { Object get() throws Exception; }

    @FunctionalInterface
    private interface ConcurrentConsumer<T> { void accept(T value) throws Exception; }
}
