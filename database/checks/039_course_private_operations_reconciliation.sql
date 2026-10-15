-- 迁移后只读核查；结果须人工复核，不能自动改价、改余额或取消历史权益。
-- LEGACY初始值核对只用于升级验收，后续经授权修改产生合法差异必须核查审计。
SELECT c.id AS legacy_contract_mismatch FROM course_private_contract c
LEFT JOIN course_private_plan_version v ON v.legacy_contract_id=c.id
LEFT JOIN course_private_contract_term t ON t.contract_id=c.id AND t.term_no=1
WHERE c.operations_mode='LEGACY_PERMANENT' AND (v.id IS NULL OR t.id IS NULL OR v.fixed_price<>c.fixed_price
 OR v.initial_deposit_amount<>c.min_initial_deposit OR NOT (t.start_at<=>c.valid_from)
 OR NOT (t.end_at<=>c.valid_until) OR NOT (t.activated_at<=>c.activated_at));
-- 交叉有效期属于业务锁保障，SQL额外发现遗留、人工写库或程序错误。
SELECT a.contract_id,a.id AS first_term_id,b.id AS overlapping_term_id
FROM course_private_contract_term a JOIN course_private_contract_term b ON b.contract_id=a.contract_id AND b.id>a.id
WHERE a.status IN ('SCHEDULED','ACTIVE') AND b.status IN ('SCHEDULED','ACTIVE')
 AND (b.end_at IS NULL OR a.start_at IS NULL OR a.start_at<b.end_at)
 AND (a.end_at IS NULL OR b.start_at IS NULL OR b.start_at<a.end_at);
-- 资金资格不冻结本金，但来源必须是同代理、同钱包、同商品真实转入。
SELECT f.id AS invalid_funding_allocation FROM course_private_funding_allocation f
JOIN course_private_contract_term t ON t.id=f.term_id
JOIN course_reserved_wallet w ON w.id=f.wallet_id JOIN course_reserved_wallet_ledger l ON l.id=f.ledger_id
WHERE w.contract_id<>t.contract_id OR w.platform_id<>t.platform_id OR l.wallet_id<>w.id
 OR l.user_id<>f.user_id OR l.biz_type<>'TRANSFER_IN' OR l.direction<>1 OR f.amount>l.amount;
-- 订单归档不能移除退款核查；可信完成及退款都不能从任意update_time猜测。
SELECT x.id AS inconsistent_term_order FROM course_private_term_order x JOIN course_order o ON o.id=x.order_id
WHERE x.term_id<>o.private_term_id OR x.ordered_amount<>o.amount
 OR (x.qualified_amount>0 AND (o.refunded_at IS NOT NULL OR EXISTS (
  SELECT 1 FROM course_reserved_wallet_ledger l WHERE l.wallet_id=o.wallet_id AND l.biz_type='REFUND' AND l.biz_no=o.order_no AND l.direction=1)))
 OR (x.qualified_amount>0 AND (o.fulfilled_at IS NULL OR x.completed_at IS NULL));
SELECT r.id AS invalid_release_execution FROM course_wallet_release_request r
LEFT JOIN course_reserved_transfer t ON t.id=r.executed_transfer_id
WHERE r.status='EXECUTED' AND (t.id IS NULL OR t.type<>'TRANSFER_OUT' OR t.amount<>r.amount
 OR t.user_id<>r.user_id OR t.wallet_id<>r.wallet_id OR t.approved_by<>r.reviewed_by);
