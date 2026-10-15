package com.course.platform.service.impl;
import com.course.platform.application.service.order.CourseOrderService;
import com.course.platform.domain.dto.BatchOrderRequest;
import com.course.platform.domain.entity.User;
import com.course.platform.infra.persistence.mapper.*;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.*;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 批量不得用普通余额总额预检查拒绝专户，也不能把各笔事务重新合并。 */
class BatchOrderFinancialBoundaryTest {
    @Test void successfulWalletItemsSurviveAnotherFailureDespiteEmptyGeneralBalance(){
        UserMapper users=mock(UserMapper.class);CoursePlatformMapper products=mock(CoursePlatformMapper.class);CourseOrderService orders=mock(CourseOrderService.class);
        var user=new User();user.setId(7L);user.setStatus(1);user.setBalance(BigDecimal.ZERO);when(users.selectById(7L)).thenReturn(user);
        when(orders.createOrder(any(),eq(7L))).thenReturn(11L).thenThrow(new com.course.platform.common.exception.BusinessException("余额不足")).thenReturn(12L);
        var batch=new BatchOrderRequest();batch.setPlatformId(9L);var item=new BatchOrderRequest.BatchOrderItem();item.setStudentAccount("student");item.setStudentPassword("secret");item.setCourseName("课程");batch.setOrders(List.of(item,item,item));
        var service=new BatchOrderServiceImpl(users,products,orders);
        assertEquals(List.of(11L,12L),service.batchCreateOrders(batch,7L));verifyNoInteractions(products);
    }
    @Test void batchSuspendsAnyOuterTransaction() throws Exception{
        var annotation=BatchOrderServiceImpl.class.getMethod("batchCreateOrders",BatchOrderRequest.class,Long.class).getAnnotation(Transactional.class);
        assertNotNull(annotation);assertEquals(Propagation.NOT_SUPPORTED,annotation.propagation());
    }
}
