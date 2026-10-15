package com.course.platform.listener;
import com.course.platform.domain.entity.*;
import com.course.platform.domain.event.OrderCreatedEvent;
import com.course.platform.domain.dto.DockResult;
import com.course.platform.application.service.platform.*;
import com.course.platform.infra.persistence.mapper.*;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** 供应商提交必须先原子占用订单，取消成功后禁止继续履约。 */
class CourseOrderSubmissionSafetyTest {
    @Test void permanentlyDisabledLocalAutoStudyDoesNotTrapNewOrderInProcessing(){
        var orders=mock(CourseOrderMapper.class);var order=new CourseOrder();order.setId(1L);order.setOrderStatus(0);when(orders.selectById(1L)).thenReturn(order);
        var local=mock(com.course.platform.service.impl.AqksStudyServiceImpl.class,CALLS_REAL_METHODS);
        new OrderEventListener(orders,mock(CoursePlatformMapper.class),mock(ApiProviderMapper.class),mock(PlatformDockingService.class),local).handleOrderCreated(new OrderCreatedEvent(this,1L,9L,1));
        verify(orders,never()).claimSubmission(anyLong(),anyInt(),anyInt(),anyInt());org.junit.jupiter.api.Assertions.assertEquals(0,order.getOrderStatus());
    }
    @Test void cancellationWinsClaimSoSupplierIsNeverCalled(){
        var orders=mock(CourseOrderMapper.class);var products=mock(CoursePlatformMapper.class);var providers=mock(ApiProviderMapper.class);var docking=mock(PlatformDockingService.class);
        var order=new CourseOrder();order.setId(1L);order.setPlatformId(9L);order.setOrderStatus(0);order.setDockStatus(0);
        var product=new CoursePlatform();product.setId(9L);product.setDockApiId(3L);var provider=new ApiProvider();provider.setStatus(1);
        when(orders.selectById(1L)).thenReturn(order);when(products.selectById(9L)).thenReturn(product);when(providers.selectById(3L)).thenReturn(provider);
        when(orders.claimSubmission(1L,0,1,0)).thenReturn(0);
        new OrderEventListener(orders,products,providers,docking,mock(AqksStudyService.class)).handleOrderCreated(new OrderCreatedEvent(this,1L,9L,0));
        verify(orders).claimSubmission(1L,0,1,0);verifyNoInteractions(docking);
    }
    @Test void networkUncertaintyKeepsOrderInNonRefundableProcessingState(){
        var orders=mock(CourseOrderMapper.class);var products=mock(CoursePlatformMapper.class);var providers=mock(ApiProviderMapper.class);var docking=mock(PlatformDockingService.class);
        var order=new CourseOrder();order.setId(1L);order.setPlatformId(9L);order.setOrderStatus(0);order.setDockStatus(0);
        var product=new CoursePlatform();product.setId(9L);product.setDockApiId(3L);var provider=new ApiProvider();provider.setStatus(1);
        when(orders.selectById(1L)).thenReturn(order);when(products.selectById(9L)).thenReturn(product);when(providers.selectById(3L)).thenReturn(provider);
        when(orders.claimSubmission(1L,0,1,0)).thenReturn(1);when(docking.dockOrder(order,product,provider)).thenThrow(new RuntimeException("模拟网络中断"));
        new OrderEventListener(orders,products,providers,docking,mock(AqksStudyService.class)).handleOrderCreated(new OrderCreatedEvent(this,1L,9L,0));
        verify(orders).finishSubmission(eq(1L),eq(1),eq(0),eq(2),isNull(),anyString());verify(orders,never()).updateById(any(CourseOrder.class));
    }
    @Test void submissionFinishesWithConditionalFieldUpdate(){
        var orders=mock(CourseOrderMapper.class);var products=mock(CoursePlatformMapper.class);var providers=mock(ApiProviderMapper.class);var docking=mock(PlatformDockingService.class);
        var order=new CourseOrder();order.setId(1L);order.setPlatformId(9L);order.setOrderStatus(0);order.setDockStatus(0);
        var product=new CoursePlatform();product.setId(9L);product.setDockApiId(3L);var provider=new ApiProvider();provider.setStatus(1);
        when(orders.selectById(1L)).thenReturn(order);when(products.selectById(9L)).thenReturn(product);when(providers.selectById(3L)).thenReturn(provider);
        when(orders.claimSubmission(1L,0,1,0)).thenReturn(1);when(docking.dockOrder(order,product,provider)).thenReturn(DockResult.success("成功","remote-1"));
        new OrderEventListener(orders,products,providers,docking,mock(AqksStudyService.class)).handleOrderCreated(new OrderCreatedEvent(this,1L,9L,0));
        verify(orders).finishSubmission(eq(1L),eq(1),eq(0),eq(1),eq("remote-1"),anyString());verify(orders,never()).updateById(any(CourseOrder.class));
    }
}
