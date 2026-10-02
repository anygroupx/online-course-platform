import { readFileSync } from 'node:fs';

// UI-only mocked HTTP fixtures. Derive the projection from the shipped SQL rather
// than from isAdmin or a second hand-maintained permission policy. Security is
// independently verified by rbac.browser.mjs against real authentication/SQL.
const roles = ['SUPER_ADMIN', 'OPERATOR', 'FINANCE', 'CUSTOMER_SERVICE', 'AUDITOR', 'USER'];
const grants = new Map(roles.map(role => [role, new Set()]));
const permissions = new Set();
const codes = sql => [...sql.matchAll(/'([a-z][a-z-]*:[a-z:-]+)'/g)].map(match => match[1]);
const schema = readFileSync(new URL('../../../database/schema.sql', import.meta.url), 'utf8');
for (const statement of schema.replace(/^--.*$/gm, '').split(';')) {
  if (/INSERT(?: IGNORE)? INTO `?sys_permission`?\s/i.test(statement)) {
    for (const code of codes(statement)) permissions.add(code);
  } else if (/DELETE FROM sys_role_permission\s/i.test(statement)) {
    // Fresh-schema policy has no unsafe grants in the NOT IN preflight subset.
    if (/role_id NOT IN/i.test(statement)) continue;
    for (const code of codes(statement)) for (const set of grants.values()) set.delete(code);
  } else if (/INSERT(?: IGNORE)? INTO `?sys_role_permission`?\s/i.test(statement)) {
    const match = statement.match(/r\.role_code\s*(?:=\s*'([A-Z_]+)'|IN\s*\(([^)]+)\))/);
    if (!match) throw new Error(`Unsupported RBAC fixture policy: ${statement}`);
    const targets = match[1] ? [match[1]] : [...match[2].matchAll(/'([A-Z_]+)'/g)].map(m => m[1]);
    const selected = codes(statement);
    for (const role of targets) for (const code of selected.length ? selected : permissions) grants.get(role).add(code);
  }
}

export function mockAuthority(...requested) {
  if (!requested.length || requested.some(role => !grants.has(role))) throw new Error('Unknown fixture role');
  const roleOrder = ['SUPER_ADMIN', 'FINANCE', 'OPERATOR', 'CUSTOMER_SERVICE', 'AUDITOR', 'USER'];
  return {
    roles: [...new Set(requested)].sort(),
    permissions: [...new Set(requested.flatMap(role => [...grants.get(role)]))].sort(),
    role: roleOrder.find(role => requested.includes(role)),
    isAdmin: requested.includes('SUPER_ADMIN'),
  };
}
