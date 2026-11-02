package com.example.ctf.dto;

import com.example.ctf.domain.Category;
import com.example.ctf.domain.Difficulty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public final class ChallengeDtos {

    private ChallengeDtos() {
    }

    public record ChallengeListResponse(EventDtos.EventDto event, List<ChallengeSummaryDto> challenges) {
    }

    public record ChallengeSummaryDto(String id, String eventId, String name, String description,
                                      Category category, String categoryLabel, Difficulty difficulty,
                                      String difficultyLabel, int initialPoints, int currentPoints,
                                      long solveCount, boolean visible, boolean solved, int hintCount,
                                      boolean hasAttachment) {
    }

    public record HintDto(String id, String content, int cost, boolean unlocked) {
    }

    public record SolveBriefDto(String username, String displayName, String teamName, int points,
                                String bloodRank, String bloodRankLabel, LocalDateTime solvedAt) {
    }

    public record ChallengeDetailDto(String id, EventDtos.EventDto event, String name, String description,
                                     Category category, String categoryLabel, Difficulty difficulty,
                                     String difficultyLabel, int initialPoints, int currentPoints,
                                     int minPoints, int decayPoints, long solveCount, boolean solved,
                                     LocalDateTime solvedAt, boolean visible, String attachmentName,
                                     Long attachmentSize, List<HintDto> hints, List<SolveBriefDto> recentSolves,
                                     LocalDateTime createdAt) {
    }

    public record SubmitRequest(
            @NotBlank(message = "flag 不能为空")
            @Size(max = 200, message = "flag 最多 200 个字符") String flag) {
    }

    public record SubmitResponse(boolean correct, String message, int basePoints, int bloodBonus,
                                 int hintCost, int points, String bloodRank, String bloodRankLabel,
                                 long totalPoints, int rank, long solveCount, int challengeCurrentPoints) {
    }

    public record UnlockHintResponse(String hintId, String content, int cost) {
    }

    public record AdminChallengeRequest(
            @NotBlank(message = "所属赛事不能为空") String eventId,
            @NotBlank(message = "题目名称不能为空")
            @Size(max = 100, message = "题目名称最多 100 个字符") String name,
            @Size(max = 3000, message = "题目描述最多 3000 个字符") String description,
            @NotNull(message = "分类不能为空") Category category,
            @NotNull(message = "难度不能为空") Difficulty difficulty,
            @Min(value = 10, message = "初始分值至少为 10")
            @Max(value = 10000, message = "初始分值最多为 10000") int initialPoints,
            @Min(value = 0, message = "最低分不能为负数") Integer minPoints,
            @Min(value = 0, message = "衰减值不能为负数") Integer decayPoints,
            Boolean visible,
            String flag) {
    }

    public record HintRequest(
            @NotBlank(message = "提示内容不能为空")
            @Size(max = 500, message = "提示内容最多 500 个字符") String content,
            @Min(value = 0, message = "提示代价不能为负数") int cost) {
    }

    public record AdminChallengeDto(String id, String eventId, String eventName, String name, String description,
                                    Category category, String categoryLabel, Difficulty difficulty,
                                    String difficultyLabel, int initialPoints, int minPoints, int decayPoints,
                                    int currentPoints, boolean visible, boolean flagConfigured,
                                    String attachmentName, Long attachmentSize, long solveCount,
                                    LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}
