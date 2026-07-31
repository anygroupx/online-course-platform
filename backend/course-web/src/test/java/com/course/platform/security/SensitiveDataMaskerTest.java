package com.course.platform.security;

import com.course.platform.domain.entity.CourseOrder;
import com.course.platform.domain.vo.CourseOrderVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 订单响应白名单转换测试
 */
class SensitiveDataMaskerTest {

    @Test
    @DisplayName("授权订单响应应包含学生密码供前端显示")
    void toOrderVO_shouldIncludeStudentPassword() {
        CourseOrder order = new CourseOrder();
        order.setId(1L);
        order.setStudentPassword("student-secret");

        CourseOrderVO result = SensitiveDataMasker.toOrderVO(order);

        assertEquals("student-secret", result.getStudentPassword());
        assertTrue(result.getHasStudentPassword());
    }
}
