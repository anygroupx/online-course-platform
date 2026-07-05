package com.course.platform.security;

import cn.hutool.json.JSONUtil;
import com.course.platform.common.result.Result;
import com.course.platform.common.result.ResultCode;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * JWT认证入口点（处理认证异常）
 * 
 * @author AI Assistant
 * @since 2025-01-17
 */
@Slf4j
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, 
                        HttpServletResponse response,
                        AuthenticationException authException) throws IOException, ServletException {
        
        log.error("认证失败: {}", authException.getMessage());
        
        // 设置响应
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        
        // 返回JSON结果
        Result<?> result = Result.error(ResultCode.UNAUTHORIZED);
        response.getWriter().write(JSONUtil.toJsonStr(result));
    }
}

