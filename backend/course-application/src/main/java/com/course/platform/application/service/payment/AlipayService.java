package com.course.platform.application.service.payment;

import com.course.platform.domain.dto.CreatePaymentRequest;
import com.course.platform.domain.dto.PaymentOrderResponse;
import com.course.platform.domain.entity.PaymentOrder;

import java.util.Map;

/**
 * 支付宝支付服务接口
 * 
 * @author AI Assistant
 * @date 2025-11-26
 */
public interface AlipayService {

    /**
     * 创建支付订单并生成支付表单
     * 
     * @param request 支付请求
     * @param userId 用户ID
     * @param clientIp 客户端IP
     * @param userAgent 用户代理
     * @return 支付订单响应(包含支付表单HTML)
     */
    PaymentOrderResponse createPayment(CreatePaymentRequest request, Long userId, 
                                      String clientIp, String userAgent);

    /**
     * 处理支付宝异步通知
     * 
     * @param params 通知参数
     * @param requestIp 请求IP
     * @return 处理结果消息(成功返回"success",失败返回"fail")
     */
    String handleNotify(Map<String, String> params, String requestIp);

    /**
     * 处理支付宝同步回调
     * 
     * @param params 回调参数
     * @return 支付订单信息
     */
    PaymentOrder handleReturn(Map<String, String> params);

    /**
     * 查询支付订单状态
     * 
     * @param orderNo 订单编号
     * @return 支付订单
     */
    PaymentOrder queryPaymentStatus(String orderNo);

    /**
     * 查询支付宝交易状态(调用支付宝接口)
     * 
     * @param orderNo 订单编号
     * @return 是否支付成功
     */
    boolean queryAlipayTradeStatus(String orderNo);

    /**
     * 关闭超时订单
     * 
     * @param orderNo 订单编号
     * @return 是否成功
     */
    boolean closeOrder(String orderNo);

    /**
     * 申请退款
     * 
     * @param orderNo 订单编号
     * @param refundReason 退款原因
     * @return 是否成功
     */
    boolean refund(String orderNo, String refundReason);
}
