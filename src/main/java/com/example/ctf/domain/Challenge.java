package com.example.ctf.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "challenges")
public class Challenge {

    @Id
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 3000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Difficulty difficulty;

    @Column(name = "initial_points", nullable = false)
    private int initialPoints;

    @Column(name = "min_points", nullable = false)
    private int minPoints;

    @Column(name = "decay_points", nullable = false)
    private int decayPoints;

    /** flag 只保存加盐哈希，不落明文 */
    @Column(name = "flag_hash", nullable = false, length = 64)
    private String flagHash;

    @Column(name = "flag_salt", nullable = false, length = 64)
    private String flagSalt;

    @Column(nullable = false)
    private boolean visible;

    @Column(name = "attachment_stored_name", length = 120)
    private String attachmentStoredName;

    @Column(name = "attachment_original_name", length = 200)
    private String attachmentOriginalName;

    @Column(name = "attachment_content_type", length = 120)
    private String attachmentContentType;

    @Column(name = "attachment_size")
    private Long attachmentSize;

    // ----- P2 动态靶机配置（全部可空；为空表示该题目不启用动态靶机） -----

    @Column(name = "instance_image", length = 200)
    private String instanceImage;

    @Column(name = "instance_port")
    private Integer instancePort;

    @Column(name = "instance_ttl_minutes")
    private Integer instanceTtlMinutes;

    @Column(name = "instance_cpu_limit", length = 30)
    private String instanceCpuLimit;

    @Column(name = "instance_memory_mb")
    private Integer instanceMemoryMb;

    /**
     * 启用后：每次申请靶机会为每个 owner 生成独立 flag 注入容器 env 并落 dynamic_flags。
     * 未启用：容器使用题目自带静态 flag（与现有行为一致）。
     */
    @Column(name = "instance_dynamic_flag", nullable = false)
    private boolean instanceDynamicFlag = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Event getEvent() {
        return event;
    }

    public void setEvent(Event event) {
        this.event = event;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(Difficulty difficulty) {
        this.difficulty = difficulty;
    }

    public int getInitialPoints() {
        return initialPoints;
    }

    public void setInitialPoints(int initialPoints) {
        this.initialPoints = initialPoints;
    }

    public int getMinPoints() {
        return minPoints;
    }

    public void setMinPoints(int minPoints) {
        this.minPoints = minPoints;
    }

    public int getDecayPoints() {
        return decayPoints;
    }

    public void setDecayPoints(int decayPoints) {
        this.decayPoints = decayPoints;
    }

    public String getFlagHash() {
        return flagHash;
    }

    public void setFlagHash(String flagHash) {
        this.flagHash = flagHash;
    }

    public String getFlagSalt() {
        return flagSalt;
    }

    public void setFlagSalt(String flagSalt) {
        this.flagSalt = flagSalt;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public String getAttachmentStoredName() {
        return attachmentStoredName;
    }

    public void setAttachmentStoredName(String attachmentStoredName) {
        this.attachmentStoredName = attachmentStoredName;
    }

    public String getAttachmentOriginalName() {
        return attachmentOriginalName;
    }

    public void setAttachmentOriginalName(String attachmentOriginalName) {
        this.attachmentOriginalName = attachmentOriginalName;
    }

    public String getAttachmentContentType() {
        return attachmentContentType;
    }

    public void setAttachmentContentType(String attachmentContentType) {
        this.attachmentContentType = attachmentContentType;
    }

    public Long getAttachmentSize() {
        return attachmentSize;
    }

    public void setAttachmentSize(Long attachmentSize) {
        this.attachmentSize = attachmentSize;
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

    public String getInstanceImage() { return instanceImage; }
    public void setInstanceImage(String instanceImage) { this.instanceImage = instanceImage; }

    public Integer getInstancePort() { return instancePort; }
    public void setInstancePort(Integer instancePort) { this.instancePort = instancePort; }

    public Integer getInstanceTtlMinutes() { return instanceTtlMinutes; }
    public void setInstanceTtlMinutes(Integer instanceTtlMinutes) { this.instanceTtlMinutes = instanceTtlMinutes; }

    public String getInstanceCpuLimit() { return instanceCpuLimit; }
    public void setInstanceCpuLimit(String instanceCpuLimit) { this.instanceCpuLimit = instanceCpuLimit; }

    public Integer getInstanceMemoryMb() { return instanceMemoryMb; }
    public void setInstanceMemoryMb(Integer instanceMemoryMb) { this.instanceMemoryMb = instanceMemoryMb; }

    public boolean isInstanceDynamicFlag() { return instanceDynamicFlag; }
    public void setInstanceDynamicFlag(boolean instanceDynamicFlag) { this.instanceDynamicFlag = instanceDynamicFlag; }
}
