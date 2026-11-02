package com.example.ctf.dto;

import com.example.ctf.domain.EventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class EventDtos {

    private EventDtos() {
    }

    public record EventDto(String id, String name, String description, EventType type, String typeLabel,
                           String status, String statusLabel, LocalDateTime startAt, LocalDateTime endAt,
                           long challengeCount, long visibleChallengeCount, LocalDateTime createdAt) {
    }

    public record EventRequest(
            @NotBlank(message = "赛事名称不能为空")
            @Size(max = 80, message = "赛事名称最多 80 个字符") String name,
            @Size(max = 1000, message = "赛事描述最多 1000 个字符") String description,
            @NotNull(message = "赛事类型不能为空") EventType type,
            LocalDateTime startAt,
            LocalDateTime endAt) {
    }
}
