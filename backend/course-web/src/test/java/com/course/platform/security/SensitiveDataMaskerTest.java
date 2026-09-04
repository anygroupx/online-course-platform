package com.course.platform.security;

import com.course.platform.domain.entity.CourseOrder;
import com.course.platform.domain.vo.CourseOrderVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 订单响应白名单转换测试
 */
class SensitiveDataMaskerTest {

    @Test
    @DisplayName("订单响应序列化不得包含学生密码")
    void toOrderVO_mustNeverSerializeStudentPassword() throws Exception {
        CourseOrder order = new CourseOrder();
        order.setId(1L);
        order.setStudentPassword("student-secret");

        CourseOrderVO result = SensitiveDataMasker.toOrderVO(order);

        String json = new ObjectMapper().writeValueAsString(result);
        assertFalse(json.contains("student-secret"));
        assertFalse(json.contains("studentPassword"));
    }
}
