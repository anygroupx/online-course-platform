package com.course.platform.service;

import com.course.platform.domain.dto.CustomerServiceMessageDTO;
import com.course.platform.domain.entity.CustomerServiceSession;
import com.course.platform.domain.vo.CustomerServiceMessageVO;
import com.course.platform.domain.vo.CustomerServiceSessionVO;

import java.util.List;

/**
 * 客服服务接口
 * 
 * @author AI Assistant
 * @since 2025-01-17
 * Source: 基于现有系统架构设计
 */
public interface CustomerServiceService {

    /**
     * 创建或获取用户会话
     * 
     * @param userId 用户ID
     * @return 会话信息
     */
    CustomerServiceSession createOrGetSession(Long userId);

    /**
     * 发送消息
     * 
     * @param messageDTO 消息DTO
     * @param userId 发送者ID
     * @return 是否成功
     */
    Boolean sendMessage(CustomerServiceMessageDTO messageDTO, Long userId);

    /**
     * 获取会话消息列表
     * 
     * @param sessionId 会话ID
     * @return 消息列表
     */
    List<CustomerServiceMessageVO> getSessionMessages(String sessionId);

    /**
     * 标记消息为已读
     * 
     * @param sessionId 会话ID
     * @param userId 用户ID
     * @return 是否成功
     */
    Boolean markMessagesAsRead(String sessionId, Long userId);

    /**
     * 获取用户未读消息数量
     * 
     * @param userId 用户ID
     * @return 未读消息数量
     */
    Integer getUnreadCount(Long userId);

    /**
     * 结束会话
     * 
     * @param sessionId 会话ID
     * @param userId 用户ID
     * @return 是否成功
     */
    Boolean endSession(String sessionId, Long userId);

    /**
     * 分配客服
     * 
     * @param sessionId 会话ID
     * @param customerServiceId 客服ID
     * @return 是否成功
     */
    Boolean assignCustomerService(String sessionId, Long customerServiceId);

    /**
     * 获取所有会话列表（管理端）
     * 
     * @param status 会话状态，null表示查询所有
     * @return 会话列表
     */
    List<CustomerServiceSessionVO> getAllSessions(Integer status);

    /**
     * 客服接入会话
     * 
     * @param sessionId 会话ID
     * @param customerServiceId 客服ID
     * @return 是否成功
     */
    Boolean takeSession(String sessionId, Long customerServiceId);
}
