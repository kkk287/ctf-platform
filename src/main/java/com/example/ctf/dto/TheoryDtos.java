package com.example.ctf.dto;

import java.time.LocalDateTime;
import java.util.List;

public final class TheoryDtos {

    private TheoryDtos() {
    }

    // ---------- 管理端：题库与配置 ----------

    public record QuestionRequest(String eventId, String kind, String stem, List<String> options,
                                  List<Integer> answer, String knowledgeTag, String difficulty) {
    }

    public record AdminQuestionDto(String id, String eventId, String kind, String kindLabel, String stem,
                                   List<String> options, List<Integer> answer, String knowledgeTag,
                                   String difficulty, String difficultyLabel, LocalDateTime createdAt) {
    }

    public record ConfigRequest(String eventId, int singleCount, int singlePoints,
                                int multiCount, int multiPoints, int judgeCount, int judgePoints) {
    }

    public record ConfigDto(String eventId, int singleCount, int singlePoints,
                            int multiCount, int multiPoints, int judgeCount, int judgePoints,
                            int totalQuestions, int totalPoints, long poolSize) {
    }

    // ---------- 选手端：试卷 / 提交 / 报告 ----------

    /** 试卷题目：不携带正确答案；answer 为选手已选（未答为 null）。 */
    public record PaperQuestionDto(String id, String kind, String kindLabel, String stem,
                                   List<String> options, String knowledgeTag, String difficulty,
                                   String difficultyLabel, int points, List<Integer> answer) {
    }

    public record PaperResponse(String eventId, int totalPoints, List<PaperQuestionDto> questions) {
    }

    public record AnswerItem(String questionId, List<Integer> answer) {
    }

    public record SubmitRequest(List<AnswerItem> answers) {
    }

    public record AnswerResultDto(String questionId, boolean correct, int points) {
    }

    public record SubmitResultDto(int totalPoints, int correctCount, int questionCount,
                                  List<AnswerResultDto> details) {
    }

    public record TagStatDto(String knowledgeTag, long questionCount, long correctCount,
                             long pointsEarned, long pointsPossible, double accuracy) {
    }

    /** 个人能力报告：按知识点聚合得分率。 */
    public record ReportDto(String eventId, long totalEarned, long totalPossible, double accuracy,
                            List<TagStatDto> tags) {
    }

    public record UserScoreDto(String userId, String username, String displayName,
                               long earned, long possible) {
    }

    /** 管理端总览：全员知识点聚合 + 每用户得分。 */
    public record OverviewDto(String eventId, long userCount, List<TagStatDto> tags,
                              List<UserScoreDto> users) {
    }
}
