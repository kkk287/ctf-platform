package com.example.ctf.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** 理论赛配置：每赛事一条，定义各题型抽题数量与单题分值。 */
@Entity
@Table(name = "theory_configs")
public class TheoryConfig {

    @Id
    @Column(name = "event_id", length = 36)
    private String eventId;

    @Column(name = "single_count", nullable = false)
    private int singleCount;

    @Column(name = "single_points", nullable = false)
    private int singlePoints;

    @Column(name = "multi_count", nullable = false)
    private int multiCount;

    @Column(name = "multi_points", nullable = false)
    private int multiPoints;

    @Column(name = "judge_count", nullable = false)
    private int judgeCount;

    @Column(name = "judge_points", nullable = false)
    private int judgePoints;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void onSave() {
        updatedAt = LocalDateTime.now();
    }

    public int countFor(QuestionKind kind) {
        return switch (kind) {
            case SINGLE -> singleCount;
            case MULTI -> multiCount;
            case JUDGE -> judgeCount;
        };
    }

    public int pointsFor(QuestionKind kind) {
        return switch (kind) {
            case SINGLE -> singlePoints;
            case MULTI -> multiPoints;
            case JUDGE -> judgePoints;
        };
    }

    public int totalQuestions() {
        return singleCount + multiCount + judgeCount;
    }

    // ----- getters / setters -----
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public int getSingleCount() { return singleCount; }
    public void setSingleCount(int singleCount) { this.singleCount = singleCount; }
    public int getSinglePoints() { return singlePoints; }
    public void setSinglePoints(int singlePoints) { this.singlePoints = singlePoints; }
    public int getMultiCount() { return multiCount; }
    public void setMultiCount(int multiCount) { this.multiCount = multiCount; }
    public int getMultiPoints() { return multiPoints; }
    public void setMultiPoints(int multiPoints) { this.multiPoints = multiPoints; }
    public int getJudgeCount() { return judgeCount; }
    public void setJudgeCount(int judgeCount) { this.judgeCount = judgeCount; }
    public int getJudgePoints() { return judgePoints; }
    public void setJudgePoints(int judgePoints) { this.judgePoints = judgePoints; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
