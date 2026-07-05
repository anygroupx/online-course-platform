package com.course.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.course.platform.domain.dto.CustomerServiceMessageDTO;
import com.course.platform.domain.entity.CustomerServiceMessage;
import com.course.platform.domain.entity.CustomerServiceSession;
import com.course.platform.domain.vo.CustomerServiceMessageVO;
import com.course.platform.domain.vo.CustomerServiceSessionVO;
import com.course.platform.infra.persistence.mapper.CustomerServiceMessageMapper;
import com.course.platform.infra.persistence.mapper.CustomerServiceSessionMapper;
import com.course.platform.application.service.support.CustomerServiceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 客服服务实现类
 * 
 * @author AI Assistant
 * @since 2025-01-17
 * Source: 基于现有系统架构设计
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerServiceServiceImpl implements CustomerServiceService {

    private final CustomerServiceSessionMapper sessionMapper;
    private final CustomerServiceMessageMapper messageMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CustomerServiceSession createOrGetSession(Long userId) {
        log.info("创建或获取用户会话，用户ID：{}", userId);
        
        // 查询用户是否有活跃会话
        CustomerServiceSession activeSession = sessionMapper.selectActiveSessionByUserId(userId);
        if (activeSession != null) {
            log.info("用户已有活跃会话，会话ID：{}", activeSession.getSessionId());
            return activeSession;
        }
        
        // 创建新会话
        CustomerServiceSession session = new CustomerServiceSession();
        session.setSessionId(UUID.randomUUID().toString().replace("-", ""));
        session.setUserId(userId);
        session.setStatus(1); // 等待中
        session.setStartTime(LocalDateTime.now());
        session.setLastMessageTime(LocalDateTime.now());
        
        sessionMapper.insert(session);
        
        log.info("新会话创建成功，会话ID：{}", session.getSessionId());
        return session;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean sendMessage(CustomerServiceMessageDTO messageDTO, Long userId) {
        log.info("发送消息，会话ID：{}，发送者：{}", messageDTO.getSessionId(), userId);
        
        // 验证会话是否存在
        LambdaQueryWrapper<CustomerServiceSession> sessionWrapper = new LambdaQueryWrapper<>();
        sessionWrapper.eq(CustomerServiceSession::getSessionId, messageDTO.getSessionId());
        CustomerServiceSession session = sessionMapper.selectOne(sessionWrapper);
        if (session == null) {
            log.warn("会话不存在，会话ID：{}", messageDTO.getSessionId());
            return false;
        }
        
        // 创建消息
        CustomerServiceMessage message = new CustomerServiceMessage();
        message.setSessionId(messageDTO.getSessionId());
        message.setSenderId(userId);
        message.setSenderType(messageDTO.getSenderType());
        message.setMessageType(messageDTO.getMessageType());
        message.setContent(messageDTO.getContent());
        message.setIsRead(0);
        
        messageMapper.insert(message);
        
        // 更新会话最后消息时间
        session.setLastMessageTime(LocalDateTime.now());
        if (session.getStatus() == 1) {
            session.setStatus(2); // 进行中
        }
        sessionMapper.updateById(session);
        
        log.info("消息发送成功，消息ID：{}", message.getId());
        return true;
    }

    @Override
    public List<CustomerServiceMessageVO> getSessionMessages(String sessionId) {
        log.info("获取会话消息列表，会话ID：{}", sessionId);
        
        return messageMapper.selectMessagesBySessionId(sessionId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean markMessagesAsRead(String sessionId, Long userId) {
        log.info("标记消息为已读，会话ID：{}，用户ID：{}", sessionId, userId);
        
        int result = messageMapper.markMessagesAsRead(sessionId, userId);
        boolean success = result > 0;
        
        if (success) {
            log.info("消息标记为已读成功，更新数量：{}", result);
        } else {
            log.warn("消息标记为已读失败");
        }
        
        return success;
    }

    @Override
    public Integer getUnreadCount(Long userId) {
        log.info("获取用户未读消息数量，用户ID：{}", userId);
        
        Integer count = messageMapper.selectUnreadCountByUserId(userId);
        return count != null ? count : 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean endSession(String sessionId, Long userId) {
        log.info("结束会话，会话ID：{}，用户ID：{}", sessionId, userId);
        
        LambdaQueryWrapper<CustomerServiceSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CustomerServiceSession::getSessionId, sessionId)
               .eq(CustomerServiceSession::getUserId, userId);
        
        CustomerServiceSession session = sessionMapper.selectOne(wrapper);
        if (session == null) {
            log.warn("会话不存在或无权限，会话ID：{}，用户ID：{}", sessionId, userId);
            return false;
        }
        
        session.setStatus(3); // 已结束
        session.setEndTime(LocalDateTime.now());
        sessionMapper.updateById(session);
        
        log.info("会话结束成功，会话ID：{}", sessionId);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean assignCustomerService(String sessionId, Long customerServiceId) {
        log.info("分配客服，会话ID：{}，客服ID：{}", sessionId, customerServiceId);
        
        LambdaQueryWrapper<CustomerServiceSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CustomerServiceSession::getSessionId, sessionId);
        
        CustomerServiceSession session = sessionMapper.selectOne(wrapper);
        if (session == null) {
            log.warn("会话不存在，会话ID：{}", sessionId);
            return false;
        }
        
        session.setCustomerServiceId(customerServiceId);
        session.setStatus(2); // 进行中
        sessionMapper.updateById(session);
        
        log.info("客服分配成功，会话ID：{}，客服ID：{}", sessionId, customerServiceId);
        return true;
    }

    @Override
    public List<CustomerServiceSessionVO> getAllSessions(Integer status) {
        log.info("获取所有会话列表，状态筛选：{}", status);
        
        List<CustomerServiceSessionVO> sessions = sessionMapper.selectAllSessionsWithInfo(status);
        
        log.info("查询到{}条会话记录", sessions != null ? sessions.size() : 0);
        return sessions;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean takeSession(String sessionId, Long customerServiceId) {
        log.info("客服接入会话，会话ID：{}，客服ID：{}", sessionId, customerServiceId);
        
        LambdaQueryWrapper<CustomerServiceSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CustomerServiceSession::getSessionId, sessionId);
        
        CustomerServiceSession session = sessionMapper.selectOne(wrapper);
        if (session == null) {
            log.warn("会话不存在，会话ID：{}", sessionId);
            return false;
        }
        
        // 检查会话是否已被其他客服接入
        if (session.getCustomerServiceId() != null && !session.getCustomerServiceId().equals(customerServiceId)) {
            log.warn("会话已被其他客服接入，会话ID：{}，当前客服ID：{}", sessionId, session.getCustomerServiceId());
            return false;
        }
        
        session.setCustomerServiceId(customerServiceId);
        session.setStatus(2); // 进行中
        sessionMapper.updateById(session);
        
        log.info("客服接入成功，会话ID：{}，客服ID：{}", sessionId, customerServiceId);
        return true;
    }
}
