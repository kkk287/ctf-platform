package com.example.ctf.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public final class AdminDtos {

    private AdminDtos() {
    }

    public record AdminUserDto(String id, String username, String displayName, String role, String roleLabel,
                               boolean banned, String teamName, long solveCount, long totalPoints,
                               LocalDateTime createdAt) {
    }

    public record BanRequest(boolean banned) {
    }

    public record SubmissionDto(String id, String username, String challengeName, String result,
                                String resultLabel, String ip, String userAgent, String submittedHashPrefix,
                                LocalDateTime createdAt) {
    }

    public record AnomalyDto(String challengeId, String challengeName, String ip, long distinctUsers,
                             List<String> usernames, LocalDateTime firstAt, LocalDateTime lastAt) {
    }

    public record AnnouncementRequest(
            @NotBlank(message = "公告标题不能为空")
            @Size(max = 100, message = "公告标题最多 100 个字符") String title,
            @NotBlank(message = "公告内容不能为空")
            @Size(max = 2000, message = "公告内容最多 2000 个字符") String content) {
    }
}
