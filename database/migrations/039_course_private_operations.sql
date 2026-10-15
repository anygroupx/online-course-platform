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
