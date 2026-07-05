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
    
}

