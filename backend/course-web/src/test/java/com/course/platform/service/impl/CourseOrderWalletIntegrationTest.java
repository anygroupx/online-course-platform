package com.course.platform.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.course.platform.application.service.order.*;
import com.course.platform.application.service.platform.PlatformDockingService;
import com.course.platform.application.service.support.OperationLogService;
import com.course.platform.application.service.course.CountdownConfigService;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.config.MyMetaObjectHandler;
import com.course.platform.controller.AdminOrderController;
import com.course.platform.domain.dto.*;
import com.course.platform.domain.entity.*;
import com.course.platform.domain.event.OrderCreatedEvent;
import com.course.platform.infra.persistence.mapper.*;
import com.course.platform.security.ResourceAuthorizationService;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.event.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 真实Mapper、事务代理、行锁及提交后事件的课程资金全链路回归。 */
class CourseOrderWalletIntegrationTest {
    CourseReservedWalletTransactionTest f;
    CourseOrderService orders;
    BatchOrderService batches;
    CourseOrderMapper orderMapper;
    PlatformDockingService docking;
    ApiProviderMapper providers;
    AnnotationConfigApplicationContext context;
    Probe probe;
    @BeforeEach void setup() throws Exception {
        f=new CourseReservedWalletTransactionTest();f.setup();
        var config=new MybatisConfiguration();config.setMapUnderscoreToCamelCase(true);
        for(var type:List.of(CourseOrderMapper.class,CoursePlatformMapper.class,PlatformCategoryMapper.class))config.addMapper(type);
        var factory=new MybatisSqlSessionFactoryBean();factory.setDataSource(f.jdbc.getDataSource());factory.setConfiguration(config);factory.setGlobalConfig(new GlobalConfig().setMetaObjectHandler(new MyMetaObjectHandler()));
        var session=new SqlSessionTemplate(factory.getObject());
        for(var type:List.of(CourseOrder.class,CoursePlatform.class,PlatformCategory.class))createTable(type);
        // 与038相同的快照/请求约束，旧API缺请求键时两字段必须同时为空。
        f.jdbc.execute("ALTER TABLE course_order ADD CONSTRAINT snapshot_source CHECK ((price_source='RATE' AND payment_source='GENERAL' AND private_contract_id IS NULL AND wallet_id IS NULL AND contract_version IS NULL) OR (price_source='PRIVATE' AND payment_source='COURSE_WALLET' AND private_contract_id IS NOT NULL AND wallet_id IS NOT NULL AND contract_version IS NOT NULL))");
        f.jdbc.execute("ALTER TABLE course_order ADD CONSTRAINT request_pair CHECK ((client_request_id IS NULL AND request_fingerprint IS NULL) OR (client_request_id IS NOT NULL AND request_fingerprint IS NOT NULL))");
        f.jdbc.execute("ALTER TABLE course_order ADD CONSTRAINT request_unique UNIQUE(user_id,client_request_id)");
        f.jdbc.update("INSERT INTO course_platform(id,name,status,base_price,rate_type,is_self_operated) VALUES(9,'商品X',1,10,'MULTIPLY',0),(10,'商品Y',1,10,'ADD',0)");
        f.jdbc.update("UPDATE sys_user SET rate=1.5,uid=CASE id WHEN 7 THEN '00000000-0000-4000-8000-000000000007' ELSE '00000000-0000-4000-8000-000000000008' END");
        f.jdbc.update("INSERT INTO sys_user(id,status,balance,total_recharge,rate) VALUES(11,1,100,100,1.5)");
        orderMapper=session.getMapper(CourseOrderMapper.class);
        var products=session.getMapper(CoursePlatformMapper.class);var pricing=new CoursePricingService(f.users,products,session.getMapper(PlatformCategoryMapper.class),f.contracts,f.wallets,f.clock);
        context=new AnnotationConfigApplicationContext();context.registerBean(TransactionalEventListenerFactory.class);context.registerBean(Probe.class);context.refresh();probe=context.getBean(Probe.class);
        var general=new AccountLedgerServiceImpl(f.users,extract(AccountLedgerMapper.class,"accountLedgerMapper",extract(AccountLedgerServiceImpl.class,"generalLedger",f.service)));
        docking=mock(PlatformDockingService.class);providers=mock(ApiProviderMapper.class);
        var service=new CourseOrderServiceImpl(orderMapper,products,f.users,mock(OperationLogService.class),docking,providers,context,general,new ResourceAuthorizationService(),mock(CourseOrderProgressLogService.class),pricing,f.service,new CourseOrderPaymentService(general,f.service,orderMapper));
        orders=proxy(service,CourseOrderService.class);batches=proxy(new BatchOrderServiceImpl(f.users,products,orders),BatchOrderService.class);auth(7);
    }
    @AfterEach void cleanup(){context.close();f.cleanup();}
    <T>T extract(Class<T> type,String name,Object bean) throws Exception {var field=bean.getClass().getDeclaredField(name);field.setAccessible(true);return type.cast(field.get(bean));}
    <T>T proxy(Object target,Class<T> type){var proxy=new ProxyFactory(target);proxy.addAdvice(new TransactionInterceptor(f.tx.getTransactionManager(),new AnnotationTransactionAttributeSource()));return type.cast(proxy.getProxy());}
    void createTable(Class<?> type){var info=TableInfoHelper.getTableInfo(type);var fields=new ArrayList<String>();fields.add(info.getKeyColumn()+" BIGINT AUTO_INCREMENT PRIMARY KEY");for(var field:info.getFieldList()){String column=field.getColumn();String sqlType=field.getPropertyType()==Integer.class?"INT":field.getPropertyType()==Long.class?"BIGINT":field.getPropertyType()==BigDecimal.class?"DECIMAL(12,2)":field.getPropertyType()==LocalDateTime.class?"TIMESTAMP":"VARCHAR(4096)";fields.add(column+" "+sqlType+(column.equals("is_deleted")?" DEFAULT 0":""));}f.jdbc.execute("CREATE TABLE "+info.getTableName()+"("+String.join(",",fields)+")");}
    void auth(long user){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,null,List.of()));}
    Authentication adminAuthentication(){
        var authentication=new UsernamePasswordAuthenticationToken(8L,null,List.of(new SimpleGrantedAuthority("order:update")));
        SecurityContextHolder.getContext().setAuthentication(authentication);return authentication;
    }
    AdminOrderController archiveController() throws Exception {
        // 归档路径保留真实订单/账户Mapper及两类退款服务，仅替换非资金协作者。
        var general=extract(AccountLedgerServiceImpl.class,"generalLedger",f.service);
        var controller=new AdminOrderController(orderMapper,f.users,mock(OperationLogService.class),mock(OrderCountdownService.class),mock(CountdownConfigService.class),mock(CountdownHistoryService.class),mock(OrderExportService.class),docking,general,new CourseOrderPaymentService(general,f.service,orderMapper));
        return proxy(controller,AdminOrderController.class);
    }
    void complete(Long id){f.jdbc.update("UPDATE course_order SET order_status=2 WHERE id=?",id);}
    int walletRefundCount(String orderNo){return f.jdbc.queryForObject("SELECT COUNT(*) FROM course_reserved_wallet_ledger WHERE wallet_id=5 AND user_id=7 AND biz_type='REFUND' AND biz_no=? AND direction=1",Integer.class,orderNo);}
    void archivedWithRefund(Long id){
        assertEquals(1,f.jdbc.queryForObject("SELECT is_deleted FROM course_order WHERE id=?",Integer.class,id));
        assertNotNull(f.jdbc.queryForObject("SELECT refunded_at FROM course_order WHERE id=?",java.sql.Timestamp.class,id));
        assertNull(orderMapper.selectById(id));
    }
    OrderCreateRequest request(String account,String key){var r=new OrderCreateRequest();r.setPlatformId(9L);r.setStudentAccount(account);r.setStudentPassword("不可暴露的口令");r.setCourseId("opaque-"+account);r.setCourseName("课程");r.setClientRequestId(key);return r;}
    @Test void legacyPrivateCreateAndCancellationRefundOnlyOriginalWallet(){f.deposit("200","in");Long id=orders.createOrder(request("one",null),7L);var order=orderMapper.selectById(id);assertEquals("PRIVATE",order.getPriceSource());assertNull(order.getRequestFingerprint());f.money("192.80",f.balance("course_reserved_wallet",5));f.money("300",f.balance("sys_user",7));assertEquals(List.of(id),probe.committed);
        f.jdbc.update("UPDATE course_private_contract SET fixed_price=20,status='TERMINATED' WHERE id=3");orders.cancelOrder(id,7L);assertNotNull(orderMapper.selectById(id).getRefundedAt());f.money("200",f.balance("course_reserved_wallet",5));f.money("300",f.balance("sys_user",7));assertThrows(BusinessException.class,()->orders.cancelOrder(id,7L));f.money("200",f.balance("course_reserved_wallet",5));}
    @Test void ordinaryOldApiStillUsesGeneralRateAndRefund(){auth(11);var request=request("ordinary",null);Long id=orders.createOrder(request,11L);var order=orderMapper.selectById(id);assertEquals("GENERAL",order.getPaymentSource());f.money("15",order.getAmount());f.money("85",f.balance("sys_user",11));orders.cancelOrder(id,11L);f.money("100",f.balance("sys_user",11));}
    @Test void sameRequestRetriesNeverDuplicateLedgerOrEvent(){f.deposit("200","in");var r=request("one","req");Long id=orders.createOrder(r,7L);assertEquals(id,orders.createOrder(r,7L));f.money("192.80",f.balance("course_reserved_wallet",5));assertEquals(1,probe.committed.size());r.setCourseName("变化");assertThrows(BusinessException.class,()->orders.createOrder(r,7L));}
    @Test void ledgerFailureRollsBackInsertedOrderAndNoEvent(){f.deposit("200","in");f.jdbc.execute("ALTER TABLE course_reserved_wallet_ledger ADD CONSTRAINT reject_order CHECK(biz_type<>'ORDER')");assertThrows(RuntimeException.class,()->orders.createOrder(request("one","req"),7L));f.money("200",f.balance("course_reserved_wallet",5));assertEquals(0,f.jdbc.queryForObject("SELECT COUNT(*) FROM course_order",Integer.class));assertTrue(probe.committed.isEmpty());}
    @Test void batchPartialFailureCommitsEarlierItemsInSeparateTransactions(){f.deposit("100","in");f.jdbc.update("UPDATE course_reserved_wallet SET balance=14.40 WHERE id=5");var batch=new BatchOrderRequest();batch.setPlatformId(9L);var items=new ArrayList<BatchOrderRequest.BatchOrderItem>();for(int i=0;i<3;i++){var item=new BatchOrderRequest.BatchOrderItem();item.setStudentAccount("student"+i);item.setStudentPassword("口令");item.setCourseName("课程");item.setClientRequestId("req"+i);items.add(item);}batch.setOrders(items);var result=batches.batchCreateDetailed(batch,7L);assertEquals(2,result.orderIds().size());assertEquals(3,result.items().size());assertFalse(result.items().get(2).success());assertEquals(2,probe.committed.size());assertEquals(2,f.jdbc.queryForObject("SELECT COUNT(*) FROM course_order",Integer.class));f.money("0",f.balance("course_reserved_wallet",5));}
    @Test void concurrentRequestAndLegacyDuplicateChecksCannotDoubleCharge() throws Exception{f.deposit("200","in");Runnable operation=()->{auth(7);try{orders.createOrder(request("same","same"),7L);}finally{SecurityContextHolder.clearContext();}};var results=f.concurrent(operation,operation);assertEquals(0,results.stream().filter(RuntimeException.class::isInstance).count());assertEquals(1,f.jdbc.queryForObject("SELECT COUNT(*) FROM course_order",Integer.class));f.money("192.80",f.balance("course_reserved_wallet",5));}
    @Test void quoteVersionChangeRejectsOldConfirmationBeforeAnyDebit(){f.deposit("200","in");var r=request("one","req");r.setExpectedContractVersion(0);assertThrows(BusinessException.class,()->orders.createOrder(r,7L));f.money("200",f.balance("course_reserved_wallet",5));assertEquals(0,f.jdbc.queryForObject("SELECT COUNT(*) FROM course_order",Integer.class));}
    @Test void genericOrderUpdateCannotRewriteFinancialSnapshots(){
        f.deposit("200","in");Long id=orders.createOrder(request("one",null),7L);var stale=new CourseOrder();stale.setId(id);stale.setAmount(new BigDecimal("99"));
        orderMapper.updateById(stale);f.money("7.20",orderMapper.selectById(id).getAmount());
    }
    @Test void refundMarkerFailureRollsBackOrderStateAndWalletCredit(){
        f.deposit("200","in");Long id=orders.createOrder(request("one",null),7L);f.jdbc.execute("ALTER TABLE course_order ADD CONSTRAINT reject_refund_mark CHECK(refunded_at IS NULL)");
        assertThrows(RuntimeException.class,()->orders.cancelOrder(id,7L));f.money("192.80",f.balance("course_reserved_wallet",5));assertEquals(0,orderMapper.selectById(id).getOrderStatus());
        assertEquals(0,f.jdbc.queryForObject("SELECT COUNT(*) FROM course_reserved_wallet_ledger WHERE biz_type='REFUND'",Integer.class));
    }
    @Test void secondRetryReadingClaimedStateCannotStartAnotherSupplierCall() throws Exception {
        f.deposit("200","in");f.jdbc.update("UPDATE course_platform SET dock_api_id=3 WHERE id=9");
        var provider=new ApiProvider();provider.setId(3L);provider.setStatus(1);when(providers.selectById(3L)).thenReturn(provider);
        Long id=orders.createOrder(request("one",null),7L);
        var entered=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);var calls=new java.util.concurrent.atomic.AtomicInteger();
        when(docking.retryOrder(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any())).thenAnswer(call->{
            assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
            if(calls.incrementAndGet()==1){entered.countDown();assertTrue(release.await(10,java.util.concurrent.TimeUnit.SECONDS));}
            return DockResult.success("成功","remote");
        });
        var first=f.threads.submit(()->{auth(7);try{orders.retryOrder(id,7L);}finally{SecurityContextHolder.clearContext();}});
        try {
            assertTrue(entered.await(10,java.util.concurrent.TimeUnit.SECONDS));assertEquals(1,orderMapper.selectById(id).getRetryCount());
            assertThrows(BusinessException.class,()->orders.retryOrder(id,7L));assertEquals(1,calls.get());
        } finally {release.countDown();}
        first.get(10,java.util.concurrent.TimeUnit.SECONDS);assertEquals(1,orderMapper.selectById(id).getRetryCount());
    }
    @Test void completedPrivateOrderAdminArchiveRefundsOnlyOriginalAgentWallet() throws Exception {
        f.deposit("200","archive-deposit");Long id=orders.createOrder(request("completed-private","archive-private"),7L);
        String orderNo=orderMapper.selectById(id).getOrderNo();complete(id);
        // 后续协议改价和终止不影响已完成订单的退款金额或原专户来源。
        f.jdbc.update("UPDATE course_private_contract SET fixed_price=99,status='TERMINATED' WHERE id=3");
        assertEquals(1,archiveController().deleteOrder(id,"核查完成后归档",adminAuthentication()).getCode());
        f.money("200",f.balance("course_reserved_wallet",5));f.money("300",f.balance("sys_user",7));f.money("500",f.balance("sys_user",8));
        assertEquals(1,walletRefundCount(orderNo));
        f.money("7.20",f.jdbc.queryForObject("SELECT amount FROM course_reserved_wallet_ledger WHERE biz_type='REFUND' AND biz_no=?",BigDecimal.class,orderNo));
        assertEquals(8L,f.jdbc.queryForObject("SELECT operator_id FROM course_reserved_wallet_ledger WHERE biz_type='REFUND' AND biz_no=?",Long.class,orderNo));
        assertEquals(0,f.jdbc.queryForObject("SELECT COUNT(*) FROM account_ledger WHERE biz_type='REFUND'",Integer.class));archivedWithRefund(id);
    }
    @Test void duplicateIdsAndRepeatedAdminArchiveCannotRefundTwice() throws Exception {
        f.deposit("200","duplicate-archive-deposit");Long id=orders.createOrder(request("duplicate-archive","archive-duplicate"),7L);
        String orderNo=orderMapper.selectById(id).getOrderNo();complete(id);
        var admin=archiveController();var authentication=adminAuthentication();
        assertEquals(1,admin.batchDeleteOrders(List.of(id,id),"重复选择只处理一次",authentication).getCode());
        assertThrows(BusinessException.class,()->admin.batchDeleteOrders(List.of(id,id),"重复批量请求",authentication));
        assertThrows(BusinessException.class,()->admin.deleteOrder(id,"重复单笔请求",authentication));
        f.money("200",f.balance("course_reserved_wallet",5));f.money("300",f.balance("sys_user",7));f.money("500",f.balance("sys_user",8));
        assertEquals(1,walletRefundCount(orderNo));assertEquals(0,f.jdbc.queryForObject("SELECT COUNT(*) FROM account_ledger WHERE biz_type='REFUND'",Integer.class));archivedWithRefund(id);
    }
    @Test void mixedLegacyOrdinaryAndPrivateAdminBatchArchiveRefundEachOriginalSource() throws Exception {
        f.deposit("200","mixed-archive-deposit");Long privateId=orders.createOrder(request("mixed-private","archive-mixed"),7L);
        String privateNo=orderMapper.selectById(privateId).getOrderNo();complete(privateId);
        // 模拟升级前已支付旧订单：历史金额15元、普通来源，无新请求键、协议或钱包ID。
        f.jdbc.update("INSERT INTO course_order(order_no,user_id,platform_id,platform_name,student_account,course_name,amount,price_source,payment_source,order_status,dock_status,is_deleted,create_time) VALUES('legacy-ordinary-archive',11,9,'商品X','legacy-student','历史课程',15,'RATE','GENERAL',2,1,0,'2025-01-17 12:00:00')");
        f.jdbc.update("UPDATE sys_user SET balance=85,rate=99 WHERE id=11");
        Long legacyId=f.jdbc.queryForObject("SELECT id FROM course_order WHERE order_no='legacy-ordinary-archive'",Long.class);
        var legacy=orderMapper.selectById(legacyId);assertNull(legacy.getClientRequestId());assertNull(legacy.getWalletId());assertNull(legacy.getPrivateContractId());
        assertEquals(0,f.jdbc.queryForObject("SELECT COUNT(*) FROM account_ledger WHERE biz_no='legacy-ordinary-archive'",Integer.class));
        assertEquals(1,archiveController().batchDeleteOrders(List.of(legacyId,privateId),"核查混合资金来源后归档",adminAuthentication()).getCode());
        f.money("200",f.balance("course_reserved_wallet",5));f.money("300",f.balance("sys_user",7));f.money("100",f.balance("sys_user",11));f.money("500",f.balance("sys_user",8));
        assertEquals(1,walletRefundCount(privateNo));assertEquals(1,f.jdbc.queryForObject("SELECT COUNT(*) FROM account_ledger WHERE user_id=11 AND biz_type='REFUND' AND biz_no='legacy-ordinary-archive' AND direction=1",Integer.class));
        f.money("15",f.jdbc.queryForObject("SELECT amount FROM account_ledger WHERE user_id=11 AND biz_type='REFUND' AND biz_no='legacy-ordinary-archive'",BigDecimal.class));
        assertEquals(0,f.jdbc.queryForObject("SELECT COUNT(*) FROM account_ledger WHERE user_id IN(7,8) AND biz_type='REFUND'",Integer.class));
        archivedWithRefund(privateId);archivedWithRefund(legacyId);
    }
    @Test void pendingAdminArchiveKeepsExistingCompletedOnlyRefundPolicy() throws Exception {
        f.deposit("200","pending-archive-deposit");Long id=orders.createOrder(request("pending-archive","archive-pending"),7L);
        assertEquals(0,orderMapper.selectById(id).getOrderStatus());
        assertEquals(1,archiveController().deleteOrder(id,"未完成订单按原归档策略处理",adminAuthentication()).getCode());
        f.money("192.80",f.balance("course_reserved_wallet",5));f.money("300",f.balance("sys_user",7));f.money("500",f.balance("sys_user",8));
        assertEquals(0,f.jdbc.queryForObject("SELECT COUNT(*) FROM course_reserved_wallet_ledger WHERE biz_type='REFUND'",Integer.class));
        assertEquals(1,f.jdbc.queryForObject("SELECT is_deleted FROM course_order WHERE id=?",Integer.class,id));assertNull(f.jdbc.queryForObject("SELECT refunded_at FROM course_order WHERE id=?",java.sql.Timestamp.class,id));
    }
    static class Probe {List<Long> committed=new java.util.concurrent.CopyOnWriteArrayList<>();@TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT) public void onCreated(OrderCreatedEvent event){committed.add(event.getOrderId());}}
}
