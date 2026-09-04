package com.course.platform.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户信息响应VO（用于首页）
 * 
 * @author AI Assistant
 * @since 2025-01-17
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInfoResponse {

    /**
     * 对外用户 UUID
     */
    private String uid;

    /**
     * 用户名
     */
    private String username;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 头像
     */
    private String avatar;

    /**
     * 余额
     */
    private BigDecimal balance;

    /**
     * 费率
     */
    private BigDecimal rate;

    /** API Key plaintext is never returned after one-time issuance. */
    private Boolean apiEnabled;
    private String apiKeyPrefix;
    private LocalDateTime apiKeyExpiresAt;

    /**
     * 邀请码
     */
    private String inviteCode;

    /**
     * 邀请费率
     */
    private BigDecimal inviteRate;

    /**
     * 订单总数
     */
    private Long totalOrders;

    /**
     * 总充值
     */
    private BigDecimal totalRecharge;

    /**
     * 上级用户名
     */
    private String parentUsername;

    /**
     * 上级公告
     */
    private String parentNotice;

    /**
     * 系统公告
     */
    private String systemNotice;

    /**
     * 代理统计
     */
    private AgentStatistics agentStats;

    /**
     * 代理统计内部类
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AgentStatistics {
        /**
         * 代理总数
         */
        private Long totalAgents;

        /**
         * 今日注册
         */
        private Long todayRegistered;

        /**
         * 今日登录
         */
        private Long todayLogin;

        /**
         * 今日下单
         */
        private Long todayOrders;
    }
}

