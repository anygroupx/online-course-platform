package com.course.platform.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * CORS 配置属性
 * Source: Docker部署修复 - 从YAML读取CORS配置列表
 * 
 * @author AI Assistant
 * @since 2025-12-09
 */
@Data
@Component
@ConfigurationProperties(prefix = "course.security")
public class CorsProperties {
    
    /**
     * 允许的跨域源列表
     */
    private List<String> allowedOrigins;
    
    /**
     * 不需要认证的路径列表
     */
    private List<String> permitAllPaths;
}
