-- =============================================
-- 支付宝支付功能数据库迁移脚本
-- 作者：AI Assistant
-- 日期：2025-11-26
-- 说明：支持PC和手机网站支付的账户充值功能
-- =============================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- =============================================
-- 1. 支付订单表 (payment_order)
-- =============================================
DROP TABLE IF EXISTS `payment_order`;
CREATE TABLE `payment_order` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '订单ID',
  `order_no` VARCHAR(32) NOT NULL COMMENT '订单编号',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `amount` DECIMAL(10,2) NOT NULL COMMENT '支付金额',
  `subject` VARCHAR(255) NOT NULL COMMENT '订单标题',
  `body` VARCHAR(500) DEFAULT NULL COMMENT '订单描述',
  
  -- 支付方式：PC/WAP
  `payment_type` VARCHAR(20) NOT NULL COMMENT '支付方式：PC-电脑网站支付 WAP-手机网站支付',
  
  -- 支付状态
  `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '订单状态：PENDING-待支付 PAID-已支付 CLOSED-已关闭 REFUNDING-退款中 REFUNDED-已退款',
  
  -- 支付宝交易信息
  `alipay_trade_no` VARCHAR(64) DEFAULT NULL COMMENT '支付宝交易号',
  `buyer_logon_id` VARCHAR(100) DEFAULT NULL COMMENT '买家支付宝账号',
  `buyer_user_id` VARCHAR(32) DEFAULT NULL COMMENT '买家支付宝用户ID',
  
  -- 时间信息
  `paid_time` DATETIME DEFAULT NULL COMMENT '支付时间',
  `close_time` DATETIME DEFAULT NULL COMMENT '关闭时间',
  `timeout_express` INT DEFAULT 30 COMMENT '超时时间(分钟)',
  
  -- 退款信息
  `refund_amount` DECIMAL(10,2) DEFAULT 0.00 COMMENT '退款金额',
  `refund_reason` VARCHAR(255) DEFAULT NULL COMMENT '退款原因',
  `refund_time` DATETIME DEFAULT NULL COMMENT '退款时间',
  
  -- 回调地址
  `return_url` VARCHAR(500) DEFAULT NULL COMMENT '同步回调地址',
  `notify_url` VARCHAR(500) DEFAULT NULL COMMENT '异步通知地址',
  
  -- 扩展信息
  `client_ip` VARCHAR(50) DEFAULT NULL COMMENT '客户端IP',
  `user_agent` VARCHAR(500) DEFAULT NULL COMMENT '用户代理',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  UNIQUE KEY `uk_alipay_trade_no` (`alipay_trade_no`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_status` (`status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付订单表';

-- =============================================
-- 2. 支付配置表 (payment_config)
-- =============================================
DROP TABLE IF EXISTS `payment_config`;
CREATE TABLE `payment_config` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '配置ID',
  `config_name` VARCHAR(100) NOT NULL COMMENT '配置名称',
  `env_type` VARCHAR(20) NOT NULL COMMENT '环境类型：SANDBOX-沙箱 PRODUCTION-生产',
  
  -- 支付宝应用配置
  `app_id` VARCHAR(32) NOT NULL COMMENT '支付宝应用APPID',
  `private_key` TEXT NOT NULL COMMENT '应用私钥',
  `alipay_public_key` TEXT NOT NULL COMMENT '支付宝公钥',
  `sign_type` VARCHAR(10) DEFAULT 'RSA2' COMMENT '签名类型：RSA/RSA2',
  `format` VARCHAR(10) DEFAULT 'json' COMMENT '数据格式',
  `charset` VARCHAR(10) DEFAULT 'utf-8' COMMENT '字符编码',
  
  -- 网关地址
  `gateway_url` VARCHAR(255) NOT NULL COMMENT '支付宝网关地址',
  
  -- 回调地址配置
  `notify_url` VARCHAR(500) DEFAULT NULL COMMENT '异步通知地址(可选,支持订单级别覆盖)',
  `return_url` VARCHAR(500) DEFAULT NULL COMMENT '同步回调地址(可选,支持订单级别覆盖)',
  
  -- 状态控制
  `is_active` TINYINT DEFAULT 0 COMMENT '是否激活：0-未激活 1-已激活',
  `status` TINYINT DEFAULT 1 COMMENT '状态：0-禁用 1-启用',
  
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  
  PRIMARY KEY (`id`),
  KEY `idx_env_type` (`env_type`),
  KEY `idx_is_active` (`is_active`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付配置表';

-- 插入沙箱环境示例配置
INSERT INTO `payment_config` 
  (`config_name`, `env_type`, `app_id`, `private_key`, `alipay_public_key`, `gateway_url`, `is_active`, `status`) 
VALUES 
  ('支付宝沙箱环境', 'SANDBOX', 
   '请替换为您的沙箱APPID', 
   '请替换为您的应用私钥', 
   '请替换为支付宝沙箱公钥',
   'https://openapi-sandbox.dl.alipaydev.com/gateway.do',
   1, 1);

-- =============================================
-- 3. 支付异步通知日志表 (payment_notify_log)
-- =============================================
DROP TABLE IF EXISTS `payment_notify_log`;
CREATE TABLE `payment_notify_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `order_no` VARCHAR(32) NOT NULL COMMENT '订单编号',
  `alipay_trade_no` VARCHAR(64) DEFAULT NULL COMMENT '支付宝交易号',
  
  -- 通知内容
  `notify_params` TEXT NOT NULL COMMENT '通知参数(JSON)',
  `notify_type` VARCHAR(50) DEFAULT NULL COMMENT '通知类型',
  `trade_status` VARCHAR(50) DEFAULT NULL COMMENT '交易状态',
  
  -- 验证结果
  `verify_result` TINYINT DEFAULT 0 COMMENT '签名验证结果：0-失败 1-成功',
  `verify_message` VARCHAR(500) DEFAULT NULL COMMENT '验证消息',
  
  -- 处理结果
  `process_status` TINYINT DEFAULT 0 COMMENT '处理状态：0-待处理 1-处理成功 2-处理失败',
  `process_message` VARCHAR(500) DEFAULT NULL COMMENT '处理消息',
  `process_time` DATETIME DEFAULT NULL COMMENT '处理时间',
  
  -- 响应结果
  `response_content` VARCHAR(100) DEFAULT NULL COMMENT '响应内容',
  
  -- 请求信息
  `request_ip` VARCHAR(50) DEFAULT NULL COMMENT '请求IP',
  `request_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '请求时间',
  
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  
  PRIMARY KEY (`id`),
  KEY `idx_order_no` (`order_no`),
  KEY `idx_alipay_trade_no` (`alipay_trade_no`),
  KEY `idx_process_status` (`process_status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付异步通知日志表';

-- =============================================
-- 4. 扩展充值记录表
-- =============================================
-- 添加支付订单关联字段
ALTER TABLE `recharge_record` 
ADD COLUMN `payment_order_id` BIGINT DEFAULT NULL COMMENT '支付订单ID' AFTER `user_id`,
ADD KEY `idx_payment_order_id` (`payment_order_id`);

SET FOREIGN_KEY_CHECKS = 1;

-- =============================================
-- 数据库迁移说明
-- =============================================
-- 1. payment_order: 独立的支付订单表,与课程订单分离
-- 2. payment_config: 支持多环境配置,可动态切换
-- 3. payment_notify_log: 记录所有异步通知,便于调试和幂等性检查
-- 4. recharge_record: 扩展关联支付订单,形成完整的充值链路
-- =============================================
