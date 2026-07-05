package com.course.platform;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 在线网课平台启动类
 * 
 * @author AI Assistant
 * @since 2025-01-17
 */
@SpringBootApplication
@MapperScan("com.course.platform.mapper")
@EnableScheduling
public class OnlineCoursePlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(OnlineCoursePlatformApplication.class, args);
        System.out.println("""
                
                ========================================
                   在线网课平台启动成功！
                   
                   API地址: http://localhost:8080/api
                   文档地址: http://localhost:8080/api/doc.html
                ========================================
                """);
    }
}

