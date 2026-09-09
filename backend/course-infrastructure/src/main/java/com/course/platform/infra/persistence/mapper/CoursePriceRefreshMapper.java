package com.course.platform.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.course.platform.domain.catalogrefresh.CoursePriceRefresh;
import com.course.platform.domain.entity.ApiProvider;
import com.course.platform.domain.entity.CoursePlatform;

import org.apache.ibatis.annotations.*;

@Mapper
public interface CoursePriceRefreshMapper extends BaseMapper<CoursePriceRefresh> {
    @Select("SELECT * FROM course_price_refresh WHERE id=#{id} FOR UPDATE")
    CoursePriceRefresh lock(@Param("id") String id);

    @Select("SELECT * FROM api_provider WHERE id=#{id} FOR UPDATE")
    ApiProvider lockProvider(@Param("id") Long id);

    @Select("SELECT * FROM course_platform WHERE id=#{id} FOR UPDATE")
    CoursePlatform lockPlatform(@Param("id") Long id);
}
