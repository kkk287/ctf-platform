package com.example.ctf.dto;

import java.time.LocalDateTime;
import java.util.List;

public final class ScoreboardDtos {

    private ScoreboardDtos() {
    }

    public record ScoreEntryDto(int rank, String id, String name, String subtitle, String teamName,
                                long memberCount, long totalPoints, long solveCount,
                                LocalDateTime lastSolveAt) {
    }

    public record ScoreboardResponse(String type, EventDtos.EventDto event, List<ScoreEntryDto> entries,
                                     ScoreEntryDto me, LocalDateTime generatedAt) {
    }

    public record RecentSolveDto(String username, String displayName, String teamName, String challengeName,
                                 String category, String categoryLabel, int points, String bloodRank,
                                 String bloodRankLabel, LocalDateTime solvedAt) {
    }

    public record EventStatDto(String eventId, String eventName, String eventType, String eventTypeLabel,
                               long points, int rank, long solveCount) {
    }

    public record MySolveDto(String challengeId, String challengeName, String category, String categoryLabel,
                             String eventId, String eventName, String eventType, String eventTypeLabel,
                             int points, int basePoints, int bloodBonus, int hintCost, String bloodRank,
                             String bloodRankLabel, LocalDateTime solvedAt) {
    }

    public record ProfileResponse(AuthDtos.UserDto user, TeamDtos.TeamInfoDto team, long totalPoints,
                                  List<EventStatDto> events, List<MySolveDto> solves, int hintCostTotal) {
    }

    public record AnnouncementDto(String id, String title, String content, LocalDateTime createdAt) {
    }
}
