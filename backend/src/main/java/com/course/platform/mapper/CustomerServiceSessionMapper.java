package com.course.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.course.platform.domain.entity.CustomerServiceSession;
import com.course.platform.domain.vo.CustomerServiceSessionVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 客服会话Mapper接口
 * 
 * @author AI Assistant
 * @since 2025-01-17
 * Source: 基于现有系统架构设计
 */
@Mapper
public interface CustomerServiceSessionMapper extends BaseMapper<CustomerServiceSession> {

    /**
     * 根据用户ID查询活跃会话
     * 
     * @param userId 用户ID
     * @return 会话信息
     */
    CustomerServiceSession selectActiveSessionByUserId(@Param("userId") Long userId);

    /**
     * 更新会话状态
     * 
     * @param sessionId 会话ID
     * @param status 状态
     * @return 更新数量
     */
    Integer updateSessionStatus(@Param("sessionId") String sessionId, @Param("status") Integer status);

    /**
     * 查询所有会话（带用户信息和最后消息）
     * 
     * @param status 会话状态，null表示查询所有
     * @return 会话列表
     */
    List<CustomerServiceSessionVO> selectAllSessionsWithInfo(@Param("status") Integer status);
}
