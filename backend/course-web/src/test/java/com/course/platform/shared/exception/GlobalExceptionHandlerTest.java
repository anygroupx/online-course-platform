package com.course.platform.shared.exception;

import com.course.platform.application.service.security.SecurityAuditService;
import com.course.platform.common.result.Result;
import com.course.platform.common.result.ResultCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.web.servlet.NoHandlerFoundException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 全局异常处理器的 HTTP 语义测试。
 */
@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @Mock
    private SecurityAuditService securityAuditService;

    @InjectMocks
    private GlobalExceptionHandler exceptionHandler;

    @Test
    @DisplayName("不存在的路由应返回 404")
    void noHandlerFound_shouldReturnNotFound() {
        NoHandlerFoundException exception = new NoHandlerFoundException(
                "GET", "/api/doc.html", HttpHeaders.EMPTY);

        ResponseEntity<Result<?>> response = exceptionHandler.handleNoHandlerFoundException(exception);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(ResultCode.NOT_FOUND.getCode(), response.getBody().getCode());
        assertEquals(ResultCode.NOT_FOUND.getMessage(), response.getBody().getMessage());
        assertFalse(response.getBody().getErrorId().isBlank());
    }

    @Test
    @DisplayName("无法解析的请求体应返回 400")
    void unreadableRequestBody_shouldReturnBadRequest() {
        HttpMessageNotReadableException exception = new HttpMessageNotReadableException(
                "JSON parse error", new MockHttpInputMessage(new byte[0]));

        ResponseEntity<Result<?>> response = exceptionHandler.handleHttpMessageNotReadableException(exception);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(ResultCode.PARAM_ERROR.getCode(), response.getBody().getCode());
        assertEquals("请求体格式错误", response.getBody().getMessage());
        assertFalse(response.getBody().getErrorId().isBlank());
    }
}
