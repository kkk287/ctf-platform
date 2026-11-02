-- CTF 平台基础表结构（H2 / MySQL 8 / PostgreSQL 兼容子集）
-- 所有主键使用 VARCHAR(36)，由应用层生成 UUID，避免各数据库自增语法差异。

CREATE TABLE app_user (
    id VARCHAR(36) PRIMARY KEY,
    username VARCHAR(40) NOT NULL,
    password VARCHAR(100) NOT NULL,
    display_name VARCHAR(60) NOT NULL,
    role VARCHAR(20) NOT NULL,
    banned BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_user_username UNIQUE (username)
);

CREATE TABLE teams (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(40) NOT NULL,
    invite_code VARCHAR(12) NOT NULL,
    captain_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_team_name UNIQUE (name),
    CONSTRAINT uk_team_invite UNIQUE (invite_code),
    CONSTRAINT fk_team_captain FOREIGN KEY (captain_id) REFERENCES app_user (id)
);

ALTER TABLE app_user ADD COLUMN team_id VARCHAR(36);
ALTER TABLE app_user ADD CONSTRAINT fk_user_team FOREIGN KEY (team_id) REFERENCES teams (id) ON DELETE SET NULL;

CREATE TABLE events (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(80) NOT NULL,
    description VARCHAR(1000),
    type VARCHAR(20) NOT NULL,
    start_at TIMESTAMP,
    end_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE challenges (
    id VARCHAR(36) PRIMARY KEY,
    event_id VARCHAR(36) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(3000),
    category VARCHAR(20) NOT NULL,
    difficulty VARCHAR(20) NOT NULL,
    initial_points INT NOT NULL,
    min_points INT NOT NULL,
    decay_points INT NOT NULL,
    flag_hash VARCHAR(64) NOT NULL,
    flag_salt VARCHAR(64) NOT NULL,
    visible BOOLEAN NOT NULL DEFAULT TRUE,
    attachment_stored_name VARCHAR(120),
    attachment_original_name VARCHAR(200),
    attachment_content_type VARCHAR(120),
    attachment_size BIGINT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_challenge_event FOREIGN KEY (event_id) REFERENCES events (id) ON DELETE CASCADE
);

CREATE TABLE hints (
    id VARCHAR(36) PRIMARY KEY,
    challenge_id VARCHAR(36) NOT NULL,
    content VARCHAR(500) NOT NULL,
    cost INT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_hint_challenge FOREIGN KEY (challenge_id) REFERENCES challenges (id) ON DELETE CASCADE
);

CREATE TABLE hint_unlocks (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    challenge_id VARCHAR(36) NOT NULL,
    hint_id VARCHAR(36) NOT NULL,
    cost INT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_unlock_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_unlock_challenge FOREIGN KEY (challenge_id) REFERENCES challenges (id) ON DELETE CASCADE,
    CONSTRAINT fk_unlock_hint FOREIGN KEY (hint_id) REFERENCES hints (id) ON DELETE CASCADE,
    CONSTRAINT uk_hint_unlock UNIQUE (user_id, hint_id)
);

CREATE TABLE submissions (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    challenge_id VARCHAR(36) NOT NULL,
    submitted_hash VARCHAR(64) NOT NULL,
    result VARCHAR(20) NOT NULL,
    ip VARCHAR(64),
    user_agent VARCHAR(300),
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_submission_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_submission_challenge FOREIGN KEY (challenge_id) REFERENCES challenges (id) ON DELETE CASCADE
);

CREATE TABLE solves (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    challenge_id VARCHAR(36) NOT NULL,
    submission_id VARCHAR(36),
    base_points INT NOT NULL,
    blood_bonus INT NOT NULL,
    hint_cost INT NOT NULL,
    final_points INT NOT NULL,
    blood_rank VARCHAR(20) NOT NULL,
    solved_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_solve_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_solve_challenge FOREIGN KEY (challenge_id) REFERENCES challenges (id) ON DELETE CASCADE,
    CONSTRAINT uk_solve_user_challenge UNIQUE (user_id, challenge_id)
);

CREATE TABLE announcements (
    id VARCHAR(36) PRIMARY KEY,
    title VARCHAR(100) NOT NULL,
    content VARCHAR(2000) NOT NULL,
    created_by VARCHAR(36),
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_announcement_user FOREIGN KEY (created_by) REFERENCES app_user (id) ON DELETE SET NULL
);

CREATE INDEX idx_challenge_event ON challenges (event_id);
CREATE INDEX idx_challenge_visible ON challenges (visible);
CREATE INDEX idx_hint_challenge ON hints (challenge_id);
CREATE INDEX idx_submission_user_challenge_time ON submissions (user_id, challenge_id, created_at);
CREATE INDEX idx_submission_challenge_result ON submissions (challenge_id, result);
CREATE INDEX idx_solve_user ON solves (user_id);
CREATE INDEX idx_solve_challenge ON solves (challenge_id);
CREATE INDEX idx_solve_time ON solves (solved_at);
CREATE INDEX idx_user_team ON app_user (team_id);
