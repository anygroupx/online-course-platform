package com.course.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.course.platform.domain.entity.RefreshToken;
import org.apache.ibatis.annotations.Mapper;

/**
 * Refresh Token Mapper接口
 * 
 * @author AI Assistant
 * @since 2025-11-25
 */
@Mapper
public interface RefreshTokenMapper extends BaseMapper<RefreshToken> {
}
