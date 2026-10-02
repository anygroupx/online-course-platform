/** UX-only checks. Backend authorization always reloads database authorities. */
export function hasPermission(info, code) {
  return typeof code === 'string' && Array.isArray(info?.permissions) && info.permissions.includes(code);
}
export function hasAnyPermission(info, codes) {
  return Array.isArray(codes) && codes.some(code => hasPermission(info, code));
}
export function hasAllPermissions(info, codes) {
  return Array.isArray(codes) && codes.every(code => hasPermission(info, code));
}
export function hasRole(info, code) {
  return typeof code === 'string' && Array.isArray(info?.roles) && info.roles.includes(code);
}
