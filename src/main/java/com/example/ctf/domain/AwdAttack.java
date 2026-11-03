package com.example.ctf.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "awd_attacks")
public class AwdAttack {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "round_id", nullable = false, length = 36)
    private String roundId;

    @Column(name = "round_no", nullable = false)
    private int roundNo;

    @Column(name = "attacker_team_id", nullable = false, length = 36)
    private String attackerTeamId;

    @Column(name = "victim_team_id", nullable = false, length = 36)
    private String victimTeamId;

    @Column(nullable = false, length = 120)
    private String flag;

    @Column(nullable = false)
    private int points;

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

    public String getAttackerTeamId() {
        return attackerTeamId;
    }

    public void setAttackerTeamId(String attackerTeamId) {
        this.attackerTeamId = attackerTeamId;
    }

    public String getVictimTeamId() {
        return victimTeamId;
    }

    public void setVictimTeamId(String victimTeamId) {
        this.victimTeamId = victimTeamId;
    }

    public String getFlag() {
        return flag;
    }

    public void setFlag(String flag) {
        this.flag = flag;
    }

    public int getPoints() {
        return points;
    }

    public void setPoints(int points) {
        this.points = points;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
