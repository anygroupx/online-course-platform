package com.course.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.course.platform.application.service.support.CustomerServiceService;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;
import com.course.platform.common.security.SecurityRoles;
import com.course.platform.domain.dto.CustomerServiceMessageDTO;
import com.course.platform.domain.entity.CustomerServiceMessage;
import com.course.platform.domain.entity.CustomerServiceSession;
import com.course.platform.domain.entity.User;
import com.course.platform.domain.vo.CustomerServiceMessageVO;
import com.course.platform.domain.vo.CustomerServiceSessionVO;
import com.course.platform.infra.persistence.mapper.CustomerServiceMessageMapper;
import com.course.platform.infra.persistence.mapper.CustomerServiceSessionMapper;
import com.course.platform.infra.persistence.mapper.UserMapper;
import com.course.platform.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 客服服务实现类（含会话归属与角色校验）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerServiceServiceImpl implements CustomerServiceService {

    private final CustomerServiceSessionMapper sessionMapper;
    private final CustomerServiceMessageMapper messageMapper;
    private final UserMapper userMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CustomerServiceSession createOrGetSession(Long userId) {
        CustomerServiceSession activeSession = sessionMapper.selectActiveSessionByUserId(userId);
        if (activeSession != null) {
            return activeSession;
        }
        CustomerServiceSession session = new CustomerServiceSession();
        session.setSessionId(UUID.randomUUID().toString().replace("-", ""));
        session.setUserId(userId);
        session.setStatus(1);
        session.setStartTime(LocalDateTime.now());
        session.setLastMessageTime(LocalDateTime.now());
        sessionMapper.insert(session);
        return session;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean sendMessage(CustomerServiceMessageDTO messageDTO, Long userId) {
        CustomerServiceSession session = requireSession(messageDTO.getSessionId());
        assertCanAccessSession(session, userId);

        // senderType 由服务端根据角色决定，禁止客户端伪造客服身份
        int senderType = resolveSenderType(session, userId);

        CustomerServiceMessage message = new CustomerServiceMessage();
        message.setSessionId(messageDTO.getSessionId());
        message.setSenderId(userId);
        message.setSenderType(senderType);
        message.setMessageType(messageDTO.getMessageType() == null ? 1 : messageDTO.getMessageType());
        message.setContent(messageDTO.getContent());
        message.setIsRead(0);
        messageMapper.insert(message);

        session.setLastMessageTime(LocalDateTime.now());
        if (session.getStatus() != null && session.getStatus() == 1) {
            session.setStatus(2);
        }
        sessionMapper.updateById(session);
        return true;
    }

    @Override
    public List<CustomerServiceMessageVO> getSessionMessages(String sessionId, Long userId) {
        CustomerServiceSession session = requireSession(sessionId);
        assertCanAccessSession(session, userId);
        return messageMapper.selectMessagesBySessionId(sessionId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean markMessagesAsRead(String sessionId, Long userId) {
        CustomerServiceSession session = requireSession(sessionId);
        assertCanAccessSession(session, userId);
        int result = messageMapper.markMessagesAsRead(sessionId, userId);
        return result >= 0;
    }

    @Override
    public Integer getUnreadCount(Long userId) {
        return messageMapper.selectUnreadCountByUserId(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean endSession(String sessionId, Long userId) {
        CustomerServiceSession session = requireSession(sessionId);
        assertCanAccessSession(session, userId);
        session.setStatus(3);
        session.setEndTime(LocalDateTime.now());
        sessionMapper.updateById(session);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean assignCustomerService(String sessionId, Long customerServiceId) {
        SecurityUtils.requireCustomerService();
        CustomerServiceSession session = requireSession(sessionId);
        session.setCustomerServiceId(customerServiceId);
        if (session.getStatus() != null && session.getStatus() == 1) {
            session.setStatus(2);
        }
        sessionMapper.updateById(session);
        return true;
    }

    @Override
    public List<CustomerServiceSessionVO> getAllSessions(Integer status) {
        SecurityUtils.requireCustomerService();
        return sessionMapper.selectAllSessionsWithInfo(status);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean takeSession(String sessionId, Long customerServiceId) {
        SecurityUtils.requireCustomerService();
        CustomerServiceSession session = requireSession(sessionId);
        session.setCustomerServiceId(customerServiceId);
        session.setStatus(2);
        sessionMapper.updateById(session);
        return true;
    }

    private CustomerServiceSession requireSession(String sessionId) {
        LambdaQueryWrapper<CustomerServiceSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CustomerServiceSession::getSessionId, sessionId);
        CustomerServiceSession session = sessionMapper.selectOne(wrapper);
        if (session == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "会话不存在");
        }
        return session;
    }

    private void assertCanAccessSession(CustomerServiceSession session, Long userId) {
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if (userId.equals(session.getUserId())) {
            return;
        }
        if (session.getCustomerServiceId() != null && userId.equals(session.getCustomerServiceId())) {
            return;
        }
        if (SecurityUtils.isCustomerService()) {
            return;
        }
        throw new BusinessException(ResultCode.FORBIDDEN);
    }

    private int resolveSenderType(CustomerServiceSession session, Long userId) {
        if (userId.equals(session.getUserId())) {
            return 1; // 用户
        }
        if (SecurityUtils.isCustomerService()
                || (session.getCustomerServiceId() != null && userId.equals(session.getCustomerServiceId()))) {
            return 2; // 客服
        }
        // 兜底再查角色
        User user = userMapper.selectById(userId);
        if (user != null && user.getRole() != null) {
            String role = user.getRole().toUpperCase();
            if (SecurityRoles.ADMIN.equals(role) || SecurityRoles.CS.equals(role)) {
                return 2;
            }
        }
        throw new BusinessException(ResultCode.FORBIDDEN);
    }
}
