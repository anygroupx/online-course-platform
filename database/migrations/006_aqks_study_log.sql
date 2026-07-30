-- =============================================
-- AQKS刷课日志表
-- 记录自营订单的刷课操作历史
-- @author AI Assistant
-- @since 2025-12-20
-- =============================================

CREATE TABLE IF NOT EXISTS `aqks_study_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    `order_id` BIGINT NOT NULL COMMENT '订单ID',
    `order_no` VARCHAR(50) NOT NULL COMMENT '订单号',
    `aqks_user_id` VARCHAR(50) NOT NULL COMMENT 'AQKS用户ID',
    `student_account` VARCHAR(100) NOT NULL COMMENT '学生账号',
    `operation_type` VARCHAR(20) NOT NULL COMMENT '操作类型：add-刷时长, login-登录, status-状态查询',
    `delta_minutes` INT DEFAULT NULL COMMENT '本次累加时长（分钟）',
    `before_minutes` INT DEFAULT NULL COMMENT '操作前总时长',
    `after_minutes` INT DEFAULT NULL COMMENT '操作后总时长',
    `required_minutes` INT DEFAULT NULL COMMENT '要求时长',
    `progress` VARCHAR(20) DEFAULT NULL COMMENT '进度百分比',
    `status` TINYINT DEFAULT 1 COMMENT '状态：1-成功 0-失败',
    `error_msg` VARCHAR(500) DEFAULT NULL COMMENT '错误信息',
    `operator_id` BIGINT DEFAULT NULL COMMENT '操作人ID',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_order_id` (`order_id`),
    KEY `idx_order_no` (`order_no`),
    KEY `idx_aqks_user_id` (`aqks_user_id`),
    KEY `idx_operation_type` (`operation_type`),
    KEY `idx_create_time` (`create_time`),
    -- 将复合索引随表创建，兼容 MySQL 8.0 并保持首次迁移的原子性。
    KEY `idx_order_status` (`order_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AQKS刷课日志表';
