package com.course.platform.service.impl;

import com.course.platform.application.service.course.CountdownConfigService;
import com.course.platform.application.service.order.CountdownHistoryService;
import com.course.platform.application.service.support.OperationLogService;
import com.course.platform.domain.entity.CourseOrder;
import com.course.platform.infra.persistence.mapper.CourseOrderMapper;
import com.course.platform.infra.persistence.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 锁定自营倒计时自动完成的幂等性回归：
 * completeOrderInternal 必须走带状态条件的原子更新，不能整行覆盖。
 *
 * 背景：2025-12 曾出现 4 个订单被每分钟重复"自动完成"14–16 次，
 * 根因是刷课任务用陈旧对象整行写回、把已完成的订单打回进行中。
 * 这里用 mapper 返回 0 行受影响来模拟"状态已被并发改变"。
 */
class OrderCountdownAutoCompleteTest {

    private final CourseOrderMapper orders = mock(CourseOrderMapper.class);
    private final CountdownConfigService config = mock(CountdownConfigService.class);
    private final CountdownHistoryService history = mock(CountdownHistoryService.class);
    private final OperationLogService operationLogs = mock(OperationLogService.class);

    private final OrderCountdownServiceImpl service = new OrderCountdownServiceImpl(
            orders, mock(UserMapper.class), operationLogs, config, history);

    private CourseOrder processingOrder() {
        CourseOrder order = new CourseOrder();
        order.setId(1614L);
        order.setOrderNo("ORD2002231310985388032");
        order.setIsSelfOperated(1);
        order.setOrderStatus(1); // 进行中
        order.setCountdownDuration(480);
        order.setCountdownEndTime(LocalDateTime.now().minusMinutes(1)); // 已过期
        order.setAutoCompleteEnabled(1);
        order.setTotalChapters(0);
        return order;
    }

    @Test
    void autoCompleteUsesConditionalUpdateAndDoesNotRowOverwrite() {
        CourseOrder order = processingOrder();
        when(orders.selectList(any())).thenReturn(java.util.List.of(order));
        when(config.getBooleanConfigValue(eq("auto_complete_enabled"), any())).thenReturn(true);
        when(config.getIntConfigValue(eq("auto_complete_status"), anyInt())).thenReturn(5);
        when(orders.completeIfStatus(anyLong(), anyInt(), anyInt(), any(LocalDateTime.class),
                any(), any(), eq(1))).thenReturn(1);

        service.processExpiredCountdownOrders();

        // 必须使用带条件（期望状态=进行中）的原子更新
        verify(orders).completeIfStatus(eq(1614L), eq(5), eq(1), any(LocalDateTime.class),
                eq("100%"), any(), eq(1));
        // 且绝不能再整行覆盖
        verify(orders, never()).updateById(any(CourseOrder.class));
    }

    @Test
    void concurrentStatusChangeSkipsCompletionWithoutLoggingSuccess() {
        CourseOrder order = processingOrder();
        when(orders.selectList(any())).thenReturn(java.util.List.of(order));
        when(config.getBooleanConfigValue(eq("auto_complete_enabled"), any())).thenReturn(true);
        when(config.getIntConfigValue(eq("auto_complete_status"), anyInt())).thenReturn(5);
        // 0 行受影响 = 状态已被并发流程改变
        when(orders.completeIfStatus(anyLong(), anyInt(), anyInt(), any(LocalDateTime.class),
                any(), any(), anyInt())).thenReturn(0);

        service.processExpiredCountdownOrders();

        // 放弃写入，且不得记"完成订单"操作日志（否则又会造出重复历史）
        verify(orders, never()).updateById(any(CourseOrder.class));
        verify(operationLogs, never()).log(any(), eq("完成订单"), any(), any(), any());
    }

    @Test
    void completionWritesTargetStatusDockSuccessAndFullProgress() {
        CourseOrder order = processingOrder();
        when(orders.selectList(any())).thenReturn(java.util.List.of(order));
        when(config.getBooleanConfigValue(eq("auto_complete_enabled"), any())).thenReturn(true);
        when(config.getIntConfigValue(eq("auto_complete_status"), anyInt())).thenReturn(5);
        when(orders.completeIfStatus(anyLong(), anyInt(), anyInt(), any(LocalDateTime.class),
                any(), any(), anyInt())).thenReturn(1);

        service.processExpiredCountdownOrders();

        ArgumentCaptor<String> progress = ArgumentCaptor.forClass(String.class);
        verify(orders).completeIfStatus(eq(1614L), eq(5), eq(1), any(LocalDateTime.class),
                progress.capture(), any(), eq(1));
        assertEquals("100%", progress.getValue());
    }
}
