package com.course.platform.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.course.platform.common.util.PublicUidUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus自动填充处理器
 * 用于自动填充创建时间和更新时间字段
 * 
 * @author AI Assistant
 * @since 2025-01-17
 * Source: 基于MyBatis-Plus官方文档和最佳实践
 */
@Slf4j
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    /**
     * 插入时自动填充
     * 填充创建时间和更新时间
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        log.debug("开始插入填充...");

        // 仅 User 等声明了 uid 字段的实体会被填充，作为所有用户创建路径的统一兜底。
        if (metaObject.hasSetter("uid") && getFieldValByName("uid", metaObject) == null) {
            this.setFieldValByName("uid", PublicUidUtil.generate(), metaObject);
        }
        
        // 填充创建时间
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
        
        // 填充更新时间
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
        
        log.debug("插入填充完成");
    }

    /**
     * 更新时自动填充
     * 强制更新更新时间（无论字段是否为null）
     * 
     * 注意：strictUpdateFill 只在字段值为 null 时才填充。
     * 从数据库加载的实体 updateTime 已有值，需要使用 setFieldValByName 强制更新。
     * Source: MyBatis-Plus 官方文档 - 自动填充功能
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        log.debug("开始更新填充...");
        
        // 强制填充更新时间（即使字段有值也覆盖）
        // 使用 setFieldValByName 替代 strictUpdateFill
        if (metaObject.hasSetter("updateTime")) {
            this.setFieldValByName("updateTime", LocalDateTime.now(), metaObject);
        }
        
        log.debug("更新填充完成");
    }
}
