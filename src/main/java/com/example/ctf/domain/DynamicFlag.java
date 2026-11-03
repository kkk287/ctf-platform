package com.example.ctf.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

/** 动态 flag：每次拉起靶机按 subjectId 生成唯一 flag，注入容器 env 并落库；明文永不出库。 */
@Entity
@Table(name = "dynamic_flags", uniqueConstraints = @UniqueConstraint(name = "uk_df_instance", columnNames = "instance_id"))
public class DynamicFlag {

    @Id
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instance_id", nullable = false)
    private Instance instance;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "challenge_id", nullable = false)
    private Challenge challenge;

    @Column(name = "owner_id", nullable = false, length = 36)
    private String ownerId;

    @Column(name = "flag_hash", nullable = false, length = 64)
    private String flagHash;

    @Column(name = "flag_salt", nullable = false, length = 64)
    private String flagSalt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    // ----- getters / setters -----
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Instance getInstance() { return instance; }
    public void setInstance(Instance instance) { this.instance = instance; }
    public Challenge getChallenge() { return challenge; }
    public void setChallenge(Challenge challenge) { this.challenge = challenge; }
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public String getFlagHash() { return flagHash; }
    public void setFlagHash(String flagHash) { this.flagHash = flagHash; }
    public String getFlagSalt() { return flagSalt; }
    public void setFlagSalt(String flagSalt) { this.flagSalt = flagSalt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
