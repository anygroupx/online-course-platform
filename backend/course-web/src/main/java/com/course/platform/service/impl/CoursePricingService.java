package com.course.platform.service.impl;

import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;
import com.course.platform.domain.entity.*;
import com.course.platform.domain.vo.CoursePricePreview;
import com.course.platform.infra.persistence.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;

/** 唯一课程成交定价入口，专户绑定商品而非上游课程名称。 */
@Service
@RequiredArgsConstructor
public class CoursePricingService {
    private final UserMapper users;
    private final CoursePlatformMapper platforms;
    private final PlatformCategoryMapper categories;
    private final CoursePrivateContractMapper contracts;
    private final CourseReservedWalletMapper wallets;
    private final Clock courseWalletClock;
    @org.springframework.beans.factory.annotation.Autowired
    private CoursePrivateTermService privateTerms;

    public CoursePricePreview preview(Long userId, Long platformId) {
        var user=requireUser(users.selectById(userId));var platform=requirePlatform(platformId);var decision=decide(user,platform,false);var preview=decision.preview();
        var policy=privateTerms==null?null:privateTerms.ordinaryPolicy(decision.contract(),false);
        if(policy!=null){var amount=ordinaryPrice(user,platform);boolean enough=user.getBalance()!=null&&user.getBalance().compareTo(amount)>=0;preview=preview.withOrdinaryOffer(new CoursePricePreview.OrdinaryOffer(platformId,amount,"RATE","GENERAL",user.getBalance(),enough,decision.contractVersion(),policy.getId(),policy.getVersionId()));}return preview;
    }

    public User requireUser(User user) {
        if (user == null) throw new BusinessException(ResultCode.USER_NOT_FOUND);
        if (!Integer.valueOf(1).equals(user.getStatus())) throw new BusinessException(ResultCode.FORBIDDEN, "账号已禁用");
        return user;
    }

    public CoursePlatform requirePlatform(Long platformId) {
        CoursePlatform platform = platforms.selectById(platformId);
        if (platform == null || !Integer.valueOf(1).equals(platform.getStatus()))
            throw new BusinessException(ResultCode.COURSE_NOT_FOUND);
        if (platform.getCategoryId() != null) {
            PlatformCategory category = categories.selectById(platform.getCategoryId());
            if (category == null || !Integer.valueOf(1).equals(category.getStatus()))
                throw new BusinessException(ResultCode.COURSE_NOT_FOUND);
        }
        return platform;
    }

    /** 普通成交价与管理员参考价共用精确计算；此方法不读取密价或资金账户。 */
    public BigDecimal ordinaryPrice(User user, CoursePlatform platform) {
        requireUser(user);
        if (platform == null || platform.getBasePrice() == null || user.getRate() == null)
            throw new BusinessException(ResultCode.PARAM_ERROR, "课程普通价格配置无效");
        // 保留既有乘法/加法与HALF_UP规则，最终金额仍须满足订单资金存储范围。
        BigDecimal amount = "MULTIPLY".equals(platform.getRateType())
                ? platform.getBasePrice().multiply(user.getRate()) : platform.getBasePrice().add(user.getRate());
        return CourseReservedWalletService.money(amount.setScale(2, RoundingMode.HALF_UP), false);
    }

    /** 调用者先锁用户；扣款报价再按协议→钱包锁序读取最新配置。 */
    public PriceDecision decide(User user, CoursePlatform platform, boolean lock) {
        requireUser(user);
        CoursePrivateContract contract = contracts.selectByBinding(user.getId(), platform.getId());
        if (contract == null) {
            BigDecimal amount = ordinaryPrice(user, platform);
            boolean enough = user.getBalance() != null && user.getBalance().compareTo(amount) >= 0;
            return new PriceDecision(platform.getId(), amount, "RATE", "GENERAL", null, null, null,
                    user.getBalance(), enough, enough ? null : "普通余额不足", null, null);
        }
        boolean modern=contract.getOperationsMode()!=null&&!"LEGACY_PERMANENT".equals(contract.getOperationsMode());
        Integer beforeVersion=contract.getVersion();Long beforeTermId=contract.getCurrentTermId();
        String beforeStatus=contract.getStatus();BigDecimal beforePrice=contract.getFixedPrice();
        LocalDateTime beforeFrom=contract.getValidFrom(),beforeUntil=contract.getValidUntil();
        CoursePrivateContractTerm term=null;
        if(modern){if(privateTerms==null)throw new BusinessException(ResultCode.CONFLICT,"权益周期服务不可用");term=privateTerms.effectiveTerm(user.getId(),platform.getId(),lock);}
        if (lock) contract = contracts.selectByIdForUpdate(contract.getId());
        CourseReservedWallet wallet = wallets.selectByBinding(user.getId(), platform.getId());
        if (lock && wallet != null) wallet = wallets.selectByIdForUpdate(wallet.getId());
        requireBinding(contract, wallet, user.getId(), platform.getId());
        if(modern){
            BigDecimal price=term==null?contract.getFixedPrice():term.getEffectiveFixedPrice();
            String reason=term==null?"课程权益尚未生效、已暂停或已到期":wallet.getBalance().compareTo(price)<0?"课程专户余额不足，请从普通余额转入":null;
            // 只允许同一冻结排程在扣款事务内物化为ACTIVE；实际改价、改期或换周期仍需重新确认。
            Integer acceptedBefore=lock&&term!=null&&"PENDING_ACTIVATION".equals(beforeStatus)&&"ACTIVE".equals(contract.getStatus())&&Objects.equals(beforeTermId,term.getId())&&Objects.equals(contract.getVersion(),beforeVersion+1)&&beforePrice.compareTo(price)==0&&Objects.equals(beforeFrom,term.getStartAt())&&Objects.equals(beforeUntil,term.getEndAt())?beforeVersion:null;
            return new PriceDecision(platform.getId(),price,"PRIVATE","COURSE_WALLET",contract.getId(),contract.getVersion(),wallet.getId(),wallet.getBalance(),reason==null,reason,contract,wallet,term,acceptedBefore);
        }
        LocalDateTime now = LocalDateTime.now(courseWalletClock);
        String reason = null;
        if (!"ACTIVE".equals(contract.getStatus()) || contract.getActivatedAt() == null) reason = "专户协议尚未激活、已暂停或已终止";
        else if (contract.getValidFrom() != null && now.isBefore(contract.getValidFrom())) reason = "专户协议尚未生效";
        else if (contract.getValidUntil() != null && !now.isBefore(contract.getValidUntil())) reason = "专户协议已到期";
        else if (wallet.getBalance().compareTo(contract.getFixedPrice()) < 0) reason = "课程专户余额不足，请从普通余额转入";
        return new PriceDecision(platform.getId(), contract.getFixedPrice(), "PRIVATE", "COURSE_WALLET",
                contract.getId(), contract.getVersion(), wallet.getId(), wallet.getBalance(), reason == null, reason, contract, wallet);
    }

    /** 订单明确意图必须携带原政策身份和已确认金额，旧API默认仍走密价拒绝路径。 */
    public PriceDecision decide(User user,CoursePlatform platform,boolean lock,com.course.platform.domain.dto.OrderCreateRequest request){
        if(request.getCheckoutIntent()==null||"PRIVATE_DEFAULT".equals(request.getCheckoutIntent())){
            var decision=decide(user,platform,lock);
            if(request.getExpectedPolicyTermId()!=null||request.getExpectedPolicyVersionId()!=null||request.getExpectedPolicyExpiresAt()!=null){
                if(request.getClientRequestId()==null||request.getExpectedAmount()==null||!"PRIVATE".equals(request.getExpectedPriceSource())||!"COURSE_WALLET".equals(request.getExpectedPaymentSource())||!decision.matchesPrivatePolicy(request))throw new BusinessException(ResultCode.CONFLICT,"密价周期、发布规则或期限已变化，请重新确认");
            }return decision;
        }
        if(!"GENERAL_EXPLICIT".equals(request.getCheckoutIntent())||request.getClientRequestId()==null||request.getExpectedAmount()==null||request.getExpectedContractVersion()==null||request.getExpectedPolicyTermId()==null||request.getExpectedPolicyVersionId()==null||!"RATE".equals(request.getExpectedPriceSource())||!"GENERAL".equals(request.getExpectedPaymentSource()))throw new BusinessException(ResultCode.PARAM_ERROR,"普通价购买须明确确认金额、支付方式及原政策版本");
        var original=decide(user,platform,lock);var policy=privateTerms==null?null:privateTerms.ordinaryPolicy(original.contract(),lock);
        if(policy==null||!Objects.equals(request.getExpectedContractVersion(),original.contractVersion())||!Objects.equals(request.getExpectedPolicyTermId(),policy.getId())||!Objects.equals(request.getExpectedPolicyVersionId(),policy.getVersionId()))throw new BusinessException(ResultCode.CONFLICT,"普通价政策或权益状态已变化，请重新确认");
        BigDecimal amount=ordinaryPrice(user,platform);boolean enough=user.getBalance()!=null&&user.getBalance().compareTo(amount)>=0;
        return new PriceDecision(platform.getId(),amount,"RATE","GENERAL",null,null,null,user.getBalance(),enough,enough?null:"普通余额不足",null,null);
    }

    public static void requireBinding(CoursePrivateContract contract, CourseReservedWallet wallet, Long userId, Long platformId) {
        if (contract == null || wallet == null || !Objects.equals(contract.getUserId(), userId)
                || !Objects.equals(contract.getPlatformId(), platformId) || !Objects.equals(wallet.getUserId(), userId)
                || !Objects.equals(wallet.getPlatformId(), platformId) || !Objects.equals(wallet.getContractId(), contract.getId()))
            throw new BusinessException(ResultCode.CONFLICT, "课程专户绑定异常，请联系管理员");
    }

    /** 内部决策含锁定实体，客户端只得到本人的白名单预览。 */
    public record PriceDecision(Long platformId, BigDecimal finalPrice, String priceSource, String paymentSource,
                                Long contractId, Integer contractVersion, Long walletId, BigDecimal walletBalance,
                                boolean canPurchase, String unavailableReason, CoursePrivateContract contract, CourseReservedWallet wallet,
                                CoursePrivateContractTerm term,Integer acceptedBeforeActivationVersion) {
        public PriceDecision(Long platformId,BigDecimal finalPrice,String priceSource,String paymentSource,Long contractId,Integer contractVersion,Long walletId,BigDecimal walletBalance,boolean canPurchase,String unavailableReason,CoursePrivateContract contract,CourseReservedWallet wallet){this(platformId,finalPrice,priceSource,paymentSource,contractId,contractVersion,walletId,walletBalance,canPurchase,unavailableReason,contract,wallet,null,null);}
        public CoursePricePreview preview() {
            return new CoursePricePreview(platformId, finalPrice, priceSource, paymentSource, contractId,
                    contractVersion, walletId, walletBalance, canPurchase, unavailableReason,
                    term==null?null:term.getId(),term==null?null:term.getVersion(),term==null?null:term.getVersionId(),term==null?null:term.getOperationsMode(),term==null?null:term.getEndAt());
        }
        /** 本期冻结身份相同时，统计或排程物化递增的协议版本不改变同组确认的金融意图。 */
        public boolean matchesPrivatePolicy(com.course.platform.domain.dto.OrderCreateRequest request){return term!=null&&Objects.equals(request.getExpectedPolicyTermId(),term.getId())&&Objects.equals(request.getExpectedPolicyVersionId(),term.getVersionId())&&Objects.equals(request.getExpectedPolicyExpiresAt(),term.getEndAt());}
        public boolean acceptsContractVersion(Integer expected){return expected==null||Objects.equals(expected,contractVersion)||Objects.equals(expected,acceptedBeforeActivationVersion);}
        public void requirePurchasable() {
            if (!canPurchase) throw new BusinessException(ResultCode.BALANCE_INSUFFICIENT.getCode(), unavailableReason);
        }
    }
}
