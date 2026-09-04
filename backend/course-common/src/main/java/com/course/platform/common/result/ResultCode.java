package com.course.platform.common.result;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 响应状态码枚举
 *
 * @author AI Assistant
 * @since 2025-01-17
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    // 成功
    SUCCESS(1, "操作成功"),

    // 通用错误 (-1 ~ -99)
    ERROR(-1, "操作失败"),
    PARAM_ERROR(-2, "参数错误"),
    NOT_FOUND(-3, "资源不存在"),
    ALREADY_EXISTS(-4, "资源已存在"),
    OPERATION_TOO_FREQUENT(-5, "操作过于频繁"),

    // 认证授权错误 (-100 ~ -199)
    UNAUTHORIZED(-100, "未登录或token已过期"),
    FORBIDDEN(-101, "无权限访问"),
    TOKEN_INVALID(-102, "Token无效"),
    TOKEN_EXPIRED(-103, "Token已过期"),
    USERNAME_OR_PASSWORD_ERROR(-104, "用户名或密码错误"),
    ACCOUNT_DISABLED(-105, "账号已被禁用"),
    ACCOUNT_LOCKED(-106, "账号已被锁定"),
    MUST_CHANGE_PASSWORD(-107, "首次登录必须修改密码"),
    PASSWORD_TOO_WEAK(-108, "密码强度不足"),
    RATE_LIMITED(-109, "请求过于频繁，请稍后再试"),
    CONFLICT(-110, "资源状态冲突"),
    MFA_REQUIRED(-111, "需要多因素认证"),
    MFA_CODE_INVALID(-112, "MFA验证码无效"),
    MFA_CHALLENGE_INVALID(-113, "MFA挑战无效或已过期"),
    MFA_NOT_ENABLED(-114, "账号未启用MFA"),
    HUMAN_VERIFICATION_FAILED(-115, "人机验证失败，请重试"),
    HUMAN_VERIFICATION_UNAVAILABLE(-116, "人机验证服务暂不可用，请稍后重试"),
    REFRESH_TOKEN_REUSE(-117, "会话已失效，请重新登录"),
    RATE_LIMIT_UNAVAILABLE(-118, "安全限流服务暂不可用，请稍后重试"),

    // 业务错误 (-200 ~ -299)
    BALANCE_INSUFFICIENT(-200, "余额不足"),
    RATE_ERROR(-201, "费率设置错误"),
    PARENT_RATE_ERROR(-202, "下级费率不能低于上级"),
    INVITE_CODE_USED(-203, "邀请码已被使用"),
    API_KEY_NOT_ENABLED(-204, "API接口未开通"),
    API_KEY_INVALID(-205, "API密钥无效"),
    ORDER_EXISTS(-206, "订单已存在"),
    ORDER_NOT_FOUND(-207, "订单不存在"),
    COURSE_NOT_FOUND(-208, "课程不存在"),
    USER_NOT_FOUND(-209, "用户不存在"),
    RECHARGE_AMOUNT_ERROR(-210, "充值金额不符合要求"),
    PRICE_CHANGE_FEE_ERROR(-211, "改价手续费不足"),
    CARD_NOT_FOUND(-212, "充值卡密不存在"),
    CARD_ALREADY_USED(-213, "充值卡密已被使用"),
    CARD_DISABLED(-214, "充值卡密已被禁用"),
    CARD_INVALID(-215, "卡号或卡密错误"),

    // 系统错误 (-500 ~ -599)
    SYSTEM_ERROR(-500, "系统内部错误"),
    DATABASE_ERROR(-501, "数据库错误"),
    NETWORK_ERROR(-502, "网络错误"),
    THIRD_PARTY_API_ERROR(-503, "第三方接口错误");

    /**
     * 响应码
     */
    private final Integer code;

    /**
     * 响应消息
     */
    private final String message;
}
