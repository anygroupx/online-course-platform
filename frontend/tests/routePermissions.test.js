import test from 'node:test';
import assert from 'node:assert/strict';
import routes from '../src/router/routes.js';
import { canAccessPath, canAccessMeta } from '../src/utils/routePermissions.js';
import { mockAuthority } from './fixtures/rbac-policy.mjs';

const operatorPaths = ['/admin/service-products', '/admin/service-orders', '/admin/service-projects', '/admin/api-providers'];
test('the shipped role matrix drives administrative routes, independently of primary role/isAdmin', () => {
  for (const role of ['OPERATOR', 'AUDITOR']) {
    const profile = mockAuthority(role);
    assert.equal(profile.isAdmin, false);
    for (const path of operatorPaths) assert.ok(canAccessPath(profile, path), `${role}: ${path}`);
  }
  const finance = mockAuthority('FINANCE');
  assert.ok(canAccessPath(finance, '/admin/service-orders'));
  assert.ok(canAccessPath(finance, '/admin/service-projects'), 'project finance needs payment reconciliation records');
  for (const path of ['/admin/service-products', '/admin/api-providers', '/admin/customer-service']) assert.equal(canAccessPath(finance, path), false);
  const support = mockAuthority('CUSTOMER_SERVICE');
  assert.ok(canAccessPath(support, '/admin/customer-service'));
  for (const path of operatorPaths) assert.equal(canAccessPath(support, path), false);
  for (const path of [...operatorPaths, '/admin/customer-service', '/admin/rbac']) {
    assert.equal(canAccessPath(mockAuthority('USER'), path), false);
    assert.ok(canAccessPath(mockAuthority('SUPER_ADMIN'), path));
  }
  const combined = mockAuthority('OPERATOR', 'FINANCE');
  assert.equal(combined.role, 'FINANCE');
  for (const path of operatorPaths) assert.ok(canAccessPath(combined, path));
  assert.ok(combined.permissions.includes('service-order:refund'));
  assert.ok(combined.permissions.includes('service-order:fulfill'));
});

test('real route records use permission metadata and deny missing or malformed projections', () => {
  const adminRoutes = routes.find(route => route.path === '/').children.filter(route => route.path.startsWith('admin/'));
  for (const route of adminRoutes) {
    assert.equal(route.meta.adminOnly, undefined);
    assert.ok(route.meta.permissionsAny?.length, route.path);
    assert.equal(canAccessMeta({ isAdmin: true, role: 'SUPER_ADMIN' }, route.meta), false);
    assert.ok(canAccessMeta(mockAuthority('SUPER_ADMIN'), route.meta));
  }
  assert.equal(canAccessMeta(mockAuthority('OPERATOR'), { permissionsAll: ['service-order:fulfill', 'service-order:refund'] }), false);
  assert.ok(canAccessMeta(mockAuthority('OPERATOR', 'FINANCE'), { permissionsAll: ['service-order:fulfill', 'service-order:refund'] }));
  assert.equal(canAccessMeta(mockAuthority('USER'), { permissionsAny: 'service-order:read' }), false);
  assert.equal(canAccessMeta(mockAuthority('USER'), { rolesAny: ['OPERATOR'] }), false);
});
