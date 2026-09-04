-- 插入测试公告数据
-- Source: 基于现有系统架构设计

-- 插入系统公告（用于首次登录弹窗）
INSERT INTO `announcement` (`title`, `content`, `type`, `priority`, `is_top`, `is_popup`, `publish_time`, `status`, `create_by`) 
VALUES (
  '欢迎使用在线网课平台',
  '欢迎使用我们的在线网课平台！\n\n本平台提供专业的网课代刷服务，支持多种主流学习平台。\n\n主要功能：\n• 支持学习通、智慧职教等主流平台\n• 智能进度管理\n• 实时订单跟踪\n• 安全可靠的服务\n\n如有任何问题，请随时联系在线客服。',
  1, 2, 0, 1, NOW(), 1, 1
);

-- 插入日常公告（用于首页显示）
INSERT INTO `announcement` (`title`, `content`, `type`, `priority`, `is_top`, `is_popup`, `publish_time`, `status`, `create_by`) 
VALUES 
(
  '平台维护通知',
  '系统将于本周六凌晨2:00-4:00进行例行维护，期间可能影响服务使用，请提前安排。',
  2, 2, 1, 0, NOW(), 1, 1
),
(
  '新功能上线',
  '平台新增批量订单功能，支持一次性提交多个订单，提高效率。详情请查看订单管理页面。',
  2, 1, 0, 0, NOW(), 1, 1
),
(
  '优惠活动',
  '即日起至月底，新用户注册享受9折优惠，老用户推荐新用户可获得充值奖励。',
  2, 1, 0, 0, NOW(), 1, 1
);

-- 插入测试客服会话数据
INSERT INTO `customer_service_session` (`session_id`, `user_id`, `status`, `start_time`, `last_message_time`) 
VALUES 
('test_session_001', 1, 1, NOW(), NOW()),
('test_session_002', 2, 2, NOW(), NOW());

-- 插入测试客服消息数据
INSERT INTO `customer_service_message` (`session_id`, `sender_id`, `sender_type`, `message_type`, `content`, `is_read`) 
VALUES 
('test_session_001', 1, 1, 1, '你好，我想咨询一下平台的使用方法', 0),
('test_session_001', 1, 2, 1, '您好！欢迎使用我们的平台，有什么可以帮助您的吗？', 0),
('test_session_002', 2, 1, 1, '我的订单状态一直显示处理中，请问什么时候能完成？', 1),
('test_session_002', 1, 2, 1, '您的订单正在处理中，预计2小时内完成，请耐心等待。', 1);

-- 插入系统变量配置数据
-- Source: 基于现有系统架构设计

-- 订单状态变量
INSERT INTO `system_variable` (`variable_key`, `variable_name`, `variable_type`, `variable_value`, `variable_label`, `sort_order`, `is_default`, `is_enabled`, `color`, `icon`) 
VALUES 
('pending', '待处理', 'order_status', '0', '订单已创建，等待处理', 1, 1, 1, '#f0ad4e', 'clock'),
('processing', '进行中', 'order_status', '1', '订单正在处理中', 2, 0, 1, '#5bc0de', 'play'),
('completed', '已完成', 'order_status', '2', '订单处理完成', 3, 0, 1, '#5cb85c', 'check'),
('cancelled', '已取消', 'order_status', '3', '订单已取消', 4, 0, 1, '#d9534f', 'times'),
('failed', '失败', 'order_status', '4', '订单处理失败', 5, 0, 1, '#d9534f', 'exclamation'),
('exam_pending', '待考试', 'order_status', '5', '等待开始考试', 6, 0, 1, '#9b59b6', 'graduation-cap'),
('exam_processing', '考试中', 'order_status', '6', '正在考试中', 7, 0, 1, '#e67e22', 'book-open'),
('exam_completed', '考试完成', 'order_status', '7', '考试已完成', 8, 0, 1, '#1abc9c', 'check-circle'),
('refund_pending', '等待退款', 'order_status', '8', '第三方已退款，等待本地退款处理', 9, 0, 1, '#d97706', 'refresh-cw');

-- 对接状态变量
INSERT INTO `system_variable` (`variable_key`, `variable_name`, `variable_type`, `variable_value`, `variable_label`, `sort_order`, `is_default`, `is_enabled`, `color`, `icon`) 
VALUES 
('dock_pending', '待对接', 'dock_status', '0', '等待对接第三方平台', 1, 1, 1, '#f0ad4e', 'clock'),
('dock_success', '对接成功', 'dock_status', '1', '成功对接第三方平台', 2, 0, 1, '#5cb85c', 'check'),
('dock_failed', '对接失败', 'dock_status', '2', '对接第三方平台失败', 3, 0, 1, '#d9534f', 'times'),
('dock_duplicate', '重复订单', 'dock_status', '3', '检测到重复订单', 4, 0, 1, '#f39c12', 'exclamation-triangle'),
('dock_cancelled', '已取消', 'dock_status', '4', '对接已取消', 5, 0, 1, '#95a5a6', 'ban');

-- 用户状态变量
INSERT INTO `system_variable` (`variable_key`, `variable_name`, `variable_type`, `variable_value`, `variable_label`, `sort_order`, `is_default`, `is_enabled`, `color`, `icon`) 
VALUES 
('normal', '正常', 'user_status', '1', '用户状态正常', 1, 1, 1, '#5cb85c', 'user'),
('disabled', '禁用', 'user_status', '0', '用户已被禁用', 2, 0, 1, '#d9534f', 'user-times'),
('frozen', '冻结', 'user_status', '2', '用户账户被冻结', 3, 0, 1, '#f0ad4e', 'snowflake');

-- 平台状态变量
INSERT INTO `system_variable` (`variable_key`, `variable_name`, `variable_type`, `variable_value`, `variable_label`, `sort_order`, `is_default`, `is_enabled`, `color`, `icon`) 
VALUES 
('online', '上架', 'platform_status', '1', '平台正常上架', 1, 1, 1, '#5cb85c', 'check-circle'),
('offline', '下架', 'platform_status', '0', '平台已下架', 2, 0, 1, '#d9534f', 'times-circle'),
('maintenance', '维护中', 'platform_status', '2', '平台维护中', 3, 0, 1, '#f0ad4e', 'wrench');

-- 充值卡状态变量
INSERT INTO `system_variable` (`variable_key`, `variable_name`, `variable_type`, `variable_value`, `variable_label`, `sort_order`, `is_default`, `is_enabled`, `color`, `icon`) 
VALUES 
('unused', '未使用', 'card_status', '0', '充值卡未使用', 1, 1, 1, '#5bc0de', 'credit-card'),
('used', '已使用', 'card_status', '1', '充值卡已使用', 2, 0, 1, '#5cb85c', 'check'),
('disabled', '已禁用', 'card_status', '2', '充值卡已禁用', 3, 0, 1, '#d9534f', 'ban');

-- 公告类型变量
INSERT INTO `system_variable` (`variable_key`, `variable_name`, `variable_type`, `variable_value`, `variable_label`, `sort_order`, `is_default`, `is_enabled`, `color`, `icon`) 
VALUES 
('system', '系统公告', 'announcement_type', '1', '系统重要公告', 1, 1, 1, '#e74c3c', 'bullhorn'),
('daily', '日常公告', 'announcement_type', '2', '日常通知公告', 2, 0, 1, '#3498db', 'info-circle'),
('maintenance', '维护通知', 'announcement_type', '3', '系统维护通知', 3, 0, 1, '#f39c12', 'wrench'),
('activity', '活动公告', 'announcement_type', '4', '活动推广公告', 4, 0, 1, '#9b59b6', 'gift');

-- 客服会话状态变量
INSERT INTO `system_variable` (`variable_key`, `variable_name`, `variable_type`, `variable_value`, `variable_label`, `sort_order`, `is_default`, `is_enabled`, `color`, `icon`) 
VALUES 
('waiting', '等待中', 'session_status', '1', '等待客服接入', 1, 1, 1, '#f0ad4e', 'clock'),
('active', '进行中', 'session_status', '2', '客服会话进行中', 2, 0, 1, '#5bc0de', 'comments'),
-- 插入测试公告数据
-- Source: 基于现有系统架构设计

-- 插入系统公告（用于首次登录弹窗）
INSERT INTO `announcement` (`title`, `content`, `type`, `priority`, `is_top`, `is_popup`, `publish_time`, `status`, `create_by`) 
VALUES (
  '欢迎使用在线网课平台',
  '欢迎使用我们的在线网课平台！\n\n本平台提供专业的网课代刷服务，支持多种主流学习平台。\n\n主要功能：\n• 支持学习通、智慧职教等主流平台\n• 智能进度管理\n• 实时订单跟踪\n• 安全可靠的服务\n\n如有任何问题，请随时联系在线客服。',
  1, 2, 0, 1, NOW(), 1, 1
);

-- 插入日常公告（用于首页显示）
INSERT INTO `announcement` (`title`, `content`, `type`, `priority`, `is_top`, `is_popup`, `publish_time`, `status`, `create_by`) 
VALUES 
(
  '平台维护通知',
  '系统将于本周六凌晨2:00-4:00进行例行维护，期间可能影响服务使用，请提前安排。',
  2, 2, 1, 0, NOW(), 1, 1
),
(
  '新功能上线',
  '平台新增批量订单功能，支持一次性提交多个订单，提高效率。详情请查看订单管理页面。',
  2, 1, 0, 0, NOW(), 1, 1
),
(
  '优惠活动',
  '即日起至月底，新用户注册享受9折优惠，老用户推荐新用户可获得充值奖励。',
  2, 1, 0, 0, NOW(), 1, 1
);

-- 插入测试客服会话数据
INSERT INTO `customer_service_session` (`session_id`, `user_id`, `status`, `start_time`, `last_message_time`) 
VALUES 
('test_session_001', 1, 1, NOW(), NOW()),
('test_session_002', 2, 2, NOW(), NOW());

-- 插入测试客服消息数据
INSERT INTO `customer_service_message` (`session_id`, `sender_id`, `sender_type`, `message_type`, `content`, `is_read`) 
VALUES 
('test_session_001', 1, 1, 1, '你好，我想咨询一下平台的使用方法', 0),
('test_session_001', 1, 2, 1, '您好！欢迎使用我们的平台，有什么可以帮助您的吗？', 0),
('test_session_002', 2, 1, 1, '我的订单状态一直显示处理中，请问什么时候能完成？', 1),
('test_session_002', 1, 2, 1, '您的订单正在处理中，预计2小时内完成，请耐心等待。', 1);

-- 插入系统变量配置数据
-- Source: 基于现有系统架构设计

-- 订单状态变量
INSERT INTO `system_variable` (`variable_key`, `variable_name`, `variable_type`, `variable_value`, `variable_label`, `sort_order`, `is_default`, `is_enabled`, `color`, `icon`) 
VALUES 
('pending', '待处理', 'order_status', '0', '订单已创建，等待处理', 1, 1, 1, '#f0ad4e', 'clock'),
('processing', '进行中', 'order_status', '1', '订单正在处理中', 2, 0, 1, '#5bc0de', 'play'),
('completed', '已完成', 'order_status', '2', '订单处理完成', 3, 0, 1, '#5cb85c', 'check'),
('cancelled', '已取消', 'order_status', '3', '订单已取消', 4, 0, 1, '#d9534f', 'times'),
('failed', '失败', 'order_status', '4', '订单处理失败', 5, 0, 1, '#d9534f', 'exclamation'),
('exam_pending', '待考试', 'order_status', '5', '等待开始考试', 6, 0, 1, '#9b59b6', 'graduation-cap'),
('exam_processing', '考试中', 'order_status', '6', '正在考试中', 7, 0, 1, '#e67e22', 'book-open'),
('exam_completed', '考试完成', 'order_status', '7', '考试已完成', 8, 0, 1, '#1abc9c', 'check-circle'),
('refund_pending', '等待退款', 'order_status', '8', '第三方已退款，等待本地退款处理', 9, 0, 1, '#d97706', 'refresh-cw');

-- 对接状态变量
INSERT INTO `system_variable` (`variable_key`, `variable_name`, `variable_type`, `variable_value`, `variable_label`, `sort_order`, `is_default`, `is_enabled`, `color`, `icon`) 
VALUES 
('dock_pending', '待对接', 'dock_status', '0', '等待对接第三方平台', 1, 1, 1, '#f0ad4e', 'clock'),
('dock_success', '对接成功', 'dock_status', '1', '成功对接第三方平台', 2, 0, 1, '#5cb85c', 'check'),
('dock_failed', '对接失败', 'dock_status', '2', '对接第三方平台失败', 3, 0, 1, '#d9534f', 'times'),
('dock_duplicate', '重复订单', 'dock_status', '3', '检测到重复订单', 4, 0, 1, '#f39c12', 'exclamation-triangle'),
('dock_cancelled', '已取消', 'dock_status', '4', '对接已取消', 5, 0, 1, '#95a5a6', 'ban');

-- 用户状态变量
INSERT INTO `system_variable` (`variable_key`, `variable_name`, `variable_type`, `variable_value`, `variable_label`, `sort_order`, `is_default`, `is_enabled`, `color`, `icon`) 
VALUES 
('normal', '正常', 'user_status', '1', '用户状态正常', 1, 1, 1, '#5cb85c', 'user'),
('disabled', '禁用', 'user_status', '0', '用户已被禁用', 2, 0, 1, '#d9534f', 'user-times'),
('frozen', '冻结', 'user_status', '2', '用户账户被冻结', 3, 0, 1, '#f0ad4e', 'snowflake');

-- 平台状态变量
INSERT INTO `system_variable` (`variable_key`, `variable_name`, `variable_type`, `variable_value`, `variable_label`, `sort_order`, `is_default`, `is_enabled`, `color`, `icon`) 
VALUES 
('online', '上架', 'platform_status', '1', '平台正常上架', 1, 1, 1, '#5cb85c', 'check-circle'),
('offline', '下架', 'platform_status', '0', '平台已下架', 2, 0, 1, '#d9534f', 'times-circle'),
('maintenance', '维护中', 'platform_status', '2', '平台维护中', 3, 0, 1, '#f0ad4e', 'wrench');

-- 充值卡状态变量
INSERT INTO `system_variable` (`variable_key`, `variable_name`, `variable_type`, `variable_value`, `variable_label`, `sort_order`, `is_default`, `is_enabled`, `color`, `icon`) 
VALUES 
('unused', '未使用', 'card_status', '0', '充值卡未使用', 1, 1, 1, '#5bc0de', 'credit-card'),
('used', '已使用', 'card_status', '1', '充值卡已使用', 2, 0, 1, '#5cb85c', 'check'),
('disabled', '已禁用', 'card_status', '2', '充值卡已禁用', 3, 0, 1, '#d9534f', 'ban');

-- 公告类型变量
INSERT INTO `system_variable` (`variable_key`, `variable_name`, `variable_type`, `variable_value`, `variable_label`, `sort_order`, `is_default`, `is_enabled`, `color`, `icon`) 
VALUES 
('system', '系统公告', 'announcement_type', '1', '系统重要公告', 1, 1, 1, '#e74c3c', 'bullhorn'),
('daily', '日常公告', 'announcement_type', '2', '日常通知公告', 2, 0, 1, '#3498db', 'info-circle'),
('maintenance', '维护通知', 'announcement_type', '3', '系统维护通知', 3, 0, 1, '#f39c12', 'wrench'),
('activity', '活动公告', 'announcement_type', '4', '活动推广公告', 4, 0, 1, '#9b59b6', 'gift');

-- 客服会话状态变量
INSERT INTO `system_variable` (`variable_key`, `variable_name`, `variable_type`, `variable_value`, `variable_label`, `sort_order`, `is_default`, `is_enabled`, `color`, `icon`) 
VALUES 
('waiting', '等待中', 'session_status', '1', '等待客服接入', 1, 1, 1, '#f0ad4e', 'clock'),
('active', '进行中', 'session_status', '2', '客服会话进行中', 2, 0, 1, '#5bc0de', 'comments'),
('ended', '已结束', 'session_status', '3', '客服会话已结束', 3, 0, 1, '#5cb85c', 'check');

-- 消息类型变量
INSERT INTO `system_variable` (`variable_key`, `variable_name`, `variable_type`, `variable_value`, `variable_label`, `sort_order`, `is_default`, `is_enabled`, `color`, `icon`) 
VALUES 
('text', '文本', 'message_type', '1', '文本消息', 1, 1, 1, '#5bc0de', 'comment'),
('image', '图片', 'message_type', '2', '图片消息', 2, 0, 1, '#5cb85c', 'image'),
('file', '文件', 'message_type', '3', '文件消息', 3, 0, 1, '#f0ad4e', 'paperclip');

-- 插入平台分类测试数据
INSERT INTO `platform_category`(`id`,`name`,`sort_order`,`status`)
VALUES('1','实验室安全','0','1');