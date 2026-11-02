package com.example.ctf.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "solves", uniqueConstraints = {
        @UniqueConstraint(name = "uk_solve_user_challenge", columnNames = {"user_id", "challenge_id"})
})
public class Solve {

    @Id
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "challenge_id", nullable = false)
    private Challenge challenge;

    @Column(name = "submission_id", length = 36)
    private String submissionId;

    @Column(name = "base_points", nullable = false)
    private int basePoints;

    @Column(name = "blood_bonus", nullable = false)
    private int bloodBonus;

    @Column(name = "hint_cost", nullable = false)
    private int hintCost;

    @Column(name = "final_points", nullable = false)
    private int finalPoints;

    @Enumerated(EnumType.STRING)
    @Column(name = "blood_rank", nullable = false, length = 20)
    private BloodRank bloodRank;

    @Column(name = "solved_at", nullable = false)
    private LocalDateTime solvedAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (solvedAt == null) {
            solvedAt = LocalDateTime.now();
        }
        if (bloodRank == null) {
            bloodRank = BloodRank.NONE;
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Challenge getChallenge() {
        return challenge;
    }

    public void setChallenge(Challenge challenge) {
        this.challenge = challenge;
    }

    public String getSubmissionId() {
        return submissionId;
    }

    public void setSubmissionId(String submissionId) {
        this.submissionId = submissionId;
    }

    public int getBasePoints() {
        return basePoints;
    }

    public void setBasePoints(int basePoints) {
        this.basePoints = basePoints;
    }

    public int getBloodBonus() {
        return bloodBonus;
    }

    public void setBloodBonus(int bloodBonus) {
        this.bloodBonus = bloodBonus;
    }

    public int getHintCost() {
        return hintCost;
    }

    public void setHintCost(int hintCost) {
        this.hintCost = hintCost;
    }

    public int getFinalPoints() {
        return finalPoints;
    }

    public void setFinalPoints(int finalPoints) {
        this.finalPoints = finalPoints;
    }

    public BloodRank getBloodRank() {
        return bloodRank;
    }

    public void setBloodRank(BloodRank bloodRank) {
        this.bloodRank = bloodRank;
    }

    public LocalDateTime getSolvedAt() {
        return solvedAt;
    }

    public void setSolvedAt(LocalDateTime solvedAt) {
        this.solvedAt = solvedAt;
    }
}
