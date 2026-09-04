-- 012 Daytime（29 系统）对接支持
-- 新增第三方“已退款”对应的本地等待退款状态。

INSERT INTO `system_variable`
  (`variable_key`, `variable_name`, `variable_type`, `variable_value`, `variable_label`,
   `sort_order`, `is_default`, `is_enabled`, `color`, `icon`)
SELECT
  'refund_pending', '等待退款', 'order_status', '8', '第三方已退款，等待本地退款处理',
  9, 0, 1, '#d97706', 'refresh-cw'
WHERE NOT EXISTS (
  SELECT 1
  FROM `system_variable`
  WHERE `variable_key` = 'refund_pending'
    AND `variable_type` = 'order_status'
);

ALTER TABLE `course_order`
  MODIFY COLUMN `order_status` TINYINT DEFAULT 0
  COMMENT '订单状态：0-待处理 1-进行中 2-已完成 3-已取消 4-失败 5-待考试 6-考试中 7-考试完成 8-等待退款';
