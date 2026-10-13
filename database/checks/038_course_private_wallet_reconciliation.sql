-- 只读对账：建议暂停资金写入，或在同一一致性只读事务中执行，避免跨时刻观察导致误报。
-- 所有以下异常结果集在正常业务数据库中应为空。禁止通过UPDATE余额消除差异。

-- 专户余额必须对应最后一笔真实流水，尚无流水的新钱包只能为0。
SELECT w.id AS wallet_id,w.user_id,w.platform_id,w.balance,l.balance_after,l.biz_no
FROM course_reserved_wallet w LEFT JOIN course_reserved_wallet_ledger l
ON l.id=(SELECT MAX(x.id) FROM course_reserved_wallet_ledger x WHERE x.wallet_id=w.id)
WHERE w.balance<>COALESCE(l.balance_after,0);

-- 同一划转单号对应两侧等额反向流水，内部划转不能作为真实充值。
SELECT t.id,t.biz_no,t.wallet_id,t.user_id,t.type,t.amount,
 g.amount AS general_amount,g.direction AS general_direction,l.amount AS wallet_amount,l.direction AS wallet_direction
FROM course_reserved_transfer t LEFT JOIN account_ledger g
 ON g.user_id=t.user_id AND g.biz_type='COURSE_TRANSFER' AND g.biz_no=t.biz_no
 AND g.direction=CASE WHEN t.type='TRANSFER_IN' THEN -1 ELSE 1 END
LEFT JOIN course_reserved_wallet_ledger l ON l.wallet_id=t.wallet_id AND l.biz_type=t.type AND l.biz_no=t.biz_no
 AND l.direction=CASE WHEN t.type='TRANSFER_IN' THEN 1 ELSE -1 END
WHERE t.status='SUCCEEDED' AND (g.id IS NULL OR l.id IS NULL OR g.amount<>t.amount OR l.amount<>t.amount
 OR g.direction+l.direction<>0);

-- 已记录退款的专户订单必须有原始等额扣款和退款，归档记录也保留用于核对。
SELECT o.id,o.order_no,o.wallet_id,o.amount,o.refunded_at FROM course_order o
WHERE o.payment_source='COURSE_WALLET' AND
 (NOT EXISTS(SELECT 1 FROM course_reserved_wallet_ledger l WHERE l.wallet_id=o.wallet_id
   AND l.user_id=o.user_id AND l.biz_type='ORDER' AND l.biz_no=o.order_no AND l.direction=-1 AND l.amount=o.amount)
 OR o.refunded_at IS NOT NULL AND NOT EXISTS(SELECT 1 FROM course_reserved_wallet_ledger r WHERE r.wallet_id=o.wallet_id
   AND r.user_id=o.user_id AND r.biz_type='REFUND' AND r.biz_no=o.order_no AND r.direction=1 AND r.amount=o.amount));

-- 绑定异常不应出现，SQL同时用于发现导入工具或权限过大的手工操作造成的异常。
SELECT w.id,w.contract_id,w.user_id,w.platform_id FROM course_reserved_wallet w
LEFT JOIN course_private_contract c ON c.id=w.contract_id
WHERE c.id IS NULL OR w.user_id<>c.user_id OR w.platform_id<>c.platform_id;
