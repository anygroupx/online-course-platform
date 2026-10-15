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
