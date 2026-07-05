package com.course.platform.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.course.platform.shared.result.Result;
import com.course.platform.domain.dto.OrderCreateRequest;
import com.course.platform.domain.dto.OrderQueryRequest;
import com.course.platform.domain.entity.CourseOrder;
import com.course.platform.service.CourseOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 课程订单控制器
 * 
 * @author AI Assistant
 * @since 2025-01-17
 */
@Tag(name = "订单管理", description = "订单创建、查询、取消、补单等接口")
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class CourseOrderController {

    private final CourseOrderService courseOrderService;

    /**
     * 创建订单
     */
    @Operation(summary = "创建订单", description = "创建新的课程订单")
    @PostMapping
    public Result<Map<String, Object>> createOrder(@Valid @RequestBody OrderCreateRequest request,
                                     Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        Long orderId = courseOrderService.createOrder(request, userId);
        
        // 查询订单获取orderNo
        CourseOrder order = courseOrderService.getOrderById(orderId, userId);
        
        Map<String, Object> data = new HashMap<>();
        data.put("orderNo", order.getOrderNo());
        return Result.success("订单创建成功", data);
    }

    /**
     * 分页查询订单
     */
    @Operation(summary = "查询订单列表", description = "分页查询订单列表")
    @PostMapping("/query")
    public Result<IPage<CourseOrder>> queryOrders(@RequestBody OrderQueryRequest request,
                                                   Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        IPage<CourseOrder> result = courseOrderService.queryOrders(request, userId);
        return Result.success(result);
    }

    /**
     * 获取订单详情
     */
    @Operation(summary = "获取订单详情", description = "根据订单编号获取订单详细信息")
    @GetMapping("/{orderNo}")
    public Result<CourseOrder> getOrder(@PathVariable String orderNo,
                                        Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        CourseOrder order = courseOrderService.getOrderByOrderNo(orderNo, userId);
        return Result.success(order);
    }

    /**
     * 取消订单
     */
    @Operation(summary = "取消订单", description = "取消待处理的订单")
    @PostMapping("/{orderNo}/cancel")
    public Result<Void> cancelOrder(@PathVariable String orderNo,
                                     Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        courseOrderService.cancelOrderByOrderNo(orderNo, userId);
        return Result.success("订单取消成功");
    }

    /**
     * 补单
     */
    @Operation(summary = "补单", description = "重新提交订单")
    @PostMapping("/{orderNo}/retry")
    public Result<Void> retryOrder(@PathVariable String orderNo,
                                    Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        courseOrderService.retryOrderByOrderNo(orderNo, userId);
        return Result.success("补单成功");
    }

    /**
     * 更新订单进度
     */
    @Operation(summary = "更新订单进度", description = "同步订单最新进度")
    @PostMapping("/{orderNo}/refresh")
    public Result<Void> refreshOrder(@PathVariable String orderNo,
                                      Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        courseOrderService.updateOrderProgressByOrderNo(orderNo, userId);
        return Result.success("进度更新成功");
    }
}

