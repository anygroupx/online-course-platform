package com.course.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.course.platform.domain.entity.ApiProvider;
import org.apache.ibatis.annotations.Mapper;

/**
 * 第三方API接口Mapper接口
 * 
 * @author AI Assistant
 * @since 2025-01-17
 */
@Mapper
public interface ApiProviderMapper extends BaseMapper<ApiProvider> {
    
}

