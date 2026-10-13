package com.course.platform.controller;
import com.course.platform.domain.entity.CourseOrder;
import com.course.platform.infra.persistence.mapper.UserMapper;
import com.course.platform.application.service.support.OperationLogService;
import com.course.platform.service.impl.CourseOrderPaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.*;

/** 管理员归档同样经统一原支付来源退款。 */
class AdminCourseOrderRefundSnapshotTest {
    @Test void completedArchiveUsesSnapshotPaymentService(){
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(8L,null,java.util.List.of()));
        var controller=mock(AdminOrderController.class,CALLS_REAL_METHODS);var payments=mock(CourseOrderPaymentService.class);
        ReflectionTestUtils.setField(controller,"paymentService",payments);ReflectionTestUtils.setField(controller,"userMapper",mock(UserMapper.class));ReflectionTestUtils.setField(controller,"operationLogService",mock(OperationLogService.class));
        var order=new CourseOrder();order.setUserId(7L);order.setOrderStatus(2);order.setOrderNo("private-order");order.setPaymentSource("COURSE_WALLET");
        ReflectionTestUtils.invokeMethod(controller,"refundCompletedOrderForArchive",order,"管理员归档订单退款");verify(payments).refund(order,8L);org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }
}
