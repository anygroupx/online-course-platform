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
  `api_url` VARCHAR(2048) DEFAULT NULL COMMENT 'API地址',
  `username` VARCHAR(100) DEFAULT NULL COMMENT '账号',
  `password` TEXT DEFAULT NULL COMMENT '密码',
  `token` TEXT DEFAULT NULL COMMENT 'Token',
  `api_key` TEXT DEFAULT NULL COMMENT 'API Key',
  `cookie` TEXT DEFAULT NULL COMMENT 'Cookie',
  `balance` DECIMAL(10,2) DEFAULT 0.00 COMMENT '接口余额',
  `last_sync_time` BIGINT DEFAULT NULL COMMENT '上次同步时间戳（秒）',
  `status` TINYINT NOT NULL DEFAULT 2 COMMENT '状态：0-禁用 1-已启用 2-待验证/待启用',
  `config_version` BIGINT NOT NULL DEFAULT 0 COMMENT '配置CAS版本',
  `verified_at` DATETIME NULL COMMENT '最近成功的人工连接验证时间',
  `verified_by` BIGINT NULL COMMENT '连接验证操作人',
  `checked_at` DATETIME NULL COMMENT '最近连接/健康检查时间',
  `last_check_reason` VARCHAR(40) NULL COMMENT '安全的健康检查分类',
  `last_check_error_id` VARCHAR(36) NULL COMMENT '健康检查错误追踪ID',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_provider_type` (`provider_type`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='第三方接口配置表';

-- 插入示例数据
INSERT INTO `api_provider` (`id`, `provider_type`, `name`, `api_url`, `status`) 
VALUES 
(1, 'xxtouba', '学习通对接', 'https://api.example.com', 2),
(2, 'zhjy', '智慧职教对接', 'https://api.example.com', 2),
(3, 'mooc', 'MOOC对接', 'https://api.example.com', 2),
(4, 'qhs', '青书学堂对接', 'https://api.example.com', 2);

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
  `course_id` VARCHAR(2048) DEFAULT NULL COMMENT '课程ID',
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
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑归档：0-否 1-是',
  
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_platform_id` (`platform_id`),
  KEY `idx_order_status` (`order_status`),
  KEY `idx_dock_status` (`dock_status`),
  KEY `idx_course_order_deleted` (`is_deleted`),
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
  `card_password` VARCHAR(32) DEFAULT NULL COMMENT '历史明文字段（必须为空）',
  `password_hash` CHAR(64) NOT NULL COMMENT '卡密 SHA-256（高熵密钥）',
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
('focus_ring', '焦点光环', 'theme_color_dark', 'rgba(71, 158, 245, 0.40)', '键盘操作时的可访问性焦点提示', 390, 0, 1, NULL, NULL),
('brand_primary', '品牌主色', 'theme_color_liquid_glass', '#3463ce', '主按钮、选中态和主要链接', 10, 0, 1, NULL, NULL),
('brand_primary_hover', '主色悬停', 'theme_color_liquid_glass', '#2952b3', '主要操作的悬停状态', 20, 0, 1, NULL, NULL),
('brand_primary_pressed', '主色按下', 'theme_color_liquid_glass', '#1f3f8c', '主要操作的按下状态', 30, 0, 1, NULL, NULL),
('brand_cyan', '品牌青色', 'theme_color_liquid_glass', '#22b8cf', '辅助品牌色和渐变终点', 40, 0, 1, NULL, NULL),
('brand_violet', '品牌紫色', 'theme_color_liquid_glass', '#6366f1', '强调装饰和数据视觉辅助色', 50, 0, 1, NULL, NULL),
('primary_gradient_start', '渐变起点', 'theme_color_liquid_glass', '#3463ce', '主品牌渐变的起始颜色', 60, 0, 1, NULL, NULL),
('primary_gradient_end', '渐变终点', 'theme_color_liquid_glass', '#22b8cf', '主品牌渐变的结束颜色', 70, 0, 1, NULL, NULL),
('color_success', '成功色', 'theme_color_liquid_glass', '#37a06f', '成功、完成和正常状态', 110, 0, 1, NULL, NULL),
('color_warning', '警告色', 'theme_color_liquid_glass', '#d97706', '提醒、等待和风险状态', 120, 0, 1, NULL, NULL),
('color_danger', '危险色', 'theme_color_liquid_glass', '#dc2626', '失败、删除和高风险状态', 130, 0, 1, NULL, NULL),
('color_info', '信息色', 'theme_color_liquid_glass', '#3463ce', '一般信息和辅助提示', 140, 0, 1, NULL, NULL),
('bg_body', '页面背景', 'theme_color_liquid_glass', '#f5f6f8', '应用主内容区的底色', 210, 0, 1, NULL, NULL),
('bg_card', '卡片背景', 'theme_color_liquid_glass', 'rgba(255, 255, 255, 0.65)', '常规卡片和容器背景', 220, 0, 1, NULL, NULL),
('bg_card_hover', '卡片悬停', 'theme_color_liquid_glass', 'rgba(255, 255, 255, 0.82)', '可交互卡片的悬停背景', 230, 0, 1, NULL, NULL),
('bg_overlay', '遮罩背景', 'theme_color_liquid_glass', 'rgba(245, 246, 248, 0.75)', '浮层后方的半透明遮罩', 240, 0, 1, NULL, NULL),
('surface_solid', '实色表面', 'theme_color_liquid_glass', '#ffffff', '输入框、弹层等不透明表面', 250, 0, 1, NULL, NULL),
('surface_mica', '云母表面', 'theme_color_liquid_glass', 'rgba(245, 247, 250, 0.80)', '页面级柔和半透明材质', 260, 0, 1, NULL, NULL),
('surface_acrylic', '亚克力表面', 'theme_color_liquid_glass', 'rgba(255, 255, 255, 0.52)', '浮动卡片和导航半透明材质', 270, 0, 1, NULL, NULL),
('text_primary', '主要文字', 'theme_color_liquid_glass', '#252b35', '标题和高强调正文', 310, 0, 1, NULL, NULL),
('text_regular', '常规文字', 'theme_color_liquid_glass', '#3b4453', '正文和表单内容', 320, 0, 1, NULL, NULL),
('text_secondary', '次要文字', 'theme_color_liquid_glass', '#717a88', '说明、辅助信息和元数据', 330, 0, 1, NULL, NULL),
('text_placeholder', '占位文字', 'theme_color_liquid_glass', '#9aa2af', '输入提示和弱化内容', 340, 0, 1, NULL, NULL),
('text_on_brand', '品牌色上文字', 'theme_color_liquid_glass', '#ffffff', '主色按钮与品牌色背景上的文字', 350, 0, 1, NULL, NULL),
('border_color', '主要边框', 'theme_color_liquid_glass', 'rgba(205, 210, 219, 0.72)', '控件和卡片的常规描边', 360, 0, 1, NULL, NULL),
('border_color_light', '弱边框', 'theme_color_liquid_glass', 'rgba(223, 226, 231, 0.60)', '分隔线和低强调描边', 370, 0, 1, NULL, NULL),
('stroke_highlight', '表面高光', 'theme_color_liquid_glass', 'rgba(255, 255, 255, 0.95)', '半透明表面的顶部高光', 380, 0, 1, NULL, NULL),
('focus_ring', '焦点光环', 'theme_color_liquid_glass', 'rgba(52, 99, 206, 0.35)', '键盘操作时的可访问性焦点提示', 390, 0, 1, NULL, NULL),
('glass_renderer_mode', '渲染模式', 'theme_color_liquid_glass', 'auto', '自动选择增强折射或兼容毛玻璃', 510, 0, 1, NULL, NULL),
('glass_refraction', '折射强度', 'theme_color_liquid_glass', '56', '控制边缘背景位移强度', 520, 0, 1, NULL, NULL),
('glass_bevel', '斜面宽度', 'theme_color_liquid_glass', '22', '控制产生折射与高光的边缘范围', 530, 0, 1, NULL, NULL),
('glass_blur', '光学模糊', 'theme_color_liquid_glass', '0.35', '增强折射路径中的轻微模糊', 540, 0, 1, NULL, NULL),
('glass_dispersion', '色散强度', 'theme_color_liquid_glass', '1.2', '控制边缘 RGB 通道的微弱分离', 550, 0, 1, NULL, NULL),
('glass_radius', '表面圆角', 'theme_color_liquid_glass', '24', '控制主壳层和玻璃卡片的圆角', 560, 0, 1, NULL, NULL),
('glass_tint', '材质着色', 'theme_color_liquid_glass', 'rgba(255,255,255,0.018)', '叠加在实时背景上的低透明度颜色', 570, 0, 1, NULL, NULL),
('glass_surface_opacity', '表面透明度', 'theme_color_liquid_glass', '0.65', '控制普通业务卡片的透明表面强度', 580, 0, 1, NULL, NULL),
('glass_backdrop_blur', '兼容模糊', 'theme_color_liquid_glass', '16', 'CSS 降级路径与普通业务表面的背景模糊', 590, 0, 1, NULL, NULL),
('glass_saturation', '背景饱和度', 'theme_color_liquid_glass', '108', '控制玻璃后方内容的饱和度百分比', 600, 0, 1, NULL, NULL);

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

-- Native service commerce snapshot, including migration 035.
-- Fresh databases only; existing installations apply numbered migrations instead.

-- Native service commerce. Apply explicitly; never execute legacy supplier SQL.
-- Existing account_ledger is reused; monetary changes and operation transitions share a transaction.
CREATE TABLE IF NOT EXISTS service_product (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 provider_id BIGINT NOT NULL, provider_type VARCHAR(20) NOT NULL,
 project VARCHAR(16) NOT NULL, remote_product_id VARCHAR(64) NOT NULL,
 title VARCHAR(100) NOT NULL, description VARCHAR(1000), unit_price DECIMAL(16,6) NOT NULL,
 enabled TINYINT NOT NULL DEFAULT 0, version BIGINT NOT NULL DEFAULT 0,
 create_time DATETIME NOT NULL, update_time DATETIME NOT NULL,
 UNIQUE KEY uk_service_product_binding (provider_id, project, remote_product_id),
 CONSTRAINT fk_service_product_provider FOREIGN KEY (provider_id) REFERENCES api_provider(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS service_order (
 id VARCHAR(36) NOT NULL PRIMARY KEY, user_id BIGINT NOT NULL, product_id BIGINT NOT NULL,
 provider_id BIGINT NOT NULL, provider_version BIGINT NOT NULL, provider_identity CHAR(64) NOT NULL, provider_type VARCHAR(20) NOT NULL,
 project VARCHAR(16) NOT NULL, remote_product_id VARCHAR(64) NOT NULL, external_order_no VARCHAR(64), external_sub_order_no VARCHAR(64),
 title VARCHAR(100) NOT NULL, account_label VARCHAR(120) NOT NULL, status VARCHAR(24) NOT NULL,
 quantity INT NOT NULL, completed INT NOT NULL DEFAULT 0, distance DECIMAL(8,2) NOT NULL,
 unit_charge DECIMAL(16,6) NOT NULL, paid_amount DECIMAL(14,2) NOT NULL, refunded_amount DECIMAL(14,2) NOT NULL DEFAULT 0,
 version BIGINT NOT NULL DEFAULT 0, pending_operation_id VARCHAR(36),
 create_time DATETIME NOT NULL, update_time DATETIME NOT NULL,
 KEY idx_service_order_owner (user_id, create_time), KEY idx_service_order_status (status, update_time),
 UNIQUE KEY uk_service_remote_order (provider_id, project, external_order_no),
 CONSTRAINT fk_service_order_product FOREIGN KEY (product_id) REFERENCES service_product(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS service_order_operation (
 id VARCHAR(36) NOT NULL PRIMARY KEY, order_id VARCHAR(36) NOT NULL, user_id BIGINT NOT NULL,
 product_id BIGINT NOT NULL, product_version BIGINT NOT NULL, provider_version BIGINT NOT NULL,
 order_version BIGINT, action VARCHAR(20) NOT NULL, state VARCHAR(20) NOT NULL,
 quantity INT NOT NULL, distance DECIMAL(8,2) NOT NULL, unit_charge DECIMAL(16,6) NOT NULL,
 amount DECIMAL(14,2) NOT NULL, account_label VARCHAR(120) NOT NULL,
 payload_encrypted MEDIUMTEXT, error_category VARCHAR(40),
 resolved_by BIGINT, resolution_note VARCHAR(1000), expires_at DATETIME NOT NULL,
 create_time DATETIME NOT NULL, update_time DATETIME NOT NULL,
 KEY idx_service_operation_owner (user_id, create_time),
 KEY idx_service_operation_order (order_id, create_time),
 KEY idx_service_operation_state (state, update_time),
 CONSTRAINT fk_service_operation_product FOREIGN KEY (product_id) REFERENCES service_product(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Requires migration 018. Explicitly authorize and validate against the target MySQL before applying.
-- sxdk_tw has no verified live-price endpoint. Administrators must attest a time-limited contract price.
ALTER TABLE service_product ADD COLUMN contract_unit_cost DECIMAL(16,6) NULL;
ALTER TABLE service_product ADD COLUMN contract_valid_until DATE NULL;
ALTER TABLE service_product ADD COLUMN contract_evidence VARCHAR(1000) NULL;
ALTER TABLE service_product ADD COLUMN contract_reviewed_by BIGINT NULL;
ALTER TABLE service_product ADD COLUMN contract_reviewed_at DATETIME NULL;
ALTER TABLE service_product ADD COLUMN contract_provider_identity CHAR(64) NULL;
-- Calendar snapshots contain only scheduling/paid-date data, never student credentials or addresses.
ALTER TABLE service_order ADD COLUMN schedule_json MEDIUMTEXT NULL;
ALTER TABLE service_order_operation ADD COLUMN schedule_json MEDIUMTEXT NULL;
ALTER TABLE service_order MODIFY COLUMN distance DECIMAL(8,2) NULL;
ALTER TABLE service_order_operation MODIFY COLUMN distance DECIMAL(8,2) NULL;

-- Requires 018/019. Temporary account authorization; never deploy without explicit authorization.
CREATE TABLE service_account_session (
 id VARCHAR(36) NOT NULL PRIMARY KEY, user_id BIGINT NOT NULL, product_id BIGINT NOT NULL,
 product_version BIGINT NOT NULL, provider_version BIGINT NOT NULL,
 mode VARCHAR(16) NOT NULL, state VARCHAR(24) NOT NULL, account_label VARCHAR(120) NOT NULL,
 rules_refresh_at DATETIME NULL, snapshot_encrypted MEDIUMTEXT NULL, version BIGINT NOT NULL DEFAULT 0,
 expires_at DATETIME NOT NULL, create_time DATETIME NOT NULL, update_time DATETIME NOT NULL,
 KEY idx_service_account_owner (user_id, expires_at),
 KEY idx_service_account_expiry (expires_at),
 CONSTRAINT fk_service_account_product FOREIGN KEY (product_id) REFERENCES service_product(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
ALTER TABLE service_order_operation ADD COLUMN account_session_id VARCHAR(36) NULL;
ALTER TABLE service_order_operation ADD COLUMN account_session_version BIGINT NULL;

-- Native service quotes and orders retain exact six-decimal price * two-decimal distance.
-- Apply after 018/019/020 before enabling native services. Requires deployment authorization.
-- Widen only: existing six-decimal snapshots and all paid/refunded/quoted amounts stay unchanged.
-- No price backfill or repricing of historical orders/READY operations.
ALTER TABLE service_order MODIFY COLUMN unit_charge DECIMAL(18,8) NOT NULL;
ALTER TABLE service_order_operation MODIFY COLUMN unit_charge DECIMAL(18,8) NOT NULL;

-- Read-only status checks. Apply explicitly before deploying this schema-dependent code.
-- Existing status, money, contracts and business versions are not rewritten.
ALTER TABLE service_order ADD COLUMN status_checked_at DATETIME NULL;
ALTER TABLE service_order ADD COLUMN status_check_after DATETIME NULL;
ALTER TABLE service_order ADD COLUMN status_check_attempt_at DATETIME NULL;
ALTER TABLE service_order ADD COLUMN status_check_failures INT NOT NULL DEFAULT 0;
ALTER TABLE service_order ADD COLUMN status_check_state VARCHAR(8) NULL;
ALTER TABLE service_order ADD COLUMN status_check_token VARCHAR(36) NULL;
ALTER TABLE service_order ADD COLUMN status_check_until DATETIME NULL;
ALTER TABLE service_order ADD CONSTRAINT chk_service_status_failures CHECK (status_check_failures BETWEEN 0 AND 8);
ALTER TABLE service_order ADD CONSTRAINT chk_service_status_check_state CHECK (status_check_state IS NULL OR status_check_state IN ('OK', 'RETRY'));
ALTER TABLE service_order ADD CONSTRAINT chk_service_status_check_lease CHECK ((status_check_token IS NULL AND status_check_until IS NULL) OR (status_check_token IS NOT NULL AND status_check_until IS NOT NULL));
CREATE INDEX idx_service_order_check ON service_order (provider_type, status, status_check_after, status_check_until);

-- Black Shark self-operated fulfillment. Apply explicitly before deploying schema-dependent code.
-- Existing products and orders remain upstream-routed historical records.
ALTER TABLE service_product
  ADD COLUMN fulfillment_mode VARCHAR(20) NOT NULL DEFAULT 'UPSTREAM' AFTER enabled;
ALTER TABLE service_product
  ADD CONSTRAINT chk_service_product_fulfillment
  CHECK (fulfillment_mode IN ('UPSTREAM', 'SELF_OPERATED'));
CREATE INDEX idx_service_product_fulfillment
  ON service_product (provider_type, fulfillment_mode, enabled);

ALTER TABLE service_order
  ADD COLUMN fulfillment_mode VARCHAR(20) NOT NULL DEFAULT 'UPSTREAM' AFTER status;
ALTER TABLE service_order
  ADD CONSTRAINT chk_service_order_fulfillment
  CHECK (fulfillment_mode IN ('UPSTREAM', 'SELF_OPERATED'));
CREATE INDEX idx_service_order_fulfillment
  ON service_order (fulfillment_mode, status, update_time);

ALTER TABLE service_order_operation
  ADD COLUMN previous_status VARCHAR(24) NULL AFTER resolution_note;
ALTER TABLE service_order_operation
  ADD COLUMN resulting_status VARCHAR(24) NULL AFTER previous_status;
ALTER TABLE service_order_operation
  ADD COLUMN completed_snapshot INT NULL AFTER resulting_status;
ALTER TABLE service_order_operation
  ADD CONSTRAINT chk_service_operation_completed_snapshot
  CHECK (completed_snapshot IS NULL OR completed_snapshot >= 0);

CREATE TABLE service_order_fulfillment (
  order_id VARCHAR(36) NOT NULL PRIMARY KEY,
  payload_encrypted MEDIUMTEXT NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL,
  update_time DATETIME NOT NULL,
  CONSTRAINT fk_service_fulfillment_order
    FOREIGN KEY (order_id) REFERENCES service_order(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO sys_permission (permission_code, permission_name)
VALUES ('service-order:fulfill', '处理自营服务订单')
ON DUPLICATE KEY UPDATE permission_name = VALUES(permission_name), enabled = 1;

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.permission_code = 'service-order:fulfill'
WHERE r.role_code IN ('SUPER_ADMIN', 'OPERATOR');

-- Native providerless self-operated Black Shark qualification workflow, migration 035.
-- Providerless Black Shark self-operated checkout and qualification workflow.
-- Apply explicitly after 034 and before deploying schema-dependent code.
ALTER TABLE service_product DROP FOREIGN KEY fk_service_product_provider;
ALTER TABLE service_product MODIFY COLUMN provider_id BIGINT NULL;
ALTER TABLE service_product
  ADD COLUMN self_operated_provider_type VARCHAR(20)
    GENERATED ALWAYS AS (CASE WHEN fulfillment_mode = 'SELF_OPERATED' THEN provider_type ELSE NULL END) STORED;
-- Check duplicate legacy local SKUs before changing any persisted binding. If this fails,
-- stop and explicitly reconcile duplicate products; never merge or delete order history here.
ALTER TABLE service_product
  ADD UNIQUE KEY uk_service_product_self_operated
    (self_operated_provider_type, project, remote_product_id, fulfillment_mode);
-- 034 allowed a provider-bound local product. Detach only local products, and invalidate
-- their existing quotes by advancing the product version. Upstream snapshots remain intact.
UPDATE service_product SET provider_id = NULL, version = version + 1
WHERE fulfillment_mode = 'SELF_OPERATED';
ALTER TABLE service_product
  ADD CONSTRAINT fk_service_product_provider FOREIGN KEY (provider_id) REFERENCES api_provider(id),
  ADD CONSTRAINT chk_service_product_provider_mode CHECK (
    (fulfillment_mode = 'UPSTREAM' AND provider_id IS NOT NULL) OR
    (fulfillment_mode = 'SELF_OPERATED' AND provider_id IS NULL AND provider_type = 'heisha'
      AND project = 'default' AND remote_product_id IN ('1','2','3','4'))
  );

ALTER TABLE service_order MODIFY COLUMN provider_id BIGINT NULL;
ALTER TABLE service_order MODIFY COLUMN provider_version BIGINT NULL;
ALTER TABLE service_order MODIFY COLUMN provider_identity CHAR(64) NULL;
-- Use the immutable order mode, not its product's current mode.
UPDATE service_order SET provider_id = NULL, provider_version = NULL, provider_identity = NULL
WHERE fulfillment_mode = 'SELF_OPERATED';
ALTER TABLE service_order ADD CONSTRAINT chk_service_order_provider_mode CHECK (
  (fulfillment_mode = 'UPSTREAM' AND provider_id IS NOT NULL AND provider_version IS NOT NULL AND provider_identity IS NOT NULL) OR
  (fulfillment_mode = 'SELF_OPERATED' AND provider_id IS NULL AND provider_version IS NULL
    AND provider_identity IS NULL AND provider_type = 'heisha')
);

ALTER TABLE service_order_operation MODIFY COLUMN provider_version BIGINT NULL;
UPDATE service_order_operation SET provider_version = NULL
WHERE order_id IN (SELECT id FROM service_order WHERE fulfillment_mode = 'SELF_OPERATED');
ALTER TABLE service_order_operation MODIFY COLUMN action VARCHAR(32) NOT NULL;
ALTER TABLE service_order_operation
  ADD COLUMN previous_verification_status VARCHAR(20) NULL AFTER completed_snapshot,
  ADD COLUMN resulting_verification_status VARCHAR(20) NULL AFTER previous_verification_status,
  ADD CONSTRAINT chk_service_operation_verification CHECK (
    (previous_verification_status IS NULL OR previous_verification_status IN ('PENDING','VERIFIED','NEEDS_INFO','REJECTED')) AND
    (resulting_verification_status IS NULL OR resulting_verification_status IN ('PENDING','VERIFIED','NEEDS_INFO','REJECTED'))
  );

ALTER TABLE service_order_fulfillment
  ADD COLUMN verification_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' AFTER payload_encrypted,
  ADD COLUMN verified_by BIGINT NULL AFTER verification_status,
  ADD COLUMN verified_at DATETIME NULL AFTER verified_by,
  ADD COLUMN verification_note VARCHAR(1000) NULL AFTER verified_at,
  ADD COLUMN material_version BIGINT NOT NULL DEFAULT 0 AFTER verification_note,
  ADD CONSTRAINT chk_service_fulfillment_verification CHECK (
    verification_status IN ('PENDING','VERIFIED','NEEDS_INFO','REJECTED'));
CREATE INDEX idx_service_fulfillment_verification
  ON service_order_fulfillment (verification_status, update_time);

CREATE TABLE service_fulfillment_material_draft (
  id VARCHAR(36) NOT NULL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  product_version BIGINT NOT NULL,
  asset_type VARCHAR(24) NOT NULL,
  content_encrypted MEDIUMTEXT NULL,
  mime_type VARCHAR(40) NOT NULL,
  width INT NOT NULL,
  height INT NOT NULL,
  byte_size INT NOT NULL,
  sha256 CHAR(64) NOT NULL,
  state VARCHAR(12) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  expires_at DATETIME NOT NULL,
  create_time DATETIME NOT NULL,
  update_time DATETIME NOT NULL,
  purged_at DATETIME NULL,
  CONSTRAINT fk_service_material_draft_product FOREIGN KEY (product_id) REFERENCES service_product(id),
  CONSTRAINT chk_service_material_draft_state CHECK (state IN ('READY','USED','EXPIRED')),
  CONSTRAINT chk_service_material_draft_asset CHECK (asset_type = 'FACE_QUALIFICATION'),
  CONSTRAINT chk_service_material_draft_dimensions CHECK (width > 0 AND height > 0 AND byte_size > 0),
  KEY idx_service_material_draft_expiry (state, expires_at),
  KEY idx_service_material_draft_owner (user_id, product_id, state)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE service_order_fulfillment_asset (
  id VARCHAR(36) NOT NULL PRIMARY KEY,
  order_id VARCHAR(36) NOT NULL,
  asset_type VARCHAR(24) NOT NULL,
  content_encrypted MEDIUMTEXT NULL,
  mime_type VARCHAR(40) NOT NULL,
  width INT NOT NULL,
  height INT NOT NULL,
  byte_size INT NOT NULL,
  sha256 CHAR(64) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL,
  purged_at DATETIME NULL,
  CONSTRAINT fk_service_fulfillment_asset_order FOREIGN KEY (order_id) REFERENCES service_order(id),
  CONSTRAINT chk_service_fulfillment_asset_type CHECK (asset_type = 'FACE_QUALIFICATION'),
  CONSTRAINT chk_service_fulfillment_asset_dimensions CHECK (width > 0 AND height > 0 AND byte_size > 0),
  UNIQUE KEY uk_service_fulfillment_asset_order_type (order_id, asset_type),
  KEY idx_service_fulfillment_asset_purge (purged_at, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO sys_permission (permission_code, permission_name)
VALUES ('service-order:biometric', '查看订单敏感资格材料')
ON DUPLICATE KEY UPDATE permission_name = VALUES(permission_name), enabled = 1;
DELETE FROM sys_role_permission
WHERE permission_id IN (
  SELECT id FROM sys_permission WHERE permission_code = 'service-order:biometric'
) AND role_id NOT IN (
  SELECT id FROM sys_role WHERE role_code IN ('SUPER_ADMIN', 'OPERATOR')
);
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p
  ON p.permission_code = 'service-order:biometric'
WHERE r.role_code IN ('SUPER_ADMIN', 'OPERATOR');

-- Service-domain RBAC policy (036).
-- Current RBAC policy is static-role-policy; role CRUD/policy editing is not exposed.
-- Apply after 035 with explicit deployment authorization.
INSERT INTO sys_permission (permission_code, permission_name) VALUES
('service-product:read', '查看服务商品'),
('service-product:update', '管理服务商品'),
('service-order:read', '查看服务订单'),
('service-order:fulfill', '处理服务订单'),
('service-order:biometric', '查看订单敏感资格材料'),
('service-order:reconcile', '核对服务订单'),
('service-order:refund', '服务订单退款'),
('service-project:read', '查看服务项目'),
('service-project:update', '管理服务项目')
ON DUPLICATE KEY UPDATE permission_name = VALUES(permission_name), enabled = 1;

-- Reset only this domain's fixed-role grants. Preserve unrelated permissions.
DELETE FROM sys_role_permission WHERE permission_id IN (
  SELECT id FROM sys_permission WHERE permission_code IN (
'service-product:read', 'service-product:update', 'service-order:read', 'service-order:fulfill', 'service-order:biometric', 'service-order:reconcile', 'service-order:refund', 'service-project:read', 'service-project:update'));

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.role_code = 'SUPER_ADMIN';

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.role_code = 'OPERATOR' AND p.permission_code IN ('service-product:read', 'service-product:update', 'service-order:read', 'service-order:fulfill', 'service-order:biometric', 'service-project:read', 'service-project:update');

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.role_code = 'FINANCE' AND p.permission_code IN ('service-order:read', 'service-order:refund', 'service-order:reconcile');

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.role_code = 'AUDITOR' AND p.permission_code IN ('service-product:read', 'service-order:read', 'service-project:read');
-- MySQL validates all existing rows when adding these constraints. Any orphan
-- aborts its ALTER; operators must reconcile data explicitly, never auto-delete it.
-- DDL is not transactional: back up first and inspect partial failures before retry.
ALTER TABLE sys_user_role
  ADD CONSTRAINT fk_sys_user_role_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
  ADD CONSTRAINT fk_sys_user_role_role FOREIGN KEY (role_id) REFERENCES sys_role(id) ON DELETE CASCADE;
ALTER TABLE sys_role_permission
  ADD CONSTRAINT fk_sys_role_permission_role FOREIGN KEY (role_id) REFERENCES sys_role(id) ON DELETE CASCADE,
  ADD CONSTRAINT fk_sys_role_permission_permission FOREIGN KEY (permission_id) REFERENCES sys_permission(id) ON DELETE CASCADE;

-- 新库普通资金账本：复用007/015定义，课程内部划转依赖该表。
CREATE TABLE IF NOT EXISTS `account_ledger` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `biz_type` VARCHAR(32) NOT NULL COMMENT '业务类型：PAYMENT/ORDER/RECHARGE/REFUND/API_FEE/ADJUST',
  `biz_no` VARCHAR(64) NOT NULL COMMENT '业务单号',
  `direction` TINYINT NOT NULL COMMENT '1-入账 -1-出账',
  `amount` DECIMAL(12,2) NOT NULL COMMENT '变动金额(正数)',
  `balance_before` DECIMAL(12,2) NOT NULL COMMENT '变动前余额',
  `balance_after` DECIMAL(12,2) NOT NULL COMMENT '变动后余额',
  `remark` VARCHAR(255) DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT `chk_ledger_amount_positive` CHECK (`amount` > 0),
  CONSTRAINT `chk_ledger_direction` CHECK (`direction` IN (-1,1)),
  CONSTRAINT `chk_ledger_balance_nonnegative` CHECK (`balance_before` >= 0 AND `balance_after` >= 0),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ledger_biz` (`biz_type`, `biz_no`, `direction`, `user_id`),
  KEY `idx_ledger_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资金流水账本';

-- 课程密价与专用钱包（038）
-- 在 037 后执行一次。要求 MySQL >= 8.0.16；上线前备份并暂停课程资金写入。
-- DDL 非事务化：失败后核对已创建对象再恢复，禁止删除协议、钱包、流水或历史订单回滚。
-- 课程商品删除由业务拒绝有资金绑定的商品；不追加 course_platform 外键影响旧删除链路。
CREATE TABLE course_private_contract (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL COMMENT '协议所属代理',
  platform_id BIGINT NOT NULL COMMENT '不可迁移的本地课程商品ID',
  fixed_price DECIMAL(12,2) NOT NULL COMMENT '绝对成交价，不叠加任何费率',
  min_initial_deposit DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '首次累计成功转入激活门槛',
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING_ACTIVATION',
  activated_at DATETIME DEFAULT NULL COMMENT '激活后消费不取消资格',
  valid_from DATETIME DEFAULT NULL,
  valid_until DATETIME DEFAULT NULL,
  version INT NOT NULL DEFAULT 0,
  created_by BIGINT NOT NULL,
  updated_by BIGINT NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_course_contract_binding (user_id,platform_id),
  UNIQUE KEY uk_course_contract_identity (id,user_id,platform_id),
  CONSTRAINT fk_course_contract_user FOREIGN KEY (user_id) REFERENCES sys_user(id),
  CONSTRAINT ck_course_contract_price CHECK (fixed_price > 0),
  CONSTRAINT ck_course_contract_deposit CHECK (min_initial_deposit >= 0),
  CONSTRAINT ck_course_contract_version CHECK (version >= 0),
  CONSTRAINT ck_course_contract_status CHECK (status IN ('PENDING_ACTIVATION','ACTIVE','SUSPENDED','EXPIRED','TERMINATED')),
  CONSTRAINT ck_course_contract_activation CHECK (status <> 'ACTIVE' OR activated_at IS NOT NULL),
  CONSTRAINT ck_course_contract_validity CHECK (valid_from IS NULL OR valid_until IS NULL OR valid_until > valid_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='代理课程密价协议';

CREATE TABLE course_reserved_wallet (
  id BIGINT NOT NULL AUTO_INCREMENT,
  contract_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  platform_id BIGINT NOT NULL,
  balance DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  version INT NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_course_wallet_contract (contract_id),
  UNIQUE KEY uk_course_wallet_binding (user_id,platform_id),
  UNIQUE KEY uk_course_wallet_owner (id,user_id),
  UNIQUE KEY uk_course_wallet_identity (id,contract_id,user_id,platform_id),
  CONSTRAINT fk_course_wallet_contract FOREIGN KEY (contract_id,user_id,platform_id)
    REFERENCES course_private_contract(id,user_id,platform_id),
  CONSTRAINT ck_course_wallet_balance CHECK (balance >= 0 AND balance <= 9999999999.99),
  CONSTRAINT ck_course_wallet_version CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='仅用于绑定课程的预存钱包';

CREATE TABLE course_reserved_wallet_ledger (
  id BIGINT NOT NULL AUTO_INCREMENT,
  wallet_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  biz_type VARCHAR(32) NOT NULL,
  biz_no VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  direction TINYINT NOT NULL COMMENT '1入账，-1出账',
  amount DECIMAL(12,2) NOT NULL,
  balance_before DECIMAL(12,2) NOT NULL,
  balance_after DECIMAL(12,2) NOT NULL,
  related_order_no VARCHAR(32) DEFAULT NULL,
  operator_id BIGINT DEFAULT NULL,
  remark VARCHAR(500) DEFAULT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_course_wallet_ledger_biz (wallet_id,biz_type,biz_no,direction),
  KEY idx_course_wallet_ledger_user_time (user_id,create_time),
  CONSTRAINT fk_course_wallet_ledger_owner FOREIGN KEY (wallet_id,user_id)
    REFERENCES course_reserved_wallet(id,user_id),
  CONSTRAINT ck_course_wallet_ledger_amount CHECK (amount > 0),
  CONSTRAINT ck_course_wallet_ledger_direction CHECK (direction IN (-1,1)),
  CONSTRAINT ck_course_wallet_ledger_balances CHECK (balance_before >= 0 AND balance_after >= 0),
  CONSTRAINT ck_course_wallet_ledger_conservation CHECK (balance_after = balance_before + direction * amount)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程专户不可变资金流水';

CREATE TABLE course_reserved_transfer (
  id BIGINT NOT NULL AUTO_INCREMENT,
  wallet_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  amount DECIMAL(12,2) NOT NULL,
  type VARCHAR(32) NOT NULL,
  request_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  biz_no VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  status VARCHAR(32) NOT NULL COMMENT '首期仅落库已成功划转，失败事务整体回滚',
  operator_id BIGINT NOT NULL,
  approved_by BIGINT DEFAULT NULL COMMENT '仅授权管理员批准转出',
  reason VARCHAR(500) DEFAULT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  completed_at DATETIME NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_course_transfer_request (user_id,request_key),
  UNIQUE KEY uk_course_transfer_biz (biz_no),
  KEY idx_course_transfer_wallet_time (wallet_id,create_time),
  CONSTRAINT fk_course_transfer_owner FOREIGN KEY (wallet_id,user_id)
    REFERENCES course_reserved_wallet(id,user_id),
  CONSTRAINT ck_course_transfer_amount CHECK (amount > 0),
  CONSTRAINT ck_course_transfer_type CHECK (type IN ('TRANSFER_IN','TRANSFER_OUT')),
  CONSTRAINT ck_course_transfer_success CHECK (status = 'SUCCEEDED'),
  CONSTRAINT ck_course_transfer_approval CHECK (type = 'TRANSFER_IN' OR approved_by IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='普通账户与课程专户一比一划转';

CREATE TABLE course_private_contract_history (
  id BIGINT NOT NULL AUTO_INCREMENT,
  contract_id BIGINT NOT NULL,
  operator_id BIGINT NOT NULL,
  reason VARCHAR(500) NOT NULL,
  before_json JSON DEFAULT NULL COMMENT '创建协议时无之前快照',
  after_json JSON NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_course_contract_history_time (contract_id,create_time),
  CONSTRAINT fk_course_contract_history FOREIGN KEY (contract_id) REFERENCES course_private_contract(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='协议新增、价格、状态与有效期审计';

-- 只扩充金额容纳范围，不改动历史成交金额；普通历史订单无需协议、钱包或请求ID。
ALTER TABLE course_order
  MODIFY COLUMN amount DECIMAL(12,2) NOT NULL COMMENT '实际成交金额快照',
  ADD COLUMN refunded_at DATETIME DEFAULT NULL COMMENT '原账户退款完成时间，历史未知不回填',
  ADD COLUMN price_source VARCHAR(16) NOT NULL DEFAULT 'RATE',
  ADD COLUMN payment_source VARCHAR(24) NOT NULL DEFAULT 'GENERAL',
  ADD COLUMN private_contract_id BIGINT DEFAULT NULL,
  ADD COLUMN wallet_id BIGINT DEFAULT NULL,
  ADD COLUMN contract_version INT DEFAULT NULL,
  ADD COLUMN client_request_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  ADD COLUMN request_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '请求内容SHA256，不保存明文凭证',
  ADD UNIQUE KEY uk_course_order_request (user_id,client_request_id),
  ADD KEY idx_course_order_wallet (wallet_id),
  ADD CONSTRAINT fk_course_order_contract FOREIGN KEY (private_contract_id,user_id,platform_id)
    REFERENCES course_private_contract(id,user_id,platform_id),
  ADD CONSTRAINT fk_course_order_wallet FOREIGN KEY (wallet_id,private_contract_id,user_id,platform_id)
    REFERENCES course_reserved_wallet(id,contract_id,user_id,platform_id),
  ADD CONSTRAINT ck_course_order_price_snapshot CHECK (
    (price_source = 'RATE' AND payment_source = 'GENERAL'
     AND private_contract_id IS NULL AND wallet_id IS NULL AND contract_version IS NULL)
    OR
    (price_source = 'PRIVATE' AND payment_source = 'COURSE_WALLET'
     AND private_contract_id IS NOT NULL AND wallet_id IS NOT NULL
     AND contract_version IS NOT NULL AND contract_version >= 0 AND amount > 0)),
  ADD CONSTRAINT ck_course_order_request_snapshot CHECK (
    (client_request_id IS NULL AND request_fingerprint IS NULL)
    OR (client_request_id IS NOT NULL AND CHAR_LENGTH(client_request_id) > 0
        AND request_fingerprint IS NOT NULL AND request_fingerprint REGEXP '^[0-9a-f]{64}$'));

INSERT INTO sys_permission (permission_code,permission_name) VALUES
('course-private-price:read','读取课程密价协议'),
('course-private-price:manage','管理课程密价协议'),
('course-wallet:read:any','读取全部课程专户'),
('course-wallet:audit','审计课程专户流水'),
('course-wallet:release','批准课程专户转回')
ON DUPLICATE KEY UPDATE permission_name=VALUES(permission_name),enabled=1;

-- 仅重建本功能的静态角色授权，不影响其他领域权限；运营及普通用户无管理员权限。
DELETE FROM sys_role_permission WHERE permission_id IN (
  SELECT id FROM sys_permission WHERE permission_code IN
  ('course-private-price:read','course-private-price:manage','course-wallet:read:any','course-wallet:audit','course-wallet:release'));
INSERT IGNORE INTO sys_role_permission (role_id,permission_id)
SELECT r.id,p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE p.permission_code IN
  ('course-private-price:read','course-private-price:manage','course-wallet:read:any','course-wallet:audit','course-wallet:release')
AND (r.role_code='SUPER_ADMIN'
  OR (r.role_code='FINANCE' AND p.permission_code IN ('course-wallet:read:any','course-wallet:audit','course-wallet:release'))
  OR (r.role_code='AUDITOR' AND p.permission_code IN ('course-private-price:read','course-wallet:read:any','course-wallet:audit')));

-- 课程密价运营中心（039）
-- 039课程密价运营中心；仅在已完成038后执行，MySQL >= 8.0.16。
-- DDL并非整体事务：部分失败先执行039只读检查，核对后从未执行阶段续行，禁止清账或DROP恢复。
-- 新业务统一秒精度、Asia/Shanghai业务Clock；区间不重叠由协议行锁保证，不用余额代替新增预存。
CREATE TABLE course_private_plan (
  id BIGINT NOT NULL AUTO_INCREMENT,
  platform_id BIGINT NOT NULL,
  plan_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  plan_name VARCHAR(120) NOT NULL,
  description VARCHAR(2000) DEFAULT NULL,
  mode VARCHAR(32) NOT NULL,
  family_code VARCHAR(64) DEFAULT NULL,
  tier_code VARCHAR(64) DEFAULT NULL,
  tier_rank INT NOT NULL DEFAULT 0,
  visible_to_agents TINYINT NOT NULL DEFAULT 0,
  application_mode VARCHAR(16) NOT NULL DEFAULT 'REVIEW',
  status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
  sort_order INT NOT NULL DEFAULT 0,
  draft_version INT NOT NULL DEFAULT 0,
  draft_config_json JSON DEFAULT NULL,
  current_version_id BIGINT DEFAULT NULL,
  created_by BIGINT NOT NULL,
  updated_by BIGINT NOT NULL,
  created_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_cpop_plan_code (platform_id,plan_code),
  UNIQUE KEY uk_cpop_plan_identity (id,platform_id),
  KEY idx_cpop_plan_sale (platform_id,status,visible_to_agents,sort_order,id),
  CONSTRAINT ck_cpop_plan_mode CHECK (mode IN ('LEGACY_PERMANENT','PERMANENT_PREPAID','TIME_PREPAID','PERFORMANCE_RENEWAL','TIERED_PRICING')),
  CONSTRAINT ck_cpop_plan_legacy_hidden CHECK (mode<>'LEGACY_PERMANENT' OR visible_to_agents=0),
  CONSTRAINT ck_cpop_plan_status CHECK (status IN ('DRAFT','PUBLISHED','DISABLED')),
  CONSTRAINT ck_cpop_plan_application CHECK (application_mode IN ('AUTO','REVIEW')),
  CONSTRAINT ck_cpop_plan_numbers CHECK (tier_rank>=0 AND draft_version>=0 AND visible_to_agents IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程运营套餐及可变草稿';

CREATE TABLE course_private_plan_version (
  id BIGINT NOT NULL AUTO_INCREMENT,
  plan_id BIGINT NOT NULL,
  platform_id BIGINT NOT NULL,
  version_no INT NOT NULL,
  fixed_price DECIMAL(12,2) NOT NULL,
  initial_deposit_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
  validity_days INT DEFAULT NULL,
  initial_funding_policy VARCHAR(24) NOT NULL DEFAULT 'NEW_DEPOSIT',
  activation_from DATETIME(0) DEFAULT NULL,
  renewal_policy VARCHAR(24) NOT NULL DEFAULT 'NONE',
  renewal_deposit_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
  renewal_consumption_target DECIMAL(30,2) NOT NULL DEFAULT 0,
  settlement_grace_days INT NOT NULL DEFAULT 7,
  renewal_auto TINYINT NOT NULL DEFAULT 0,
  renewal_price_policy VARCHAR(24) NOT NULL DEFAULT 'SAME_VERSION',
  renewal_before_days INT NOT NULL DEFAULT 0,
  renewal_after_days INT NOT NULL DEFAULT 0,
  upgrade_policy VARCHAR(24) NOT NULL DEFAULT 'DISABLED',
  upgrade_deposit_policy VARCHAR(24) NOT NULL DEFAULT 'DIFFERENCE',
  allow_general_checkout TINYINT NOT NULL DEFAULT 0,
  effective_from DATETIME(0) DEFAULT NULL,
  effective_until DATETIME(0) DEFAULT NULL,
  cost_version_id BIGINT DEFAULT NULL,
  risk_approval_id BIGINT DEFAULT NULL,
  legacy_contract_id BIGINT DEFAULT NULL,
  published_at DATETIME(0) DEFAULT NULL,
  created_by BIGINT NOT NULL,
  created_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  config_snapshot JSON NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_cpop_version_no (plan_id,version_no),
  UNIQUE KEY uk_cpop_version_legacy (legacy_contract_id),
  UNIQUE KEY uk_cpop_version_identity (id,plan_id,platform_id),
  UNIQUE KEY uk_cpop_version_platform (id,platform_id),
  CONSTRAINT fk_cpop_version_plan FOREIGN KEY (plan_id,platform_id) REFERENCES course_private_plan(id,platform_id),
  CONSTRAINT fk_cpop_version_legacy FOREIGN KEY (legacy_contract_id) REFERENCES course_private_contract(id),
  CONSTRAINT ck_cpop_version_amount CHECK (fixed_price>0 AND initial_deposit_amount>=0 AND renewal_deposit_amount>=0 AND renewal_consumption_target>=0),
  CONSTRAINT ck_cpop_version_days CHECK (version_no>0 AND (validity_days IS NULL OR validity_days>0) AND settlement_grace_days>=0 AND renewal_before_days>=0 AND renewal_after_days>=0),
  CONSTRAINT ck_cpop_version_initial CHECK (initial_funding_policy IN ('NEW_DEPOSIT','EXISTING_BALANCE')),
  CONSTRAINT ck_cpop_version_renewal CHECK (renewal_policy IN ('NONE','NEW_DEPOSIT','PERFORMANCE','MANUAL') AND renewal_price_policy IN ('SAME_VERSION','LATEST_PUBLISHED')),
  CONSTRAINT ck_cpop_version_upgrade CHECK (upgrade_policy IN ('DISABLED','NEXT_TERM','REVIEW_IMMEDIATE') AND upgrade_deposit_policy IN ('DIFFERENCE','FULL_TARGET','EXISTING_BALANCE')),
  CONSTRAINT ck_cpop_version_flags CHECK (renewal_auto IN (0,1) AND allow_general_checkout IN (0,1)),
  CONSTRAINT ck_cpop_version_dates CHECK (effective_from IS NULL OR effective_until IS NULL OR effective_until>effective_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发布后不可覆盖的运营规则版本';

CREATE TABLE course_private_contract_term (
  id BIGINT NOT NULL AUTO_INCREMENT,
  contract_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  platform_id BIGINT NOT NULL,
  plan_id BIGINT NOT NULL,
  version_id BIGINT NOT NULL,
  term_no INT NOT NULL,
  relation_generation INT NOT NULL DEFAULT 1,
  operations_mode VARCHAR(32) NOT NULL,
  tier_code VARCHAR(64) DEFAULT NULL,
  tier_rank INT NOT NULL DEFAULT 0,
  effective_fixed_price DECIMAL(12,2) NOT NULL,
  effective_initial_deposit DECIMAL(12,2) NOT NULL DEFAULT 0,
  renewal_target DECIMAL(30,2) NOT NULL DEFAULT 0,
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING_ACTIVATION',
  qualification_status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
  qualification_kind VARCHAR(24) NOT NULL DEFAULT 'INITIAL_FUNDS',
  start_at DATETIME(0) DEFAULT NULL,
  end_at DATETIME(0) DEFAULT NULL,
  settlement_deadline_at DATETIME(0) DEFAULT NULL,
  activated_at DATETIME(0) DEFAULT NULL,
  qualified_at DATETIME(0) DEFAULT NULL,
  renewal_source_term_id BIGINT DEFAULT NULL,
  funding_window_start DATETIME(0) DEFAULT NULL,
  funding_window_end DATETIME(0) DEFAULT NULL,
  funding_start_ledger_id BIGINT DEFAULT NULL,
  funding_end_ledger_id BIGINT DEFAULT NULL,
  qualified_deposit_amount DECIMAL(30,2) NOT NULL DEFAULT 0,
  qualification_basis_amount DECIMAL(30,2) NOT NULL DEFAULT 0 COMMENT '实际已满足门槛基数，历史未知不猜测',
  renewal_net_deposit_amount DECIMAL(30,2) NOT NULL DEFAULT 0 COMMENT '续期窗口净新增，可为负，不是初次资格',
  settled_consumption_amount DECIMAL(30,2) NOT NULL DEFAULT 0,
  settled_order_count INT NOT NULL DEFAULT 0,
  adjustment_amount DECIMAL(30,2) NOT NULL DEFAULT 0 COMMENT '经审计的统计调整，不改变钱包本金',
  finalized_at DATETIME(0) DEFAULT NULL,
  review_required TINYINT NOT NULL DEFAULT 0,
  superseded_at DATETIME(0) DEFAULT NULL,
  config_snapshot JSON NOT NULL,
  version INT NOT NULL DEFAULT 0,
  created_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_cpop_term_no (contract_id,term_no),
  UNIQUE KEY uk_cpop_term_renewal (renewal_source_term_id),
  UNIQUE KEY uk_cpop_term_binding (id,contract_id,user_id,platform_id),
  UNIQUE KEY uk_cpop_term_contract (id,contract_id),
  UNIQUE KEY uk_cpop_term_owner (id,user_id),
  UNIQUE KEY uk_cpop_term_order_snapshot (id,contract_id,user_id,platform_id,version_id,operations_mode),
  KEY idx_cpop_term_due (status,end_at,id),
  KEY idx_cpop_term_settle (status,settlement_deadline_at,id),
  KEY idx_cpop_term_user (user_id,platform_id,status,id),
  KEY idx_cpop_term_plan (plan_id,contract_id),
  CONSTRAINT fk_cpop_term_contract FOREIGN KEY (contract_id,user_id,platform_id) REFERENCES course_private_contract(id,user_id,platform_id),
  CONSTRAINT fk_cpop_term_version FOREIGN KEY (version_id,plan_id,platform_id) REFERENCES course_private_plan_version(id,plan_id,platform_id),
  CONSTRAINT fk_cpop_term_source FOREIGN KEY (renewal_source_term_id,contract_id) REFERENCES course_private_contract_term(id,contract_id),
  CONSTRAINT ck_cpop_term_mode CHECK (operations_mode IN ('LEGACY_PERMANENT','PERMANENT_PREPAID','TIME_PREPAID','PERFORMANCE_RENEWAL','TIERED_PRICING')),
  CONSTRAINT ck_cpop_term_status CHECK (status IN ('PENDING_ACTIVATION','SCHEDULED','ACTIVE','PENDING_SETTLEMENT','QUALIFIED','RENEWED','EXPIRED','CANCELLED','SUPERSEDED','SUSPENDED','TERMINATED')),
  CONSTRAINT ck_cpop_term_activation CHECK (operations_mode='LEGACY_PERMANENT' OR status<>'ACTIVE' OR activated_at IS NOT NULL),
  CONSTRAINT ck_cpop_term_qualification CHECK (qualification_status IN ('PENDING','QUALIFIED','NOT_QUALIFIED','REVIEW_REQUIRED')),
  CONSTRAINT ck_cpop_term_qualification_kind CHECK (qualification_kind IN ('INITIAL_FUNDS','PERFORMANCE','NEW_DEPOSIT','UPGRADE_FUNDS','MANUAL_GRANT','LEGACY_UNKNOWN')),
  CONSTRAINT ck_cpop_term_amount CHECK (effective_fixed_price>0 AND effective_initial_deposit>=0 AND renewal_target>=0 AND qualified_deposit_amount>=0 AND qualification_basis_amount>=0 AND settled_consumption_amount>=0),
  CONSTRAINT ck_cpop_term_numbers CHECK (term_no>0 AND relation_generation>0 AND tier_rank>=0 AND settled_order_count>=0 AND version>=0 AND review_required IN (0,1)),
  CONSTRAINT ck_cpop_term_window CHECK (operations_mode='LEGACY_PERMANENT' OR ((start_at IS NULL AND end_at IS NULL AND settlement_deadline_at IS NULL AND status IN ('PENDING_ACTIVATION','CANCELLED')) OR (start_at IS NOT NULL AND (end_at IS NULL AND operations_mode IN ('PERMANENT_PREPAID','TIERED_PRICING') OR end_at IS NOT NULL AND end_at>start_at) AND (end_at IS NULL AND settlement_deadline_at IS NULL OR end_at IS NOT NULL AND settlement_deadline_at IS NOT NULL AND settlement_deadline_at>=end_at)))),
  CONSTRAINT ck_cpop_term_funding_window CHECK (funding_window_start IS NULL OR funding_window_end IS NULL OR funding_window_end>=funding_window_start),
  CONSTRAINT ck_cpop_term_funding_watermark CHECK ((funding_start_ledger_id IS NULL OR funding_start_ledger_id>=0) AND (funding_end_ledger_id IS NULL OR funding_end_ledger_id>=0) AND (funding_start_ledger_id IS NULL OR funding_end_ledger_id IS NULL OR funding_end_ledger_id>=funding_start_ledger_id))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='同钱包关系下不可混期的历史及排期权益';

CREATE TABLE course_private_term_order (
  id BIGINT NOT NULL AUTO_INCREMENT,
  term_id BIGINT NOT NULL,
  contract_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  platform_id BIGINT NOT NULL,
  order_id BIGINT NOT NULL,
  order_no VARCHAR(32) NOT NULL,
  ordered_amount DECIMAL(12,2) NOT NULL,
  qualified_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
  qualification_status VARCHAR(24) NOT NULL DEFAULT 'PAID',
  paid_at DATETIME(0) NOT NULL,
  completed_at DATETIME(0) DEFAULT NULL,
  completion_confirmed_at DATETIME(0) DEFAULT NULL,
  completion_basis VARCHAR(40) NOT NULL DEFAULT 'UNKNOWN',
  refunded_at DATETIME(0) DEFAULT NULL,
  last_reconciled_at DATETIME(0) DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_cpop_term_order_id (order_id),
  UNIQUE KEY uk_cpop_term_order_no (order_no),
  KEY idx_cpop_term_order_status (term_id,qualification_status,order_id),
  KEY idx_cpop_term_order_page (term_id,order_id),
  CONSTRAINT fk_cpop_term_order_term FOREIGN KEY (term_id,contract_id,user_id,platform_id) REFERENCES course_private_contract_term(id,contract_id,user_id,platform_id),
  CONSTRAINT ck_cpop_term_order_amount CHECK (ordered_amount>0 AND qualified_amount>=0 AND qualified_amount<=ordered_amount),
  CONSTRAINT ck_cpop_term_order_status CHECK (qualification_status IN ('PAID','PENDING','QUALIFIED','REFUNDED','EXCLUDED')),
  CONSTRAINT ck_cpop_term_order_refund CHECK (refunded_at IS NULL OR qualified_amount=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='成交事务确定归期的订单及可信结算投影';

CREATE TABLE course_private_application (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  platform_id BIGINT NOT NULL,
  plan_id BIGINT NOT NULL,
  version_id BIGINT NOT NULL,
  contract_id BIGINT DEFAULT NULL,
  source_term_id BIGINT DEFAULT NULL,
  funding_start_ledger_id BIGINT DEFAULT NULL COMMENT '申请时旧协议资金资格水位，不猜测历史门槛',
  application_type VARCHAR(16) NOT NULL,
  application_reason VARCHAR(255) NOT NULL DEFAULT '' COMMENT '原始申请原因，审核不覆盖',
  effective_policy VARCHAR(24) NOT NULL DEFAULT 'NEXT_TERM',
  acknowledge_legacy_change TINYINT NOT NULL DEFAULT 0,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  submitted_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  submitted_by BIGINT DEFAULT NULL COMMENT '会话提交主体，未知历史不冒充本人确认',
  reviewed_by BIGINT DEFAULT NULL,
  reviewed_at DATETIME(0) DEFAULT NULL,
  review_reason VARCHAR(255) DEFAULT NULL,
  request_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  operation_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  version INT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_cpop_application_request (user_id,request_key),
  KEY idx_cpop_application_review (status,submitted_at,id),
  KEY idx_cpop_application_user (user_id,id),
  CONSTRAINT fk_cpop_application_user FOREIGN KEY (user_id) REFERENCES sys_user(id),
  CONSTRAINT fk_cpop_application_version FOREIGN KEY (version_id,plan_id,platform_id) REFERENCES course_private_plan_version(id,plan_id,platform_id),
  CONSTRAINT fk_cpop_application_contract FOREIGN KEY (contract_id,user_id,platform_id) REFERENCES course_private_contract(id,user_id,platform_id),
  CONSTRAINT fk_cpop_application_source FOREIGN KEY (source_term_id,contract_id,user_id,platform_id) REFERENCES course_private_contract_term(id,contract_id,user_id,platform_id),
  CONSTRAINT ck_cpop_application_type CHECK (application_type IN ('OPEN','RENEW','UPGRADE','REJOIN')),
  CONSTRAINT ck_cpop_application_status CHECK (status IN ('PENDING','APPROVED','REJECTED','CANCELLED')),
  CONSTRAINT ck_cpop_application_policy CHECK (effective_policy IN ('ON_ACTIVATION','NEXT_TERM','IMMEDIATE','REVIEW_IMMEDIATE')),
  CONSTRAINT ck_cpop_application_identity CHECK (request_key REGEXP '^[A-Za-z0-9_-]{1,64}$' AND operation_fingerprint REGEXP '^[0-9a-f]{64}$' AND version>=0 AND acknowledge_legacy_change IN (0,1)),
  CONSTRAINT ck_cpop_application_source CHECK (source_term_id IS NULL OR contract_id IS NOT NULL),
  CONSTRAINT ck_cpop_application_funding CHECK (funding_start_ledger_id IS NULL OR funding_start_ledger_id>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='开通续期升级及重新签约申请身份';

CREATE TABLE course_wallet_release_request (
  id BIGINT NOT NULL AUTO_INCREMENT,
  wallet_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  amount DECIMAL(12,2) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  reason VARCHAR(255) NOT NULL,
  request_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  operation_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  reviewed_by BIGINT DEFAULT NULL,
  reviewed_at DATETIME(0) DEFAULT NULL,
  review_reason VARCHAR(255) DEFAULT NULL,
  executed_transfer_id BIGINT DEFAULT NULL,
  version INT NOT NULL DEFAULT 0,
  created_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_cpop_release_request (user_id,request_key),
  UNIQUE KEY uk_cpop_release_transfer (executed_transfer_id),
  KEY idx_cpop_release_review (status,created_at,id),
  CONSTRAINT fk_cpop_release_wallet FOREIGN KEY (wallet_id,user_id) REFERENCES course_reserved_wallet(id,user_id),
  CONSTRAINT ck_cpop_release_amount CHECK (amount>0),
  CONSTRAINT ck_cpop_release_status CHECK (status IN ('PENDING','APPROVED','REJECTED','EXECUTED','CANCELLED')),
  CONSTRAINT ck_cpop_release_execution CHECK ((status='EXECUTED' AND executed_transfer_id IS NOT NULL AND reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL) OR (status<>'EXECUTED' AND executed_transfer_id IS NULL)),
  CONSTRAINT ck_cpop_release_identity CHECK (request_key REGEXP '^[A-Za-z0-9_-]{1,64}$' AND operation_fingerprint REGEXP '^[0-9a-f]{64}$' AND version>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='不冻结本金的独立财务转回申请';

CREATE TABLE course_private_term_event (
  id BIGINT NOT NULL AUTO_INCREMENT,
  contract_id BIGINT NOT NULL,
  term_id BIGINT DEFAULT NULL,
  source_term_id BIGINT DEFAULT NULL,
  event_type VARCHAR(48) NOT NULL,
  before_json JSON DEFAULT NULL,
  after_json JSON DEFAULT NULL,
  source_version_id BIGINT DEFAULT NULL,
  target_version_id BIGINT DEFAULT NULL,
  operator_id BIGINT DEFAULT NULL,
  reason VARCHAR(500) NOT NULL,
  biz_no VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  happened_at DATETIME(0) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_cpop_event_biz (biz_no),
  KEY idx_cpop_event_contract (contract_id,happened_at,id),
  CONSTRAINT fk_cpop_event_contract FOREIGN KEY (contract_id) REFERENCES course_private_contract(id),
  CONSTRAINT fk_cpop_event_term FOREIGN KEY (term_id,contract_id) REFERENCES course_private_contract_term(id,contract_id),
  CONSTRAINT fk_cpop_event_source FOREIGN KEY (source_term_id,contract_id) REFERENCES course_private_contract_term(id,contract_id),
  CONSTRAINT fk_cpop_event_source_version FOREIGN KEY (source_version_id) REFERENCES course_private_plan_version(id),
  CONSTRAINT fk_cpop_event_target_version FOREIGN KEY (target_version_id) REFERENCES course_private_plan_version(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='只新增的不可变运营事件';

CREATE TABLE course_private_term_settlement (
  id BIGINT NOT NULL AUTO_INCREMENT,
  term_id BIGINT NOT NULL,
  revision_no INT NOT NULL,
  evaluation_type VARCHAR(24) NOT NULL,
  settled_amount DECIMAL(30,2) NOT NULL DEFAULT 0,
  order_count INT NOT NULL DEFAULT 0,
  target_amount DECIMAL(30,2) NOT NULL DEFAULT 0,
  pending_count INT NOT NULL DEFAULT 0,
  result VARCHAR(24) NOT NULL,
  evaluated_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  operation_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_cpop_settlement_revision (term_id,revision_no),
  UNIQUE KEY uk_cpop_settlement_operation (term_id,operation_fingerprint),
  CONSTRAINT fk_cpop_settlement_term FOREIGN KEY (term_id) REFERENCES course_private_contract_term(id),
  CONSTRAINT ck_cpop_settlement_numbers CHECK (revision_no>0 AND settled_amount>=0 AND target_amount>=0 AND order_count>=0 AND pending_count>=0),
  CONSTRAINT ck_cpop_settlement_type CHECK (evaluation_type IN ('PREVIEW','FINAL','REFUND_REVIEW','FACT_REVIEW','MANUAL')),
  CONSTRAINT ck_cpop_settlement_result CHECK (result IN ('PENDING','QUALIFIED','NOT_QUALIFIED','REVIEW_REQUIRED')),
  CONSTRAINT ck_cpop_settlement_identity CHECK (operation_fingerprint REGEXP '^[0-9a-f]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='不可覆盖的逐次结算和退款复核证据';

CREATE TABLE course_private_funding_allocation (
  id BIGINT NOT NULL AUTO_INCREMENT,
  term_id BIGINT NOT NULL,
  wallet_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  ledger_id BIGINT NOT NULL,
  amount DECIMAL(12,2) NOT NULL,
  created_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_cpop_funding_ledger (ledger_id),
  KEY idx_cpop_funding_term (term_id,id),
  CONSTRAINT fk_cpop_funding_term FOREIGN KEY (term_id,user_id) REFERENCES course_private_contract_term(id,user_id),
  CONSTRAINT fk_cpop_funding_wallet FOREIGN KEY (wallet_id,user_id) REFERENCES course_reserved_wallet(id,user_id),
  CONSTRAINT ck_cpop_funding_amount CHECK (amount>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='真实转入流水资格认领，不移动或冻结资金';

CREATE TABLE course_private_cost_version (
  id BIGINT NOT NULL AUTO_INCREMENT,
  platform_id BIGINT NOT NULL,
  unit_cost DECIMAL(12,2) NOT NULL,
  evidence VARCHAR(2000) NOT NULL,
  verified_by BIGINT NOT NULL,
  verified_at DATETIME(0) NOT NULL,
  valid_from DATETIME(0) DEFAULT NULL,
  valid_until DATETIME(0) DEFAULT NULL,
  reason VARCHAR(500) NOT NULL,
  created_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_cpop_cost_platform (id,platform_id),
  KEY idx_cpop_cost_current (platform_id,verified_at,id),
  CONSTRAINT ck_cpop_cost_amount CHECK (unit_cost>=0),
  CONSTRAINT ck_cpop_cost_dates CHECK (valid_from IS NULL OR valid_until IS NULL OR valid_until>valid_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='人工核验单位成本版本，不推测采购成本';

CREATE TABLE course_private_risk_approval (
  id BIGINT NOT NULL AUTO_INCREMENT,
  plan_id BIGINT NOT NULL,
  draft_version INT NOT NULL,
  cost_version_id BIGINT NOT NULL,
  reviewed_by BIGINT NOT NULL,
  reviewed_at DATETIME(0) NOT NULL,
  status VARCHAR(16) NOT NULL,
  reason VARCHAR(500) NOT NULL,
  biz_no VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_cpop_risk_biz (biz_no),
  UNIQUE KEY uk_cpop_risk_binding (id,plan_id,cost_version_id),
  KEY idx_cpop_risk_draft (plan_id,draft_version,cost_version_id),
  CONSTRAINT fk_cpop_risk_plan FOREIGN KEY (plan_id) REFERENCES course_private_plan(id),
  CONSTRAINT fk_cpop_risk_cost FOREIGN KEY (cost_version_id) REFERENCES course_private_cost_version(id),
  CONSTRAINT ck_cpop_risk_status CHECK (status IN ('APPROVED','REJECTED')),
  CONSTRAINT ck_cpop_risk_draft CHECK (draft_version>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='草稿及成本版本对应的独立风险审批';

CREATE TABLE course_private_notification (
  id BIGINT NOT NULL AUTO_INCREMENT,
  term_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  stage VARCHAR(48) NOT NULL,
  payload_json JSON NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  available_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  delivered_at DATETIME(0) DEFAULT NULL,
  read_at DATETIME(0) DEFAULT NULL,
  attempts INT NOT NULL DEFAULT 0,
  version INT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_cpop_notification_stage (term_id,stage),
  KEY idx_cpop_notification_delivery (status,available_at,id),
  KEY idx_cpop_notification_user (user_id,read_at,id),
  CONSTRAINT fk_cpop_notification_owner FOREIGN KEY (term_id,user_id) REFERENCES course_private_contract_term(id,user_id),
  CONSTRAINT ck_cpop_notification_status CHECK (status IN ('PENDING','DELIVERED','FAILED')),
  CONSTRAINT ck_cpop_notification_numbers CHECK (attempts>=0 AND version>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站内提醒出站及阅读状态，按周期阶段去重';

-- 039阶段二：绑定快照与兼容迁移
-- 循环引用在对象齐备后补约束；所有历史订单新快照和履约事实保持NULL。
ALTER TABLE course_private_plan
  ADD CONSTRAINT fk_cpop_plan_current FOREIGN KEY (current_version_id,id,platform_id) REFERENCES course_private_plan_version(id,plan_id,platform_id);
ALTER TABLE course_private_plan_version
  ADD CONSTRAINT fk_cpop_version_cost FOREIGN KEY (cost_version_id,platform_id) REFERENCES course_private_cost_version(id,platform_id),
  ADD CONSTRAINT fk_cpop_version_risk FOREIGN KEY (risk_approval_id,plan_id,cost_version_id) REFERENCES course_private_risk_approval(id,plan_id,cost_version_id),
  ADD CONSTRAINT ck_cpop_version_risk_cost CHECK (risk_approval_id IS NULL OR cost_version_id IS NOT NULL);
ALTER TABLE course_private_contract
  ADD COLUMN operations_mode VARCHAR(32) NOT NULL DEFAULT 'LEGACY_PERMANENT',
  ADD COLUMN current_term_id BIGINT DEFAULT NULL,
  ADD COLUMN relation_generation INT NOT NULL DEFAULT 1,
  ADD CONSTRAINT fk_cpop_contract_current FOREIGN KEY (current_term_id,id,user_id,platform_id) REFERENCES course_private_contract_term(id,contract_id,user_id,platform_id),
  ADD CONSTRAINT ck_cpop_contract_mode CHECK (operations_mode IN ('LEGACY_PERMANENT','PERMANENT_PREPAID','TIME_PREPAID','PERFORMANCE_RENEWAL','TIERED_PRICING')),
  ADD CONSTRAINT ck_cpop_contract_generation CHECK (relation_generation>0);
ALTER TABLE course_order
  ADD COLUMN private_term_id BIGINT DEFAULT NULL,
  ADD COLUMN private_term_version INT DEFAULT NULL,
  ADD COLUMN private_plan_version_id BIGINT DEFAULT NULL,
  ADD COLUMN private_operations_mode VARCHAR(32) DEFAULT NULL,
  ADD COLUMN fulfilled_at DATETIME(0) DEFAULT NULL COMMENT '可信完整履约事实，不以课程结束或更新时间回填',
  ADD COLUMN completion_confirmed_at DATETIME(0) DEFAULT NULL COMMENT '上游完成确认时刻，仅代表时间上界，不是实际完成',
  ADD COLUMN completion_confirmation_source VARCHAR(32) DEFAULT NULL,
  ADD COLUMN fulfillment_source VARCHAR(48) DEFAULT NULL,
  ADD UNIQUE KEY uk_cpop_order_identity (id,order_no,private_contract_id,user_id,platform_id,private_term_id,amount),
  ADD KEY idx_cpop_order_term (private_term_id,id),
  ADD CONSTRAINT fk_cpop_order_term FOREIGN KEY (private_term_id,private_contract_id,user_id,platform_id,private_plan_version_id,private_operations_mode) REFERENCES course_private_contract_term(id,contract_id,user_id,platform_id,version_id,operations_mode),
  ADD CONSTRAINT ck_cpop_order_term_snapshot CHECK ((private_term_id IS NULL AND private_term_version IS NULL AND private_plan_version_id IS NULL AND private_operations_mode IS NULL) OR (private_term_id IS NOT NULL AND private_term_version IS NOT NULL AND private_term_version>=0 AND private_plan_version_id IS NOT NULL AND private_operations_mode IS NOT NULL AND price_source='PRIVATE' AND payment_source='COURSE_WALLET')),
  ADD CONSTRAINT ck_cpop_order_fulfillment CHECK ((fulfilled_at IS NULL AND fulfillment_source IS NULL) OR (fulfilled_at IS NOT NULL AND fulfillment_source IS NOT NULL AND CHAR_LENGTH(fulfillment_source)>0)),
  ADD CONSTRAINT ck_cpop_order_confirmation CHECK ((completion_confirmed_at IS NULL AND completion_confirmation_source IS NULL) OR (completion_confirmed_at IS NOT NULL AND completion_confirmation_source='UPSTREAM_STATUS'));
ALTER TABLE course_private_term_order
  ADD CONSTRAINT fk_cpop_term_order_order FOREIGN KEY (order_id,order_no,contract_id,user_id,platform_id,term_id,ordered_amount) REFERENCES course_order(id,order_no,private_contract_id,user_id,platform_id,private_term_id,amount);
ALTER TABLE course_reserved_wallet_ledger
  ADD UNIQUE KEY uk_cpop_ledger_identity (id,wallet_id,user_id);
ALTER TABLE course_private_funding_allocation
  ADD CONSTRAINT fk_cpop_funding_ledger FOREIGN KEY (ledger_id,wallet_id,user_id) REFERENCES course_reserved_wallet_ledger(id,wallet_id,user_id);
ALTER TABLE course_reserved_transfer
  ADD UNIQUE KEY uk_cpop_transfer_identity (id,wallet_id,user_id),
  ADD KEY idx_cpop_transfer_qualification (wallet_id,completed_at,id);
ALTER TABLE course_wallet_release_request
  ADD CONSTRAINT fk_cpop_release_transfer FOREIGN KEY (executed_transfer_id,wallet_id,user_id) REFERENCES course_reserved_transfer(id,wallet_id,user_id);

-- 隐藏兼容套餐按商品建立，每合同一份版本；逐值保全，不推导起始或扩大旧有效期。
INSERT INTO course_private_plan(platform_id,plan_code,plan_name,mode,visible_to_agents,status,created_by,updated_by)
SELECT DISTINCT platform_id,'__LEGACY_PERMANENT','旧版长期密价兼容','LEGACY_PERMANENT',0,'DISABLED',MIN(created_by),MIN(updated_by)
FROM course_private_contract GROUP BY platform_id;
INSERT INTO course_private_plan_version(plan_id,platform_id,version_no,fixed_price,initial_deposit_amount,initial_funding_policy,
  renewal_policy,legacy_contract_id,published_at,created_by,created_at,config_snapshot)
SELECT p.id,c.platform_id,ROW_NUMBER() OVER (PARTITION BY c.platform_id ORDER BY c.id),c.fixed_price,c.min_initial_deposit,
  'EXISTING_BALANCE','NONE',c.id,c.create_time,c.created_by,c.create_time,
  JSON_OBJECT('operationsMode','LEGACY_PERMANENT','fixedPrice',c.fixed_price,'initialDepositAmount',c.min_initial_deposit,'status',c.status,'activatedAt',c.activated_at,'validFrom',c.valid_from,'validUntil',c.valid_until)
FROM course_private_contract c JOIN course_private_plan p ON p.platform_id=c.platform_id AND p.plan_code='__LEGACY_PERMANENT';
INSERT INTO course_private_contract_term(contract_id,user_id,platform_id,plan_id,version_id,term_no,relation_generation,
  operations_mode,effective_fixed_price,effective_initial_deposit,status,qualification_status,qualification_kind,start_at,end_at,settlement_deadline_at,
  activated_at,qualified_at,config_snapshot,created_at,updated_at)
SELECT c.id,c.user_id,c.platform_id,v.plan_id,v.id,1,1,'LEGACY_PERMANENT',c.fixed_price,c.min_initial_deposit,c.status,
  IF(c.activated_at IS NULL,'PENDING','QUALIFIED'),'LEGACY_UNKNOWN',c.valid_from,c.valid_until,c.valid_until,c.activated_at,c.activated_at,v.config_snapshot,c.create_time,c.update_time
FROM course_private_contract c JOIN course_private_plan_version v ON v.legacy_contract_id=c.id;
-- ON UPDATE时间显式保全，新增周期指针不得修改旧审计时间。
UPDATE course_private_contract c JOIN course_private_contract_term t ON t.contract_id=c.id AND t.term_no=1
SET c.current_term_id=t.id,c.update_time=c.update_time;

-- 发布版本与审计证据不可覆盖；资格认领额不是钱包扣款，跨商品及非转入来源数据库拒绝。
DELIMITER $$
CREATE TRIGGER trg_cpop_term_source_insert BEFORE INSERT ON course_private_contract_term FOR EACH ROW
BEGIN
  IF NEW.renewal_source_term_id=NEW.id THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='周期不可续约自身'; END IF;
END$$
CREATE TRIGGER trg_cpop_term_source_update BEFORE UPDATE ON course_private_contract_term FOR EACH ROW
BEGIN
  IF NEW.renewal_source_term_id=NEW.id THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='周期不可续约自身'; END IF;
END$$
CREATE TRIGGER trg_cpop_version_immutable BEFORE UPDATE ON course_private_plan_version FOR EACH ROW
BEGIN
  IF OLD.published_at IS NOT NULL THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='已发布规则版本不可覆盖'; END IF;
END$$
CREATE TRIGGER trg_cpop_version_delete BEFORE DELETE ON course_private_plan_version FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='规则历史禁止删除'; END$$
CREATE TRIGGER trg_cpop_funding_validate BEFORE INSERT ON course_private_funding_allocation FOR EACH ROW
BEGIN
  IF NOT EXISTS (SELECT 1 FROM course_private_contract_term t JOIN course_reserved_wallet w ON w.contract_id=t.contract_id AND w.user_id=t.user_id AND w.platform_id=t.platform_id JOIN course_reserved_wallet_ledger l ON l.wallet_id=w.id AND l.user_id=w.user_id JOIN course_reserved_transfer x ON x.wallet_id=l.wallet_id AND x.user_id=l.user_id AND x.biz_no=l.biz_no AND x.amount=l.amount AND x.type='TRANSFER_IN' AND x.status='SUCCEEDED' WHERE t.id=NEW.term_id AND w.id=NEW.wallet_id AND w.user_id=NEW.user_id AND l.id=NEW.ledger_id AND l.biz_type='TRANSFER_IN' AND l.direction=1 AND l.amount>=NEW.amount) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='资金资格必须来自同绑定真实转入流水';
  END IF;
END$$
-- 已生效或已经结束的周期是成交依据，生命周期只能推进状态和事实统计。
CREATE TRIGGER trg_cpop_term_snapshot_update BEFORE UPDATE ON course_private_contract_term FOR EACH ROW
BEGIN
  IF OLD.activated_at IS NOT NULL OR OLD.status IN ('ACTIVE','PENDING_SETTLEMENT','QUALIFIED','RENEWED','EXPIRED','SUPERSEDED') THEN
    IF NOT (NEW.contract_id<=>OLD.contract_id) OR NOT (NEW.user_id<=>OLD.user_id) OR NOT (NEW.platform_id<=>OLD.platform_id)
      OR NOT (NEW.plan_id<=>OLD.plan_id) OR NOT (NEW.version_id<=>OLD.version_id) OR NOT (NEW.term_no<=>OLD.term_no)
      OR NOT (NEW.relation_generation<=>OLD.relation_generation) OR NOT (NEW.operations_mode<=>OLD.operations_mode)
      OR NOT (NEW.tier_code<=>OLD.tier_code) OR NOT (NEW.tier_rank<=>OLD.tier_rank)
      OR NOT (NEW.effective_fixed_price<=>OLD.effective_fixed_price) OR NOT (NEW.effective_initial_deposit<=>OLD.effective_initial_deposit)
      OR NOT (NEW.renewal_target<=>OLD.renewal_target) OR NOT (NEW.qualification_kind<=>OLD.qualification_kind)
      OR NOT (NEW.start_at<=>OLD.start_at) OR NOT (NEW.end_at<=>OLD.end_at) OR NOT (NEW.settlement_deadline_at<=>OLD.settlement_deadline_at)
      OR NOT (NEW.activated_at<=>OLD.activated_at) OR NOT (NEW.renewal_source_term_id<=>OLD.renewal_source_term_id)
      OR NOT (NEW.funding_start_ledger_id<=>OLD.funding_start_ledger_id) OR NOT (NEW.funding_end_ledger_id<=>OLD.funding_end_ledger_id)
      OR NOT (NEW.qualification_basis_amount<=>OLD.qualification_basis_amount) OR NOT (NEW.config_snapshot<=>OLD.config_snapshot) THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='已生效周期的价格、期限、来源和规则快照不可改写';
    END IF;
  END IF;
END$$
-- 原始申请确认与资金水位只能在提交时保存，审核只改状态/意见及首开确定的协议。
CREATE TRIGGER trg_cpop_application_update BEFORE UPDATE ON course_private_application FOR EACH ROW
BEGIN
  IF NOT (NEW.user_id<=>OLD.user_id) OR NOT (NEW.platform_id<=>OLD.platform_id)
    OR NOT (NEW.plan_id<=>OLD.plan_id) OR NOT (NEW.version_id<=>OLD.version_id)
    OR NOT (NEW.source_term_id<=>OLD.source_term_id) OR NOT (NEW.funding_start_ledger_id<=>OLD.funding_start_ledger_id)
    OR NOT (NEW.application_type<=>OLD.application_type) OR NOT (NEW.application_reason<=>OLD.application_reason)
    OR NOT (NEW.effective_policy<=>OLD.effective_policy) OR NOT (NEW.acknowledge_legacy_change<=>OLD.acknowledge_legacy_change)
    OR NOT (NEW.submitted_by<=>OLD.submitted_by) OR NOT (NEW.submitted_at<=>OLD.submitted_at) OR NOT (NEW.request_key<=>OLD.request_key)
    OR NOT (NEW.operation_fingerprint<=>OLD.operation_fingerprint)
    OR (OLD.contract_id IS NOT NULL AND NOT (NEW.contract_id<=>OLD.contract_id)) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='原申请确认与资金资格水位不可修改';
  END IF;
END$$
CREATE TRIGGER trg_cpop_release_insert BEFORE INSERT ON course_wallet_release_request FOR EACH ROW
BEGIN
  IF NEW.executed_transfer_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM course_reserved_transfer t WHERE t.id=NEW.executed_transfer_id AND t.wallet_id=NEW.wallet_id AND t.user_id=NEW.user_id AND t.type='TRANSFER_OUT' AND t.status='SUCCEEDED' AND t.approved_by=NEW.reviewed_by AND t.amount=NEW.amount) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='转回申请必须关联同额原账户批准转回'; END IF;
END$$
CREATE TRIGGER trg_cpop_release_update BEFORE UPDATE ON course_wallet_release_request FOR EACH ROW
BEGIN
  IF NEW.executed_transfer_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM course_reserved_transfer t WHERE t.id=NEW.executed_transfer_id AND t.wallet_id=NEW.wallet_id AND t.user_id=NEW.user_id AND t.type='TRANSFER_OUT' AND t.status='SUCCEEDED' AND t.approved_by=NEW.reviewed_by AND t.amount=NEW.amount) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='转回申请必须关联同额原账户批准转回'; END IF;
END$$
CREATE TRIGGER trg_cpop_event_update BEFORE UPDATE ON course_private_term_event FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='运营事件只能新增'; END$$
CREATE TRIGGER trg_cpop_event_delete BEFORE DELETE ON course_private_term_event FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='运营事件只能新增'; END$$
CREATE TRIGGER trg_cpop_settlement_update BEFORE UPDATE ON course_private_term_settlement FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='结算证据只能新增'; END$$
CREATE TRIGGER trg_cpop_settlement_delete BEFORE DELETE ON course_private_term_settlement FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='结算证据只能新增'; END$$
CREATE TRIGGER trg_cpop_allocation_update BEFORE UPDATE ON course_private_funding_allocation FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='资金资格认领只能新增'; END$$
CREATE TRIGGER trg_cpop_allocation_delete BEFORE DELETE ON course_private_funding_allocation FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='资金资格认领只能新增'; END$$
CREATE TRIGGER trg_cpop_cost_update BEFORE UPDATE ON course_private_cost_version FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='核验成本只能新增'; END$$
CREATE TRIGGER trg_cpop_cost_delete BEFORE DELETE ON course_private_cost_version FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='核验成本只能新增'; END$$
CREATE TRIGGER trg_cpop_risk_update BEFORE UPDATE ON course_private_risk_approval FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='风险审批只能新增'; END$$
CREATE TRIGGER trg_cpop_risk_delete BEFORE DELETE ON course_private_risk_approval FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='风险审批只能新增'; END$$
DELIMITER ;

INSERT INTO sys_permission(permission_code,permission_name) VALUES
('course-private-plan:read','读取课程运营套餐'),
('course-private-plan:manage','维护课程套餐草稿'),
('course-private-plan:publish','发布课程运营套餐'),
('course-private-contract:review','审核课程权益申请'),
('course-private-term:read','读取课程权益周期'),
('course-private-term:adjust','调整课程权益周期'),
('course-private-settlement:read','读取课程权益结算'),
('course-private-settlement:execute','执行课程权益结算'),
('course-wallet-release:review','审核课程资金转回申请'),
('course-private-analytics:read','读取课程运营分析'),
('course-private-cost:verify','核验课程真实成本'),
('course-private-risk:approve','独立审批课程低毛利风险')
ON DUPLICATE KEY UPDATE permission_name=VALUES(permission_name),enabled=1;
-- 仅配置新增领域，运营没有资金转回权；财务没有价格草稿/发布权。
DELETE FROM sys_role_permission WHERE permission_id IN (SELECT id FROM sys_permission WHERE permission_code IN ('course-private-plan:read','course-private-plan:manage','course-private-plan:publish','course-private-contract:review','course-private-term:read','course-private-term:adjust','course-private-settlement:read','course-private-settlement:execute','course-wallet-release:review','course-private-analytics:read','course-private-cost:verify','course-private-risk:approve'));
INSERT IGNORE INTO sys_role_permission(role_id,permission_id) SELECT r.id,p.id FROM sys_role r CROSS JOIN sys_permission p WHERE p.permission_code IN ('course-private-plan:read','course-private-plan:manage','course-private-plan:publish','course-private-contract:review','course-private-term:read','course-private-term:adjust','course-private-settlement:read','course-private-settlement:execute','course-wallet-release:review','course-private-analytics:read','course-private-cost:verify','course-private-risk:approve') AND (r.role_code='SUPER_ADMIN'
 OR (r.role_code='OPERATOR' AND p.permission_code IN ('course-private-plan:read','course-private-plan:manage','course-private-contract:review','course-private-term:read','course-private-settlement:read','course-private-analytics:read'))
 OR (r.role_code='FINANCE' AND p.permission_code IN ('course-wallet-release:review','course-private-settlement:read','course-private-analytics:read','course-private-cost:verify'))
 OR (r.role_code='AUDITOR' AND p.permission_code IN ('course-private-plan:read','course-private-term:read','course-private-settlement:read','course-private-analytics:read'))
);
