package com.course.platform.security;

import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;
import com.course.platform.common.security.SecurityAuthorities;
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

    public static boolean hasAuthority(String authorityName) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authorityName == null) {
            return false;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (authorityName.equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasRole(String role) {
        if (role == null || role.isBlank()) {
            return false;
        }
        String normalized = role.trim().toUpperCase();
        return hasAuthority(normalized.startsWith("ROLE_") ? normalized : "ROLE_" + normalized);
    }

    public static boolean isAdmin() {
        return hasAuthority(SecurityAuthorities.ROLE_SUPER_ADMIN);
    }

    public static boolean isCustomerService() {
        return hasAuthority(SecurityAuthorities.CUSTOMER_SERVICE_READ);
    }

    public static void requireAuthority(String authorityName) {
        if (!hasAuthority(authorityName)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
    }

    public static void requireAdmin() {
        requireAuthority(SecurityAuthorities.ROLE_SUPER_ADMIN);
    }

    public static void requireCustomerService() {
        requireAuthority(SecurityAuthorities.CUSTOMER_SERVICE_READ);
    }
}
