package com.course.platform.common;

import com.course.platform.common.result.Result;
import com.course.platform.common.result.ResultCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 统一响应结构测试
 */
class ResultTest {

    @Test
    @DisplayName("成功响应应使用业务成功码 1")
    void success_shouldUseCodeOne() {
        Result<String> result = Result.success("ok", "data");
        assertEquals(ResultCode.SUCCESS.getCode(), result.getCode());
        assertEquals("ok", result.getMessage());
        assertEquals("data", result.getData());
    }

    @Test
    @DisplayName("错误响应应携带自定义消息")
    void error_shouldCarryMessage() {
        Result<Void> result = Result.error("失败了");
        assertNotEquals(ResultCode.SUCCESS.getCode(), result.getCode());
        assertEquals("失败了", result.getMessage());
    }

    @Test
    @DisplayName("带数据成功响应应保留 payload")
    void success_withMapData() {
        Result<Map<String, Object>> result = Result.success(Map.of("count", 11));
        assertEquals(11, result.getData().get("count"));
    }
}
