package com.course.platform.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Knife4j配置类（API文档）
 * 
 * @author AI Assistant
 * @since 2025-01-17
 */
@Configuration
public class Knife4jConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("在线网课平台 API文档")
                        .version("1.0.0")
                        .description("基于Spring Boot + Vue3的前后端分离在线网课管理平台")
                        .contact(new Contact()
                                .name("AI Assistant")
                                .email("support@example.com")));
    }
}

