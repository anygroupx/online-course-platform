-- =============================================
-- 在线网课平台数据库设计（优化版）
-- 作者：AI Assistant
-- 日期：2025-01-17
-- 说明：基于原有业务优化，符合SOLID原则
-- =============================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- =============================================
-- 1. 用户表 (sys_user)
-- =============================================
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `uid` CHAR(36) NOT NULL COMMENT '对外公开的随机UUID v4',
  `parent_id` BIGINT DEFAULT 0 COMMENT '上级代理ID',
  `username` VARCHAR(50) NOT NULL COMMENT '用户账号',
  `password` VARCHAR(255) NOT NULL COMMENT '密码（加密）',
  `nickname` VARCHAR(50) DEFAULT NULL COMMENT '昵称',
  `qq_openid` VARCHAR(100) DEFAULT NULL COMMENT 'QQ OpenID',
  `avatar` VARCHAR(255) DEFAULT NULL COMMENT '头像URL',
  `balance` DECIMAL(10,2) DEFAULT 0.00 COMMENT '账户余额',
  `total_recharge` DECIMAL(10,2) DEFAULT 0.00 COMMENT '总充值金额',
  `rate` DECIMAL(5,2) DEFAULT 1.00 COMMENT '费率倍数',
  `api_key` VARCHAR(64) DEFAULT NULL COMMENT 'API密钥（兼容历史数据，禁止接口返回）',
  `api_key_hash` VARCHAR(128) DEFAULT NULL COMMENT 'API Key SHA-256哈希',
  `api_key_prefix` VARCHAR(32) DEFAULT NULL COMMENT 'API Key可展示前缀',
  `api_key_scopes` VARCHAR(255) DEFAULT NULL COMMENT 'API Key作用域',
  `api_key_expire_time` DATETIME DEFAULT NULL COMMENT 'API Key过期时间',
  `invite_code` VARCHAR(20) DEFAULT NULL COMMENT '邀请码',
  `invite_rate` DECIMAL(5,2) DEFAULT NULL COMMENT '邀请费率',
  `notice` TEXT COMMENT '代理公告',
  `status` TINYINT DEFAULT 1 COMMENT '状态：0-禁用 1-正常',
  `role` VARCHAR(32) NOT NULL DEFAULT 'USER' COMMENT '历史角色镜像（授权以RBAC关系表为准）',
  `must_change_password` TINYINT NOT NULL DEFAULT 0 COMMENT '是否必须修改密码',
  `password_changed_at` DATETIME DEFAULT NULL COMMENT '密码最后修改时间',
  `mfa_enabled` TINYINT NOT NULL DEFAULT 0 COMMENT '是否启用MFA',
  `mfa_secret` VARCHAR(128) DEFAULT NULL COMMENT '加密的TOTP密钥',
  `mfa_enabled_at` DATETIME DEFAULT NULL COMMENT 'MFA启用时间',
  `mfa_backup_codes_hash` VARCHAR(512) DEFAULT NULL COMMENT '备用码哈希',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `last_login_time` DATETIME DEFAULT NULL COMMENT '最后登录时间',
  `last_login_ip` VARCHAR(50) DEFAULT NULL COMMENT '最后登录IP',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_uid` (`uid`),
  UNIQUE KEY `uk_username` (`username`),
  UNIQUE KEY `uk_invite_code` (`invite_code`),
  KEY `idx_parent_id` (`parent_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 安全说明：开源初始化脚本不创建默认管理员或 API 密钥。
-- 管理员账号应在部署后通过受控流程创建，并使用随机密码和唯一 API 密钥。

-- =============================================
-- 1.1 P0 RBAC（授权以服务端关系表为唯一可信来源）
-- =============================================
DROP TABLE IF EXISTS `sys_role_permission`;
DROP TABLE IF EXISTS `sys_user_role`;
DROP TABLE IF EXISTS `sys_permission`;
DROP TABLE IF EXISTS `sys_role`;

CREATE TABLE `sys_role` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `role_code` VARCHAR(64) NOT NULL,
  `role_name` VARCHAR(128) NOT NULL,
  `enabled` TINYINT NOT NULL DEFAULT 1,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_sys_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RBAC角色';

CREATE TABLE `sys_permission` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `permission_code` VARCHAR(128) NOT NULL,
  `permission_name` VARCHAR(128) NOT NULL,
  `enabled` TINYINT NOT NULL DEFAULT 1,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_sys_permission_code` (`permission_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RBAC权限';

CREATE TABLE `sys_user_role` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `role_id` BIGINT NOT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_sys_user_role` (`user_id`,`role_id`), KEY `idx_sys_user_role_role` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关系';

CREATE TABLE `sys_role_permission` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `role_id` BIGINT NOT NULL,
  `permission_id` BIGINT NOT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_sys_role_permission` (`role_id`,`permission_id`), KEY `idx_sys_role_permission_permission` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限关系';

INSERT INTO `sys_role` (`role_code`,`role_name`) VALUES
('SUPER_ADMIN','超级管理员'),('OPERATOR','运营'),('FINANCE','财务'),
('CUSTOMER_SERVICE','客服'),('USER','普通用户'),('AUDITOR','审计员');

INSERT INTO `sys_permission` (`permission_code`,`permission_name`) VALUES
('user:read','读取用户'),('user:update','更新用户'),('order:read','读取订单'),('order:update','更新订单'),
('payment:read','读取支付'),('payment:refund','支付退款'),('payment:config','支付配置'),('payment:reconcile','执行支付对账'),
('announcement:create','创建公告'),('announcement:update','更新公告'),('announcement:delete','删除公告'),('announcement:publish','发布/下线公告'),
('customer-service:read','读取客服会话'),('customer-service:assign','分配客服会话'),('customer-service:take','接管客服会话'),
('customer-service:read:any','读取任意客服会话'),('api-provider:read','读取API供应商'),('api-provider:update','更新API供应商'),
('security:event:read','读取安全事件'),('system-config:read','读取系统配置'),('system-config:update','更新系统配置'),
('platform:read','读取平台配置'),('platform:update','更新平台配置'),('mfa:manage','管理MFA'),('rbac:manage','管理RBAC角色');

INSERT INTO `sys_role_permission` (`role_id`,`permission_id`)
SELECT r.id,p.id FROM sys_role r CROSS JOIN sys_permission p WHERE r.role_code='SUPER_ADMIN';
INSERT INTO `sys_role_permission` (`role_id`,`permission_id`)
SELECT r.id,p.id FROM sys_role r JOIN sys_permission p ON p.permission_code IN
('user:read','user:update','order:read','order:update','announcement:create','announcement:update','announcement:delete','announcement:publish',
'api-provider:read','api-provider:update','system-config:read','system-config:update','platform:read','platform:update') WHERE r.role_code='OPERATOR';
INSERT INTO `sys_role_permission` (`role_id`,`permission_id`)
SELECT r.id,p.id FROM sys_role r JOIN sys_permission p ON p.permission_code IN
('user:read','order:read','payment:read','payment:refund','payment:config','payment:reconcile') WHERE r.role_code='FINANCE';
INSERT INTO `sys_role_permission` (`role_id`,`permission_id`)
SELECT r.id,p.id FROM sys_role r JOIN sys_permission p ON p.permission_code IN
('user:read','order:read','customer-service:read','customer-service:take') WHERE r.role_code='CUSTOMER_SERVICE';
INSERT INTO `sys_role_permission` (`role_id`,`permission_id`)
SELECT r.id,p.id FROM sys_role r JOIN sys_permission p ON p.permission_code IN
('user:read','order:read','payment:read','api-provider:read','security:event:read','system-config:read','platform:read') WHERE r.role_code='AUDITOR';

-- =============================================
-- 2. 课程平台表 (course_platform)
-- =============================================
DROP TABLE IF EXISTS `course_platform`;
CREATE TABLE `course_platform` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '平台ID',
  `name` VARCHAR(100) NOT NULL COMMENT '平台名称',
  `query_param` VARCHAR(50) DEFAULT NULL COMMENT '查询参数标识',
  `dock_param` VARCHAR(50) DEFAULT NULL COMMENT '对接参数标识',
  `base_price` DECIMAL(10,2) NOT NULL COMMENT '基础价格',
  `query_api_id` BIGINT DEFAULT NULL COMMENT '查询接口ID',
  `dock_api_id` BIGINT DEFAULT NULL COMMENT '对接接口ID',
  `rate_type` ENUM('MULTIPLY', 'ADD') DEFAULT 'MULTIPLY' COMMENT '费率计算方式：乘法/加法',
  `password_rule` VARCHAR(200) DEFAULT NULL COMMENT '密码生成规则：{account}表示账号，如{account}@ZII',
  `password_enabled` TINYINT DEFAULT 0 COMMENT '是否启用自动生成密码：0-禁用 1-启用',
  `is_self_operated` TINYINT DEFAULT 0 COMMENT '是否自营平台：0-否 1-是',
  `description` VARCHAR(500) DEFAULT NULL COMMENT '说明',
  `sort_order` INT DEFAULT 10 COMMENT '排序',
  `status` TINYINT DEFAULT 1 COMMENT '状态：0-下架 1-上架',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`),
  KEY `idx_sort` (`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程平台表';

-- 插入示例数据
INSERT INTO `course_platform` (`name`, `query_param`, `dock_param`, `base_price`, `query_api_id`, `dock_api_id`, `rate_type`, `password_rule`, `password_enabled`, `is_self_operated`, `sort_order`, `status`) 
VALUES 
('学习通', '1', '1', 10.00, 1, 1, 'MULTIPLY', '{account}@ZII', 1, 0, 1, 1),
('智慧职教-职教云', '智慧职教', '', 0.10, 2, 2, 'MULTIPLY', '', 0, 0, 2, 1),
('智慧职教-MOOC', '', '', 0.10, 3, 3, 'MULTIPLY', '', 0, 0, 3, 1),
('青书学堂', 'qhs', '1', 1.00, 4, 4, 'MULTIPLY', '', 0, 0, 4, 1),
('自营平台', 'self', 'self', 5.00, NULL, NULL, 'MULTIPLY', '', 0, 1, 5, 1);

-- =============================================
-- 3. 第三方接口配置表 (api_provider)
-- =============================================
DROP TABLE IF EXISTS `api_provider`;
CREATE TABLE `api_provider` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '接口ID',
  `provider_type` VARCHAR(50) NOT NULL COMMENT '接口类型标识',
  `name` VARCHAR(100) NOT NULL COMMENT '接口名称',
  `api_url` VARCHAR(255) DEFAULT NULL COMMENT 'API地址',
  `username` VARCHAR(100) DEFAULT NULL COMMENT '账号',
  `password` VARCHAR(255) DEFAULT NULL COMMENT '密码',
  `token` VARCHAR(500) DEFAULT NULL COMMENT 'Token',
  `api_key` VARCHAR(255) DEFAULT NULL COMMENT 'API Key',
  `cookie` TEXT DEFAULT NULL COMMENT 'Cookie',
  `balance` DECIMAL(10,2) DEFAULT 0.00 COMMENT '接口余额',
  `status` TINYINT DEFAULT 1 COMMENT '状态：0-禁用 1-正常',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_provider_type` (`provider_type`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='第三方接口配置表';

-- 插入示例数据
INSERT INTO `api_provider` (`id`, `provider_type`, `name`, `api_url`, `status`) 
VALUES 
(1, 'xxtouba', '学习通对接', 'https://api.example.com', 1),
(2, 'zhjy', '智慧职教对接', 'https://api.example.com', 1),
(3, 'mooc', 'MOOC对接', 'https://api.example.com', 1),
(4, 'qhs', '青书学堂对接', 'https://api.example.com', 1);

-- =============================================
-- 4. 订单表 (course_order)
-- =============================================
DROP TABLE IF EXISTS `course_order`;
CREATE TABLE `course_order` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '订单ID',
  `order_no` VARCHAR(32) NOT NULL COMMENT '订单编号',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `platform_id` BIGINT NOT NULL COMMENT '课程平台ID',
  `platform_name` VARCHAR(100) NOT NULL COMMENT '平台名称',
  `api_provider_id` BIGINT DEFAULT NULL COMMENT '对接接口ID',
  `third_order_id` VARCHAR(100) DEFAULT NULL COMMENT '第三方订单ID',
  
  -- 学生信息
  `school_name` VARCHAR(200) DEFAULT NULL COMMENT '学校名称',
  `student_name` VARCHAR(50) DEFAULT NULL COMMENT '学生姓名',
  `student_account` VARCHAR(100) NOT NULL COMMENT '学生账号',
  `student_password` VARCHAR(255) NOT NULL COMMENT '学生密码',
  `student_phone` VARCHAR(20) DEFAULT NULL COMMENT '学生手机号',
  
  -- 课程信息
  `course_id` VARCHAR(100) DEFAULT NULL COMMENT '课程ID',
  `course_name` VARCHAR(255) NOT NULL COMMENT '课程名称',
  `course_start_time` DATETIME DEFAULT NULL COMMENT '课程开始时间',
  `course_end_time` DATETIME DEFAULT NULL COMMENT '课程结束时间',
  `exam_start_time` DATETIME DEFAULT NULL COMMENT '考试开始时间',
  `exam_end_time` DATETIME DEFAULT NULL COMMENT '考试结束时间',
  
  -- 进度信息
  `total_chapters` INT DEFAULT 0 COMMENT '总章节数',
  `finished_chapters` INT DEFAULT 0 COMMENT '已完成章节数',
  `progress` VARCHAR(500) DEFAULT '0%' COMMENT '完成进度',
  
  -- 订单信息
  `amount` DECIMAL(10,2) NOT NULL COMMENT '订单金额',
  `dock_param` VARCHAR(50) DEFAULT NULL COMMENT '对接参数',
  `is_fast_mode` TINYINT DEFAULT 0 COMMENT '是否秒刷：0-否 1-是',
  `retry_count` INT DEFAULT 0 COMMENT '补刷次数',
  `order_status` TINYINT DEFAULT 0 COMMENT '订单状态：0-待处理 1-进行中 2-已完成 3-已取消 4-失败 5-待考试 6-考试中 7-考试完成 8-等待退款',
  `dock_status` TINYINT DEFAULT 0 COMMENT '对接状态：0-待对接 1-对接成功 2-对接失败 3-重复订单 4-已取消',
  `login_status` VARCHAR(50) DEFAULT NULL COMMENT '登录状态',
  `remarks` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  
  -- 自营订单倒计时相关字段
  `is_self_operated` TINYINT DEFAULT 0 COMMENT '是否自营订单：0-否 1-是',
  `countdown_duration` INT DEFAULT NULL COMMENT '倒计时时长（分钟）',
  `countdown_start_time` DATETIME DEFAULT NULL COMMENT '倒计时开始时间',
  `countdown_end_time` DATETIME DEFAULT NULL COMMENT '倒计时结束时间',
  `auto_complete_enabled` TINYINT DEFAULT 0 COMMENT '是否启用自动完成：0-否 1-是',
  
  -- 考试倒计时相关字段
  `exam_countdown_duration` INT DEFAULT NULL COMMENT '考试倒计时时长（分钟）',
  `exam_countdown_start_time` DATETIME DEFAULT NULL COMMENT '考试倒计时开始时间',
  `exam_countdown_end_time` DATETIME DEFAULT NULL COMMENT '考试倒计时结束时间',
  `exam_auto_complete_enabled` TINYINT DEFAULT 0 COMMENT '是否启用考试自动完成：0-否 1-是',
  
  `create_ip` VARCHAR(50) DEFAULT NULL COMMENT '下单IP',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_platform_id` (`platform_id`),
  KEY `idx_order_status` (`order_status`),
  KEY `idx_dock_status` (`dock_status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程订单表';

-- =============================================
-- 5. 操作日志表 (operation_log)
-- =============================================
DROP TABLE IF EXISTS `operation_log`;
CREATE TABLE `operation_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `operation_type` VARCHAR(50) NOT NULL COMMENT '操作类型',
  `operation_desc` VARCHAR(500) NOT NULL COMMENT '操作描述',
  `amount_change` DECIMAL(10,2) DEFAULT 0.00 COMMENT '金额变动',
  `balance_after` DECIMAL(10,2) DEFAULT 0.00 COMMENT '操作后余额',
  `ip_address` VARCHAR(50) DEFAULT NULL COMMENT 'IP地址',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_operation_type` (`operation_type`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志表';

-- =============================================
-- 6. 系统配置表 (system_config)
-- =============================================
DROP TABLE IF EXISTS `system_config`;
CREATE TABLE `system_config` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '配置ID',
  `config_key` VARCHAR(100) NOT NULL COMMENT '配置键',
  `config_value` TEXT COMMENT '配置值',
  `config_desc` VARCHAR(255) DEFAULT NULL COMMENT '配置描述',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置表';

-- 插入默认配置
INSERT INTO `system_config` (`config_key`, `config_value`, `config_desc`) VALUES
('site_name', '在线网课平台', '网站名称'),
('site_keywords', '网课,在线教育,代刷', '网站关键词'),
('site_description', '专业的在线网课服务平台', '网站描述'),
('system_notice', '欢迎使用本平台！', '系统公告'),
('user_register_enabled', '1', '用户注册开关：0-关闭 1-开启'),
('user_register_fee', '5', '用户开户费用'),
('min_recharge_amount', '10', '最低充值金额'),
('api_enable_threshold', '300', 'API开通免费门槛（余额）');

-- =============================================
-- 7. 充值记录表 (recharge_record)
-- =============================================
DROP TABLE IF EXISTS `recharge_record`;
CREATE TABLE `recharge_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `recharger_id` BIGINT DEFAULT NULL COMMENT '充值人ID（代理充值时）',
  `amount` DECIMAL(10,2) NOT NULL COMMENT '充值金额',
  `actual_cost` DECIMAL(10,2) DEFAULT NULL COMMENT '实际扣费（代理充值时）',
  `payment_method` VARCHAR(50) DEFAULT NULL COMMENT '支付方式',
  `trade_no` VARCHAR(100) DEFAULT NULL COMMENT '交易号',
  `status` TINYINT DEFAULT 0 COMMENT '状态：0-待支付 1-已完成 2-已取消',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_recharger_id` (`recharger_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='充值记录表';

-- =============================================
-- 8. 充值卡密表 (recharge_card)
-- =============================================
DROP TABLE IF EXISTS `recharge_card`;
CREATE TABLE `recharge_card` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '卡密ID',
  `card_no` VARCHAR(32) NOT NULL COMMENT '卡号',
  `card_password` VARCHAR(32) NOT NULL COMMENT '卡密',
  `amount` DECIMAL(10,2) NOT NULL COMMENT '面额',
  `status` TINYINT DEFAULT 0 COMMENT '状态：0-未使用 1-已使用 2-已禁用',
  `used_by` BIGINT DEFAULT NULL COMMENT '使用者ID',
  `used_time` DATETIME DEFAULT NULL COMMENT '使用时间',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_card_no` (`card_no`),
  KEY `idx_status` (`status`),
  KEY `idx_used_by` (`used_by`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='充值卡密表';

-- =============================================
-- 9. 公告表 (announcement)
-- =============================================
DROP TABLE IF EXISTS `announcement`;
CREATE TABLE `announcement` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '公告ID',
  `title` VARCHAR(200) NOT NULL COMMENT '公告标题',
  `content` TEXT NOT NULL COMMENT '公告内容',
  `type` TINYINT DEFAULT 1 COMMENT '公告类型：1-系统公告 2-日常公告 3-维护通知 4-活动公告',
  `priority` TINYINT DEFAULT 1 COMMENT '优先级：1-普通 2-重要 3-紧急',
  `is_top` TINYINT DEFAULT 0 COMMENT '是否置顶：0-否 1-是',
  `is_popup` TINYINT DEFAULT 0 COMMENT '是否弹窗显示：0-否 1-是',
  `publish_time` DATETIME DEFAULT NULL COMMENT '发布时间',
  `expire_time` DATETIME DEFAULT NULL COMMENT '过期时间',
  `status` TINYINT DEFAULT 1 COMMENT '状态：0-草稿 1-已发布 2-已下线',
  `create_by` BIGINT DEFAULT NULL COMMENT '创建人ID',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_type` (`type`),
  KEY `idx_status` (`status`),
  KEY `idx_is_top` (`is_top`),
  KEY `idx_publish_time` (`publish_time`),
  KEY `idx_create_by` (`create_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公告表';

-- =============================================
-- 10. 客服会话表 (customer_service_session)
-- =============================================
DROP TABLE IF EXISTS `customer_service_session`;
CREATE TABLE `customer_service_session` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '会话ID',
  `session_id` VARCHAR(64) NOT NULL COMMENT '会话标识',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `customer_service_id` BIGINT DEFAULT NULL COMMENT '客服ID',
  `status` TINYINT DEFAULT 1 COMMENT '会话状态：1-等待中 2-进行中 3-已结束',
  `start_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
  `end_time` DATETIME DEFAULT NULL COMMENT '结束时间',
  `last_message_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '最后消息时间',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_session_id` (`session_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_customer_service_id` (`customer_service_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客服会话表';

-- =============================================
-- 11. 客服消息表 (customer_service_message)
-- =============================================
DROP TABLE IF EXISTS `customer_service_message`;
CREATE TABLE `customer_service_message` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '消息ID',
  `session_id` VARCHAR(64) NOT NULL COMMENT '会话ID',
  `sender_id` BIGINT NOT NULL COMMENT '发送者ID',
  `sender_type` TINYINT NOT NULL COMMENT '发送者类型：1-用户 2-客服',
  `message_type` TINYINT DEFAULT 1 COMMENT '消息类型：1-文本 2-图片 3-文件',
  `content` TEXT NOT NULL COMMENT '消息内容',
  `is_read` TINYINT DEFAULT 0 COMMENT '是否已读：0-未读 1-已读',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_session_id` (`session_id`),
  KEY `idx_sender_id` (`sender_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客服消息表';

-- =============================================
-- 12. 系统变量配置表 (system_variable)
-- =============================================
DROP TABLE IF EXISTS `system_variable`;
CREATE TABLE `system_variable` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '变量ID',
  `variable_key` VARCHAR(100) NOT NULL COMMENT '变量键名',
  `variable_name` VARCHAR(200) NOT NULL COMMENT '变量显示名称',
  `variable_type` VARCHAR(50) NOT NULL COMMENT '变量类型：order_status,user_status,platform_status等',
  `variable_value` VARCHAR(100) NOT NULL COMMENT '变量值',
  `variable_label` VARCHAR(200) DEFAULT NULL COMMENT '变量标签/描述',
  `sort_order` INT DEFAULT 0 COMMENT '排序',
  `is_default` TINYINT DEFAULT 0 COMMENT '是否默认值：0-否 1-是',
  `is_enabled` TINYINT DEFAULT 1 COMMENT '是否启用：0-禁用 1-启用',
  `color` VARCHAR(100) DEFAULT NULL COMMENT '显示颜色（前端使用）',
  `icon` VARCHAR(50) DEFAULT NULL COMMENT '图标（前端使用）',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_variable_key_type` (`variable_key`, `variable_type`),
  KEY `idx_variable_type` (`variable_type`),
  KEY `idx_is_enabled` (`is_enabled`),
  KEY `idx_sort_order` (`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统变量配置表';

-- 补齐 Daytime 对接所需的退款中状态
INSERT IGNORE INTO `system_variable`
  (`variable_key`, `variable_name`, `variable_type`, `variable_value`, `variable_label`, `sort_order`, `is_default`, `is_enabled`, `color`, `icon`) VALUES
('refund_pending', '等待退款', 'order_status', '8', '第三方已退款，等待本地退款处理', 9, 0, 1, '#d97706', 'refresh-cw');

-- 插入默认浅色/深色主题语义颜色（复合渐变由前端根据起止色派生）
INSERT IGNORE INTO `system_variable`
  (`variable_key`, `variable_name`, `variable_type`, `variable_value`, `variable_label`, `sort_order`, `is_default`, `is_enabled`, `color`, `icon`) VALUES
('brand_primary', '品牌主色', 'theme_color_light', '#0f6cbd', '主按钮、选中态和主要链接', 10, 0, 1, NULL, NULL),
('brand_primary_hover', '主色悬停', 'theme_color_light', '#115ea3', '主要操作的悬停状态', 20, 0, 1, NULL, NULL),
('brand_primary_pressed', '主色按下', 'theme_color_light', '#0c3b5e', '主要操作的按下状态', 30, 0, 1, NULL, NULL),
('brand_cyan', '品牌青色', 'theme_color_light', '#00b7c3', '辅助品牌色和渐变终点', 40, 0, 1, NULL, NULL),
('brand_violet', '品牌紫色', 'theme_color_light', '#7160e8', '强调装饰和数据视觉辅助色', 50, 0, 1, NULL, NULL),
('primary_gradient_start', '渐变起点', 'theme_color_light', '#0f6cbd', '主品牌渐变的起始颜色', 60, 0, 1, NULL, NULL),
('primary_gradient_end', '渐变终点', 'theme_color_light', '#00b7c3', '主品牌渐变的结束颜色', 70, 0, 1, NULL, NULL),
('color_success', '成功色', 'theme_color_light', '#107c10', '成功、完成和正常状态', 110, 0, 1, NULL, NULL),
('color_warning', '警告色', 'theme_color_light', '#f7630c', '提醒、等待和风险状态', 120, 0, 1, NULL, NULL),
('color_danger', '危险色', 'theme_color_light', '#c50f1f', '失败、删除和高风险状态', 130, 0, 1, NULL, NULL),
('color_info', '信息色', 'theme_color_light', '#0078d4', '一般信息和辅助提示', 140, 0, 1, NULL, NULL),
('bg_body', '页面背景', 'theme_color_light', '#eef4fb', '应用主内容区的底色', 210, 0, 1, NULL, NULL),
('bg_card', '卡片背景', 'theme_color_light', 'rgba(255, 255, 255, 0.78)', '常规卡片和容器背景', 220, 0, 1, NULL, NULL),
('bg_card_hover', '卡片悬停', 'theme_color_light', 'rgba(255, 255, 255, 0.94)', '可交互卡片的悬停背景', 230, 0, 1, NULL, NULL),
('bg_overlay', '遮罩背景', 'theme_color_light', 'rgba(244, 248, 253, 0.78)', '浮层后方的半透明遮罩', 240, 0, 1, NULL, NULL),
('surface_solid', '实色表面', 'theme_color_light', '#ffffff', '输入框、弹层等不透明表面', 250, 0, 1, NULL, NULL),
('surface_mica', '云母表面', 'theme_color_light', 'rgba(242, 247, 252, 0.82)', '页面级柔和半透明材质', 260, 0, 1, NULL, NULL),
('surface_acrylic', '亚克力表面', 'theme_color_light', 'rgba(255, 255, 255, 0.68)', '浮动卡片和导航半透明材质', 270, 0, 1, NULL, NULL),
('text_primary', '主要文字', 'theme_color_light', '#17202b', '标题和高强调正文', 310, 0, 1, NULL, NULL),
('text_regular', '常规文字', 'theme_color_light', '#354052', '正文和表单内容', 320, 0, 1, NULL, NULL),
('text_secondary', '次要文字', 'theme_color_light', '#5c6675', '说明、辅助信息和元数据', 330, 0, 1, NULL, NULL),
('text_placeholder', '占位文字', 'theme_color_light', '#737d8c', '输入提示和弱化内容', 340, 0, 1, NULL, NULL),
('text_on_brand', '品牌色上文字', 'theme_color_light', '#ffffff', '主色按钮与品牌色背景上的文字', 350, 0, 1, NULL, NULL),
('border_color', '主要边框', 'theme_color_light', 'rgba(74, 91, 113, 0.22)', '控件和卡片的常规描边', 360, 0, 1, NULL, NULL),
('border_color_light', '弱边框', 'theme_color_light', 'rgba(74, 91, 113, 0.12)', '分隔线和低强调描边', 370, 0, 1, NULL, NULL),
('stroke_highlight', '表面高光', 'theme_color_light', 'rgba(255, 255, 255, 0.92)', '半透明表面的顶部高光', 380, 0, 1, NULL, NULL),
('focus_ring', '焦点光环', 'theme_color_light', 'rgba(15, 108, 189, 0.32)', '键盘操作时的可访问性焦点提示', 390, 0, 1, NULL, NULL),
('brand_primary', '品牌主色', 'theme_color_dark', '#479ef5', '主按钮、选中态和主要链接', 10, 0, 1, NULL, NULL),
('brand_primary_hover', '主色悬停', 'theme_color_dark', '#62abf5', '主要操作的悬停状态', 20, 0, 1, NULL, NULL),
('brand_primary_pressed', '主色按下', 'theme_color_dark', '#2886de', '主要操作的按下状态', 30, 0, 1, NULL, NULL),
('brand_cyan', '品牌青色', 'theme_color_dark', '#38d5de', '辅助品牌色和渐变终点', 40, 0, 1, NULL, NULL),
('brand_violet', '品牌紫色', 'theme_color_dark', '#9c89ff', '强调装饰和数据视觉辅助色', 50, 0, 1, NULL, NULL),
('primary_gradient_start', '渐变起点', 'theme_color_dark', '#479ef5', '主品牌渐变的起始颜色', 60, 0, 1, NULL, NULL),
('primary_gradient_end', '渐变终点', 'theme_color_dark', '#38d5de', '主品牌渐变的结束颜色', 70, 0, 1, NULL, NULL),
('color_success', '成功色', 'theme_color_dark', '#54b054', '成功、完成和正常状态', 110, 0, 1, NULL, NULL),
('color_warning', '警告色', 'theme_color_dark', '#f9a825', '提醒、等待和风险状态', 120, 0, 1, NULL, NULL),
('color_danger', '危险色', 'theme_color_dark', '#f1707b', '失败、删除和高风险状态', 130, 0, 1, NULL, NULL),
('color_info', '信息色', 'theme_color_dark', '#62abf5', '一般信息和辅助提示', 140, 0, 1, NULL, NULL),
('bg_body', '页面背景', 'theme_color_dark', '#07111f', '应用主内容区的底色', 210, 0, 1, NULL, NULL),
('bg_card', '卡片背景', 'theme_color_dark', 'rgba(14, 29, 48, 0.76)', '常规卡片和容器背景', 220, 0, 1, NULL, NULL),
('bg_card_hover', '卡片悬停', 'theme_color_dark', 'rgba(20, 40, 64, 0.90)', '可交互卡片的悬停背景', 230, 0, 1, NULL, NULL),
('bg_overlay', '遮罩背景', 'theme_color_dark', 'rgba(7, 17, 31, 0.80)', '浮层后方的半透明遮罩', 240, 0, 1, NULL, NULL),
('surface_solid', '实色表面', 'theme_color_dark', '#101d2e', '输入框、弹层等不透明表面', 250, 0, 1, NULL, NULL),
('surface_mica', '云母表面', 'theme_color_dark', 'rgba(11, 24, 41, 0.86)', '页面级柔和半透明材质', 260, 0, 1, NULL, NULL),
('surface_acrylic', '亚克力表面', 'theme_color_dark', 'rgba(17, 35, 57, 0.68)', '浮动卡片和导航半透明材质', 270, 0, 1, NULL, NULL),
('text_primary', '主要文字', 'theme_color_dark', '#f5f8fc', '标题和高强调正文', 310, 0, 1, NULL, NULL),
('text_regular', '常规文字', 'theme_color_dark', '#d6e0ec', '正文和表单内容', 320, 0, 1, NULL, NULL),
('text_secondary', '次要文字', 'theme_color_dark', '#a8b5c5', '说明、辅助信息和元数据', 330, 0, 1, NULL, NULL),
('text_placeholder', '占位文字', 'theme_color_dark', '#8391a3', '输入提示和弱化内容', 340, 0, 1, NULL, NULL),
('text_on_brand', '品牌色上文字', 'theme_color_dark', '#ffffff', '主色按钮与品牌色背景上的文字', 350, 0, 1, NULL, NULL),
('border_color', '主要边框', 'theme_color_dark', 'rgba(157, 192, 231, 0.24)', '控件和卡片的常规描边', 360, 0, 1, NULL, NULL),
('border_color_light', '弱边框', 'theme_color_dark', 'rgba(157, 192, 231, 0.13)', '分隔线和低强调描边', 370, 0, 1, NULL, NULL),
('stroke_highlight', '表面高光', 'theme_color_dark', 'rgba(209, 231, 255, 0.20)', '半透明表面的顶部高光', 380, 0, 1, NULL, NULL),
('focus_ring', '焦点光环', 'theme_color_dark', 'rgba(71, 158, 245, 0.40)', '键盘操作时的可访问性焦点提示', 390, 0, 1, NULL, NULL);

-- =============================================
-- 13. 倒计时配置表 (countdown_config)
-- =============================================
DROP TABLE IF EXISTS `countdown_config`;
CREATE TABLE `countdown_config` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '配置ID',
  `config_key` VARCHAR(100) NOT NULL COMMENT '配置键',
  `config_value` TEXT NOT NULL COMMENT '配置值',
  `config_desc` VARCHAR(255) DEFAULT NULL COMMENT '配置描述',
  `is_enabled` TINYINT DEFAULT 1 COMMENT '是否启用：0-禁用 1-启用',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='倒计时配置表';

-- 插入默认倒计时配置
INSERT INTO `countdown_config` (`config_key`, `config_value`, `config_desc`, `is_enabled`) VALUES
('default_countdown_duration', '60', '默认倒计时时长（分钟）', 1),
('auto_complete_status', '2', '倒计时结束后自动跳转的状态（订单状态值）', 1),
('auto_complete_enabled', '1', '是否启用自动完成功能：0-禁用 1-启用', 1),
('countdown_warning_time', '10', '倒计时警告时间（分钟，剩余时间少于此时长时显示警告）', 1),
('default_exam_countdown_duration', '120', '默认考试倒计时时长（分钟）', 1),
('exam_auto_complete_status', '7', '考试倒计时结束后自动跳转的状态（订单状态值）', 1),
('exam_auto_complete_enabled', '1', '是否启用考试自动完成功能：0-禁用 1-启用', 1),
('exam_countdown_warning_time', '15', '考试倒计时警告时间（分钟，剩余时间少于此时长时显示警告）', 1);

-- =============================================
-- 14. 倒计时历史记录表 (countdown_history)
-- =============================================
DROP TABLE IF EXISTS `countdown_history`;
CREATE TABLE `countdown_history` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `order_id` BIGINT NOT NULL COMMENT '订单ID',
  `order_no` VARCHAR(50) NOT NULL COMMENT '订单号',
  `operation_type` VARCHAR(20) NOT NULL COMMENT '操作类型：start-开始倒计时, adjust-调整倒计时, complete-完成订单, expire-过期, exam_start-开始考试倒计时, exam_adjust-调整考试倒计时, exam_complete-完成考试',
  `old_duration` INT DEFAULT NULL COMMENT '操作前时长（分钟）',
  `new_duration` INT DEFAULT NULL COMMENT '操作后时长（分钟）',
  `reason` VARCHAR(500) DEFAULT NULL COMMENT '操作原因',
  `operator_id` BIGINT DEFAULT NULL COMMENT '操作人ID',
  `operator_name` VARCHAR(100) DEFAULT NULL COMMENT '操作人姓名',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_order_no` (`order_no`),
  KEY `idx_operation_type` (`operation_type`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='倒计时历史记录表';

SET FOREIGN_KEY_CHECKS = 1;

-- =============================================
-- 数据库设计说明
-- =============================================
-- 1. 所有主键使用BIGINT类型，支持大数据量
-- 2. 所有金额字段使用DECIMAL(10,2)，精确存储
-- 3. 所有时间字段使用DATETIME类型
-- 4. 添加必要的索引，优化查询性能
-- 5. 使用InnoDB引擎，支持事务
-- 6. 字符集使用utf8mb4，支持emoji等特殊字符
-- 7. 符合SOLID原则，表结构清晰，职责单一
-- =============================================
