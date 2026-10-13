package com.course.platform.service.impl;
import com.course.platform.application.service.order.CourseOrderProgressLogService;
import com.course.platform.application.service.platform.PlatformDockingService;
import com.course.platform.application.service.support.OperationLogService;
import com.course.platform.domain.dto.OrderCreateRequest;
import com.course.platform.domain.entity.*;
import com.course.platform.infra.persistence.mapper.*;
import com.course.platform.security.ResourceAuthorizationService;
import com.course.platform.common.exception.BusinessException;
import java.math.BigDecimal;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.springframework.context.ApplicationEventPublisher;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 下单资金来源、历史退款及报价冲突验证。 */
class CourseOrderPrivatePaymentTest {
    @Mock CourseOrderMapper orders;
    @Mock CoursePlatformMapper platforms;
    @Mock UserMapper users;
    @Mock OperationLogService logs;
    @Mock PlatformDockingService docking;
    @Mock ApiProviderMapper providers;
    @Mock ApplicationEventPublisher events;
    @Mock AccountLedgerServiceImpl general;
    @Mock ResourceAuthorizationService authorization;
    @Mock CourseOrderProgressLogService progress;
    @Mock CoursePricingService pricing;
    @Mock CourseReservedWalletService wallets;
    @Mock CourseOrderPaymentService payments;
    @InjectMocks CourseOrderServiceImpl service;
    AutoCloseable mocks;
    User user; CoursePlatform platform; OrderCreateRequest request;
    @BeforeEach void setup() {
        mocks=MockitoAnnotations.openMocks(this);
        user=new User();user.setId(7L);user.setStatus(1);user.setBalance(new BigDecimal("500"));
        platform=new CoursePlatform();platform.setId(9L);platform.setName("商品X");platform.setStatus(1);
        when(users.selectByIdForUpdate(7L)).thenReturn(user);when(users.selectById(7L)).thenReturn(user);
        when(pricing.requireUser(user)).thenReturn(user);when(pricing.requirePlatform(9L)).thenReturn(platform);
        when(orders.insert(any(CourseOrder.class))).thenAnswer(c->{c.<CourseOrder>getArgument(0).setId(11L);return 1;});
        when(orders.updateById(any(CourseOrder.class))).thenReturn(1);
        request=new OrderCreateRequest();request.setPlatformId(9L);request.setStudentAccount("student");request.setStudentPassword("secret");request.setCourseId("course");request.setCourseName("课程");
    }
    @AfterEach void cleanup() throws Exception{mocks.close();}
    void quote(boolean enough){when(pricing.decide(user,platform,true)).thenReturn(new CoursePricingService.PriceDecision(9L,new BigDecimal("7.20"),"PRIVATE","COURSE_WALLET",3L,2,5L,new BigDecimal("100"),enough,enough?null:"课程专户余额不足",null,null));}
    @Test void privateOrderSnapshotsPriceAndOnlyChargesWallet(){quote(true);assertEquals(11L,service.createOrder(request,7L));var order=ArgumentCaptor.forClass(CourseOrder.class);verify(wallets).charge(order.capture());assertNull(order.getValue().getRequestFingerprint());assertEquals("PRIVATE",order.getValue().getPriceSource());assertEquals("COURSE_WALLET",order.getValue().getPaymentSource());assertEquals(5L,order.getValue().getWalletId());assertEquals(2,order.getValue().getContractVersion());verifyNoInteractions(general);verify(events).publishEvent(any(com.course.platform.domain.event.OrderCreatedEvent.class));}
    @Test void insufficientWalletDoesNotUseGeneralFunds(){quote(false);assertThrows(BusinessException.class,()->service.createOrder(request,7L));verifyNoInteractions(general,wallets,events);verify(orders,never()).insert(any(CourseOrder.class));}
    @Test void stalePreviewVersionOrAmountIsRejected(){quote(true);request.setExpectedContractVersion(1);assertThrows(BusinessException.class,()->service.createOrder(request,7L));request.setExpectedContractVersion(2);request.setExpectedAmount(new BigDecimal("8"));assertThrows(BusinessException.class,()->service.createOrder(request,7L));verifyNoInteractions(general,wallets,events);}
    @Test void unchangedIdempotentRetryReturnsOriginalOrder(){quote(true);request.setClientRequestId("request-1");var saved=new CourseOrder();saved.setId(11L);saved.setRequestFingerprint(CourseOrderRequestIdentity.fingerprint(request));when(orders.selectByRequestKey(7L,"request-1")).thenReturn(saved);assertEquals(11L,service.createOrder(request,7L));verifyNoInteractions(general,wallets,events);}
    @Test void reusedRequestWithDifferentPayloadConflicts(){quote(true);request.setClientRequestId("request-1");var saved=new CourseOrder();saved.setId(11L);saved.setRequestFingerprint("other");when(orders.selectByRequestKey(7L,"request-1")).thenReturn(saved);assertThrows(BusinessException.class,()->service.createOrder(request,7L));verifyNoInteractions(general,wallets,events);}
    @Test void failedOrderInsertCannotDebit(){quote(true);when(orders.insert(any(CourseOrder.class))).thenReturn(0);assertThrows(BusinessException.class,()->service.createOrder(request,7L));verifyNoInteractions(general,wallets,events);}
    @Test void administratorCancellationRefundsOwnerSnapshot(){var order=new CourseOrder();order.setId(11L);order.setUserId(8L);order.setOrderNo("old");order.setOrderStatus(0);when(orders.selectById(11L)).thenReturn(order);when(orders.selectByIdForUpdate(11L)).thenReturn(order);service.cancelOrder(11L,7L);verify(users).selectByIdForUpdate(8L);verify(payments).refund(order,7L);verifyNoInteractions(general);}
}
