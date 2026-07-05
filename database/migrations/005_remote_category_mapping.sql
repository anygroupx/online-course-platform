-- 005: 添加远程分类ID映射字段
-- 用于解决远程分类ID重复问题

-- 添加字段到 platform_category 表
ALTER TABLE platform_category
    ADD COLUMN remote_category_id VARCHAR(50) DEFAULT NULL COMMENT '远程API的分类ID',
    ADD COLUMN remote_api_provider_id BIGINT DEFAULT NULL COMMENT '关联的API提供商ID';

-- 添加唯一索引，确保同一API提供商的同一远程分类ID只创建一次
ALTER TABLE platform_category
    ADD UNIQUE INDEX uk_remote_category (remote_category_id, remote_api_provider_id);

-- 添加外键约束（可选，根据需要启用）
-- ALTER TABLE platform_category
--     ADD CONSTRAINT fk_category_api_provider
--     FOREIGN KEY (remote_api_provider_id) REFERENCES api_provider(id)
--     ON DELETE SET NULL;

-- 为已存在的分类添加注释
ALTER TABLE platform_category
    MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '本地分类ID（自增）',
    MODIFY COLUMN name VARCHAR(100) NOT NULL COMMENT '分类名称',
    MODIFY COLUMN sort_order INT DEFAULT 0 COMMENT '排序值',
    MODIFY COLUMN status TINYINT DEFAULT 1 COMMENT '状态：0-禁用 1-启用';
