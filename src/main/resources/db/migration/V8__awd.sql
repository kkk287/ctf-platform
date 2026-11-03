-- P4 AWD/AWDP：轮次、队伍、flag 轮换、攻防计分、check 结果
CREATE TABLE awd_rounds (
    id VARCHAR(36) PRIMARY KEY,
    event_id VARCHAR(36) NOT NULL,
    name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL,
    current_round INTEGER NOT NULL DEFAULT 0,
    total_rounds INTEGER NOT NULL DEFAULT 5,
    round_seconds INTEGER NOT NULL DEFAULT 300,
    remaining_seconds INTEGER NOT NULL DEFAULT 0,
    attack_points INTEGER NOT NULL DEFAULT 100,
    defense_penalty INTEGER NOT NULL DEFAULT 50,
    downtime_penalty INTEGER NOT NULL DEFAULT 30,
    check_type VARCHAR(10) NOT NULL DEFAULT 'TCP',
    check_target VARCHAR(300),
    image VARCHAR(200),
    container_port INTEGER,
    started_at TIMESTAMP,
    round_ends_at TIMESTAMP,
    last_check_at TIMESTAMP,
    finished_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_awd_round_event FOREIGN KEY (event_id) REFERENCES events (id) ON DELETE CASCADE
);

CREATE TABLE awd_teams (
    id VARCHAR(36) PRIMARY KEY,
    round_id VARCHAR(36) NOT NULL,
    name VARCHAR(80) NOT NULL,
    members VARCHAR(1000) NOT NULL,
    host VARCHAR(80),
    port INTEGER,
    container_id VARCHAR(120),
    flag VARCHAR(120),
    flag_round INTEGER,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_awd_team_round FOREIGN KEY (round_id) REFERENCES awd_rounds (id) ON DELETE CASCADE,
    CONSTRAINT uk_awd_team_name UNIQUE (round_id, name)
);

CREATE TABLE awd_flags (
    id VARCHAR(36) PRIMARY KEY,
    round_id VARCHAR(36) NOT NULL,
    round_no INTEGER NOT NULL,
    team_id VARCHAR(36) NOT NULL,
    flag VARCHAR(120) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_awd_flag_round FOREIGN KEY (round_id) REFERENCES awd_rounds (id) ON DELETE CASCADE,
    CONSTRAINT fk_awd_flag_team FOREIGN KEY (team_id) REFERENCES awd_teams (id) ON DELETE CASCADE,
    CONSTRAINT uk_awd_flag_round_team UNIQUE (round_id, round_no, team_id),
    CONSTRAINT uk_awd_flag_value UNIQUE (flag)
);

CREATE TABLE awd_attacks (
    id VARCHAR(36) PRIMARY KEY,
    round_id VARCHAR(36) NOT NULL,
    round_no INTEGER NOT NULL,
    attacker_team_id VARCHAR(36) NOT NULL,
    victim_team_id VARCHAR(36) NOT NULL,
    flag VARCHAR(120) NOT NULL,
    points INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_awd_attack_round FOREIGN KEY (round_id) REFERENCES awd_rounds (id) ON DELETE CASCADE,
    CONSTRAINT fk_awd_attack_attacker FOREIGN KEY (attacker_team_id) REFERENCES awd_teams (id) ON DELETE CASCADE,
    CONSTRAINT fk_awd_attack_victim FOREIGN KEY (victim_team_id) REFERENCES awd_teams (id) ON DELETE CASCADE,
    CONSTRAINT uk_awd_attack_flag UNIQUE (round_id, flag)
);

CREATE TABLE awd_scores (
    id VARCHAR(36) PRIMARY KEY,
    round_id VARCHAR(36) NOT NULL,
    round_no INTEGER NOT NULL,
    team_id VARCHAR(36) NOT NULL,
    attack_gain INTEGER NOT NULL DEFAULT 0,
    defense_loss INTEGER NOT NULL DEFAULT 0,
    downtime_loss INTEGER NOT NULL DEFAULT 0,
    round_score INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_awd_score_round FOREIGN KEY (round_id) REFERENCES awd_rounds (id) ON DELETE CASCADE,
    CONSTRAINT fk_awd_score_team FOREIGN KEY (team_id) REFERENCES awd_teams (id) ON DELETE CASCADE,
    CONSTRAINT uk_awd_score_round_team UNIQUE (round_id, round_no, team_id)
);

CREATE TABLE awd_checks (
    id VARCHAR(36) PRIMARY KEY,
    round_id VARCHAR(36) NOT NULL,
    round_no INTEGER NOT NULL,
    team_id VARCHAR(36) NOT NULL,
    ok BOOLEAN NOT NULL,
    latency_ms INTEGER,
    detail VARCHAR(500),
    checked_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_awd_check_round FOREIGN KEY (round_id) REFERENCES awd_rounds (id) ON DELETE CASCADE,
    CONSTRAINT fk_awd_check_team FOREIGN KEY (team_id) REFERENCES awd_teams (id) ON DELETE CASCADE
);

CREATE INDEX idx_awd_checks_round ON awd_checks (round_id, round_no);
CREATE INDEX idx_awd_scores_round ON awd_scores (round_id, round_no);
CREATE INDEX idx_awd_teams_round ON awd_teams (round_id);
