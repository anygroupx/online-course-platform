-- Local downstream ticket rasters only. Source images are normalized before encrypted storage.
-- Requires026. Explicit approval required before any production migration.
CREATE TABLE project_client_ticket_image (
 id VARCHAR(36) NOT NULL PRIMARY KEY, ticket_id VARCHAR(36) NOT NULL, ticket_version BIGINT NOT NULL,
 width INT NOT NULL, height INT NOT NULL, byte_size INT NOT NULL, sha256 CHAR(64) NOT NULL,
 content_encrypted MEDIUMTEXT NOT NULL, create_time DATETIME NOT NULL,
 UNIQUE KEY uk_client_ticket_image_message (ticket_id,ticket_version),
 CONSTRAINT fk_client_ticket_image_ticket FOREIGN KEY (ticket_id) REFERENCES project_client_ticket(id),
 CONSTRAINT ck_client_ticket_image_bounds CHECK (width BETWEEN 1 AND 4096 AND height BETWEEN 1 AND 4096 AND width*height<=4000000 AND byte_size BETWEEN 1 AND 4194304 AND ticket_version>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
ALTER TABLE project_client_ticket ADD COLUMN image_id VARCHAR(36) NULL;
ALTER TABLE project_client_ticket ADD CONSTRAINT fk_client_ticket_image FOREIGN KEY (image_id) REFERENCES project_client_ticket_image(id);
ALTER TABLE project_client_ticket_reply ADD COLUMN image_id VARCHAR(36) NULL;
ALTER TABLE project_client_ticket_reply ADD CONSTRAINT fk_client_ticket_reply_image FOREIGN KEY (image_id) REFERENCES project_client_ticket_image(id);
