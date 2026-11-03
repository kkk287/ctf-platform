package com.example.ctf.dto;

import com.example.ctf.domain.EventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class EventDtos {

    private EventDtos() {
    }

    public record EventDto(String id, String namespace, String name, String description, EventType type,
                           String typeLabel, String status, String statusLabel, LocalDateTime startAt,
                           LocalDateTime endAt, boolean blackoutMode, long challengeCount,
                           long visibleChallengeCount, LocalDateTime createdAt) {
    }

    public record EventRequest(
            @NotBlank(message = "赛事名称不能为空")
            @Size(max = 80, message = "赛事名称最多 80 个字符") String name,
            @Pattern(regexp = "^[a-z0-9][a-z0-9-]{1,59}$", message = "命名空间须为 2-60 位小写字母/数字/连字符，且以字母或数字开头")
            String namespace,
            @Size(max = 1000, message = "赛事描述最多 1000 个字符") String description,
            @NotNull(message = "赛事类型不能为空") EventType type,
            LocalDateTime startAt,
            LocalDateTime endAt,
            Boolean blackoutMode) {
    }
}
