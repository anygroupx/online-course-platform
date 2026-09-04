package com.course.platform.security;

import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;
import com.course.platform.common.security.SecurityAuthorities;
import com.course.platform.domain.entity.CourseOrder;
import com.course.platform.domain.entity.CustomerServiceSession;
import com.course.platform.domain.entity.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Central object-level authorization policy. Roles never bypass ownership by
 * themselves: broad access must be represented by an explicit permission.
 */
@Service("authorizationService")
public class ResourceAuthorizationService {

    public boolean canReadOrder(Authentication authentication, CourseOrder order) {
        Long actorId = principal(authentication);
        return order != null && actorId != null
                && (actorId.equals(order.getUserId()) || has(authentication, SecurityAuthorities.ORDER_READ));
    }

    public void requireCanReadOrder(CourseOrder order) {
        if (!canReadOrder(current(), order)) {
            // Hide existence from callers that do not own the identifier.
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }
    }

    public boolean canReadAllOrders(Authentication authentication) {
        return has(authentication, SecurityAuthorities.ORDER_READ);
    }

    public boolean canUpdateOrder(Authentication authentication, CourseOrder order) {
        Long actorId = principal(authentication);
        return order != null && actorId != null
                && (actorId.equals(order.getUserId()) || has(authentication, SecurityAuthorities.ORDER_UPDATE));
    }

    public void requireCanUpdateOrder(CourseOrder order) {
        if (!canUpdateOrder(current(), order)) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }
    }

    public boolean canAccessCustomerServiceSession(Authentication authentication,
                                                    CustomerServiceSession session) {
        Long actorId = principal(authentication);
        if (session == null || actorId == null) {
            return false;
        }
        return actorId.equals(session.getUserId())
                || actorId.equals(session.getCustomerServiceId())
                || has(authentication, SecurityAuthorities.CUSTOMER_SERVICE_READ_ANY);
    }

    public void requireCanAccessCustomerServiceSession(CustomerServiceSession session) {
        if (!canAccessCustomerServiceSession(current(), session)) {
            // Same externally visible result for missing and unauthorized sessions.
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "会话不存在");
        }
    }

    public boolean canReadUser(Authentication authentication, User target) {
        Long actorId = principal(authentication);
        return target != null && actorId != null
                && (actorId.equals(target.getId())
                || actorId.equals(target.getParentId())
                || has(authentication, SecurityAuthorities.USER_READ));
    }

    public boolean canUpdateUser(Authentication authentication, User target) {
        Long actorId = principal(authentication);
        return target != null && actorId != null
                && (actorId.equals(target.getParentId())
                || has(authentication, SecurityAuthorities.USER_UPDATE));
    }

    private Authentication current() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private Long principal(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof Long id) {
            return id;
        }
        try {
            return Long.valueOf(String.valueOf(principal));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private boolean has(Authentication authentication, String authorityName) {
        if (authentication == null) {
            return false;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (authorityName.equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
