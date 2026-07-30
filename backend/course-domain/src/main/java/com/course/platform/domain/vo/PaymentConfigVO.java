package com.course.platform.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 支付配置脱敏 VO（永不返回完整私钥）
 */
@Data
@Builder
public class PaymentConfigVO {
    private Long id;
    private String configName;
    private String envType;
    private String appIdMasked;
    private Boolean hasPrivateKey;
    private Boolean hasAlipayPublicKey;
    private String signType;
    private String format;
    private String charset;
    private String gatewayUrl;
    private String notifyUrl;
    private String returnUrl;
    private Integer isActive;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
