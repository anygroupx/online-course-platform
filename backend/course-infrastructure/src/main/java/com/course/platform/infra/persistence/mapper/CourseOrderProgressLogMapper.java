package com.course.platform.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.course.platform.domain.entity.CourseOrderProgressLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CourseOrderProgressLogMapper extends BaseMapper<CourseOrderProgressLog> {
}
