-- Run with explicit deployment authorization and RBAC writes paused.
-- Check ALL four relations before changing ANY persistent schema; never delete orphans.
-- mysql must stop on errors (do not use --force). CHECK is enforced on MySQL >= 8.0.16.
CREATE TEMPORARY TABLE rbac_fk_preflight (
  orphan_count BIGINT NOT NULL,
  CONSTRAINT ck_rbac_no_orphans CHECK (orphan_count = 0)
);
INSERT INTO rbac_fk_preflight (orphan_count)
SELECT
  (SELECT COUNT(*) FROM sys_user_role ur LEFT JOIN sys_user u ON u.id = ur.user_id WHERE u.id IS NULL)
  + (SELECT COUNT(*) FROM sys_user_role ur LEFT JOIN sys_role r ON r.id = ur.role_id WHERE r.id IS NULL)
  + (SELECT COUNT(*) FROM sys_role_permission rp LEFT JOIN sys_role r ON r.id = rp.role_id WHERE r.id IS NULL)
  + (SELECT COUNT(*) FROM sys_role_permission rp LEFT JOIN sys_permission p ON p.id = rp.permission_id WHERE p.id IS NULL);
DROP TEMPORARY TABLE rbac_fk_preflight;

-- MySQL revalidates at ALTER time. DDL is not transactional: inspect any partial
-- DDL failure before retrying. CASCADE removes mappings, never users/roles/permissions.
ALTER TABLE sys_user_role
  ADD CONSTRAINT fk_sys_user_role_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
  ADD CONSTRAINT fk_sys_user_role_role FOREIGN KEY (role_id) REFERENCES sys_role(id) ON DELETE CASCADE;
ALTER TABLE sys_role_permission
  ADD CONSTRAINT fk_sys_role_permission_role FOREIGN KEY (role_id) REFERENCES sys_role(id) ON DELETE CASCADE,
  ADD CONSTRAINT fk_sys_role_permission_permission FOREIGN KEY (permission_id) REFERENCES sys_permission(id) ON DELETE CASCADE;
