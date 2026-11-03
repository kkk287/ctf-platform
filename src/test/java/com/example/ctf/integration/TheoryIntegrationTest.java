package com.example.ctf.integration;

import com.example.ctf.domain.QuestionKind;
import com.example.ctf.repository.TheoryAnswerRepository;
import com.example.ctf.repository.TheoryConfigRepository;
import com.example.ctf.repository.TheoryQuestionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P2 理论赛集成测试：题库 CRUD、抽题物化（同人同卷）、三种题型判分（含多选部分得分）、
 * 榜单并入理论赛得分、按知识点聚合的能力报告。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TheoryIntegrationTest {

    private static final MediaType JSON = MediaType.APPLICATION_JSON;

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private TheoryQuestionRepository questionRepository;
    @Autowired private TheoryConfigRepository configRepository;
    @Autowired private TheoryAnswerRepository answerRepository;

    private String adminToken;
    private String userToken;
    private String userId;
    private String eventId;
    private List<String> createdQuestionIds;
    /** 题目 id -> 正确选项下标 */
    private Map<String, List<Integer>> answersByQuestion;
    /** 题目 id -> 题型 */
    private Map<String, QuestionKind> kindByQuestion;

    @BeforeEach
    void seed() throws Exception {
        adminToken = loginOrRegister("admin", "Admin@123");
        JsonNode events = dataGet("/api/events", adminToken);
        eventId = events.get(0).get("id").asText();  // 常驻练习区：始终可见、可提交
        // 每个用例使用全新用户，保证试卷互不影响
        userToken = loginOrRegister("thy_it_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10),
                "Pass123456");
        userId = dataGet("/api/auth/me", userToken).get("id").asText();
        createdQuestionIds = new ArrayList<>();
        answersByQuestion = new LinkedHashMap<>();
        kindByQuestion = new LinkedHashMap<>();

        // 题库：3 单选（网络基础）/ 2 多选（Web安全）/ 2 判断（网络基础）
        for (int i = 0; i < 3; i++) {
            createQuestion("SINGLE", "单选题-" + i, List.of("选项A", "选项B", "选项C", "选项D"),
                    List.of(1), "网络基础");
        }
        for (int i = 0; i < 2; i++) {
            createQuestion("MULTI", "多选题-" + i, List.of("选项A", "选项B", "选项C", "选项D"),
                    List.of(0, 2), "Web安全");
        }
        for (int i = 0; i < 2; i++) {
            createQuestion("JUDGE", "判断题-" + i, List.of("正确", "错误"), List.of(0), "网络基础");
        }

        // 抽题配置：单选 2x10、多选 1x20、判断 1x10，总分 50
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("eventId", eventId);
        config.put("singleCount", 2);
        config.put("singlePoints", 10);
        config.put("multiCount", 1);
        config.put("multiPoints", 20);
        config.put("judgeCount", 1);
        config.put("judgePoints", 10);
        mvc.perform(put("/api/admin/theory/config").contentType(JSON)
                        .content(mapper.writeValueAsString(config))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.totalQuestions").value(4))
                .andExpect(jsonPath("$.data.totalPoints").value(50));
    }

    @AfterEach
    void cleanup() {
        if (userId != null && eventId != null) {
            answerRepository.deleteAll(answerRepository.findPaper(eventId, userId));
        }
        createdQuestionIds.forEach(questionRepository::deleteById);
        if (eventId != null) {
            configRepository.deleteById(eventId);
        }
    }

    // ---------- 取卷与判分 ----------

    @Test
    void paper_materializedAndDeterministic() throws Exception {
        JsonNode paper = dataGet("/api/events/" + eventId + "/theory/paper", userToken);
        assertThat(paper.get("totalPoints").asInt()).isEqualTo(50);
        assertThat(paper.get("questions")).hasSize(4);
        Map<String, Integer> kindCount = new HashMap<>();
        for (JsonNode q : paper.get("questions")) {
            kindCount.merge(q.get("kind").asText(), 1, Integer::sum);
            // 试卷不泄露正确答案：answer 字段仅为选手已选（首访为 null）
            assertThat(q.get("answer").isNull()).isTrue();
            assertThat(q.get("options").size()).isGreaterThanOrEqualTo(2);
        }
        assertThat(kindCount).containsEntry("SINGLE", 2).containsEntry("MULTI", 1)
                .containsEntry("JUDGE", 1);

        // 再次取卷：同一份试卷（物化稳定）
        JsonNode again = dataGet("/api/events/" + eventId + "/theory/paper", userToken);
        assertThat(idsOf(again)).isEqualTo(idsOf(paper));
    }

    @Test
    void submit_gradesAllKindsAndMultiPartial() throws Exception {
        JsonNode paper = dataGet("/api/events/" + eventId + "/theory/paper", userToken);
        Map<String, JsonNode> byId = new LinkedHashMap<>();
        paper.get("questions").forEach(q -> byId.put(q.get("id").asText(), q));

        List<Map<String, Object>> answers = new ArrayList<>();
        int multiAnswered = 0;
        for (JsonNode q : paper.get("questions")) {
            String qid = q.get("id").asText();
            List<Integer> correct = answersByQuestion.get(qid);
            switch (kindByQuestion.get(qid)) {
                case SINGLE -> answers.add(item(qid, List.of(correct.get(0) == 0 ? 1 : 0)));  // 答错
                case MULTI -> {
                    answers.add(item(qid, List.of(correct.get(0))));  // 无错选但漏选 → 部分得分
                    multiAnswered = 1;
                }
                case JUDGE -> answers.add(item(qid, correct));  // 答对
            }
        }
        assertThat(multiAnswered).isEqualTo(1);

        JsonNode result = dataPost("/api/events/" + eventId + "/theory/submit",
                Map.of("answers", answers), userToken);
        // 单选错 0、多选半对 20*1/2=10、判断对 10；另一道单选未作答 0 → 总分 20
        assertThat(result.get("totalPoints").asInt()).isEqualTo(20);
        assertThat(result.get("questionCount").asInt()).isEqualTo(4);
        assertThat(result.get("correctCount").asInt()).isEqualTo(1);
        // 提交结果不返回正确答案字段
        for (JsonNode detail : result.get("details")) {
            assertThat(detail.has("answer")).as("提交结果不应泄露正确答案").isFalse();
        }
    }

    @Test
    void submit_multiWrongPickCleared() throws Exception {
        JsonNode paper = dataGet("/api/events/" + eventId + "/theory/paper", userToken);
        String multiId = null;
        for (JsonNode q : paper.get("questions")) {
            if ("MULTI".equals(q.get("kind").asText())) {
                multiId = q.get("id").asText();
            }
        }
        assertThat(multiId).isNotNull();
        List<Integer> correct = answersByQuestion.get(multiId);
        // 选一个正确项 + 一个错误项 → 错选清零
        int wrongPick = (correct.get(0) + 1) % 4;
        JsonNode result = dataPost("/api/events/" + eventId + "/theory/submit",
                Map.of("answers", List.of(item(multiId, List.of(correct.get(0), wrongPick)))), userToken);
        assertThat(result.get("totalPoints").asInt()).isEqualTo(0);
    }

    // ---------- 榜单并入与报告 ----------

    @Test
    void scoreboard_includesTheoryPoints() throws Exception {
        submitAllCorrect();
        JsonNode board = dataGet("/api/scoreboard?eventId=" + eventId + "&type=USER&limit=50", userToken);
        JsonNode me = board.get("me");
        assertThat(me).isNotNull();
        assertThat(me.get("id").asText()).isEqualTo(userId);
        assertThat(me.get("totalPoints").asLong()).isEqualTo(50);  // 纯理论赛得分上榜
    }

    @Test
    void report_aggregatesByKnowledgeTag() throws Exception {
        JsonNode paper = dataGet("/api/events/" + eventId + "/theory/paper", userToken);
        List<Map<String, Object>> answers = new ArrayList<>();
        for (JsonNode q : paper.get("questions")) {
            String qid = q.get("id").asText();
            if ("JUDGE".equals(q.get("kind").asText())) {
                answers.add(item(qid, answersByQuestion.get(qid)));  // 判断答对
            }
        }
        dataPost("/api/events/" + eventId + "/theory/submit", Map.of("answers", answers), userToken);

        JsonNode report = dataGet("/api/events/" + eventId + "/theory/report", userToken);
        assertThat(report.get("totalEarned").asLong()).isEqualTo(10);
        assertThat(report.get("totalPossible").asLong()).isEqualTo(50);
        Map<String, JsonNode> tags = new HashMap<>();
        report.get("tags").forEach(t -> tags.put(t.get("knowledgeTag").asText(), t));
        // 网络基础：判断对 10 + 两道单选未答 0 → 10/30；Web安全：0/20
        assertThat(tags.get("网络基础").get("pointsEarned").asLong()).isEqualTo(10);
        assertThat(tags.get("网络基础").get("pointsPossible").asLong()).isEqualTo(30);
        assertThat(tags.get("网络基础").get("correctCount").asLong()).isEqualTo(1);
        assertThat(tags.get("Web安全").get("pointsEarned").asLong()).isEqualTo(0);
        assertThat(tags.get("Web安全").get("pointsPossible").asLong()).isEqualTo(20);
    }

    @Test
    void adminOverview_listsUserScores() throws Exception {
        submitAllCorrect();
        JsonNode overview = dataGet("/api/admin/theory/report?eventId=" + eventId, adminToken);
        assertThat(overview.get("userCount").asInt()).isGreaterThanOrEqualTo(1);
        boolean found = false;
        for (JsonNode u : overview.get("users")) {
            if (userId.equals(u.get("userId").asText())) {
                found = true;
                assertThat(u.get("earned").asLong()).isEqualTo(50);
                assertThat(u.get("possible").asLong()).isEqualTo(50);
            }
        }
        assertThat(found).as("总览应包含测试用户").isTrue();
    }

    @Test
    void paper_requiresConfig() throws Exception {
        // 另一个赛事（秋季夺旗赛）未配置理论赛 → 4040
        JsonNode events = dataGet("/api/events", adminToken);
        String otherEventId = events.get(1).get("id").asText();
        mvc.perform(get("/api/events/" + otherEventId + "/theory/paper")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(4040));
    }

    // ---------- 工具 ----------

    private void createQuestion(String kind, String stem, List<String> options, List<Integer> answer,
                                String tag) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", eventId);
        body.put("kind", kind);
        body.put("stem", stem);
        body.put("options", options);
        body.put("answer", answer);
        body.put("knowledgeTag", tag);
        body.put("difficulty", "MEDIUM");
        MvcResult result = mvc.perform(post("/api/admin/theory/questions").contentType(JSON)
                        .content(mapper.writeValueAsString(body))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andReturn();
        JsonNode data = mapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("data");
        String id = data.get("id").asText();
        createdQuestionIds.add(id);
        answersByQuestion.put(id, answer);
        kindByQuestion.put(id, QuestionKind.valueOf(kind));
    }

    private void submitAllCorrect() throws Exception {
        JsonNode paper = dataGet("/api/events/" + eventId + "/theory/paper", userToken);
        List<Map<String, Object>> answers = new ArrayList<>();
        for (JsonNode q : paper.get("questions")) {
            answers.add(item(q.get("id").asText(), answersByQuestion.get(q.get("id").asText())));
        }
        JsonNode result = dataPost("/api/events/" + eventId + "/theory/submit",
                Map.of("answers", answers), userToken);
        assertThat(result.get("totalPoints").asInt()).isEqualTo(50);
        assertThat(result.get("correctCount").asInt()).isEqualTo(4);
    }

    private Map<String, Object> item(String questionId, List<Integer> answer) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("questionId", questionId);
        item.put("answer", answer);
        return item;
    }

    private List<String> idsOf(JsonNode paper) {
        List<String> ids = new ArrayList<>();
        paper.get("questions").forEach(q -> ids.add(q.get("id").asText()));
        return ids;
    }

    private String loginOrRegister(String username, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login").contentType(JSON)
                        .content(mapper.writeValueAsString(Map.of("username", username, "password", password))))
                .andReturn();
        JsonNode json = mapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        if (json.path("code").asInt() == 0 && json.path("data").path("token").asText(null) != null) {
            return json.path("data").path("token").asText();
        }
        result = mvc.perform(post("/api/auth/register").contentType(JSON)
                        .content(mapper.writeValueAsString(Map.of("username", username, "password", password))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return mapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("token").asText();
    }

    private JsonNode dataGet(String uri, String token) throws Exception {
        MvcResult result = mvc.perform(get(uri).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("data");
    }

    private JsonNode dataPost(String uri, Object body, String token) throws Exception {
        MvcResult result = mvc.perform(post(uri).contentType(JSON)
                        .content(mapper.writeValueAsString(body))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("data");
    }
}
