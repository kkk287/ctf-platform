package com.example.ctf.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "awd_rounds")
public class AwdRound {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "event_id", nullable = false, length = 36)
    private String eventId;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AwdStatus status;

    @Column(name = "current_round", nullable = false)
    private int currentRound;

    @Column(name = "total_rounds", nullable = false)
    private int totalRounds;

    @Column(name = "round_seconds", nullable = false)
    private int roundSeconds;

    @Column(name = "remaining_seconds", nullable = false)
    private int remainingSeconds;

    @Column(name = "attack_points", nullable = false)
    private int attackPoints;

    @Column(name = "defense_penalty", nullable = false)
    private int defensePenalty;

    @Column(name = "downtime_penalty", nullable = false)
    private int downtimePenalty;

    @Column(name = "check_type", nullable = false, length = 10)
    private String checkType;

    @Column(name = "check_target", length = 300)
    private String checkTarget;

    @Column(length = 200)
    private String image;

    @Column(name = "container_port")
    private Integer containerPort;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "round_ends_at")
    private LocalDateTime roundEndsAt;

    @Column(name = "last_check_at")
    private LocalDateTime lastCheckAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public AwdStatus getStatus() {
        return status;
    }

    public void setStatus(AwdStatus status) {
        this.status = status;
    }

    public int getCurrentRound() {
        return currentRound;
    }

    public void setCurrentRound(int currentRound) {
        this.currentRound = currentRound;
    }

    public int getTotalRounds() {
        return totalRounds;
    }

    public void setTotalRounds(int totalRounds) {
        this.totalRounds = totalRounds;
    }

    public int getRoundSeconds() {
        return roundSeconds;
    }

    public void setRoundSeconds(int roundSeconds) {
        this.roundSeconds = roundSeconds;
    }

    public int getRemainingSeconds() {
        return remainingSeconds;
    }

    public void setRemainingSeconds(int remainingSeconds) {
        this.remainingSeconds = remainingSeconds;
    }

    public int getAttackPoints() {
        return attackPoints;
    }

    public void setAttackPoints(int attackPoints) {
        this.attackPoints = attackPoints;
    }

    public int getDefensePenalty() {
        return defensePenalty;
    }

    public void setDefensePenalty(int defensePenalty) {
        this.defensePenalty = defensePenalty;
    }

    public int getDowntimePenalty() {
        return downtimePenalty;
    }

    public void setDowntimePenalty(int downtimePenalty) {
        this.downtimePenalty = downtimePenalty;
    }

    public String getCheckType() {
        return checkType;
    }

    public void setCheckType(String checkType) {
        this.checkType = checkType;
    }

    public String getCheckTarget() {
        return checkTarget;
    }

    public void setCheckTarget(String checkTarget) {
        this.checkTarget = checkTarget;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public Integer getContainerPort() {
        return containerPort;
    }

    public void setContainerPort(Integer containerPort) {
        this.containerPort = containerPort;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getRoundEndsAt() {
        return roundEndsAt;
    }

    public void setRoundEndsAt(LocalDateTime roundEndsAt) {
        this.roundEndsAt = roundEndsAt;
    }

    public LocalDateTime getLastCheckAt() {
        return lastCheckAt;
    }

    public void setLastCheckAt(LocalDateTime lastCheckAt) {
        this.lastCheckAt = lastCheckAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(LocalDateTime finishedAt) {
        this.finishedAt = finishedAt;
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
