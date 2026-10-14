package com.course.platform.service.impl;

import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;
import com.course.platform.domain.entity.User;
import com.course.platform.infra.persistence.mapper.UserMapper;
import com.course.platform.infra.persistence.mapper.UserAuthorityMapper;
import com.course.platform.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.Arrays;
import java.util.Objects;

/** 操作前同时校验当前会话和数据库权限，撤销权限立即生效。 */
@Component
@RequiredArgsConstructor
public class CoursePrivateOperationsAccess {
    private final UserMapper users;
    private final UserAuthorityMapper authorities;

    public User self(Long userId) {
        if (!Objects.equals(SecurityUtils.getCurrentUserId(), userId)) throw new BusinessException(ResultCode.FORBIDDEN);
        User user = users.selectById(userId);
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) throw new BusinessException(ResultCode.ACCOUNT_DISABLED);
        return user;
    }
    public void require(Long operatorId, String... permissions) {
        self(operatorId);
        SecurityUtils.requireAnyAuthority(permissions);
        var current = authorities.findAuthoritiesByUserId(operatorId);
        if (current == null || Arrays.stream(permissions).noneMatch(current::contains)) throw new BusinessException(ResultCode.FORBIDDEN);
    }
}
