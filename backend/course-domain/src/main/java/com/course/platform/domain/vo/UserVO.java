package com.course.platform.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户响应白名单 VO（禁止直出密码/API Key 等敏感字段）
 */
@Data
@Builder
public class UserVO {
    private String uid;
    private String username;
    private String nickname;
    private String avatar;
    private BigDecimal balance;
    private BigDecimal totalRecharge;
    private BigDecimal rate;
    private Boolean apiEnabled;
    private String apiKeyPrefix;
    private String inviteCode;
    private BigDecimal inviteRate;
    private String notice;
    private Integer status;
    private String role;
    private Boolean mustChangePassword;
    private LocalDateTime createTime;
    private LocalDateTime lastLoginTime;
    private String lastLoginIp;
}
