-- P2：理论赛（单选/多选/判断、随机抽题、判分、能力报告）
-- 理论赛配置独立于 events 表，不污染赛事主表；答卷按 (event_id, user_id, question_id) 唯一物化。

-- 理论题库：按赛事维度挂题
CREATE TABLE theory_questions (
    id VARCHAR(36) PRIMARY KEY,
    event_id VARCHAR(36) NOT NULL,
    kind VARCHAR(10) NOT NULL,               -- SINGLE / MULTI / JUDGE
    stem TEXT NOT NULL,
    options_json TEXT NOT NULL,              -- JSON 数组：选项文本（判断题固定 ["正确","错误"]）
    answer_json TEXT NOT NULL,               -- JSON 数组：正确选项下标
    knowledge_tag VARCHAR(60),               -- 知识点标签，能力报告按此聚合
    difficulty VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_tq_event FOREIGN KEY (event_id) REFERENCES events (id) ON DELETE CASCADE
);

CREATE INDEX idx_tq_event_kind ON theory_questions (event_id, kind);

-- 理论赛配置：每赛事一条，定义各题型抽题数量与单题分值
CREATE TABLE theory_configs (
    event_id VARCHAR(36) PRIMARY KEY,
    single_count INT NOT NULL DEFAULT 0,
    single_points INT NOT NULL DEFAULT 0,
    multi_count INT NOT NULL DEFAULT 0,
    multi_points INT NOT NULL DEFAULT 0,
    judge_count INT NOT NULL DEFAULT 0,
    judge_points INT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_tc_event FOREIGN KEY (event_id) REFERENCES events (id) ON DELETE CASCADE
);

-- 理论答卷：首次取卷时按配置抽题物化空卷，提交后回写答案与得分
CREATE TABLE theory_answers (
    id VARCHAR(36) PRIMARY KEY,
    event_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    question_id VARCHAR(36) NOT NULL,
    given_json TEXT,                         -- JSON 数组：选手所选下标，未答为 NULL
    correct BOOLEAN NOT NULL DEFAULT FALSE,
    points INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_ta_event FOREIGN KEY (event_id) REFERENCES events (id) ON DELETE CASCADE,
    CONSTRAINT fk_ta_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_ta_question FOREIGN KEY (question_id) REFERENCES theory_questions (id) ON DELETE CASCADE,
    CONSTRAINT uk_ta_paper UNIQUE (event_id, user_id, question_id)
);

CREATE INDEX idx_ta_event_user ON theory_answers (event_id, user_id);
