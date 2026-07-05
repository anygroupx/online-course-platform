package com.course.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.course.platform.domain.entity.PlatformCategory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 平台分类Mapper接口
 */
@Mapper
public interface PlatformCategoryMapper extends BaseMapper<PlatformCategory> {
    
}
