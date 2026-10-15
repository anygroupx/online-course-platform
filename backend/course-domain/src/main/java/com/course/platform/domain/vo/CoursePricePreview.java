package com.course.platform.domain.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonInclude;

/** 当前登录用户的价格与支付预览，不包含其他代理价格或凭证。 */
public record CoursePricePreview(Long platformId, BigDecimal finalPrice, String priceSource,
                                 String paymentSource, Long contractId, Integer contractVersion,
                                 Long walletId, BigDecimal walletBalance, boolean canPurchase,
                                 String unavailableReason,
                                 @JsonInclude(JsonInclude.Include.NON_NULL) Long privateTermId,
                                 @JsonInclude(JsonInclude.Include.NON_NULL) Integer privateTermVersion,
                                 @JsonInclude(JsonInclude.Include.NON_NULL) Long privatePlanVersionId,
                                 @JsonInclude(JsonInclude.Include.NON_NULL) String operationsMode,
                                 @JsonInclude(JsonInclude.Include.NON_NULL) LocalDateTime expiresAt,
                                 @JsonInclude(JsonInclude.Include.NON_NULL) OrdinaryOffer ordinaryOffer) {
    /** 显式普通价报价保留原政策身份，成交订单仍使用RATE/GENERAL且不绑定私有资金。 */
    public record OrdinaryOffer(Long platformId,BigDecimal finalPrice,String priceSource,String paymentSource,BigDecimal walletBalance,boolean canPurchase,Integer contractVersion,Long policyTermId,Long policyVersionId){}
    public CoursePricePreview withOrdinaryOffer(OrdinaryOffer offer){return new CoursePricePreview(platformId,finalPrice,priceSource,paymentSource,contractId,contractVersion,walletId,walletBalance,canPurchase,unavailableReason,privateTermId,privateTermVersion,privatePlanVersionId,operationsMode,expiresAt,offer);}
    public CoursePricePreview(Long platformId,BigDecimal finalPrice,String priceSource,String paymentSource,Long contractId,Integer contractVersion,Long walletId,BigDecimal walletBalance,boolean canPurchase,String unavailableReason,Long privateTermId,Integer privateTermVersion,Long privatePlanVersionId,String operationsMode,LocalDateTime expiresAt){this(platformId,finalPrice,priceSource,paymentSource,contractId,contractVersion,walletId,walletBalance,canPurchase,unavailableReason,privateTermId,privateTermVersion,privatePlanVersionId,operationsMode,expiresAt,null);}
    /** 旧调用保持原十字段语义，未知历史周期不会被猜测补齐。 */
    public CoursePricePreview(Long platformId,BigDecimal finalPrice,String priceSource,String paymentSource,Long contractId,Integer contractVersion,Long walletId,BigDecimal walletBalance,boolean canPurchase,String unavailableReason){this(platformId,finalPrice,priceSource,paymentSource,contractId,contractVersion,walletId,walletBalance,canPurchase,unavailableReason,null,null,null,null,null);}
}
