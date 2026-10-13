package com.course.platform.domain.vo;

import java.math.BigDecimal;

/** 当前登录用户的价格与支付预览，不包含其他代理价格或凭证。 */
public record CoursePricePreview(Long platformId, BigDecimal finalPrice, String priceSource,
                                 String paymentSource, Long contractId, Integer contractVersion,
                                 Long walletId, BigDecimal walletBalance, boolean canPurchase,
                                 String unavailableReason) {}
