-- P2：动态靶机下发基础设施
-- 为 challenges 增加靶机配置字段，新增 instances（靶机实例）与 dynamic_flags（动态 flag）两张表。

-- challenges 扩展：靶机配置（全部可空，存量题目不受影响）
ALTER TABLE challenges ADD COLUMN instance_image VARCHAR(200);
ALTER TABLE challenges ADD COLUMN instance_port INT;
ALTER TABLE challenges ADD COLUMN instance_ttl_minutes INT;
ALTER TABLE challenges ADD COLUMN instance_cpu_limit VARCHAR(30);   -- cpus=1.5 格式
ALTER TABLE challenges ADD COLUMN instance_memory_mb INT;
ALTER TABLE challenges ADD COLUMN instance_dynamic_flag BOOLEAN NOT NULL DEFAULT FALSE;

-- 靶机实例表（每次申请一条，TTL 到期自动回收）
CREATE TABLE instances (
    id VARCHAR(36) PRIMARY KEY,
    challenge_id VARCHAR(36) NOT NULL,
    owner_id VARCHAR(36) NOT NULL,
    owner_type VARCHAR(20) NOT NULL,        -- USER / TEAM
    container_id VARCHAR(120),              -- Docker 容器 ID（可能为空，Noop 或拉起失败时）
    container_name VARCHAR(120),
    host VARCHAR(80),                      -- 宿主机 IP / 域名（前端给选手访问）
    port INT,                              -- 映射到宿主的对外端口
    status VARCHAR(20) NOT NULL,           -- PENDING / RUNNING / STOPPED / EXPIRED / FAILED
    expires_at TIMESTAMP NOT NULL,          -- TTL 到期时间
    started_at TIMESTAMP,
    stopped_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_instance_challenge FOREIGN KEY (challenge_id) REFERENCES challenges (id) ON DELETE CASCADE
);

CREATE INDEX idx_instance_owner ON instances (owner_id, status);
CREATE INDEX idx_instance_challenge ON instances (challenge_id);
CREATE INDEX idx_instance_status_expires ON instances (status, expires_at);

-- 动态 flag 表：每次拉起靶机生成一条，注入容器 env 并落库
CREATE TABLE dynamic_flags (
    id VARCHAR(36) PRIMARY KEY,
    instance_id VARCHAR(36) NOT NULL,
    challenge_id VARCHAR(36) NOT NULL,
    owner_id VARCHAR(36) NOT NULL,
    flag_hash VARCHAR(64) NOT NULL,         -- SHA-256(flag_plaintext)，明文永不出库
    flag_salt VARCHAR(64) NOT NULL,          -- 复用 FlagHasher 加盐
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_df_instance FOREIGN KEY (instance_id) REFERENCES instances (id) ON DELETE CASCADE,
    CONSTRAINT fk_df_challenge FOREIGN KEY (challenge_id) REFERENCES challenges (id) ON DELETE CASCADE
);

CREATE INDEX idx_df_owner ON dynamic_flags (owner_id, challenge_id);
CREATE UNIQUE INDEX uk_df_instance ON dynamic_flags (instance_id);
