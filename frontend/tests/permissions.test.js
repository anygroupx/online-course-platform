import test from 'node:test';
import assert from 'node:assert/strict';
import { hasPermission, hasAnyPermission, hasAllPermissions, hasRole } from '../src/utils/permissions.js';
test('UX permissions use the full union, never legacy administrator flags', () => {
  const info = { role: 'FINANCE', roles: ['OPERATOR', 'FINANCE'], permissions: ['service-order:fulfill', 'service-order:refund'] };
  assert.ok(hasRole(info, 'OPERATOR'));
  assert.ok(hasAllPermissions(info, ['service-order:fulfill', 'service-order:refund']));
  assert.ok(hasAnyPermission(info, ['service-order:read', 'service-order:refund']));
  assert.equal(hasPermission({isAdmin:true,role:'SUPER_ADMIN'}, 'service-order:refund'), false);
  assert.equal(hasPermission(info, 'service-order:biometric'), false);
  for (const malformed of [null, {}, {permissions:'service-order:refund'}]) assert.equal(hasPermission(malformed, 'service-order:refund'), false);
});
