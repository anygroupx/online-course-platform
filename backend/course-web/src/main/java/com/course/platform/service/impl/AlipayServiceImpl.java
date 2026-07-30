package com.course.platform.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONUtil;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.domain.*;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.*;
import com.alipay.api.response.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.course.platform.config.AlipayClientFactory;
import com.course.platform.domain.dto.CreatePaymentRequest;
import com.course.platform.domain.dto.PaymentOrderResponse;
import com.course.platform.domain.entity.PaymentConfig;
import com.course.platform.domain.entity.PaymentNotifyLog;
import com.course.platform.domain.entity.PaymentOrder;
import com.course.platform.domain.entity.User;
import com.course.platform.infra.persistence.mapper.PaymentNotifyLogMapper;
import com.course.platform.infra.persistence.mapper.PaymentOrderMapper;
import com.course.platform.infra.persistence.mapper.UserMapper;
import com.course.platform.application.service.payment.AlipayService;
import com.course.platform.application.service.security.SecurityAuditService;
import com.course.platform.application.service.system.SystemConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 支付宝支付服务实现类
 *
 * @author AI Assistant
 * @date 2025-11-26
 */
@Slf4j
@Service
public class AlipayServiceImpl implements AlipayService {

    @Autowired
    private AlipayClientFactory alipayClientFactory;

    @Autowired
    private PaymentOrderMapper paymentOrderMapper;

    @Autowired
    private PaymentNotifyLogMapper paymentNotifyLogMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private SystemConfigService systemConfigService;

    @Autowired
    private AccountLedgerServiceImpl accountLedgerService;

    @Autowired
    private com.course.platform.infra.persistence.mapper.PaymentEventMapper paymentEventMapper;

    @Autowired
    private SecurityAuditService securityAuditService;

    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaymentOrderResponse createPayment(CreatePaymentRequest request, Long userId,
                                              String clientIp, String userAgent) {
        // 1. 检查支付功能是否可用
        if (!alipayClientFactory.isAvailable()) {
            throw new RuntimeException("支付功能暂不可用，请联系管理员配置支付参数");
        }

        // 2. 验证最低充值金额
        Integer minAmountInt = systemConfigService.getConfigValueAsInteger("min_recharge_amount", 10);
        BigDecimal minAmount = new BigDecimal(minAmountInt);
        if (request.getAmount().compareTo(minAmount) < 0) {
            throw new RuntimeException("充值金额不能小于" + minAmount + "元");
        }

        // 3. 创建支付订单
        PaymentOrder order = new PaymentOrder();
        order.setOrderNo(generateOrderNo());
        order.setUserId(userId);
        order.setAmount(request.getAmount());
        order.setSubject(request.getSubject() != null ? request.getSubject() : "账户充值");
        order.setBody(request.getBody() != null ? request.getBody() : "在线网课平台账户充值");
        order.setPaymentType(request.getPaymentType());
        order.setStatus("PENDING");
        order.setTimeoutExpress(30); // 30分钟超时
        order.setClientIp(clientIp);
        order.setUserAgent(userAgent);

        // 设置回调地址
        PaymentConfig config = alipayClientFactory.getCurrentConfig();
        order.setReturnUrl(request.getReturnUrl() != null ? request.getReturnUrl() : config.getReturnUrl());
        order.setNotifyUrl(config.getNotifyUrl());

        paymentOrderMapper.insert(order);

        // 4. 生成支付表单
        String paymentForm;
        try {
            if ("PC".equals(request.getPaymentType())) {
                paymentForm = generatePcPaymentForm(order);
            } else {
                paymentForm = generateWapPaymentForm(order);
            }
        } catch (AlipayApiException e) {
            log.error("生成支付表单失败，订单号：{}", order.getOrderNo(), e);
            throw new RuntimeException("生成支付表单失败：" + e.getMessage());
        }

        // 5. 返回响应
        return PaymentOrderResponse.builder()
                .orderNo(order.getOrderNo())
                .amount(order.getAmount())
                .subject(order.getSubject())
                .paymentType(order.getPaymentType())
                .status(order.getStatus())
                .createTime(order.getCreateTime())
                .paymentForm(paymentForm)
                .build();
    }

    /**
     * 生成PC网站支付表单
     */
    private String generatePcPaymentForm(PaymentOrder order) throws AlipayApiException {
        AlipayClient client = alipayClientFactory.getClient();

        AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
        request.setReturnUrl(order.getReturnUrl());
        request.setNotifyUrl(order.getNotifyUrl());

        AlipayTradePagePayModel model = new AlipayTradePagePayModel();
        model.setOutTradeNo(order.getOrderNo());
        model.setTotalAmount(order.getAmount().toString());
        model.setSubject(order.getSubject());
        model.setBody(order.getBody());
        model.setProductCode("FAST_INSTANT_TRADE_PAY");
        model.setTimeoutExpress(order.getTimeoutExpress() + "m");

        request.setBizModel(model);

        AlipayTradePagePayResponse response = client.pageExecute(request);

        if (!response.isSuccess()) {
            throw new AlipayApiException("生成PC支付表单失败：" + response.getSubMsg());
        }

        return response.getBody();
    }

    /**
     * 生成手机网站支付表单
     */
    private String generateWapPaymentForm(PaymentOrder order) throws AlipayApiException {
        AlipayClient client = alipayClientFactory.getClient();

        AlipayTradeWapPayRequest request = new AlipayTradeWapPayRequest();
        request.setReturnUrl(order.getReturnUrl());
        request.setNotifyUrl(order.getNotifyUrl());

        AlipayTradeWapPayModel model = new AlipayTradeWapPayModel();
        model.setOutTradeNo(order.getOrderNo());
        model.setTotalAmount(order.getAmount().toString());
        model.setSubject(order.getSubject());
        model.setBody(order.getBody());
        model.setProductCode("QUICK_WAP_WAY");
        model.setTimeoutExpress(order.getTimeoutExpress() + "m");

        request.setBizModel(model);

        AlipayTradeWapPayResponse response = client.pageExecute(request);

        if (!response.isSuccess()) {
            throw new AlipayApiException("生成WAP支付表单失败：" + response.getSubMsg());
        }

        return response.getBody();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String handleNotify(Map<String, String> params, String requestIp) {
        // 1. 记录通知日志
        PaymentNotifyLog log = new PaymentNotifyLog();
        log.setOrderNo(params.get("out_trade_no"));
        log.setAlipayTradeNo(params.get("trade_no"));
        log.setNotifyParams(JSONUtil.toJsonStr(params));
        log.setNotifyType(params.get("notify_type"));
        log.setTradeStatus(params.get("trade_status"));
        log.setRequestIp(requestIp);
        log.setRequestTime(LocalDateTime.now());

        try {
            // 2. 验证签名
            PaymentConfig config = alipayClientFactory.getCurrentConfig();
            boolean signVerified = AlipaySignature.rsaCheckV1(
                params,
                config.getAlipayPublicKey(),
                config.getCharset(),
                config.getSignType()
            );

            log.setVerifyResult(signVerified ? 1 : 0);
            log.setVerifyMessage(signVerified ? "签名验证成功" : "签名验证失败");

            if (!signVerified) {
                log.setProcessStatus(2);
                log.setProcessMessage("签名验证失败");
                log.setResponseContent("fail");
                paymentNotifyLogMapper.insert(log);
                securityAuditService.record("PAYMENT_CALLBACK", "CRITICAL", null, null,
                        "/payment/notify", "POST", "支付宝回调签名验证失败",
                        "orderNo=" + params.get("out_trade_no") + ",ip=" + requestIp);
                return "fail";
            }

            // 3. 查询订单
            String orderNo = params.get("out_trade_no");
            PaymentOrder order = paymentOrderMapper.selectOne(
                new LambdaQueryWrapper<PaymentOrder>()
                    .eq(PaymentOrder::getOrderNo, orderNo)
            );

            if (order == null) {
                log.setProcessStatus(2);
                log.setProcessMessage("订单不存在");
                log.setResponseContent("fail");
                paymentNotifyLogMapper.insert(log);
                return "fail";
            }

            // 4. 检查订单状态(幂等性检查)
            if ("PAID".equals(order.getStatus())) {
                log.setProcessStatus(1);
                log.setProcessMessage("订单已处理，重复通知");
                log.setResponseContent("success");
                log.setProcessTime(LocalDateTime.now());
                paymentNotifyLogMapper.insert(log);
                return "success";
            }

            // 5. 验证金额
            BigDecimal notifyAmount = new BigDecimal(params.get("total_amount"));
            if (order.getAmount().compareTo(notifyAmount) != 0) {
                log.setProcessStatus(2);
                log.setProcessMessage("金额不匹配");
                log.setResponseContent("fail");
                paymentNotifyLogMapper.insert(log);
                securityAuditService.record("PAYMENT_CALLBACK", "CRITICAL", order.getUserId(), null,
                        "/payment/notify", "POST", "支付宝回调金额不匹配",
                        "orderNo=" + orderNo + ", expect=" + order.getAmount() + ", actual=" + notifyAmount);
                return "fail";
            }

            // 6. 处理交易状态
            String tradeStatus = params.get("trade_status");
            if ("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus)) {
                boolean credited = markPaidAndCredit(
                        order,
                        params.get("trade_no"),
                        params.get("buyer_logon_id"),
                        params.get("buyer_user_id")
                );
                log.setProcessStatus(1);
                log.setProcessMessage(credited ? "支付成功，余额已更新" : "支付成功（重复通知，未重复入账）");
                log.setProcessTime(LocalDateTime.now());
            } else {
                log.setProcessStatus(2);
                log.setProcessMessage("交易状态：" + tradeStatus);
            }

            log.setResponseContent("success");
            paymentNotifyLogMapper.insert(log);
            return "success";

        } catch (Exception e) {
            this.log.error("处理支付宝通知异常", e);
            log.setProcessStatus(2);
            log.setProcessMessage("处理异常：" + e.getMessage());
            log.setResponseContent("fail");
            paymentNotifyLogMapper.insert(log);
            return "fail";
        }
    }

    @Override
    public PaymentOrder handleReturn(Map<String, String> params) {
        try {
            // 验证签名
            PaymentConfig config = alipayClientFactory.getCurrentConfig();
            boolean signVerified = AlipaySignature.rsaCheckV1(
                params,
                config.getAlipayPublicKey(),
                config.getCharset(),
                config.getSignType()
            );

            if (!signVerified) {
                throw new RuntimeException("签名验证失败");
            }

            // 查询订单
            String orderNo = params.get("out_trade_no");
            return paymentOrderMapper.selectOne(
                new LambdaQueryWrapper<PaymentOrder>()
                    .eq(PaymentOrder::getOrderNo, orderNo)
            );

        } catch (AlipayApiException e) {
            log.error("处理同步回调异常", e);
            throw new RuntimeException("处理同步回调失败：" + e.getMessage());
        }
    }

    @Override
    public PaymentOrder queryPaymentStatus(String orderNo) {
        return paymentOrderMapper.selectOne(
            new LambdaQueryWrapper<PaymentOrder>()
                .eq(PaymentOrder::getOrderNo, orderNo)
        );
    }

    @Override
    public boolean queryAlipayTradeStatus(String orderNo) {
        try {
            AlipayClient client = alipayClientFactory.getClient();

            AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
            AlipayTradeQueryModel model = new AlipayTradeQueryModel();
            model.setOutTradeNo(orderNo);
            request.setBizModel(model);

            AlipayTradeQueryResponse response = client.execute(request);

            if (response.isSuccess()) {
                String tradeStatus = response.getTradeStatus();
                return "TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus);
            }

            return false;

        } catch (AlipayApiException e) {
            log.error("查询支付宝交易状态失败，订单号：{}", orderNo, e);
            return false;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean closeOrder(String orderNo) {
        PaymentOrder order = queryPaymentStatus(orderNo);

        if (order == null || !"PENDING".equals(order.getStatus())) {
            return false;
        }

        order.setStatus("CLOSED");
        order.setCloseTime(LocalDateTime.now());
        paymentOrderMapper.updateById(order);

        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean refund(String orderNo, String refundReason) {
        PaymentOrder order = queryPaymentStatus(orderNo);

        if (order == null || !"PAID".equals(order.getStatus())) {
            throw new RuntimeException("订单状态不正确，无法退款");
        }

        try {
            AlipayClient client = alipayClientFactory.getClient();

            AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();
            AlipayTradeRefundModel model = new AlipayTradeRefundModel();
            model.setOutTradeNo(orderNo);
            model.setRefundAmount(order.getAmount().toString());
            model.setRefundReason(refundReason != null ? refundReason : "用户申请退款");
            request.setBizModel(model);

            AlipayTradeRefundResponse response = client.execute(request);

            if (response.isSuccess()) {
                // 更新订单状态
                order.setStatus("REFUNDED");
                order.setRefundAmount(order.getAmount());
                order.setRefundReason(refundReason);
                order.setRefundTime(LocalDateTime.now());
                paymentOrderMapper.updateById(order);

                // 扣减用户余额
                updateUserBalance(order.getUserId(), order.getAmount().negate(), order.getId());

                return true;
            }

            return false;

        } catch (AlipayApiException e) {
            log.error("退款失败，订单号：{}", orderNo, e);
            throw new RuntimeException("退款失败：" + e.getMessage());
        }
    }

    /**
     * 更新用户余额（原子 + 账本）
     */
    private void updateUserBalance(Long userId, BigDecimal amount, Long paymentOrderId) {
        String bizNo = String.valueOf(paymentOrderId);
        if (amount.compareTo(BigDecimal.ZERO) > 0) {
            accountLedgerService.credit(userId, amount, AccountLedgerServiceImpl.BIZ_PAYMENT, bizNo,
                    "支付宝充值入账", true);
        } else if (amount.compareTo(BigDecimal.ZERO) < 0) {
            accountLedgerService.debit(userId, amount.abs(), AccountLedgerServiceImpl.BIZ_REFUND, bizNo,
                    "支付宝退款扣减");
        }
    }

    /**
     * 条件更新订单为已支付并入账，返回是否由本线程完成入账
     */
    private boolean markPaidAndCredit(PaymentOrder order, String alipayTradeNo,
                                      String buyerLogonId, String buyerUserId) {
        int updated = paymentOrderMapper.markPaidIfPending(
                order.getOrderNo(),
                alipayTradeNo,
                buyerLogonId,
                buyerUserId,
                LocalDateTime.now()
        );
        if (updated != 1) {
            // 可能是并发回调/主动同步：若已是 PAID，仍需确保账本入账（幂等）
            PaymentOrder latest = paymentOrderMapper.selectById(order.getId());
            if (latest == null || !"PAID".equals(latest.getStatus())) {
                log.info("支付订单非待支付且未完成，跳过入账：{}", order.getOrderNo());
                return false;
            }
            order = latest;
            log.info("支付订单已标记支付，执行账本幂等补齐：{}", order.getOrderNo());
        }
        // 支付事件审计（唯一键幂等，失败不阻断入账）
        try {
            com.course.platform.domain.entity.PaymentEvent event = new com.course.platform.domain.entity.PaymentEvent();
            event.setOrderNo(order.getOrderNo());
            event.setEventType("PAID");
            event.setProviderEventId(alipayTradeNo);
            paymentEventMapper.insert(event);
        } catch (Exception e) {
            log.info("支付事件已存在：{}", order.getOrderNo());
        }
        // 账本唯一键保证余额只加一次
        updateUserBalance(order.getUserId(), order.getAmount(), order.getId());
        return true;
    }

    /**
     * 主动同步：查询支付宝成功后安全入账
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean syncPaidOrder(String orderNo) {
        PaymentOrder order = queryPaymentStatus(orderNo);
        if (order == null) {
            return false;
        }
        if ("PAID".equals(order.getStatus())) {
            return true;
        }
        if (!queryAlipayTradeStatus(orderNo)) {
            return false;
        }
        return markPaidAndCredit(order, order.getAlipayTradeNo(), order.getBuyerLogonId(), order.getBuyerUserId());
    }

    /**
     * 生成订单号
     */
    private String generateOrderNo() {
        return "PAY" + IdUtil.getSnowflakeNextIdStr();
    }
}
