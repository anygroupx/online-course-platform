-- Append-only course order progress history.
-- Apply explicitly before deploying code that reads or writes progress logs.
CREATE TABLE course_order_progress_log (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  order_id BIGINT NOT NULL COMMENT '订单ID',
  third_order_id VARCHAR(100) DEFAULT NULL COMMENT '第三方订单ID',
  api_provider_id BIGINT DEFAULT NULL COMMENT '接口配置ID',
  progress VARCHAR(500) DEFAULT NULL COMMENT '完成进度快照',
  order_status TINYINT DEFAULT NULL COMMENT '订单状态快照',
  remarks VARCHAR(500) DEFAULT NULL COMMENT '备注快照',
  source VARCHAR(32) NOT NULL COMMENT '记录来源：manual_refresh/scheduled_sync',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',
  PRIMARY KEY (id),
  KEY idx_order_progress_log_order_time (order_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程订单进度日志表';
