package com.course.platform.service.impl;
import com.course.platform.domain.dto.OrderCreateRequest;
import com.course.platform.common.security.TokenHashUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;

/** 只保存请求指纹，不在响应和日志暴露学生口令。 */
final class CourseOrderRequestIdentity {
    private CourseOrderRequestIdentity() {}
    static String fingerprint(OrderCreateRequest r) {
        try {
            var values = new java.util.ArrayList<Object>(Arrays.asList(r.getPlatformId(),r.getSchoolName(),r.getStudentName(),
                    r.getStudentAccount(),r.getStudentPassword(),r.getCourseId(),r.getCourseName(),Boolean.TRUE.equals(r.getIsFastMode()),
                    r.getExpectedAmount()==null?null:r.getExpectedAmount().stripTrailingZeros().toPlainString(),// 现代私有报价以不可变周期/规则/期限识别，物化或统计版本变化不使原成功请求失去恢复身份。
                    r.getExpectedPolicyTermId()!=null&&r.getExpectedPolicyVersionId()!=null&&"PRIVATE".equals(r.getExpectedPriceSource())&&"COURSE_WALLET".equals(r.getExpectedPaymentSource())&&(r.getCheckoutIntent()==null||"PRIVATE_DEFAULT".equals(r.getCheckoutIntent()))?null:r.getExpectedContractVersion(),
                    r.getExpectedPriceSource(),r.getExpectedPaymentSource()));
            // 旧请求保持原指纹，升级后同键重试仍能找回原成交。
            if(r.getCheckoutIntent()!=null||r.getExpectedPolicyTermId()!=null||r.getExpectedPolicyVersionId()!=null||r.getExpectedPolicyExpiresAt()!=null){values.add(r.getCheckoutIntent());values.add(r.getExpectedPolicyTermId());values.add(r.getExpectedPolicyVersionId());values.add(r.getExpectedPolicyExpiresAt()==null?null:r.getExpectedPolicyExpiresAt().toString());}
            String payload=new ObjectMapper().writeValueAsString(values);
            return TokenHashUtil.sha256(payload);
        } catch (Exception e) { throw new IllegalArgumentException("订单请求无法生成幂等指纹",e); }
    }
}
