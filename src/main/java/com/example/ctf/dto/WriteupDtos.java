package com.example.ctf.dto;

import java.time.LocalDateTime;

public final class WriteupDtos {

    private WriteupDtos() {
    }

    public record WriteupRequest(String challengeId, String title, String markdown) {
    }

    public record WriteupDto(String id, String eventId, String challengeId, String challengeName,
                             String userId, String username, String displayName,
                             String title, String status, String statusLabel, boolean accepted,
                             String markdown, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }

    /** 赛后挑战维度 Writeup 列表（不泄露正文，只给摘要） */
    public record SummaryDto(String id, String title, String status, String statusLabel,
                             boolean accepted, String userId, String username, String displayName,
                             LocalDateTime createdAt) {
    }

    public record ModerateRequest(String status, String note) {
    }
}
