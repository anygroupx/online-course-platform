package com.course.platform.controller;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.course.platform.common.result.Result;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import com.course.platform.domain.dto.CreatePaymentRequest;
import com.course.platform.domain.dto.PaymentOrderResponse;
import com.course.platform.domain.entity.PaymentOrder;
import com.course.platform.application.service.payment.AlipayService;
import com.course.platform.application.service.payment.PaymentOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 支付控制器
 *
 * @author AI Assistant
 * @date 2025-11-26
 */
@Slf4j
@Tag(name = "支付管理", description = "支付宝支付相关接口")
@RestController
@RequestMapping("/payment")
public class PaymentController {

    @Autowired
    private AlipayService alipayService;

    @Autowired
    private PaymentOrderService paymentOrderService;

    @Autowired
    private com.course.platform.infra.persistence.mapper.UserMapper userMapper;

    @Autowired
    private com.course.platform.infra.persistence.mapper.PaymentOrderMapper paymentOrderMapper;

    @Autowired
    private com.course.platform.infra.persistence.mapper.OperationLogMapper operationLogMapper;

    @Operation(summary = "创建支付订单", description = "创建支付订单并返回支付表单")
    @PostMapping("/create")
    public Result<PaymentOrderResponse> createPayment(
            @Valid @RequestBody CreatePaymentRequest request,
            HttpServletRequest httpRequest,
            Authentication authentication) {

        Long userId = (Long) authentication.getPrincipal();
        String clientIp = getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        PaymentOrderResponse response = alipayService.createPayment(request, userId, clientIp, userAgent);

        return Result.success(response);
    }

    @Operation(summary = "支付宝异步通知", description = "接收支付宝异步通知(此接口无需认证)")
    @PostMapping("/notify")
    public String handleNotify(HttpServletRequest request) {
        // 获取所有参数
        Map<String, String> params = new HashMap<>();
        Map<String, String[]> requestParams = request.getParameterMap();

        for (String key : requestParams.keySet()) {
            String[] values = requestParams.get(key);
            String valueStr = "";
            for (int i = 0; i < values.length; i++) {
                valueStr = (i == values.length - 1) ? valueStr + values[i] : valueStr + values[i] + ",";
            }
            params.put(key, valueStr);
        }

        String clientIp = getClientIp(request);

        log.info("收到支付宝异步通知，订单号：{}，IP：{}", params.get("out_trade_no"), clientIp);

        return alipayService.handleNotify(params, clientIp);
    }

    @Operation(summary = "支付宝同步回调", description = "支付宝同步回调地址(此接口无需认证)")
    @GetMapping("/return")
    public Result<Map<String, Object>> handleReturn(HttpServletRequest request) {
        // 获取所有参数
        Map<String, String> params = new HashMap<>();
        Map<String, String[]> requestParams = request.getParameterMap();

        for (String key : requestParams.keySet()) {
            String[] values = requestParams.get(key);
            String valueStr = "";
            for (int i = 0; i < values.length; i++) {
                valueStr = (i == values.length - 1) ? valueStr + values[i] : valueStr + values[i] + ",";
            }
            params.put(key, valueStr);
        }

        log.info("收到支付宝同步回调，订单号：{}", params.get("out_trade_no"));

        PaymentOrder order = alipayService.handleReturn(params);

        Map<String, Object> result = new HashMap<>();
        result.put("orderNo", order.getOrderNo());
        result.put("amount", order.getAmount());
        result.put("status", order.getStatus());
        result.put("success", "PAID".equals(order.getStatus()));

        return Result.success(result);
    }

    @Operation(summary = "查询订单状态", description = "根据订单号查询支付订单状态")
    @GetMapping("/query/{orderNo}")
    public Result<PaymentOrderResponse> queryOrder(@PathVariable String orderNo,
                                           Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();

        PaymentOrder order = paymentOrderService.getByOrderNoAndUserId(orderNo, userId);

        if (order == null) {
            return Result.error("订单不存在");
        }

        return Result.success(toPaymentOrderResponse(order));
    }

    @Operation(summary = "同步订单状态", description = "主动查询支付宝并同步订单状态（幂等入账）")
    @PostMapping("/sync/{orderNo}")
    public Result<PaymentOrderResponse> syncOrder(@PathVariable String orderNo,
                                          Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        PaymentOrder order = paymentOrderService.getByOrderNoAndUserId(orderNo, userId);
        if (order == null) {
            return Result.error("订单不存在");
        }
        // 统一走幂等入账逻辑，避免与异步回调竞态重复加余额
        alipayService.syncPaidOrder(orderNo);
        order = paymentOrderService.getByOrderNoAndUserId(orderNo, userId);
        return Result.success(toPaymentOrderResponse(order));
    }

    @Operation(summary = "我的支付订单", description = "分页查询当前用户的支付订单列表")
    @GetMapping("/orders")
    public Result<Page<PaymentOrderResponse>> getMyOrders(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            Authentication authentication) {

        Long userId = (Long) authentication.getPrincipal();

        Page<PaymentOrder> page = paymentOrderService.getUserOrders(userId, status, pageNum, pageSize);
        Page<PaymentOrderResponse> responsePage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        responsePage.setRecords(page.getRecords().stream().map(this::toPaymentOrderResponse).toList());
        return Result.success(responsePage);
    }

    @Operation(summary = "申请退款", description = "对已支付的订单申请退款")
    @PostMapping("/refund/{orderNo}")
    @PreAuthorize("hasAuthority('payment:refund')")
    public Result<String> refund(
            @PathVariable String orderNo,
            @RequestParam(required = false) String refundReason,
            Authentication authentication) {

        Long userId = (Long) authentication.getPrincipal();

        // 验证订单归属
        PaymentOrder order = paymentOrderService.getByOrderNoAndUserId(orderNo, userId);
        if (order == null) {
            return Result.error("订单不存在");
        }

        boolean success = alipayService.refund(orderNo, refundReason);

        if (success) {
            return Result.success("退款申请提交成功");
        } else {
            return Result.error("退款失败");
        }
    }

    private PaymentOrderResponse toPaymentOrderResponse(PaymentOrder order) {
        if (order == null) {
            return null;
        }
        return PaymentOrderResponse.builder()
                .orderNo(order.getOrderNo())
                .amount(order.getAmount())
                .subject(order.getSubject())
                .paymentType(order.getPaymentType())
                .status(order.getStatus())
                .alipayTradeNo(order.getAlipayTradeNo())
                .buyerLogonId(order.getBuyerLogonId())
                .paidTime(order.getPaidTime())
                .createTime(order.getCreateTime())
                .build();
    }

    /**
     * 获取客户端IP
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (StrUtil.isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (StrUtil.isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}
