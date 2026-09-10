package com.course.platform.domain.exception;

import com.course.platform.common.exception.BusinessException;

import java.util.UUID;

/** Safe classification only. Never retain an upstream exception, URL, response or credentials. */
public class ProviderRequestException extends BusinessException {
    public static final String PUBLIC_MESSAGE = "服务暂不可用";

    public enum Reason {
        BLOCKED_DESTINATION("API 地址未通过出站安全检查，请检查协议、域名和端口"),
        PRIVATE_ADDRESS("服务域名解析到了非公网地址，已阻止请求"),
        PROVIDER_NOT_ACTIVE("API 接口尚未启用或需要重新验证"),
        DNS_FAILURE("服务域名解析失败"),
        REDIRECT_BLOCKED("服务接口返回了不允许的重定向"),
        TIMEOUT("服务接口连接或响应超时"),
        RESPONSE_TOO_LARGE("服务接口响应超过大小限制"),
        NETWORK_FAILURE("服务接口网络连接失败"),
        TLS_FAILURE("服务接口 TLS 证书或安全连接校验失败"),
        HTTP_ERROR("服务接口返回异常 HTTP 状态"),
        INVALID_RESPONSE("服务接口响应格式错误或缺少必要字段"),
        UPSTREAM_REJECTED("服务接口拒绝请求，请检查账号凭据和接口授权"),
        UNSUPPORTED_OPERATION("该接口类型暂不支持此操作，或尚未完成协议验证");

        private final String adminMessage;

        Reason(String adminMessage) {
            this.adminMessage = adminMessage;
        }

        public String getAdminMessage() {
            return adminMessage;
        }
    }

    private final Reason reason;
    private final String errorId = UUID.randomUUID().toString();

    public ProviderRequestException(Reason reason) {
        super(PUBLIC_MESSAGE);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }

    public String getErrorId() {
        return errorId;
    }
}
