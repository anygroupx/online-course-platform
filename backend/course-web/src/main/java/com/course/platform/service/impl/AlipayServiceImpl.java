package com.course.platform.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONUtil;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.domain.*;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.*;
import com.alipay.api.response.*;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;
import com.course.platform.config.AlipayClientFactory;
import com.course.platform.domain.dto.CreatePaymentRequest;
import com.course.platform.domain.dto.PaymentOrderResponse;
import com.course.platform.domain.entity.PaymentConfig;
import com.course.platform.domain.entity.PaymentNotifyLog;
import com.course.platform.domain.entity.PaymentOrder;
import com.course.platform.infra.persistence.mapper.PaymentNotifyLogMapper;
import com.course.platform.infra.persistence.mapper.PaymentOrderMapper;
import com.course.platform.application.service.payment.AlipayService;
import com.course.platform.application.service.security.SecurityAuditService;
import com.course.platform.application.service.system.SystemConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

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
    private SystemConfigService systemConfigService;

    @Autowired
    private AccountLedgerServiceImpl accountLedgerService;

    @Autowired
    private com.course.platform.infra.persistence.mapper.PaymentEventMapper paymentEventMapper;

    @Autowired
    private SecurityAuditService securityAuditService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private static final Set<String> NOTIFY_LOG_ALLOWLIST = Set.of(
            "notify_id", "notify_type", "notify_time", "app_id", "out_trade_no",
            "trade_no", "trade_status", "total_amount", "receipt_amount", "seller_id"
    );

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
        // 回调地址只能来自服务端可信配置，禁止客户端覆盖形成开放重定向。
        order.setReturnUrl(config.getReturnUrl());
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
    public String handleNotify(Map<String, String> params, String requestIp) {
        PaymentNotifyLog notifyLog = buildNotifyLog(params, requestIp);
        try {
            PaymentConfig config = requirePaymentConfig();
            boolean signVerified = AlipaySignature.rsaCheckV1(
                    params,
                    config.getAlipayPublicKey(),
                    config.getCharset(),
                    config.getSignType()
            );
            notifyLog.setVerifyResult(signVerified ? 1 : 0);
            notifyLog.setVerifyMessage(signVerified ? "签名验证成功" : "签名验证失败");
            if (!signVerified) {
                recordCallbackSecurityEvent(null, params.get("out_trade_no"), requestIp, "支付宝回调签名验证失败");
                return finishNotify(notifyLog, false, "签名验证失败");
            }

            String orderNo = requireCallbackField(params, "out_trade_no");
            String tradeNo = requireCallbackField(params, "trade_no");
            String appId = requireCallbackField(params, "app_id");
            requireCallbackField(params, "total_amount");
            String tradeStatus = requireCallbackField(params, "trade_status");

            if (!config.getAppId().equals(appId)) {
                recordCallbackSecurityEvent(null, orderNo, requestIp, "支付宝回调 app_id 不匹配");
                return finishNotify(notifyLog, false, "应用标识不匹配");
            }

            if (!"TRADE_SUCCESS".equals(tradeStatus) && !"TRADE_FINISHED".equals(tradeStatus)) {
                // 合法但非终态的通知无需改变资金；确认接收以避免无意义重试。
                return finishNotify(notifyLog, true, "忽略非支付成功状态：" + safeText(tradeStatus, 32));
            }

            TransactionTemplate transaction = new TransactionTemplate(transactionManager);
            Boolean credited = transaction.execute(status -> processVerifiedPayment(
                    orderNo,
                    tradeNo,
                    parseAmount(params.get("total_amount")),
                    params.get("buyer_logon_id"),
                    params.get("buyer_user_id")
            ));
            return finishNotify(notifyLog, true,
                    Boolean.TRUE.equals(credited) ? "支付成功，余额已更新" : "重复通知，未重复入账");
        } catch (Exception e) {
            this.log.error("处理支付宝通知失败，订单号：{}", params.get("out_trade_no"), e);
            recordCallbackSecurityEvent(null, params.get("out_trade_no"), requestIp, "支付宝回调验证或处理失败");
            return finishNotify(notifyLog, false, "回调处理失败");
        }
    }

    boolean processVerifiedPayment(String orderNo, String tradeNo, BigDecimal notifyAmount,
                                           String buyerLogonId, String buyerUserId) {
        PaymentOrder order = paymentOrderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }
        if (order.getAmount() == null || order.getAmount().compareTo(notifyAmount) != 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "回调金额不匹配");
        }
        return markPaidAndCredit(order, tradeNo, buyerLogonId, buyerUserId);
    }

    private PaymentNotifyLog buildNotifyLog(Map<String, String> params, String requestIp) {
        PaymentNotifyLog notifyLog = new PaymentNotifyLog();
        notifyLog.setOrderNo(safeText(params.get("out_trade_no"), 64));
        notifyLog.setAlipayTradeNo(safeText(params.get("trade_no"), 64));
        notifyLog.setNotifyParams(JSONUtil.toJsonStr(sanitizedNotifyParams(params)));
        notifyLog.setNotifyType(safeText(params.get("notify_type"), 50));
        notifyLog.setTradeStatus(safeText(params.get("trade_status"), 50));
        notifyLog.setRequestIp(safeText(requestIp, 50));
        notifyLog.setRequestTime(LocalDateTime.now());
        return notifyLog;
    }

    private Map<String, String> sanitizedNotifyParams(Map<String, String> params) {
        Map<String, String> sanitized = new LinkedHashMap<>();
        NOTIFY_LOG_ALLOWLIST.forEach(key -> {
            String value = params.get(key);
            if (value != null) {
                sanitized.put(key, safeText(value, 256));
            }
        });
        return sanitized;
    }

    private String finishNotify(PaymentNotifyLog notifyLog, boolean success, String message) {
        notifyLog.setProcessStatus(success ? 1 : 2);
        notifyLog.setProcessMessage(safeText(message, 255));
        notifyLog.setProcessTime(LocalDateTime.now());
        notifyLog.setResponseContent(success ? "success" : "fail");
        try {
            paymentNotifyLogMapper.insert(notifyLog);
        } catch (Exception logError) {
            log.error("支付通知审计日志写入失败，orderNo={}", notifyLog.getOrderNo(), logError);
        }
        return success ? "success" : "fail";
    }

    private void recordCallbackSecurityEvent(Long userId, String orderNo, String requestIp, String summary) {
        try {
            securityAuditService.record("PAYMENT_CALLBACK", "CRITICAL", userId, null,
                    "/payment/notify", "POST", summary,
                    "orderNo=" + safeText(orderNo, 64) + ",ip=" + safeText(requestIp, 50));
        } catch (Exception auditError) {
            log.error("支付回调安全事件写入失败", auditError);
        }
    }

    private PaymentConfig requirePaymentConfig() {
        PaymentConfig config = alipayClientFactory.getCurrentConfig();
        if (config == null || config.getAppId() == null || config.getAppId().isBlank()) {
            throw new IllegalStateException("支付配置不可用");
        }
        return config;
    }

    private String requireCallbackField(Map<String, String> params, String name) {
        String value = params.get(name);
        if (value == null || value.isBlank() || value.length() > 256) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "支付回调字段无效：" + name);
        }
        return value;
    }

    private BigDecimal parseAmount(String raw) {
        try {
            BigDecimal amount = new BigDecimal(raw);
            if (amount.compareTo(BigDecimal.ZERO) <= 0 || amount.stripTrailingZeros().scale() > 2) {
                throw new NumberFormatException("invalid monetary value");
            }
            return amount;
        } catch (RuntimeException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "支付回调金额无效");
        }
    }

    private String safeText(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replaceAll("[\r\n\t]", " ");
        return cleaned.substring(0, Math.min(cleaned.length(), maxLength));
    }

    @Override
    public PaymentOrder handleReturn(Map<String, String> params) {
        try {
            PaymentConfig config = requirePaymentConfig();
            boolean signVerified = AlipaySignature.rsaCheckV1(
                    params,
                    config.getAlipayPublicKey(),
                    config.getCharset(),
                    config.getSignType()
            );
            if (!signVerified) {
                throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "签名验证失败");
            }

            String orderNo = requireCallbackField(params, "out_trade_no");
            String tradeNo = requireCallbackField(params, "trade_no");
            String appId = requireCallbackField(params, "app_id");
            BigDecimal amount = parseAmount(requireCallbackField(params, "total_amount"));
            if (!config.getAppId().equals(appId)) {
                throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "应用标识不匹配");
            }

            PaymentOrder order = paymentOrderMapper.selectByOrderNo(orderNo);
            if (order == null) {
                throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
            }
            if (order.getAmount() == null || order.getAmount().compareTo(amount) != 0) {
                throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "回调金额不匹配");
            }
            if (order.getAlipayTradeNo() != null && !order.getAlipayTradeNo().equals(tradeNo)) {
                throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "交易号不匹配");
            }
            return order;
        } catch (AlipayApiException e) {
            log.error("处理同步回调验签异常", e);
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "同步回调验证失败");
        }
    }

    @Override
    public PaymentOrder queryPaymentStatus(String orderNo) {
        if (orderNo == null || orderNo.isBlank() || orderNo.length() > 64) {
            return null;
        }
        return paymentOrderMapper.selectByOrderNo(orderNo);
    }

    @Override
    public boolean queryAlipayTradeStatus(String orderNo) {
        AlipayTradeQueryResponse response = queryAlipayTrade(orderNo);
        if (response == null || !response.isSuccess()) {
            return false;
        }
        return isSuccessfulTradeStatus(response.getTradeStatus());
    }

    private AlipayTradeQueryResponse queryAlipayTrade(String orderNo) {
        try {
            AlipayClient client = alipayClientFactory.getClient();
            if (client == null) {
                return null;
            }
            AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
            AlipayTradeQueryModel model = new AlipayTradeQueryModel();
            model.setOutTradeNo(orderNo);
            request.setBizModel(model);
            return client.execute(request);
        } catch (AlipayApiException e) {
            log.error("查询支付宝交易状态失败，订单号：{}", orderNo, e);
            return null;
        }
    }

    private boolean isSuccessfulTradeStatus(String tradeStatus) {
        return "TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean closeOrder(String orderNo) {
        return paymentOrderMapper.closeIfPending(orderNo, LocalDateTime.now()) == 1;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean refund(String orderNo, String refundReason) {
        String reason = refundReason == null || refundReason.isBlank() ? "用户申请退款" : refundReason.trim();
        if (reason.length() > 200) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "退款原因不能超过200个字符");
        }

        int claimed = paymentOrderMapper.markRefundingIfPaid(orderNo, reason);
        PaymentOrder order = paymentOrderMapper.selectByOrderNoForUpdate(orderNo);
        if (claimed != 1) {
            if (order != null && "REFUNDED".equals(order.getStatus())) {
                return true;
            }
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "订单状态不正确，无法退款");
        }
        if (order == null || !"REFUNDING".equals(order.getStatus())) {
            throw new IllegalStateException("退款订单状态抢占失败");
        }

        // 先在同一事务内锁定并扣减余额，余额不足时绝不调用支付渠道。
        updateUserBalance(order.getUserId(), order.getAmount().negate(), order.getId());

        String outRequestNo = "REFUND-" + order.getOrderNo();
        try {
            AlipayClient client = alipayClientFactory.getClient();
            AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();
            AlipayTradeRefundModel model = new AlipayTradeRefundModel();
            model.setOutTradeNo(orderNo);
            model.setOutRequestNo(outRequestNo);
            model.setRefundAmount(order.getAmount().toPlainString());
            model.setRefundReason(reason);
            request.setBizModel(model);

            AlipayTradeRefundResponse response = client.execute(request);
            if (response == null || !response.isSuccess()) {
                String providerMessage = response == null ? "empty response" : safeText(response.getSubMsg(), 120);
                throw new BusinessException(ResultCode.ERROR.getCode(), "支付渠道退款失败：" + providerMessage);
            }
        } catch (AlipayApiException e) {
            log.error("退款调用失败，订单号：{}", orderNo, e);
            throw new BusinessException(ResultCode.ERROR.getCode(), "支付渠道退款调用失败");
        }

        if (paymentOrderMapper.markRefundedIfRefunding(orderNo, reason, LocalDateTime.now()) != 1) {
            throw new IllegalStateException("退款状态更新失败");
        }
        recordPaymentEvent(orderNo, "REFUND", outRequestNo);
        return true;
    }

    /** 更新用户余额（原子账户锁 + 账本）。 */
    private void updateUserBalance(Long userId, BigDecimal amount, Long paymentOrderId) {
        if (paymentOrderId == null) {
            throw new IllegalStateException("支付订单ID缺失");
        }
        String bizNo = String.valueOf(paymentOrderId);
        if (amount.compareTo(BigDecimal.ZERO) > 0) {
            accountLedgerService.credit(userId, amount, AccountLedgerServiceImpl.BIZ_PAYMENT, bizNo,
                    "支付宝充值入账", true);
        } else if (amount.compareTo(BigDecimal.ZERO) < 0) {
            accountLedgerService.debit(userId, amount.abs(), AccountLedgerServiceImpl.BIZ_REFUND, bizNo,
                    "支付宝退款扣减");
        }
    }

    /** 条件更新订单为已支付并入账，返回本事务是否首次完成入账。 */
    private boolean markPaidAndCredit(PaymentOrder order, String alipayTradeNo,
                                      String buyerLogonId, String buyerUserId) {
        if (alipayTradeNo == null || alipayTradeNo.isBlank() || alipayTradeNo.length() > 64) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "支付宝交易号无效");
        }
        int updated = paymentOrderMapper.markPaidIfPending(
                order.getOrderNo(),
                alipayTradeNo,
                safeText(buyerLogonId, 100),
                safeText(buyerUserId, 32),
                LocalDateTime.now()
        );
        if (updated != 1) {
            PaymentOrder latest = paymentOrderMapper.selectByOrderNoForUpdate(order.getOrderNo());
            if (latest == null
                    || !("PAID".equals(latest.getStatus())
                    || "REFUNDING".equals(latest.getStatus())
                    || "REFUNDED".equals(latest.getStatus()))
                    || !alipayTradeNo.equals(latest.getAlipayTradeNo())) {
                throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "支付订单状态或交易号冲突");
            }
            // affectedRows != 1 即 replay；严格禁止进入 credit 路径。
            return false;
        }

        recordPaymentEvent(order.getOrderNo(), "PAID", alipayTradeNo);
        updateUserBalance(order.getUserId(), order.getAmount(), order.getId());
        return true;
    }

    private void recordPaymentEvent(String orderNo, String eventType, String providerEventId) {
        com.course.platform.domain.entity.PaymentEvent event = new com.course.platform.domain.entity.PaymentEvent();
        event.setOrderNo(orderNo);
        event.setEventType(eventType);
        event.setProviderEventId(providerEventId);
        if (paymentEventMapper.insert(event) != 1) {
            throw new IllegalStateException("支付事件写入失败");
        }
    }

    /** 主动同步：渠道查询结果也必须校验金额和第三方交易号。 */
    @Transactional(rollbackFor = Exception.class)
    public boolean syncPaidOrder(String orderNo) {
        PaymentOrder order = queryPaymentStatus(orderNo);
        if (order == null) {
            return false;
        }
        if ("PAID".equals(order.getStatus()) || "REFUNDING".equals(order.getStatus())
                || "REFUNDED".equals(order.getStatus())) {
            return true;
        }
        if (!"PENDING".equals(order.getStatus())) {
            return false;
        }

        AlipayTradeQueryResponse response = queryAlipayTrade(orderNo);
        if (response == null || !response.isSuccess() || !isSuccessfulTradeStatus(response.getTradeStatus())) {
            return false;
        }
        String tradeNo = response.getTradeNo();
        BigDecimal providerAmount = parseAmount(response.getTotalAmount());
        if (order.getAmount().compareTo(providerAmount) != 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "渠道查询金额不匹配");
        }
        return markPaidAndCredit(order, tradeNo, response.getBuyerLogonId(), response.getBuyerUserId());
    }

    /**
     * 生成订单号
     */
    private String generateOrderNo() {
        return "PAY" + IdUtil.getSnowflakeNextIdStr();
    }
}
