package com.example.ctf.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "awd_flags")
public class AwdFlag {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "round_id", nullable = false, length = 36)
    private String roundId;

    @Column(name = "round_no", nullable = false)
    private int roundNo;

    @Column(name = "team_id", nullable = false, length = 36)
    private String teamId;

    @Column(nullable = false, length = 120)
    private String flag;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

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

    public String getFlag() {
        return flag;
    }

    public void setFlag(String flag) {
        this.flag = flag;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
