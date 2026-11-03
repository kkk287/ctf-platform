-- P1：赛事命名空间（逻辑隔离单元） + 审计日志表
-- 兼容子集说明：namespace 列允许 NULL（H2/MySQL/PG 三方 ALTER ... SET NOT NULL 语法不一致），
-- 应用层（Event 实体 @PrePersist）保证写入非空；唯一性由索引保证。

ALTER TABLE events ADD COLUMN namespace VARCHAR(60);
UPDATE events SET namespace = id WHERE namespace IS NULL;
CREATE UNIQUE INDEX uk_event_namespace ON events (namespace);

CREATE TABLE audit_logs (
    id VARCHAR(36) PRIMARY KEY,
    actor_id VARCHAR(36),
    actor_name VARCHAR(60),
    actor_role VARCHAR(20),
    action VARCHAR(40) NOT NULL,
    target VARCHAR(200),
    detail VARCHAR(1000),
    ip VARCHAR(60),
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_audit_actor FOREIGN KEY (actor_id) REFERENCES app_user (id) ON DELETE SET NULL
);

CREATE INDEX idx_audit_time ON audit_logs (created_at);
