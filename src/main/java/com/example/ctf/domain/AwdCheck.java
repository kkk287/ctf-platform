package com.example.ctf.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "awd_checks")
public class AwdCheck {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "round_id", nullable = false, length = 36)
    private String roundId;

    @Column(name = "round_no", nullable = false)
    private int roundNo;

    @Column(name = "team_id", nullable = false, length = 36)
    private String teamId;

    @Column(nullable = false)
    private boolean ok;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(length = 500)
    private String detail;

    @Column(name = "checked_at", nullable = false)
    private LocalDateTime checkedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRoundId() {
        return roundId;
    }

    public void setRoundId(String roundId) {
        this.roundId = roundId;
    }

    public int getRoundNo() {
        return roundNo;
    }

    public void setRoundNo(int roundNo) {
        this.roundNo = roundNo;
    }

    public String getTeamId() {
        return teamId;
    }

    public void setTeamId(String teamId) {
        this.teamId = teamId;
    }

    public boolean isOk() {
        return ok;
    }

    public void setOk(boolean ok) {
        this.ok = ok;
    }

    public Integer getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(Integer latencyMs) {
        this.latencyMs = latencyMs;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public LocalDateTime getCheckedAt() {
        return checkedAt;
    }

    public void setCheckedAt(LocalDateTime checkedAt) {
        this.checkedAt = checkedAt;
    }
}
