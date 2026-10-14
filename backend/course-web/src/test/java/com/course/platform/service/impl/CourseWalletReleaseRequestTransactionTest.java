package com.course.platform.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.domain.entity.CourseWalletReleaseRequest;
import com.course.platform.infra.persistence.mapper.*;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 真实Mapper和事务验证申请不冻结、重复审批及中途失败的资金守恒。 */
class CourseWalletReleaseRequestTransactionTest {
    CourseReservedWalletTransactionTest f;
    CourseWalletReleaseRequestService service;
    CourseWalletReleaseRequestMapper requests;
    UserAuthorityMapper authorities;
    @BeforeEach void setup() throws Exception {
        f=new CourseReservedWalletTransactionTest();f.setup();f.deposit("200","initial");
        f.jdbc.execute("CREATE TABLE course_wallet_release_request(id BIGINT AUTO_INCREMENT PRIMARY KEY,wallet_id BIGINT,user_id BIGINT,amount DECIMAL(12,2),status VARCHAR(16),reason VARCHAR(255),request_key VARCHAR(64),operation_fingerprint CHAR(64),reviewed_by BIGINT,reviewed_at TIMESTAMP,review_reason VARCHAR(255),executed_transfer_id BIGINT,version INT,created_at TIMESTAMP,updated_at TIMESTAMP,UNIQUE(user_id,request_key),UNIQUE(executed_transfer_id))");
        var config=new MybatisConfiguration();config.setMapUnderscoreToCamelCase(true);config.addMapper(CourseWalletReleaseRequestMapper.class);config.addMapper(CourseReservedTransferMapper.class);
        var factory=new MybatisSqlSessionFactoryBean();factory.setDataSource(f.jdbc.getDataSource());factory.setConfiguration(config);
        var session=new SqlSessionTemplate(factory.getObject());requests=session.getMapper(CourseWalletReleaseRequestMapper.class);
        authorities=mock(UserAuthorityMapper.class);when(authorities.findAuthoritiesByUserId(8L)).thenReturn(List.of("course-wallet-release:review","course-wallet:release"));
        var target=new CourseWalletReleaseRequestService(requests,f.users,f.contracts,f.wallets,session.getMapper(CourseReservedTransferMapper.class),f.service,new CoursePrivateOperationsAccess(f.users,authorities),new CoursePrivateOperationsTime(f.clock));
        org.springframework.test.util.ReflectionTestUtils.setField(target,"enabled",true);
        var proxy=new ProxyFactory(target);proxy.addAdvice(new TransactionInterceptor(f.tx.getTransactionManager(),new AnnotationTransactionAttributeSource()));service=(CourseWalletReleaseRequestService)proxy.getProxy();auth(7);
    }
    @AfterEach void clear(){f.cleanup();}
    void auth(long id){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(id,null,id==8?List.of(new SimpleGrantedAuthority("course-wallet-release:review"),new SimpleGrantedAuthority("course-wallet:release")):List.of()));}
    CourseWalletReleaseRequest submit(String amount,String key){return service.submit(7L,5L,new BigDecimal(amount),key,"退出转回未用款");}
    @Test void submissionDoesNotFreezeMoveOrRechargeMoney(){var request=submit("100","one");assertEquals("PENDING",request.getStatus());f.money("200",f.balance("course_reserved_wallet",5));f.money("300",f.balance("sys_user",7));assertEquals(1,f.jdbc.queryForObject("SELECT COUNT(*) FROM account_ledger",Integer.class));}
    @Test void administrativeResponseUsesPublicUidAndOmitsInternalIdentity(){f.jdbc.update("UPDATE sys_user SET uid='00000000-0000-4000-8000-000000000007' WHERE id=7");var row=submit("100","one");var view=service.administrativeView(row);assertEquals("00000000-0000-4000-8000-000000000007",view.get("userUid"));assertEquals("100.00",view.get("amount"));assertFalse(view.containsKey("userId"));assertFalse(view.containsKey("operationFingerprint"));assertFalse(view.containsKey("reviewedBy"));}
    @Test void exactRetryReturnsOriginalButChangedCommandConflicts(){var request=submit("100","one");assertEquals(request.getId(),submit("100.00","one").getId());assertThrows(BusinessException.class,()->submit("101","one"));assertEquals(1,f.jdbc.queryForObject("SELECT COUNT(*) FROM course_wallet_release_request",Integer.class));}
    @Test void anotherUserCannotReadOrCancelForeignRequest(){var request=submit("100","one");auth(8);assertThrows(BusinessException.class,()->service.cancel(8L,request.getId()));assertEquals("PENDING",requests.selectById(request.getId()).getStatus());}
    @Test void cancelledRequestNeverMovesMoney(){var request=submit("100","one");service.cancel(7L,request.getId());auth(8);assertThrows(BusinessException.class,()->service.review(request.getId(),8L,true,"批准"));f.money("200",f.balance("course_reserved_wallet",5));}
    @Test void approvedRequestProducesExactlyOneLinkedTransfer(){var request=submit("100","one");auth(8);var approved=service.review(request.getId(),8L,true,"核验通过");assertEquals("EXECUTED",approved.getStatus());assertNotNull(approved.getExecutedTransferId());assertEquals(approved.getExecutedTransferId(),service.review(request.getId(),8L,true,"核验通过").getExecutedTransferId());f.money("100",f.balance("course_reserved_wallet",5));f.money("400",f.balance("sys_user",7));assertEquals(1,f.jdbc.queryForObject("SELECT COUNT(*) FROM course_reserved_transfer WHERE type='TRANSFER_OUT'",Integer.class));f.money("500",f.jdbc.queryForObject("SELECT total_recharge FROM sys_user WHERE id=7",BigDecimal.class));}
    @Test void rejectionWritesDecisionWithoutMoney(){var request=submit("100","one");auth(8);assertEquals("REJECTED",service.review(request.getId(),8L,false,"未核实").getStatus());f.money("200",f.balance("course_reserved_wallet",5));assertEquals(0,f.jdbc.queryForObject("SELECT COUNT(*) FROM course_reserved_transfer WHERE type='TRANSFER_OUT'",Integer.class));}
    @Test void spendingAfterSubmissionMakesApprovalFailAndRemainPending(){var request=submit("200","one");f.charge("paid-order");auth(8);assertThrows(BusinessException.class,()->service.review(request.getId(),8L,true,"批准"));assertEquals("PENDING",requests.selectById(request.getId()).getStatus());f.money("192.80",f.balance("course_reserved_wallet",5));f.money("300",f.balance("sys_user",7));}
    @Test void requestWriteFailureRollsBackBothLedgersAndTransfer(){var request=submit("100","one");f.jdbc.execute("ALTER TABLE course_wallet_release_request ADD CONSTRAINT reject_execution CHECK(status<>'EXECUTED')");auth(8);assertThrows(RuntimeException.class,()->service.review(request.getId(),8L,true,"批准"));assertEquals("PENDING",requests.selectById(request.getId()).getStatus());f.money("200",f.balance("course_reserved_wallet",5));f.money("300",f.balance("sys_user",7));assertEquals(0,f.jdbc.queryForObject("SELECT COUNT(*) FROM course_reserved_transfer WHERE type='TRANSFER_OUT'",Integer.class));}
    @Test void stalePermissionIsRejectedBeforeAnyTransfer(){var request=submit("100","one");auth(8);when(authorities.findAuthoritiesByUserId(8L)).thenReturn(List.of("course-wallet:release"));assertThrows(BusinessException.class,()->service.review(request.getId(),8L,true,"批准"));f.money("200",f.balance("course_reserved_wallet",5));}
    @Test void oldTransferCannotBeMisidentifiedAsNewApproval(){var request=submit("100","one");auth(8);f.tx.executeWithoutResult(s->f.service.release(5L,8L,f.request("100","release_"+request.getId())));assertThrows(BusinessException.class,()->service.review(request.getId(),8L,true,"批准"));assertEquals("PENDING",requests.selectById(request.getId()).getStatus());assertNull(requests.selectById(request.getId()).getExecutedTransferId());f.money("100",f.balance("course_reserved_wallet",5));}
    @Test void concurrentApprovalsHaveOneRealFinancialEffect() throws Exception {var request=submit("100","one");Runnable approve=()->{auth(8);try{service.review(request.getId(),8L,true,"批准");}finally{SecurityContextHolder.clearContext();}};var outcomes=f.concurrent(approve,approve);assertEquals(0,outcomes.stream().filter(RuntimeException.class::isInstance).count());f.money("100",f.balance("course_reserved_wallet",5));assertEquals(1,f.jdbc.queryForObject("SELECT COUNT(*) FROM course_reserved_transfer WHERE type='TRANSFER_OUT'",Integer.class));}
}
