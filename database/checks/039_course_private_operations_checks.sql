-- 只读清单同时适用于迁移前、部分DDL失败和迁移后；不得据此自动DROP或补填财务事实。
SELECT DATABASE() AS target_database,VERSION() AS mysql_version;
SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN
 ('course_private_plan','course_private_plan_version','course_private_contract_term','course_private_term_order',
  'course_private_application','course_wallet_release_request','course_private_term_event','course_private_term_settlement',
  'course_private_funding_allocation','course_private_cost_version','course_private_risk_approval','course_private_notification')
ORDER BY table_name;
SELECT table_name,column_name,column_type,is_nullable,column_default FROM information_schema.columns
WHERE table_schema=DATABASE() AND
 ((table_name='course_private_contract' AND column_name IN ('operations_mode','current_term_id','relation_generation'))
 OR (table_name='course_order' AND column_name IN ('private_term_id','private_term_version','private_plan_version_id','private_operations_mode','fulfilled_at','fulfillment_source'))
 OR (table_name='course_private_contract_term' AND column_name IN ('funding_start_ledger_id','funding_end_ledger_id')))
ORDER BY table_name,ordinal_position;
SELECT table_name,constraint_name,constraint_type,enforced FROM information_schema.table_constraints
WHERE constraint_schema=DATABASE() AND constraint_name LIKE '%cpop%' ORDER BY table_name,constraint_name;
SELECT table_name,index_name,non_unique,GROUP_CONCAT(column_name ORDER BY seq_in_index) AS indexed_columns
FROM information_schema.statistics WHERE table_schema=DATABASE() AND index_name LIKE '%cpop%'
GROUP BY table_name,index_name,non_unique ORDER BY table_name,index_name;
SELECT trigger_name,event_manipulation,event_object_table FROM information_schema.triggers
WHERE trigger_schema=DATABASE() AND trigger_name LIKE 'trg_cpop_%' ORDER BY trigger_name;
SELECT r.role_code,p.permission_code FROM sys_role r JOIN sys_role_permission rp ON rp.role_id=r.id
JOIN sys_permission p ON p.id=rp.permission_id WHERE p.permission_code LIKE 'course-private-plan:%'
 OR p.permission_code LIKE 'course-private-contract:%' OR p.permission_code LIKE 'course-private-term:%'
 OR p.permission_code LIKE 'course-private-settlement:%' OR p.permission_code LIKE 'course-wallet-release:%'
 OR p.permission_code LIKE 'course-private-analytics:%' OR p.permission_code LIKE 'course-private-cost:%'
 OR p.permission_code LIKE 'course-private-risk:%' ORDER BY r.role_code,p.permission_code;
