package com.example.ctf.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "awd_teams")
public class AwdTeam {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "round_id", nullable = false, length = 36)
    private String roundId;

    @Column(nullable = false, length = 80)
    private String name;

    /** 逗号分隔的 username，用于判断 submitter 归属与展示成员 */
    @Column(nullable = false, length = 1000)
    private String members;

    @Column(length = 80)
    private String host;

    private Integer port;

    @Column(name = "container_id", length = 120)
    private String containerId;

    @Column(length = 120)
    private String flag;

    @Column(name = "flag_round")
    private Integer flagRound;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public boolean hasMember(String username) {
        if (username == null) {
            return false;
        }
        for (String member : members.split(",")) {
            if (member.trim().equalsIgnoreCase(username)) {
                return true;
            }
        }
        return false;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMembers() {
        return members;
    }

    public void setMembers(String members) {
        this.members = members;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getContainerId() {
        return containerId;
    }

    public void setContainerId(String containerId) {
        this.containerId = containerId;
    }

    public String getFlag() {
        return flag;
    }

    public void setFlag(String flag) {
        this.flag = flag;
    }

    public Integer getFlagRound() {
        return flagRound;
    }

    public void setFlagRound(Integer flagRound) {
        this.flagRound = flagRound;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
