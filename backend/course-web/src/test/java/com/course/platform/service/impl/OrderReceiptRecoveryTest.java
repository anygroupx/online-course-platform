package com.course.platform.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.course.platform.application.service.orderreceipt.OrderReceiptGateway;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.security.SecretCrypto;
import com.course.platform.domain.orderreceipt.OrderReceiptRecovery;
import com.course.platform.domain.orderreceipt.OrderReceiptTypes.*;
import com.course.platform.domain.servicecommerce.ServiceTime;
import com.course.platform.infra.http.ProviderUrlNormalizer;
import com.course.platform.infra.persistence.mapper.*;
import com.course.platform.security.UserAuthorityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class OrderReceiptRecoveryTest {
    static final String SECRET = "receipt-test-only-master-key-32-characters";
    static final String NOTE = "已根据原始订单回执核实客户、商品及原执行账户归属";
    JdbcTemplate jdbc;
    OrderReceiptRecoveryServiceImpl service;
    OrderReceiptRecoveryMapper rows;
    OrderReceiptGateway gateway;
    UserAuthorityService authorities;
    ValidatorFactory validators;
    ExecutorService threads;
    final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @BeforeEach void setup() throws Exception {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:receipt_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000","sa","");
        jdbc = new JdbcTemplate(ds);
        Path root = Path.of("").toAbsolutePath(); while (!Files.exists(root.resolve("database/schema.sql"))) root=root.getParent();
        String schema = Files.readString(root.resolve("database/schema.sql"));
        for (String table:List.of("sys_user","api_provider","course_platform","course_order")) {
            var match=Pattern.compile("CREATE TABLE `"+table+"` \\(.*?;",Pattern.DOTALL).matcher(schema);
            assertTrue(match.find());
            jdbc.execute(match.group().replaceAll("KEY `([^`]+)`", "KEY `"+table+"_$1`").replaceAll("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4.*?;",";"));
        }
        jdbc.execute("ALTER TABLE course_platform ADD COLUMN category_id BIGINT");
        String migration=Files.readString(root.resolve("database/migrations/028_course_order_receipt_recovery.sql"))
                .replaceAll("(?m)^--.*$","").replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4","");
        for (String sql:migration.split(";")) if(!sql.isBlank())jdbc.execute(sql);
        jdbc.update("INSERT INTO sys_user(id,uid,username,password,balance,status) VALUES(7,'u7','admin','hash',100,1),(8,'u8','other','hash',200,1),(9,'u9','owner','hash',300,1)");
        jdbc.update("INSERT INTO api_provider(id,provider_type,name,api_url,username,api_key,status,config_version,verified_at) VALUES(9,'27','receipt','https://receipt.invalid','saved-user',?,1,2,CURRENT_TIMESTAMP)",SecretCrypto.encrypt("saved-private-key",SECRET));
        jdbc.update("INSERT INTO course_platform(id,name,dock_api_id,dock_param,base_price) VALUES(1,'product',9,'product-2',2.50)");
        order(1,9,"student");
        var config=new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true);
        for(var mapper:List.of(OrderReceiptRecoveryMapper.class,CourseOrderMapper.class,CoursePlatformMapper.class,ApiProviderMapper.class,UserMapper.class))config.addMapper(mapper);
        var factory=new MybatisSqlSessionFactoryBean();factory.setDataSource(ds);factory.setConfiguration(config);
        var sql=new SqlSessionTemplate(factory.getObject()); rows=spy(sql.getMapper(OrderReceiptRecoveryMapper.class));
        authorities=mock(UserAuthorityService.class);
        when(authorities.loadAuthorities(anyLong())).thenReturn(List.of(new SimpleGrantedAuthority("order:update"),new SimpleGrantedAuthority("api-provider:update")));
        gateway=mock(OrderReceiptGateway.class);
        when(gateway.verify(any(),any(),any(),anyString())).thenAnswer(a->{
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM course_order_receipt_recovery WHERE state='READING'",Integer.class)>0);
            assertEquals("saved-private-key",((com.course.platform.domain.entity.ApiProvider)a.getArgument(0)).getApiKey());
            return new Verified(a.getArgument(3));
        });
        validators=Validation.buildDefaultValidatorFactory();
        service=new OrderReceiptRecoveryServiceImpl(rows,sql.getMapper(CourseOrderMapper.class),sql.getMapper(CoursePlatformMapper.class),sql.getMapper(ApiProviderMapper.class),sql.getMapper(UserMapper.class),authorities,gateway,new ProviderUrlNormalizer(),validators.getValidator(),new DataSourceTransactionManager(ds));
        ReflectionTestUtils.setField(service,"enabled",true);ReflectionTestUtils.setField(service,"cryptoSecret",SECRET);
        auth(7,"order:update","api-provider:update");threads=Executors.newFixedThreadPool(2);
    }
    @AfterEach void close(){SecurityContextHolder.clearContext();validators.close();threads.shutdownNow();}
    void auth(long user,String... perms){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,null,Arrays.stream(perms).map(SimpleGrantedAuthority::new).toList()));}
    void order(long id,long provider,String account){jdbc.update("INSERT INTO course_order(id,order_no,user_id,platform_id,platform_name,api_provider_id,student_account,student_password,course_name,course_id,amount,order_status,dock_status) VALUES(?, ?,9,1,'product',?,?,'private-student-password','exact course','student-course',2.50,4,2)",id,"ORD-"+id,provider,account);}
    PreviewForm form(){return new PreviewForm(UUID.randomUUID().toString(),"receipt-9",NOTE,true);}
    View ready(){var view=service.preview(1,form());assertEquals("READY",view.state());return view;}
    String receipt(long order){return jdbc.queryForObject("SELECT third_order_id FROM course_order WHERE id=?",String.class,order);}
    void noFunds(){assertEquals("300.00",jdbc.queryForObject("SELECT balance FROM sys_user WHERE id=9",java.math.BigDecimal.class).toPlainString());assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_name='ACCOUNT_LEDGER'",Integer.class));}

    @Test void exactReceiptPreviewIsDurablePrivateAndDoesNotMutateTheOrder() throws Exception {
        var before=jdbc.queryForMap("SELECT * FROM course_order WHERE id=1");var view=ready();
        assertEquals(before,jdbc.queryForMap("SELECT * FROM course_order WHERE id=1"));
        var row=rows.selectById(view.id());assertTrue(SecretCrypto.isEncrypted(row.getEvidenceEncrypted()));
        assertTrue(SecretCrypto.decrypt(row.getEvidenceEncrypted(),SECRET).contains(NOTE));
        String body=json.writeValueAsString(view);assertFalse(body.contains(NOTE));assertFalse(body.contains("private"));assertFalse(body.contains("sourceIdentity"));
        assertFalse(row.toString().contains(NOTE));assertTrue(SecretCrypto.isEncrypted(jdbc.queryForObject("SELECT api_key FROM api_provider WHERE id=9",String.class)));noFunds();
    }
    @Test void confirmAssociatesOnlyReceiptAndAuditWithoutStatusProgressOrFinancialWrites(){
        var before=jdbc.queryForMap("SELECT * FROM course_order WHERE id=1");var view=ready();
        assertEquals("APPLIED",service.confirm(1,view.id(),new ConfirmForm(true)).state());
        assertEquals("receipt-9",receipt(1));var after=jdbc.queryForMap("SELECT * FROM course_order WHERE id=1");
        for(String key:before.keySet())if(!Set.of("THIRD_ORDER_ID","UPDATE_TIME").contains(key))assertEquals(before.get(key),after.get(key),key);
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM course_order_receipt_claim",Integer.class));
        verify(gateway,times(1)).verify(any(),any(),any(),anyString());noFunds();
    }
    @Test void sameRequestReplaysOnlyOriginalResultAndNeverRepeatsRead(){
        var form=form();var first=service.preview(1,form);assertEquals(first.id(),service.preview(1,form).id());
        service.confirm(1,first.id(),new ConfirmForm(true));assertEquals("APPLIED",service.preview(1,form).state());
        assertEquals("APPLIED",service.confirm(1,first.id(),new ConfirmForm(true)).state());
        verify(gateway,times(1)).verify(any(),any(),any(),anyString());assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM course_order_receipt_claim",Integer.class));
    }
    @Test void reusedRequestWithChangedReceiptOrEvidenceIsRejected(){
        var f=form();service.preview(1,f);
        for(var altered:List.of(new PreviewForm(f.requestId(),"other",NOTE,true),new PreviewForm(f.requestId(),f.receiptId(),NOTE+"changed",true)))assertThrows(BusinessException.class,()->service.preview(1,altered));
        verify(gateway,times(1)).verify(any(),any(),any(),anyString());assertNull(receipt(1));
    }
    @Test void everyServiceCallRequiresActiveActorAndBothCurrentDatabasePermissions(){
        for(String permission:List.of("ROLE_SUPER_ADMIN","order:update","api-provider:update")){
            auth(7,permission);assertThrows(BusinessException.class,()->service.preview(1,form()));
        }
        auth(7,"order:update","api-provider:update");when(authorities.loadAuthorities(7L)).thenReturn(List.of(new SimpleGrantedAuthority("order:update")));
        assertThrows(BusinessException.class,()->service.preview(1,form()));verifyNoInteractions(gateway);
        when(authorities.loadAuthorities(7L)).thenReturn(List.of(new SimpleGrantedAuthority("order:update"),new SimpleGrantedAuthority("api-provider:update")));
        var view=ready();jdbc.update("UPDATE sys_user SET status=0 WHERE id=7");
        assertThrows(BusinessException.class,()->service.get(1,view.id()));assertThrows(BusinessException.class,()->service.confirm(1,view.id(),new ConfirmForm(true)));assertNull(receipt(1));
    }
    @Test void anotherAdminCannotReadOrConfirmActorScopedReceiptEvenWithBothPermissions(){
        var view=ready();auth(8,"order:update","api-provider:update");
        assertThrows(BusinessException.class,()->service.get(1,view.id()));assertThrows(BusinessException.class,()->service.confirm(1,view.id(),new ConfirmForm(true)));assertNull(receipt(1));
    }
    @Test void previewOwnershipAndFinalAssociationNeedSeparateExplicitConsent(){
        var f=form();assertThrows(BusinessException.class,()->service.preview(1,new PreviewForm(f.requestId(),f.receiptId(),NOTE,false)));
        verifyNoInteractions(gateway);var view=ready();assertThrows(BusinessException.class,()->service.confirm(1,view.id(),new ConfirmForm(false)));assertNull(receipt(1));
    }
    @Test void malformedInputsFailBeforeQueryOrDatabaseDraft(){
        for(var f:List.of(new PreviewForm("bad","receipt-9",NOTE,true),new PreviewForm(UUID.randomUUID().toString(),"../id",NOTE,true),new PreviewForm(UUID.randomUUID().toString(),"ok","short",true),new PreviewForm(UUID.randomUUID().toString(),"ok",NOTE+"\n",true))){
            assertThrows(BusinessException.class,()->service.preview(1,f));
        }
        assertThrows(BusinessException.class,()->service.preview(0,form()));verifyNoInteractions(gateway);
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM course_order_receipt_recovery",Integer.class));
    }
    @Test void featureDisabledDoesNotQueryRecoveryTablesOrSupplier(){
        ReflectionTestUtils.setField(service,"enabled",false);jdbc.execute("DROP TABLE course_order_receipt_claim");jdbc.execute("DROP TABLE course_order_receipt_recovery");
        assertThrows(BusinessException.class,()->service.preview(1,form()));assertThrows(BusinessException.class,()->service.get(1,UUID.randomUUID().toString()));verifyNoInteractions(gateway);
    }
    @Test void readFailureIsRecordedAndSameRequestNeverReissuesHttp(){
        doThrow(new IllegalStateException("sensitive response")).when(gateway).verify(any(),any(),any(),anyString());var f=form();
        assertEquals("READ_FAILED",service.preview(1,f).state());assertEquals("READ_FAILED",service.preview(1,f).state());
        assertEquals("READ_FAILED",service.confirm(1,f.requestId(),new ConfirmForm(true)).state());assertNull(receipt(1));verify(gateway,times(1)).verify(any(),any(),any(),anyString());
    }
    @Test void wrongReturnedReceiptCannotBecomeReady(){
        doReturn(new Verified("other")).when(gateway).verify(any(),any(),any(),anyString());
        var view=service.preview(1,form());assertEquals("READ_FAILED",view.state());assertNull(receipt(1));
    }
    @Test void noAcceptanceWhenOrderChangesDuringTheRead(){
        doAnswer(a->{jdbc.update("UPDATE course_order SET student_account='changed' WHERE id=1");return new Verified(a.getArgument(3));}).when(gateway).verify(any(),any(),any(),anyString());
        assertEquals("CONFLICT",service.preview(1,form()).state());assertNull(receipt(1));
    }
    @Test void changedOwnerCredentialsPriceBindingAndStatusInvalidateConfirmation(){
        for(String change:List.of("user_id=8","student_password='changed'","amount=3.50","order_status=3","dock_status=0","third_order_id='preexisting'","is_deleted=1")){
            var view=ready();jdbc.update("UPDATE course_order SET "+change+" WHERE id=1");
            assertEquals("CONFLICT",service.confirm(1,view.id(),new ConfirmForm(true)).state(),change);
            jdbc.update("DELETE FROM course_order_receipt_recovery");jdbc.update("DELETE FROM course_order");order(1,9,"student");
        }
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM course_order_receipt_claim",Integer.class));
    }
    @Test void changedProviderOrProductInvalidatesConfirmationWithoutHttp(){
        for(String sql:List.of("UPDATE api_provider SET config_version=config_version+1 WHERE id=9","UPDATE api_provider SET api_key='rotated' WHERE id=9","UPDATE api_provider SET api_url='https://changed.invalid' WHERE id=9","UPDATE course_platform SET dock_param='different' WHERE id=1")){
            var view=ready();jdbc.update(sql);assertEquals("CONFLICT",service.confirm(1,view.id(),new ConfirmForm(true)).state());
            jdbc.update("UPDATE api_provider SET api_url='https://receipt.invalid',api_key=? WHERE id=9",SecretCrypto.encrypt("saved-private-key",SECRET));
            jdbc.update("UPDATE course_platform SET dock_param='product-2' WHERE id=1");
        }
        assertNull(receipt(1));
    }
    @Test void inactiveUnverifiedOtherTypeSelfOperatedAndPendingOrdersNeverQuery(){
        for(String sql:List.of("UPDATE api_provider SET status=0 WHERE id=9","UPDATE api_provider SET verified_at=NULL WHERE id=9","UPDATE api_provider SET provider_type='flash' WHERE id=9","UPDATE course_order SET is_self_operated=1 WHERE id=1","UPDATE course_order SET dock_status=0 WHERE id=1","UPDATE course_order SET order_status=8 WHERE id=1")){
            jdbc.update(sql);assertThrows(BusinessException.class,()->service.preview(1,form()));
            jdbc.update("UPDATE api_provider SET status=1,verified_at=CURRENT_TIMESTAMP,provider_type='27' WHERE id=9");jdbc.update("UPDATE course_order SET is_self_operated=0,dock_status=2,order_status=4 WHERE id=1");
        }
        verifyNoInteractions(gateway);
    }
    @Test void duplicateLocalFullIdentityEvenArchivedCannotBeGuessed(){
        order(2,9,"student");jdbc.update("UPDATE course_order SET is_deleted=1 WHERE id=2");
        assertThrows(BusinessException.class,()->service.preview(1,form()));verifyNoInteractions(gateway);
    }
    void alias(){jdbc.update("INSERT INTO api_provider(id,provider_type,name,api_url,username,api_key,status,config_version,verified_at) VALUES(10,'27','alias','HTTPS://RECEIPT.INVALID/','saved-user',?,1,2,CURRENT_TIMESTAMP)",SecretCrypto.encrypt("saved-private-key",SECRET));}
    @Test void boundReceiptAcrossCanonicalSourceAliasesCannotBeReused(){
        alias();order(2,10,"different student");jdbc.update("UPDATE course_order SET third_order_id='receipt-9',is_deleted=1 WHERE id=2");
        assertThrows(BusinessException.class,()->service.preview(1,form()));verifyNoInteractions(gateway);
    }
    @Test void expirationAndInterruptedReadNeverAssociate(){
        var view=ready();jdbc.update("UPDATE course_order_receipt_recovery SET expires_at='2000-01-01' WHERE id=?",view.id());
        assertEquals("EXPIRED",service.confirm(1,view.id(),new ConfirmForm(true)).state());
        jdbc.update("UPDATE course_order_receipt_recovery SET state='READING' WHERE id=?",view.id());assertEquals("INTERRUPTED",service.get(1,view.id()).state());assertNull(receipt(1));
    }
    @Test void confirmationExpiringDuringLockWaitNeverAssociates() {
        var view = ready();
        var now = new java.util.concurrent.atomic.AtomicReference<>(view.expiresAt().minusSeconds(1));
        try (var clock = mockStatic(ServiceTime.class)) {
            clock.when(ServiceTime::now).thenAnswer(a -> now.get());
            doAnswer(a -> { now.set(view.expiresAt().plusSeconds(1)); return a.callRealMethod(); })
                    .when(rows).lockProvider(9L);
            assertEquals("EXPIRED", service.confirm(1, view.id(), new ConfirmForm(true)).state());
        }
        assertNull(receipt(1));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM course_order_receipt_claim", Integer.class));
        verify(gateway, times(1)).verify(any(), any(), any(), anyString());
        noFunds();
    }
    @Test void previewExpiringDuringFinalLockWaitCannotBecomeReady() {
        var form = form();
        var now = new java.util.concurrent.atomic.AtomicReference<>(ServiceTime.now());
        try (var clock = mockStatic(ServiceTime.class)) {
            clock.when(ServiceTime::now).thenAnswer(a -> now.get());
            doAnswer(a -> {
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                var row = rows.selectById(form.requestId());
                assertEquals("READING", row.getState());
                doAnswer(lock -> { now.set(row.getExpiresAt().plusSeconds(1)); return lock.callRealMethod(); })
                        .when(rows).lockProvider(9L);
                return new Verified(form.receiptId());
            }).when(gateway).verify(any(), any(), any(), anyString());
            assertEquals("INTERRUPTED", service.preview(1, form).state());
            assertEquals("INTERRUPTED", service.confirm(1, form.requestId(), new ConfirmForm(true)).state());
        }
        assertNull(receipt(1));
        verify(gateway, times(1)).verify(any(), any(), any(), anyString());
        noFunds();
    }
    @Test void evidenceLookingLikeCiphertextIsStillEncryptedAsUntrustedPlainInput(){
        var f=form();service.preview(1,new PreviewForm(f.requestId(),f.receiptId(),"ENC:v1:untrusted-marker-not-real-cipher",true));
        String encrypted=rows.selectById(f.requestId()).getEvidenceEncrypted();assertNotEquals("ENC:v1:untrusted-marker-not-real-cipher",encrypted);
        assertTrue(SecretCrypto.decrypt(encrypted,SECRET).contains("untrusted-marker"));
    }
    @Test void samePreviewConfirmedConcurrentlyCreatesExactlyOneClaim() throws Exception {
        var view=ready();CountDownLatch start=new CountDownLatch(1);List<Future<View>> tasks=new ArrayList<>();
        for(int i=0;i<2;i++)tasks.add(threads.submit(()->{auth(7,"order:update","api-provider:update");start.await();try{return service.confirm(1,view.id(),new ConfirmForm(true));}finally{SecurityContextHolder.clearContext();}}));
        start.countDown();for(var task:tasks)assertEquals("APPLIED",task.get(20,TimeUnit.SECONDS).state());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM course_order_receipt_claim",Integer.class));verify(gateway,times(1)).verify(any(),any(),any(),anyString());
    }
    @Test void twoReadyOrdersCannotClaimSameReceiptConcurrently() throws Exception {
        order(2,9,"second student");var a=ready();var b=service.preview(2,form());assertEquals("READY",b.state());
        CountDownLatch start=new CountDownLatch(1);List<Future<String>> tasks=new ArrayList<>();
        for(var v:List.of(a,b))tasks.add(threads.submit(()->{auth(7,"order:update","api-provider:update");start.await();try{return service.confirm(v.orderId(),v.id(),new ConfirmForm(true)).state();}finally{SecurityContextHolder.clearContext();}}));
        start.countDown();Set<String> outcomes=new HashSet<>();for(var task:tasks)outcomes.add(task.get(20,TimeUnit.SECONDS));assertEquals(Set.of("APPLIED","CONFLICT"),outcomes);
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM course_order WHERE third_order_id='receipt-9'",Integer.class));noFunds();
    }
    @Test void uniqueClaimFailureRollsBackAssociationAndRecordsConflict(){
        var view=ready();doThrow(new DuplicateKeyException("private constraint detail")).when(rows).claim(any());
        assertEquals("CONFLICT",service.confirm(1,view.id(),new ConfirmForm(true)).state());assertNull(receipt(1));noFunds();
    }
    @Test void failedAuditUpdateRollsBackBothBindingAndClaim(){
        var view=ready();doAnswer(a->{var row=(OrderReceiptRecovery)a.getArgument(0);if("APPLIED".equals(row.getState()))throw new IllegalStateException("local persistence failure");return a.callRealMethod();}).when(rows).updateById(any(OrderReceiptRecovery.class));
        assertThrows(IllegalStateException.class,()->service.confirm(1,view.id(),new ConfirmForm(true)));assertNull(receipt(1));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM course_order_receipt_claim",Integer.class));
        assertEquals("READY",service.get(1,view.id()).state());verify(gateway,times(1)).verify(any(),any(),any(),anyString());
    }
    @Test void recentRequestsAreBoundedAndActorOrderScopedWithoutRepeatingQueries() {
        var first=ready();
        assertEquals(List.of(first.id()),service.recent(1).stream().map(View::id).toList());
        assertTrue(service.recent(2).isEmpty());auth(8,"order:update","api-provider:update");assertTrue(service.recent(1).isEmpty());
        verify(gateway,times(1)).verify(any(),any(),any(),anyString());
    }
    @Test void databasePermissionRevocationBetweenPreviewAndConfirmStopsAssociation() {
        var view=ready();when(authorities.loadAuthorities(7L)).thenReturn(List.of());
        assertThrows(BusinessException.class,()->service.confirm(1,view.id(),new ConfirmForm(true)));
        assertNull(receipt(1));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM course_order_receipt_claim",Integer.class));
    }

}
