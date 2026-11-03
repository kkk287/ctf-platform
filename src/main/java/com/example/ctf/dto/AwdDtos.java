package com.example.ctf.dto;

import java.time.LocalDateTime;
import java.util.List;

public final class AwdDtos {

    private AwdDtos() {
    }

    public record CreateRoundRequest(String eventId, String name, int totalRounds, int roundSeconds,
                                     int attackPoints, int defensePenalty, int downtimePenalty,
                                     String checkType, String checkTarget, String image,
                                     Integer containerPort) {
    }

    public record TransitionRequest(String action) {
    }

    public record TeamRequest(String name, List<String> members) {
    }

    public record RoundDto(String id, String eventId, String eventName, String name,
                           String status, String statusLabel, int currentRound, int totalRounds,
                           int roundSeconds, int remainingSeconds, int attackPoints,
                           int defensePenalty, int downtimePenalty, String checkType, String checkTarget,
                           String image, Integer containerPort, long teamCount,
                           LocalDateTime startedAt, LocalDateTime roundEndsAt,
                           LocalDateTime lastCheckAt, LocalDateTime finishedAt, LocalDateTime createdAt) {
    }

    public record TeamDto(String id, String name, List<String> members, String host, Integer port,
                          String containerId, String flag, Integer flagRound) {
    }

    public record AttackRequest(String flag) {
    }

    public record AttackResultDto(boolean accepted, String message, int points, int roundNo,
                                  String victimTeamName, int attackerTotal) {
    }

    public record CheckDto(String teamId, String teamName, boolean ok, Integer latencyMs,
                           String detail, LocalDateTime checkedAt) {
    }

    public record AttackLogDto(String attackerTeam, String victimTeam, int points,
                               int roundNo, LocalDateTime createdAt) {
    }

    public record TeamScoreDto(String teamId, String teamName, int attackGain, int defenseLoss,
                               int downtimeLoss, int totalScore, int rank) {
    }

    public record RoundScoreDto(int roundNo, String teamId, String teamName, int attackGain,
                                int defenseLoss, int downtimeLoss, int roundScore) {
    }

    public record ScoreboardDto(String roundId, String roundName, String status, String statusLabel,
                                int currentRound, int totalRounds, int remainingSeconds,
                                LocalDateTime roundEndsAt, String myTeamId, String myTeamName,
                                String myFlag, Integer myFlagRound, List<TeamScoreDto> teams,
                                List<RoundScoreDto> rounds, boolean simulateChecks) {
    }

    public record AdminDetailDto(RoundDto round, List<TeamDto> teams, List<CheckDto> checks,
                                 List<AttackLogDto> attacks, List<TeamScoreDto> scoreboard,
                                 List<RoundScoreDto> rounds) {
    }
}
