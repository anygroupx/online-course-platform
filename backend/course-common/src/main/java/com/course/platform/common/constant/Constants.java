package com.course.platform.common.constant;

/**
 * 常量类（已废弃）
 * 
 * @author AI Assistant
 * @since 2025-01-17
 */
public class Constants {

    /**
     * UTF-8编码
     */
    public static final String UTF8 = "UTF-8";

    /**
     * Token前缀
     */
    public static final String TOKEN_PREFIX = "Bearer ";

    /**
     * Token Header名称
     */
    public static final String TOKEN_HEADER = "Authorization";

    /**
     * 对外用户 UUID 键
     */
    public static final String USER_UID_KEY = "uid";

    /**
     * 用户名键
     */
    public static final String USERNAME_KEY = "username";

    /**
     * Redis Key前缀
     */
    public static final String REDIS_KEY_PREFIX = "course:";

    /**
     * Token Redis Key前缀
     */
    public static final String TOKEN_REDIS_KEY = REDIS_KEY_PREFIX + "token:";

    /**
     * 验证码Redis Key前缀
     */
    public static final String CAPTCHA_REDIS_KEY = REDIS_KEY_PREFIX + "captcha:";

    /**
     * 用户状态 - 正常
     */
    public static final int USER_STATUS_NORMAL = 1;

    /**
     * 用户状态 - 禁用
     */
    public static final int USER_STATUS_DISABLED = 0;

    /**
     * 订单状态 - 待处理
     */
    public static final int ORDER_STATUS_PENDING = 0;

    /**
     * 订单状态 - 进行中
     */
    public static final int ORDER_STATUS_PROCESSING = 1;

    /**
     * 订单状态 - 已完成
     */
    public static final int ORDER_STATUS_COMPLETED = 2;

    /**
     * 订单状态 - 已取消
     */
    public static final int ORDER_STATUS_CANCELLED = 3;

    /**
     * 订单状态 - 失败
     */
    public static final int ORDER_STATUS_FAILED = 4;

    /**
     * 订单状态 - 待考试
     */
    public static final int ORDER_STATUS_EXAM_PENDING = 5;

    /**
     * 订单状态 - 考试中
     */
    public static final int ORDER_STATUS_EXAM_PROCESSING = 6;

    /**
     * 订单状态 - 考试完成
     */
    public static final int ORDER_STATUS_EXAM_COMPLETED = 7;

    /**
     * 订单状态 - 等待退款
     */
    public static final int ORDER_STATUS_REFUND_PENDING = 8;

    /**
     * 对接状态 - 待对接
     */
    public static final int DOCK_STATUS_PENDING = 0;

    /**
     * 对接状态 - 对接成功
     */
    public static final int DOCK_STATUS_SUCCESS = 1;

    /**
     * 对接状态 - 对接失败
     */
    public static final int DOCK_STATUS_FAILED = 2;

    /**
     * 对接状态 - 重复订单
     */
    public static final int DOCK_STATUS_DUPLICATE = 3;

    /**
     * 对接状态 - 已取消
     */
    public static final int DOCK_STATUS_CANCELLED = 4;

    /**
     * 费率计算方式 - 乘法
     */
    public static final String RATE_TYPE_MULTIPLY = "MULTIPLY";

    /**
     * 费率计算方式 - 加法
     */
    public static final String RATE_TYPE_ADD = "ADD";

    /**
     * 默认管理员ID
     */

    /**
     * 根代理父ID
     */
    public static final Long ROOT_PARENT_ID = 0L;

    /**
     * 充值卡密状态 - 未使用
     */
    public static final int CARD_STATUS_UNUSED = 0;

    /**
     * 充值卡密状态 - 已使用
     */
    public static final int CARD_STATUS_USED = 1;

    /**
     * 充值卡密状态 - 已禁用
     */
    public static final int CARD_STATUS_DISABLED = 2;
}

