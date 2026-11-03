-- P2：Writeup（赛后开放的解题报告，Markdown 存 MySQL，可沉淀入题库）
-- 规则：已解出该题的用户才能提交；比赛进行中可写但仅作者/管理员可见；赛后全员可见。

CREATE TABLE writeups (
    id VARCHAR(36) PRIMARY KEY,
    event_id VARCHAR(36) NOT NULL,
    challenge_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    title VARCHAR(120) NOT NULL,
    markdown TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',   -- DRAFT / SUBMITTED / ACCEPTED / REJECTED
    accepted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_wu_event FOREIGN KEY (event_id) REFERENCES events (id) ON DELETE CASCADE,
    CONSTRAINT fk_wu_challenge FOREIGN KEY (challenge_id) REFERENCES challenges (id) ON DELETE CASCADE,
    CONSTRAINT fk_wu_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT uk_wu_user_challenge UNIQUE (event_id, user_id, challenge_id)
);

CREATE INDEX idx_wu_event ON writeups (event_id, status);
CREATE INDEX idx_wu_challenge ON writeups (event_id, challenge_id, status);
CREATE INDEX idx_wu_user ON writeups (user_id, status);
