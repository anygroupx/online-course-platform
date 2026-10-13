package com.course.platform.service.impl;
import com.course.platform.application.service.order.CourseOrderProgressLogService;
import com.course.platform.application.service.platform.PlatformDockingService;
import com.course.platform.application.service.support.OperationLogService;
import com.course.platform.domain.entity.*;
import com.course.platform.domain.dto.DockResult;
import com.course.platform.infra.persistence.mapper.*;
import com.course.platform.security.ResourceAuthorizationService;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 已退款订单不能因补单重新履约，外部HTTP前的占用必须独立提交。 */
class CourseRetryPaymentSafetyTest {
    @Test void cancelledOrderNeverCallsSupplier(){
        var orders=mock(CourseOrderMapper.class);var products=mock(CoursePlatformMapper.class);var providers=mock(ApiProviderMapper.class);var docking=mock(PlatformDockingService.class);
        var order=new CourseOrder();order.setId(11L);order.setUserId(7L);order.setPlatformId(9L);order.setOrderStatus(3);order.setRetryCount(0);
        var platform=new CoursePlatform();platform.setId(9L);platform.setDockApiId(3L);var provider=new ApiProvider();provider.setStatus(1);
        when(orders.selectById(11L)).thenReturn(order);when(products.selectById(9L)).thenReturn(platform);when(providers.selectById(3L)).thenReturn(provider);when(docking.retryOrder(order,platform,provider)).thenReturn(DockResult.success("成功","remote"));
        var service=new CourseOrderServiceImpl(orders,products,mock(UserMapper.class),mock(OperationLogService.class),docking,providers,mock(ApplicationEventPublisher.class),mock(AccountLedgerServiceImpl.class),mock(ResourceAuthorizationService.class),mock(CourseOrderProgressLogService.class),mock(CoursePricingService.class),mock(CourseReservedWalletService.class),mock(CourseOrderPaymentService.class));
        assertThrows(com.course.platform.common.exception.BusinessException.class,()->service.retryOrder(11L,7L));verifyNoInteractions(docking);
    }
    @Test void retryAndOrderNumberWrapperDoNotHoldTransactionAcrossHttp() throws Exception {
        for(var signature:new Object[][]{{"retryOrder",Long.class},{"retryOrderByOrderNo",String.class}}){var annotation=CourseOrderServiceImpl.class.getMethod((String)signature[0],(Class<?>)signature[1],Long.class).getAnnotation(Transactional.class);assertEquals(Propagation.NOT_SUPPORTED,annotation.propagation());}
    }
}
