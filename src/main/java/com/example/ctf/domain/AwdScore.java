package com.example.ctf.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "awd_scores")
public class AwdScore {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "round_id", nullable = false, length = 36)
    private String roundId;

    @Column(name = "round_no", nullable = false)
    private int roundNo;

    @Column(name = "team_id", nullable = false, length = 36)
    private String teamId;

    @Column(name = "attack_gain", nullable = false)
    private int attackGain;

    @Column(name = "defense_loss", nullable = false)
    private int defenseLoss;

    @Column(name = "downtime_loss", nullable = false)
    private int downtimeLoss;

    @Column(name = "round_score", nullable = false)
    private int roundScore;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public void recalculate() {
        this.roundScore = attackGain - defenseLoss - downtimeLoss;
    }

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

    public int getAttackGain() {
        return attackGain;
    }

    public void setAttackGain(int attackGain) {
        this.attackGain = attackGain;
    }

    public int getDefenseLoss() {
        return defenseLoss;
    }

    public void setDefenseLoss(int defenseLoss) {
        this.defenseLoss = defenseLoss;
    }

    public int getDowntimeLoss() {
        return downtimeLoss;
    }

    public void setDowntimeLoss(int downtimeLoss) {
        this.downtimeLoss = downtimeLoss;
    }

    public int getRoundScore() {
        return roundScore;
    }

    public void setRoundScore(int roundScore) {
        this.roundScore = roundScore;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
