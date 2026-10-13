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
            String payload = new ObjectMapper().writeValueAsString(Arrays.asList(r.getPlatformId(),r.getSchoolName(),r.getStudentName(),
                    r.getStudentAccount(),r.getStudentPassword(),r.getCourseId(),r.getCourseName(),Boolean.TRUE.equals(r.getIsFastMode()),
                    r.getExpectedAmount()==null?null:r.getExpectedAmount().stripTrailingZeros().toPlainString(),r.getExpectedContractVersion(),
                    r.getExpectedPriceSource(),r.getExpectedPaymentSource()));
            return TokenHashUtil.sha256(payload);
        } catch (Exception e) { throw new IllegalArgumentException("订单请求无法生成幂等指纹",e); }
    }
}
