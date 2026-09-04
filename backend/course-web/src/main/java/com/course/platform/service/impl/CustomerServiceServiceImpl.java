package com.course.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.course.platform.application.service.support.CustomerServiceService;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;
import com.course.platform.common.security.SecurityAuthorities;
import com.course.platform.common.util.PublicUidUtil;
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
import com.course.platform.security.ResourceAuthorizationService;
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
    private final ResourceAuthorizationService authorizationService;

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
    public Boolean assignCustomerService(String sessionId, String customerServiceUid) {
        SecurityUtils.requireAuthority(SecurityAuthorities.CUSTOMER_SERVICE_ASSIGN);
        if (!PublicUidUtil.isValid(customerServiceUid)) {
            throw new BusinessException("客服 UUID 格式错误");
        }
        User customerService = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUid, PublicUidUtil.normalize(customerServiceUid))
                .last("LIMIT 1"));
        if (customerService == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        CustomerServiceSession session = requireSession(sessionId);
        session.setCustomerServiceId(customerService.getId());
        if (session.getStatus() != null && session.getStatus() == 1) {
            session.setStatus(2);
        }
        sessionMapper.updateById(session);
        return true;
    }

    @Override
    public List<CustomerServiceSessionVO> getAllSessions(Integer status) {
        SecurityUtils.requireAuthority(SecurityAuthorities.CUSTOMER_SERVICE_READ);
        return sessionMapper.selectAllSessionsWithInfo(status);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean takeSession(String sessionId, Long customerServiceId) {
        SecurityUtils.requireAuthority(SecurityAuthorities.CUSTOMER_SERVICE_TAKE);
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
        if (userId == null || !userId.equals(SecurityUtils.getCurrentUserId())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        authorizationService.requireCanAccessCustomerServiceSession(session);
    }

    private int resolveSenderType(CustomerServiceSession session, Long userId) {
        if (userId.equals(session.getUserId())) {
            return 1; // 用户
        }
        if ((session.getCustomerServiceId() != null && userId.equals(session.getCustomerServiceId()))
                || SecurityUtils.hasAuthority(SecurityAuthorities.CUSTOMER_SERVICE_READ_ANY)) {
            return 2; // 客服身份完全由已分配关系/服务端权限决定
        }
        throw new BusinessException(ResultCode.FORBIDDEN);
    }
}
