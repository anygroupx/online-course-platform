import { hasAnyPermission, hasAllPermissions, hasRole } from './permissions.js';

export const routePermissions = Object.freeze({
  '/settings': ['system-config:read', 'system-config:update'],
  '/admin/platforms': ['platform:read', 'platform:update'],
  '/admin/categories': ['platform:read', 'platform:update'],
  '/admin/api-providers': ['api-provider:read', 'api-provider:update'],
  '/admin/plugin-integrations': ['api-provider:read', 'api-provider:update'],
  '/admin/service-products': ['service-product:read', 'service-product:update'],
  '/admin/service-orders': ['service-order:read', 'service-order:fulfill', 'service-order:reconcile', 'service-order:refund'],
  '/admin/service-projects': ['service-project:read', 'service-project:update', 'payment:reconcile'],
  '/admin/orders': ['order:update'],
  '/admin/cards': ['payment:read', 'payment:config'],
  '/admin/announcements': ['announcement:create', 'announcement:update', 'announcement:delete', 'announcement:publish'],
  '/admin/variables': ['system-config:read', 'system-config:update'],
  '/theme-config': ['system-config:read', 'system-config:update'],
  '/admin/countdown': ['order:update'],
  '/admin/aqks': ['order:update'],
  '/admin/customer-service': ['customer-service:read'],
  '/admin/rbac': ['rbac:manage'],
  '/logs': ['security:event:read'],
});
export function canAccessMeta(info, meta = {}) {
  return (!meta.permissionsAny || hasAnyPermission(info, meta.permissionsAny))
    && (!meta.permissionsAll || hasAllPermissions(info, meta.permissionsAll))
    && (!meta.rolesAny || (Array.isArray(meta.rolesAny) && meta.rolesAny.some(code => hasRole(info, code))));
}
export function canAccessPath(info, path) {
  return !!routePermissions[path] && hasAnyPermission(info, routePermissions[path]);
}
