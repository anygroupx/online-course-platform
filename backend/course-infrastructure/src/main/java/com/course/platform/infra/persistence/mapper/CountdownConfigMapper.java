package com.course.platform.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.course.platform.domain.entity.CountdownConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 倒计时配置Mapper接口
 * 
 * @author AI Assistant
 * @since 2025-01-17
 */
@Mapper
public interface CountdownConfigMapper extends BaseMapper<CountdownConfig> {
}
