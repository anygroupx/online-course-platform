package com.course.platform.security;

import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;
import com.course.platform.common.security.SecurityRoles;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 当前登录用户工具
 */
public final class SecurityUtils {
    private SecurityUtils() {}

    public static Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof Long id) {
            return id;
        }
        try {
            return Long.parseLong(principal.toString());
        } catch (Exception e) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
    }

    public static boolean hasRole(String role) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        String expected = SecurityRoles.toSpringRole(role);
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (expected.equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    public static boolean isAdmin() {
        return hasRole(SecurityRoles.ADMIN);
    }

    public static boolean isCustomerService() {
        return hasRole(SecurityRoles.CS) || isAdmin();
    }

    public static void requireAdmin() {
        if (!isAdmin()) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
    }

    public static void requireCustomerService() {
        if (!isCustomerService()) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
    }
}
