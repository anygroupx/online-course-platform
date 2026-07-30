package com.course.platform.common.security;

/**
 * 系统角色与权限常量
 */
public final class SecurityRoles {
    private SecurityRoles() {}

    public static final String ADMIN = "ADMIN";
    public static final String CS = "CS";
    public static final String USER = "USER";

    public static final String ROLE_ADMIN = "ROLE_ADMIN";
    public static final String ROLE_CS = "ROLE_CS";
    public static final String ROLE_USER = "ROLE_USER";

    public static String toSpringRole(String role) {
        if (role == null || role.isBlank()) {
            return ROLE_USER;
        }
        String normalized = role.trim().toUpperCase();
        if (normalized.startsWith("ROLE_")) {
            return normalized;
        }
        return "ROLE_" + normalized;
    }
}
