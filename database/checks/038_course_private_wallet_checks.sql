-- 只读检查：在迁移前/后手工针对已明确的目标库执行，禁止把输出作为自动修复依据。
SELECT DATABASE() AS target_database, VERSION() AS mysql_version;

-- 首次迁移前以下新对象应不存在；部分失败恢复时按实际对象判定已完成的DDL。
SELECT table_name FROM information_schema.tables
WHERE table_schema=DATABASE() AND table_name IN
 ('course_private_contract','course_reserved_wallet','course_reserved_wallet_ledger',
  'course_reserved_transfer','course_private_contract_history') ORDER BY table_name;
SELECT column_name,column_type,column_default,is_nullable FROM information_schema.columns
WHERE table_schema=DATABASE() AND table_name='course_order' AND column_name IN
 ('amount','price_source','payment_source','private_contract_id','wallet_id','contract_version',
  'client_request_id','request_fingerprint','refunded_at');

-- 迁移后新增CHECK必须ENFORCED=YES，外键与唯一索引应与038一致。
SELECT table_name,constraint_name,enforced FROM information_schema.table_constraints
WHERE constraint_schema=DATABASE() AND (table_name LIKE 'course_reserved_%' OR table_name='course_private_contract'
 OR table_name='course_order') AND constraint_type='CHECK' ORDER BY table_name,constraint_name;
SELECT table_name,index_name,non_unique,GROUP_CONCAT(column_name ORDER BY seq_in_index) AS indexed_columns
FROM information_schema.statistics WHERE table_schema=DATABASE()
AND table_name IN ('course_private_contract','course_reserved_wallet','course_reserved_wallet_ledger','course_reserved_transfer','course_order')
GROUP BY table_name,index_name,non_unique ORDER BY table_name,index_name;
SELECT r.role_code,p.permission_code FROM sys_role r JOIN sys_role_permission rp ON rp.role_id=r.id
JOIN sys_permission p ON p.id=rp.permission_id WHERE p.permission_code LIKE 'course-private-price:%'
OR p.permission_code LIKE 'course-wallet:%' ORDER BY r.role_code,p.permission_code;
