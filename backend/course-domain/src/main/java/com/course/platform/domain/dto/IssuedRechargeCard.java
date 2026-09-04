package com.course.platform.domain.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 新签发充值卡。cardPassword 仅在生成响应中出现一次，之后无法查询恢复。
 */
@Value
@Builder
public class IssuedRechargeCard {
    Long id;
    String cardNo;
    String cardPassword;
    BigDecimal amount;
    Integer status;
    LocalDateTime createTime;
}
