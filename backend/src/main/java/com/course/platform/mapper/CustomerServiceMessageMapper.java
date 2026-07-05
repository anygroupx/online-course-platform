package com.course.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.course.platform.domain.entity.CustomerServiceMessage;
import com.course.platform.domain.vo.CustomerServiceMessageVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 客服消息Mapper接口
 * 
 * @author AI Assistant
 * @since 2025-01-17
 * Source: 基于现有系统架构设计
 */
@Mapper
public interface CustomerServiceMessageMapper extends BaseMapper<CustomerServiceMessage> {

    /**
     * 查询会话消息列表
     * 
     * @param sessionId 会话ID
     * @return 消息列表
     */
    List<CustomerServiceMessageVO> selectMessagesBySessionId(@Param("sessionId") String sessionId);

    /**
     * 查询用户未读消息数量
     * 
     * @param userId 用户ID
     * @return 未读消息数量
     */
    Integer selectUnreadCountByUserId(@Param("userId") Long userId);

    /**
     * 标记消息为已读
     * 
     * @param sessionId 会话ID
     * @param userId 用户ID
     * @return 更新数量
     */
    Integer markMessagesAsRead(@Param("sessionId") String sessionId, @Param("userId") Long userId);
}
