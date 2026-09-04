package com.course.platform.common.security;

/**
 * Canonical RBAC role and permission codes. These values are persisted in the
 * RBAC tables and are the only authorities trusted by the server.
 */
public final class SecurityAuthorities {
    private SecurityAuthorities() {}

    public static final String ROLE_SUPER_ADMIN = "ROLE_SUPER_ADMIN";
    public static final String ROLE_OPERATOR = "ROLE_OPERATOR";
    public static final String ROLE_FINANCE = "ROLE_FINANCE";
    public static final String ROLE_CUSTOMER_SERVICE = "ROLE_CUSTOMER_SERVICE";
    public static final String ROLE_USER = "ROLE_USER";
    public static final String ROLE_AUDITOR = "ROLE_AUDITOR";

    public static final String USER_READ = "user:read";
    public static final String USER_UPDATE = "user:update";
    public static final String ORDER_READ = "order:read";
    public static final String ORDER_UPDATE = "order:update";
    public static final String PAYMENT_READ = "payment:read";
    public static final String PAYMENT_REFUND = "payment:refund";
    public static final String PAYMENT_CONFIG = "payment:config";
    public static final String PAYMENT_RECONCILE = "payment:reconcile";
    public static final String ANNOUNCEMENT_CREATE = "announcement:create";
    public static final String ANNOUNCEMENT_UPDATE = "announcement:update";
    public static final String ANNOUNCEMENT_DELETE = "announcement:delete";
    public static final String ANNOUNCEMENT_PUBLISH = "announcement:publish";
    public static final String CUSTOMER_SERVICE_READ = "customer-service:read";
    public static final String CUSTOMER_SERVICE_ASSIGN = "customer-service:assign";
    public static final String CUSTOMER_SERVICE_TAKE = "customer-service:take";
    public static final String CUSTOMER_SERVICE_READ_ANY = "customer-service:read:any";
    public static final String API_PROVIDER_READ = "api-provider:read";
    public static final String API_PROVIDER_UPDATE = "api-provider:update";
    public static final String SECURITY_EVENT_READ = "security:event:read";
    public static final String SYSTEM_CONFIG_READ = "system-config:read";
    public static final String SYSTEM_CONFIG_UPDATE = "system-config:update";
    public static final String PLATFORM_READ = "platform:read";
    public static final String PLATFORM_UPDATE = "platform:update";
    public static final String MFA_MANAGE = "mfa:manage";
    public static final String RBAC_MANAGE = "rbac:manage";
}
