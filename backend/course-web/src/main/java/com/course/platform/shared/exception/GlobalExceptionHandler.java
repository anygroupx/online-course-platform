package com.course.platform.shared.exception;

import com.course.platform.application.service.security.SecurityAuditService;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.Result;
import com.course.platform.common.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 全局异常处理器：返回正确 HTTP 状态码 + errorId
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final SecurityAuditService securityAuditService;

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<?>> handleBusinessException(BusinessException e) {
        String errorId = newErrorId();
        log.error("业务异常 errorId={} code={} msg={}", errorId, e.getCode(), e.getMessage());
        Result<?> body = Result.error(e.getCode(), e.getMessage());
        body.setErrorId(errorId);
        return ResponseEntity.status(mapStatus(e.getCode())).body(body);
    }

    /**
     * 将不存在的路由映射为 404，避免进入通用 500 兜底。
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<Result<?>> handleNoHandlerFoundException(NoHandlerFoundException e) {
        String errorId = newErrorId();
        log.warn("请求路径不存在 errorId={} msg={}", errorId, e.getMessage());
        Result<?> body = Result.error(ResultCode.NOT_FOUND);
        body.setErrorId(errorId);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    /**
     * 将无法解析的请求体映射为 400，明确区分客户端格式错误与服务端异常。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<?>> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        String errorId = newErrorId();
        log.warn("请求体格式错误 errorId={} msg={}", errorId, e.getMessage());
        Result<?> body = Result.error(ResultCode.PARAM_ERROR.getCode(), "请求体格式错误");
        body.setErrorId(errorId);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class})
    public ResponseEntity<Result<?>> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String errorId = newErrorId();
        String errorMessage = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.error("参数校验异常 errorId={} msg={}", errorId, errorMessage);
        Result<?> body = Result.error(ResultCode.PARAM_ERROR.getCode(), errorMessage);
        body.setErrorId(errorId);
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<?>> handleBindException(BindException e) {
        String errorId = newErrorId();
        String errorMessage = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.error("参数绑定异常 errorId={} msg={}", errorId, errorMessage);
        Result<?> body = Result.error(ResultCode.PARAM_ERROR.getCode(), errorMessage);
        body.setErrorId(errorId);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result<?>> handleIllegalArgumentException(IllegalArgumentException e) {
        String errorId = newErrorId();
        log.error("非法参数异常 errorId={} msg={}", errorId, e.getMessage());
        Result<?> body = Result.error(ResultCode.PARAM_ERROR.getCode(), e.getMessage());
        body.setErrorId(errorId);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<?>> handleAccessDeniedException(AccessDeniedException e) {
        String errorId = newErrorId();
        log.error("访问拒绝 errorId={} msg={}", errorId, e.getMessage());
        auditDenied("ACCESS_DENIED", e.getMessage());
        Result<?> body = Result.error(ResultCode.FORBIDDEN.getCode(), ResultCode.FORBIDDEN.getMessage());
        body.setErrorId(errorId);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Result<?>> handleAuthenticationException(AuthenticationException e) {
        String errorId = newErrorId();
        log.error("认证失败 errorId={} msg={}", errorId, e.getMessage());
        auditDenied("AUTH_FAIL", e.getMessage());
        Result<?> body = Result.error(ResultCode.UNAUTHORIZED.getCode(), ResultCode.UNAUTHORIZED.getMessage());
        body.setErrorId(errorId);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    @ExceptionHandler(NullPointerException.class)
    public ResponseEntity<Result<?>> handleNullPointerException(NullPointerException e) {
        String errorId = newErrorId();
        log.error("空指针异常 errorId={}", errorId, e);
        Result<?> body = Result.error(ResultCode.SYSTEM_ERROR);
        body.setErrorId(errorId);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<?>> handleException(Exception e) {
        String errorId = newErrorId();
        log.error("系统异常 errorId={}", errorId, e);
        Result<?> body = Result.error(ResultCode.SYSTEM_ERROR);
        body.setErrorId(errorId);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    private void auditDenied(String eventType, String message) {
        try {
            Long userId = null;
            String username = null;
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() != null && !"anonymousUser".equals(auth.getPrincipal())) {
                try {
                    userId = auth.getPrincipal() instanceof Long id ? id : Long.parseLong(auth.getPrincipal().toString());
                } catch (Exception ignored) {
                    username = String.valueOf(auth.getPrincipal());
                }
            }
            String path = null;
            String method = null;
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                path = request.getRequestURI();
                method = request.getMethod();
            }
            securityAuditService.record(eventType, "WARN", userId, username, path, method,
                    message == null ? eventType : message, null);
        } catch (Exception ex) {
            log.debug("安全审计写入失败: {}", ex.getMessage());
        }
    }

    private String newErrorId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private HttpStatus mapStatus(Integer code) {
        if (code == null) {
            return HttpStatus.BAD_REQUEST;
        }
        if (code.equals(ResultCode.UNAUTHORIZED.getCode()) || code.equals(ResultCode.TOKEN_EXPIRED.getCode())
                || code.equals(ResultCode.TOKEN_INVALID.getCode())
                || code.equals(ResultCode.REFRESH_TOKEN_REUSE.getCode())) {
            return HttpStatus.UNAUTHORIZED;
        }
        if (code.equals(ResultCode.FORBIDDEN.getCode())
                || code.equals(ResultCode.MFA_REQUIRED.getCode())
                || code.equals(ResultCode.MFA_CODE_INVALID.getCode())
                || code.equals(ResultCode.MFA_CHALLENGE_INVALID.getCode())
                || code.equals(ResultCode.MFA_NOT_ENABLED.getCode())
                || code.equals(ResultCode.HUMAN_VERIFICATION_FAILED.getCode())
                || code.equals(ResultCode.MUST_CHANGE_PASSWORD.getCode())) {
            return HttpStatus.FORBIDDEN;
        }
        if (code.equals(ResultCode.HUMAN_VERIFICATION_UNAVAILABLE.getCode())) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        if (code.equals(ResultCode.NOT_FOUND.getCode()) || code.equals(ResultCode.USER_NOT_FOUND.getCode())
                || code.equals(ResultCode.COURSE_NOT_FOUND.getCode())) {
            return HttpStatus.NOT_FOUND;
        }
        if (code.equals(ResultCode.ALREADY_EXISTS.getCode()) || code.equals(ResultCode.ORDER_EXISTS.getCode())) {
            return HttpStatus.CONFLICT;
        }
        if (code.equals(ResultCode.RATE_LIMITED.getCode())) {
            return HttpStatus.TOO_MANY_REQUESTS;
        }
        if (code.equals(ResultCode.PARAM_ERROR.getCode())) {
            return HttpStatus.UNPROCESSABLE_ENTITY;
        }
        if (code.equals(ResultCode.SYSTEM_ERROR.getCode())) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return HttpStatus.BAD_REQUEST;
    }
}
