package com.course.platform.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * RestTemplate配置类
 * 
 * @author AI Assistant
 * @since 2025-01-17
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        
        // 设置连接超时：15秒
        factory.setConnectTimeout(15000);
        
        // 设置读取超时：30秒（暗网等慢速API需要更长的超时时间）
        factory.setReadTimeout(30000);
        
        return new RestTemplate(factory);
    }
}
