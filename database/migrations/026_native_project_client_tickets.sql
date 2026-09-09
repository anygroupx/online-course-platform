-- Source-platform downstream after-sales, NOT supplier tickets. Review records never move money.
-- Explicit, manually approved migration after025; no production auto-migration.
ALTER TABLE project_client ADD CONSTRAINT uk_project_client_identity UNIQUE (id, owner_id, project_id);
CREATE TABLE project_client_ticket (
 id VARCHAR(36) NOT NULL PRIMARY KEY, owner_id BIGINT NOT NULL, client_id VARCHAR(36) NOT NULL,
 project_id BIGINT NOT NULL, project_title VARCHAR(100) NOT NULL, kind VARCHAR(20) NOT NULL,
 title VARCHAR(120) NOT NULL, description VARCHAR(5000) NOT NULL,
 requested_amount DECIMAL(10,2) NOT NULL, status VARCHAR(20) NOT NULL, version BIGINT NOT NULL,
 review_result VARCHAR(20) NOT NULL, review_note VARCHAR(2000) NULL, reviewed_at DATETIME NULL,
 create_time DATETIME NOT NULL, update_time DATETIME NOT NULL,
 KEY idx_client_ticket_owner (owner_id,update_time), KEY idx_client_ticket_client (owner_id,client_id,update_time),
 CONSTRAINT fk_client_ticket_identity FOREIGN KEY (client_id,owner_id,project_id) REFERENCES project_client(id,owner_id,project_id),
 CONSTRAINT ck_client_ticket_kind CHECK (kind IN ('SUGGESTION','BUG','COMPENSATION')),
 CONSTRAINT ck_client_ticket_status CHECK (status IN ('OPEN','IN_PROGRESS','RESOLVED','CLOSED')),
 CONSTRAINT ck_client_ticket_amount CHECK (requested_amount>=0 AND version>=0),
 CONSTRAINT ck_client_ticket_review CHECK (
  (kind<>'COMPENSATION' AND requested_amount=0 AND review_result='NONE') OR
  (kind='COMPENSATION' AND (
   (review_result='PENDING' AND status IN ('OPEN','IN_PROGRESS')) OR
   (review_result='APPROVED' AND status='RESOLVED') OR
   (review_result='REJECTED' AND status='CLOSED'))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE project_client_ticket_reply (
 id VARCHAR(36) NOT NULL PRIMARY KEY, ticket_id VARCHAR(36) NOT NULL, ticket_version BIGINT NOT NULL,
 author VARCHAR(16) NOT NULL, content VARCHAR(5000) NOT NULL, create_time DATETIME NOT NULL,
 UNIQUE KEY uk_client_ticket_reply_version (ticket_id,ticket_version),
 CONSTRAINT fk_client_ticket_reply FOREIGN KEY (ticket_id) REFERENCES project_client_ticket(id),
 CONSTRAINT ck_client_ticket_reply_author CHECK (author IN ('OWNER','CUSTOMER'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE project_client_ticket_command (
 id VARCHAR(36) NOT NULL PRIMARY KEY, owner_id BIGINT NOT NULL, actor_subject VARCHAR(36) NOT NULL,
 request_id VARCHAR(36) NOT NULL, request_hash CHAR(64) NOT NULL,
 ticket_id VARCHAR(36) NOT NULL, action VARCHAR(20) NOT NULL, ticket_version BIGINT NOT NULL,
 create_time DATETIME NOT NULL,
 UNIQUE KEY uk_client_ticket_command_request (owner_id,actor_subject,request_id),
 CONSTRAINT fk_client_ticket_command FOREIGN KEY (ticket_id) REFERENCES project_client_ticket(id),
 CONSTRAINT ck_client_ticket_command_action CHECK (action IN ('CREATE','REPLY','RESOLVE','CLOSE','APPROVE','REJECT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
