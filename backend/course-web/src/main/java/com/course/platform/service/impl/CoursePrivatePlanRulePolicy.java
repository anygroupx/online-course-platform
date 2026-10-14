package com.course.platform.service.impl;

import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;
import com.course.platform.domain.dto.CoursePrivatePlanRules;
import java.time.LocalDateTime;
import java.util.Set;

/** 规则组合统一校验；开通、发布和续约使用同一金额域与白名单。 */
public final class CoursePrivatePlanRulePolicy {
    private CoursePrivatePlanRulePolicy() {}
    public static void validate(CoursePrivatePlanRules value) {
        if(value==null)throw invalid("套餐规则不能为空");
        choice(value.getMode(),Set.of("PERMANENT_PREPAID","TIME_PREPAID","PERFORMANCE_RENEWAL","TIERED_PRICING"),"运营模式");
        CourseReservedWalletService.money(value.getFixedPrice(),false);
        CourseReservedWalletService.money(value.getInitialDepositAmount(),true);
        var deposit=CourseReservedWalletService.money(value.getRenewalDepositAmount(),true);
        var consumption=CourseReservedWalletService.money(value.getRenewalConsumptionTarget(),true);
        choice(value.getInitialFundingPolicy(),Set.of("NEW_DEPOSIT","EXISTING_BALANCE"),"首次预存政策");
        choice(value.getRenewalPolicy(),Set.of("NONE","NEW_DEPOSIT","PERFORMANCE","MANUAL"),"续期政策");
        choice(value.getRenewalPricePolicy(),Set.of("SAME_VERSION","LATEST_PUBLISHED"),"续期价格政策");
        choice(value.getUpgradePolicy(),Set.of("DISABLED","NEXT_TERM","REVIEW_IMMEDIATE"),"升级政策");
        choice(value.getUpgradeDepositPolicy(),Set.of("DIFFERENCE","FULL_TARGET","EXISTING_BALANCE"),"升级预存政策");
        choice(value.getExpirationPolicy(),Set.of("KEEP_BALANCE"),"到期余额政策");
        // 档位只是组合维度，长期档位不应被强行变成有期限的套餐。
        if("PERMANENT_PREPAID".equals(value.getMode()) || "TIERED_PRICING".equals(value.getMode()) && value.getValidityDays()==null && "NONE".equals(value.getRenewalPolicy())) {
            if(value.getValidityDays()!=null || !"NONE".equals(value.getRenewalPolicy()))throw invalid("长期预存不设置固定期限或周期续期门槛");
        } else if(value.getValidityDays()==null || value.getValidityDays()<1 || value.getValidityDays()>3650)throw invalid("限期权益天数必须为1到3650");
        if(value.getSettlementGraceDays()<0 || value.getSettlementGraceDays()>365)throw invalid("结算观察期必须为0到365天");
        if(value.getRenewalBeforeDays()<0 || value.getRenewalAfterDays()<0 || value.getRenewalBeforeDays()>3650 || value.getRenewalAfterDays()>3650)throw invalid("续期申请窗口无效");
        if("PERFORMANCE_RENEWAL".equals(value.getMode()) && !"PERFORMANCE".equals(value.getRenewalPolicy()))throw invalid("消费续期模式必须使用有效成交政策");
        if("NEW_DEPOSIT".equals(value.getRenewalPolicy())) {
            if(deposit.signum()<=0 || consumption.signum()!=0)throw invalid("新增预存续期须设置正门槛，不能与消费目标混记");
        } else if("PERFORMANCE".equals(value.getRenewalPolicy())) {
            if(consumption.signum()<=0 || deposit.signum()!=0)throw invalid("有效消费续期须设置正目标，预存不能冒充消费");
        } else if(deposit.signum()!=0 || consumption.signum()!=0)throw invalid("无自动资格门槛的政策不配置预存或消费目标");
        if(value.isRenewalAuto() && Set.of("NONE","MANUAL").contains(value.getRenewalPolicy()))throw invalid("当前政策不能自动续期");
        if(value.isAllowManualGrant() && (value.getValidityDays()==null||"NONE".equals(value.getRenewalPolicy())))throw invalid("人工补授仅用于有明确周期和续期政策的权益");
        if(value.isUpgradeAuto() && !"NEXT_TERM".equals(value.getUpgradePolicy()))throw invalid("自动升级只能排期，不能绕过即时升级审核");
        second(value.getActivationFrom());second(value.getEffectiveFrom());second(value.getEffectiveUntil());
        if(value.getEffectiveFrom()!=null && value.getEffectiveUntil()!=null && !value.getEffectiveUntil().isAfter(value.getEffectiveFrom()))throw invalid("规则销售结束必须晚于开始");
    }
    private static void choice(String value,Set<String> allowed,String name){if(value==null || !allowed.contains(value))throw invalid(name+"无效");}
    private static void second(LocalDateTime value){if(value!=null && value.getNano()!=0)throw invalid("业务时间必须精确到秒");}
    private static BusinessException invalid(String message){return new BusinessException(ResultCode.PARAM_ERROR,message);}
}
