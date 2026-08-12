package com.course.platform.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * MyBatis Plus配置类
 * 
 * @author AI Assistant
 * @since 2025-01-17
 */
@Configuration
public class MyBatisPlusConfig {

    /**
     * MyBatis Plus拦截器（分页插件）
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        
        // 分页插件
        PaginationInnerInterceptor paginationInterceptor = new PaginationInnerInterceptor(DbType.MYSQL);
        // 与分页请求 DTO 保持同一上限，避免绕过接口层时出现超大分页查询。
        paginationInterceptor.setMaxLimit(100L);
        paginationInterceptor.setOverflow(false); // 溢出总页数后是否处理
        
        interceptor.addInnerInterceptor(paginationInterceptor);
        
        return interceptor;
    }

    /**
     * 自动填充处理器
     * 用于自动填充创建时间和更新时间字段
     */
    @Bean
    @Primary
    public MetaObjectHandler metaObjectHandler() {
        return new MyMetaObjectHandler();
    }
}
