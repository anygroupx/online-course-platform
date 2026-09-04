package com.course.platform.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.course.platform.application.service.order.CourseOrderService;
import com.course.platform.common.result.Result;
import com.course.platform.domain.dto.OrderCreateRequest;
import com.course.platform.domain.dto.OrderQueryRequest;
import com.course.platform.domain.entity.CourseOrder;
import com.course.platform.domain.entity.User;
import com.course.platform.infra.persistence.mapper.UserMapper;
import com.course.platform.domain.vo.CourseOrderVO;
import com.course.platform.security.SensitiveDataMasker;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 课程订单控制器（响应仅返回经过字段白名单控制的 CourseOrderVO）
 */
@Tag(name = "订单管理", description = "订单创建、查询、取消、补单等接口")
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class CourseOrderController {

    private final CourseOrderService courseOrderService;
    private final UserMapper userMapper;

    @Operation(summary = "创建订单", description = "创建新的课程订单")
    @PostMapping
    public Result<Map<String, Object>> createOrder(@Valid @RequestBody OrderCreateRequest request,
                                                   Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        Long orderId = courseOrderService.createOrder(request, userId);
        CourseOrder order = courseOrderService.getOrderById(orderId, userId);
        Map<String, Object> data = new HashMap<>();
        data.put("orderNo", order.getOrderNo());
        return Result.success("订单创建成功", data);
    }

    @Operation(summary = "查询订单列表", description = "分页查询订单列表")
    @PostMapping("/query")
    public Result<IPage<CourseOrderVO>> queryOrders(@Valid @RequestBody OrderQueryRequest request,
                                                    Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        IPage<CourseOrder> page = courseOrderService.queryOrders(request, userId);
        Page<CourseOrderVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        String userUid = requireCurrentUserUid(userId);
        voPage.setRecords(page.getRecords().stream()
                .map(order -> SensitiveDataMasker.toOrderVO(order, userUid)).toList());
        return Result.success(voPage);
    }

    @Operation(summary = "获取订单详情", description = "根据订单编号获取订单详细信息")
    @GetMapping("/{orderNo}")
    public Result<CourseOrderVO> getOrder(@PathVariable String orderNo,
                                          Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        CourseOrder order = courseOrderService.getOrderByOrderNo(orderNo, userId);
        return Result.success(SensitiveDataMasker.toOrderVO(order, requireCurrentUserUid(userId)));
    }

    @Operation(summary = "取消订单", description = "取消待处理的订单")
    @PostMapping("/{orderNo}/cancel")
    public Result<Void> cancelOrder(@PathVariable String orderNo,
                                    Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        courseOrderService.cancelOrderByOrderNo(orderNo, userId);
        return Result.success("订单取消成功");
    }

    @Operation(summary = "补单", description = "重新提交订单")
    @PostMapping("/{orderNo}/retry")
    public Result<Void> retryOrder(@PathVariable String orderNo,
                                   Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        courseOrderService.retryOrderByOrderNo(orderNo, userId);
        return Result.success("补单成功");
    }

    @Operation(summary = "更新订单进度", description = "同步订单最新进度")
    @PostMapping("/{orderNo}/refresh")
    public Result<Void> refreshOrder(@PathVariable String orderNo,
                                     Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        courseOrderService.updateOrderProgressByOrderNo(orderNo, userId);
        return Result.success("进度更新成功");
    }
    private String requireCurrentUserUid(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new com.course.platform.common.exception.BusinessException(
                    com.course.platform.common.result.ResultCode.USER_NOT_FOUND);
        }
        return user.getUid();
    }

}
