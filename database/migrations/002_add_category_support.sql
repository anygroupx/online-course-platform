-- 创建平台分类表（参考 benz 的 qingka_wangke_fenlei）
CREATE TABLE IF NOT EXISTS `platform_category` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '分类ID',
  `name` VARCHAR(100) NOT NULL COMMENT '分类名称',
  `sort_order` INT DEFAULT 0 COMMENT '排序',
  `status` TINYINT DEFAULT 1 COMMENT '状态：0-禁用 1-启用',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`),
  KEY `idx_sort` (`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台分类表';

-- 为 course_platform 表添加分类ID字段
ALTER TABLE `course_platform`
ADD COLUMN `category_id` BIGINT DEFAULT NULL COMMENT '分类ID' AFTER `name`,
ADD KEY `idx_category_id` (`category_id`);
