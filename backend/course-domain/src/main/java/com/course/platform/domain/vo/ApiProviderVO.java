package com.course.platform.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 第三方接口配置脱敏 VO
 */
@Data
@Builder
public class ApiProviderVO {
    private Long id;
    private String providerType;
    private String name;
    private String apiUrl;
    private String usernameMasked;
    private Boolean hasPassword;
    private Boolean hasToken;
    private Boolean hasApiKey;
    private Boolean hasCookie;
    private BigDecimal balance;
    private Long lastSyncTime;
    private Integer status;
    private LocalDateTime verifiedAt;
    private Long verifiedBy;
    private LocalDateTime checkedAt;
    private String lastCheckReason;
    private String lastCheckErrorId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
