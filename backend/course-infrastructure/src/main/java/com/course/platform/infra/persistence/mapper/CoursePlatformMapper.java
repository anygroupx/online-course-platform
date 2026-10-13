package com.course.platform.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.course.platform.domain.entity.CoursePlatform;
import org.apache.ibatis.annotations.Mapper;

/**
 * 课程平台Mapper接口
 * 
 * @author AI Assistant
 * @since 2025-01-17
 */
@Mapper
public interface CoursePlatformMapper extends BaseMapper<CoursePlatform> {
    /** 商品锁协调新协议创建与删除，不跨供应商HTTP持锁。 */
    @org.apache.ibatis.annotations.Select("SELECT * FROM course_platform WHERE id=#{id} FOR UPDATE")
    CoursePlatform selectByIdForUpdate(@org.apache.ibatis.annotations.Param("id") Long id);

    @org.apache.ibatis.annotations.Select("SELECT * FROM course_platform WHERE category_id=#{categoryId} ORDER BY id FOR UPDATE")
    java.util.List<CoursePlatform> selectByCategoryForUpdate(@org.apache.ibatis.annotations.Param("categoryId") Long categoryId);

    
}

