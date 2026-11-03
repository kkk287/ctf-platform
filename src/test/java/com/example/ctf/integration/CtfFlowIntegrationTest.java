package com.example.ctf.integration;

import com.example.ctf.repository.ChallengeRepository;
import com.example.ctf.repository.EventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc 集成测试：覆盖认证、赛事/练习隔离、判分、排行榜、战队、管理端与审计。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CtfFlowIntegrationTest {

    private static final MediaType JSON = MediaType.APPLICATION_JSON;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private ChallengeRepository challengeRepository;

    /** 本测试创建的额外 event/challenge，结束后清理防止污染后续测试 */
    private final List<String> createdEventIds = new ArrayList<>();
    private final List<String> createdChallengeIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        for (String id : createdChallengeIds) {
            challengeRepository.findById(id).ifPresent(challengeRepository::delete);
        }
        for (String id : createdEventIds) {
            eventRepository.findById(id).ifPresent(eventRepository::delete);
        }
        createdChallengeIds.clear();
        createdEventIds.clear();
    }

    // ---------- 认证 ----------

    @Test
    void authenticationRulesAndValidation() throws Exception {
        mvc.perform(get("/api/challenges"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(4010));

        mvc.perform(post("/api/auth/login").contentType(JSON)
                        .content(json(Map.of("username", "admin", "password", "wrong-password"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(4011));

        mvc.perform(post("/api/auth/register").contentType(JSON)
                        .content(json(Map.of("username", "it_auth", "password", "short"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4002));

        register("it_auth", "ValidPass123");
        mvc.perform(post("/api/auth/register").contentType(JSON)
                        .content(json(Map.of("username", "it_auth", "password", "ValidPass123"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(4090));

        // 已加入战队的用户登录时也必须能正确返回战队信息（懒加载回归用例）
        mvc.perform(post("/api/auth/login").contentType(JSON)
                        .content(json(Map.of("username", "alice", "password", "Alice@123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.teamName").value("0x00sec"));
    }

    // ---------- 选手完整链路 ----------

    @Test
    void playerSolvesChallengesAndAppearsOnScoreboards() throws Exception {
        String playerToken = register("it_player", "PlayerPass123");
        String mateToken = register("it_mate", "MatePass123");
        String adminToken = login("admin", "Admin@123");
        assertThat(playerToken).isNotBlank();

        String teamName = "IT Team";
        MvcResult teamResult = postJson("/api/teams", playerToken, Map.of("name", teamName))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        String inviteCode = data(teamResult).get("inviteCode").asText();
        postJson("/api/teams/join", mateToken, Map.of("inviteCode", inviteCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        JsonNode events = dataGet("/api/events", playerToken);
        JsonNode practice = findEvent(events, "常驻练习区");
        JsonNode competition = findEvent(events, "秋季夺旗赛 2026");
        assertThat(practice).isNotNull();
        assertThat(competition).isNotNull();
        String practiceId = practice.get("id").asText();
        String competitionId = competition.get("id").asText();

        JsonNode practiceChallenges = dataGet(
                "/api/challenges?eventId=" + practiceId, playerToken).get("challenges");
        JsonNode pwn = findChallenge(practiceChallenges, "栈溢出入门");
        assertThat(pwn).isNotNull();
        String pwnId = pwn.get("id").asText();

        // 错误 flag：业务错误码 + 审计留痕
        submitExpect(pwnId, playerToken, "flag{definitely_wrong}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4004));

        MvcResult correctResult = submitExpect(pwnId, playerToken, "flag{r3t2w1n_4nd_r0p}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.correct").value(true))
                .andReturn();
        int playerPoints = data(correctResult).get("points").asInt();
        assertThat(playerPoints).isPositive();

        // 重复提交已解出题目
        submitExpect(pwnId, playerToken, "flag{r3t2w1n_4nd_r0p}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(4009));

        // 个人榜可见自己；赛事榜不受练习区分数影响（隔离）
        JsonNode practiceBoard = dataGet(
                "/api/scoreboard?eventId=" + practiceId + "&type=USER&limit=50", playerToken);
        JsonNode me = practiceBoard.get("me");
        assertThat(me).isNotNull();
        assertThat(me.get("rank").asInt()).isPositive();
        assertThat(me.get("totalPoints").asLong()).isEqualTo(playerPoints);

        JsonNode competitionBoard = dataGet(
                "/api/scoreboard?eventId=" + competitionId + "&type=USER&limit=50", playerToken);
        assertThat(competitionBoard.get("me").isNull()).isTrue();

        // 同一战队另一成员解出同一题，战队榜同题只计最高
        MvcResult mateResult = submitExpect(pwnId, mateToken, "flag{r3t2w1n_4nd_r0p}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        int matePoints = data(mateResult).get("points").asInt();

        JsonNode teamBoard = dataGet(
                "/api/scoreboard?eventId=" + practiceId + "&type=TEAM&limit=50", playerToken);
        JsonNode teamEntry = findScoreEntry(teamBoard.get("entries"), teamName);
        assertThat(teamEntry).isNotNull();
        assertThat(teamEntry.get("memberCount").asLong()).isEqualTo(2);
        assertThat(teamEntry.get("totalPoints").asLong())
                .isEqualTo(Math.max(playerPoints, matePoints));
        assertThat(teamBoard.get("me").get("totalPoints").asLong())
                .isEqualTo(Math.max(playerPoints, matePoints));

        // 解题动态接口可用
        JsonNode recent = dataGet(
                "/api/scoreboard/recent?eventId=" + practiceId + "&limit=10", playerToken);
        assertThat(recent.size()).isPositive();

        // 管理端提交审计包含 IP 与 UA
        JsonNode submissions = dataGet(
                "/api/admin/submissions?challengeId=" + pwnId + "&size=20", adminToken);
        assertThat(submissions.get("total").asLong()).isPositive();
        JsonNode firstSubmission = submissions.get("items").get(0);
        assertThat(firstSubmission.get("ip").asText()).isNotBlank();
        assertThat(firstSubmission.get("userAgent").asText()).contains("JUnit-Agent");

        // 我的战绩
        JsonNode profile = dataGet("/api/me/profile", playerToken);
        assertThat(profile.get("solves").size()).isPositive();
        assertThat(profile.get("totalPoints").asLong()).isEqualTo(playerPoints);
    }

    // ---------- 管理端 ----------

    @Test
    void adminCreatesEventsChallengesAndManagesUsers() throws Exception {
        String adminToken = login("admin", "Admin@123");
        String userToken = register("it_bystander", "Bystander123");

        // 练习区 + 新题
        MvcResult practiceResult = postJson("/api/admin/events", adminToken, Map.of(
                        "name", "集成测试练习区",
                        "description", "用于验证管理端建赛与建题",
                        "type", "PRACTICE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        String practiceId = data(practiceResult).get("id").asText();
        createdEventIds.add(practiceId);

        MvcResult challengeResult = postJson("/api/admin/challenges", adminToken, Map.of(
                        "eventId", practiceId,
                        "name", "管理员创建的练习题目",
                        "description", "验证 flag 设置与附件上传",
                        "category", "MISC",
                        "difficulty", "EASY",
                        "initialPoints", 100,
                        "flag", "flag{integration_created}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        String challengeId = data(challengeResult).get("id").asText();
        createdChallengeIds.add(challengeId);

        JsonNode visible = dataGet("/api/challenges?eventId=" + practiceId, userToken)
                .get("challenges");
        assertThat(findChallenge(visible, "管理员创建的练习题目")).isNotNull();

        // 附件上传 + 选手下载
        MockMultipartFile file = new MockMultipartFile("file", "readme.txt", "text/plain",
                "attachment-body".getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/admin/challenges/" + challengeId + "/attachment")
                        .file(file).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.attachmentName").value("readme.txt"));

        MvcResult download = mvc.perform(get("/api/challenges/" + challengeId + "/attachment")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(header().exists("Content-Disposition"))
                .andReturn();
        assertThat(download.getResponse().getContentAsString()).isEqualTo("attachment-body");

        // 正确提交并检查审计
        submitExpect(challengeId, userToken, "flag{integration_created}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.correct").value(true));

        JsonNode audit = dataGet(
                "/api/admin/submissions?challengeId=" + challengeId + "&size=10", adminToken);
        assertThat(audit.get("total").asLong()).isEqualTo(1);
        assertThat(audit.get("items").get(0).get("userAgent").asText()).contains("JUnit-Agent");
        assertThat(audit.get("items").get(0).get("ip").asText()).isNotBlank();

        // 未开始赛事：选手不可见，管理员可见
        MvcResult notStartedResult = postJson("/api/admin/events", adminToken, Map.of(
                        "name", "集成测试未开始赛事",
                        "type", "COMPETITION",
                        "startAt", LocalDateTime.now().plusDays(2).withNano(0).toString(),
                        "endAt", LocalDateTime.now().plusDays(4).withNano(0).toString()))
                .andExpect(status().isOk())
                .andReturn();
        String notStartedId = data(notStartedResult).get("id").asText();
        createdEventIds.add(notStartedId);
        String notStartedChallengeId = createChallenge(adminToken, notStartedId, "未开始题目", "flag{not_started}");
        createdChallengeIds.add(notStartedChallengeId);

        getJson("/api/challenges?eventId=" + notStartedId, userToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(4031));
        JsonNode adminView = dataGet("/api/challenges?eventId=" + notStartedId, adminToken);
        assertThat(adminView.get("challenges").size()).isPositive();

        // 已结束赛事：可以看题，不能提交
        MvcResult endedResult = postJson("/api/admin/events", adminToken, Map.of(
                        "name", "集成测试已结束赛事",
                        "type", "COMPETITION",
                        "startAt", LocalDateTime.now().minusDays(3).withNano(0).toString(),
                        "endAt", LocalDateTime.now().minusDays(1).withNano(0).toString()))
                .andExpect(status().isOk())
                .andReturn();
        String endedId = data(endedResult).get("id").asText();
        createdEventIds.add(endedId);
        String endedChallengeId = createChallenge(adminToken, endedId, "已结束题目", "flag{ended_event}");
        createdChallengeIds.add(endedChallengeId);

        JsonNode endedChallenges = dataGet("/api/challenges?eventId=" + endedId, userToken)
                .get("challenges");
        assertThat(findChallenge(endedChallenges, "已结束题目")).isNotNull();
        submitExpect(endedChallengeId, userToken, "flag{ended_event}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(4032));

        // 普通选手无法访问管理接口
        getJson("/api/admin/challenges", userToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(4030));

        // 公告
        postJson("/api/admin/announcements", adminToken, Map.of(
                        "title", "集成测试公告",
                        "content", "这是一条由集成测试发布的公告"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        JsonNode announcements = dataGet("/api/announcements", userToken);
        assertThat(jsonArrayContains(announcements, "title", "集成测试公告")).isTrue();

        // 封禁 / 解封
        String victimToken = register("it_blocked", "BlockedPass123");
        assertThat(victimToken).isNotBlank();
        JsonNode usersPage = dataGet("/api/admin/users?page=0&size=50&keyword=it_blocked", adminToken);
        String victimId = usersPage.get("items").get(0).get("id").asText();

        patchJson("/api/admin/users/" + victimId + "/ban", adminToken, Map.of("banned", true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.banned").value(true));
        mvc.perform(post("/api/auth/login").contentType(JSON)
                        .content(json(Map.of("username", "it_blocked", "password", "BlockedPass123"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(4033));

        patchJson("/api/admin/users/" + victimId + "/ban", adminToken, Map.of("banned", false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.banned").value(false));
        login("it_blocked", "BlockedPass123");
    }

    // ---------- 防爆破 ----------

    @Test
    void wrongFlagAttemptsAreRateLimited() throws Exception {
        String token = register("it_brute", "BrutePass123");
        String competitionEventId = findEvent(dataGet("/api/events", token),
                "秋季夺旗赛 2026").get("id").asText();
        JsonNode challenges = dataGet(
                "/api/challenges?eventId=" + competitionEventId, token).get("challenges");
        String welcomeId = findChallenge(challenges, "欢迎来到 CTF").get("id").asText();

        for (int i = 0; i < 3; i++) {
            submitExpect(welcomeId, token, "flag{wrong_" + i + "}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(4004));
        }
        submitExpect(welcomeId, token, "flag{wrong_final}")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(4290));
    }

    // ---------- 工具方法 ----------

    private String register(String username, String password) throws Exception {
        MvcResult result = postJson("/api/auth/register", null,
                Map.of("username", username, "password", password))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return data(result).get("token").asText();
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = postJson("/api/auth/login", null,
                Map.of("username", username, "password", password))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return data(result).get("token").asText();
    }

    private ResultActions submitExpect(String challengeId, String token, String flag) throws Exception {
        return mvc.perform(post("/api/challenges/" + challengeId + "/submit")
                .contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .header("User-Agent", "JUnit-Agent/1.0")
                .content(json(Map.of("flag", flag))));
    }

    private String createChallenge(String adminToken, String eventId, String name, String flag)
            throws Exception {
        MvcResult result = postJson("/api/admin/challenges", adminToken, Map.of(
                        "eventId", eventId,
                        "name", name,
                        "description", "集成测试创建",
                        "category", "MISC",
                        "difficulty", "EASY",
                        "initialPoints", 100,
                        "flag", flag))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return data(result).get("id").asText();
    }

    private JsonNode dataGet(String path, String token) throws Exception {
        return data(getJson(path, token).andReturn());
    }

    private ResultActions getJson(String path, String token) throws Exception {
        MockHttpServletRequestBuilder builder = get(path);
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return mvc.perform(builder);
    }

    private ResultActions postJson(String path, String token, Object body) throws Exception {
        MockHttpServletRequestBuilder builder = post(path).contentType(JSON)
                .characterEncoding(StandardCharsets.UTF_8.name()).content(json(body));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return mvc.perform(builder);
    }

    private ResultActions patchJson(String path, String token, Object body) throws Exception {
        MockHttpServletRequestBuilder builder = patch(path).contentType(JSON)
                .characterEncoding(StandardCharsets.UTF_8.name()).content(json(body));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return mvc.perform(builder);
    }

    private String json(Object body) throws Exception {
        if (body instanceof Map<?, ?> map) {
            return mapper.writeValueAsString(map);
        }
        return mapper.writeValueAsString(body);
    }

    private JsonNode data(MvcResult result) throws Exception {
        // MockMvc 默认按 ISO-8859-1 读取响应，需显式用 UTF-8，否则中文赛事/题目名会乱码
        return mapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
    }

    private JsonNode findEvent(JsonNode events, String name) {
        for (JsonNode event : events) {
            if (name.equals(event.get("name").asText())) {
                return event;
            }
        }
        return null;
    }

    private JsonNode findChallenge(JsonNode challenges, String name) {
        for (JsonNode challenge : challenges) {
            if (name.equals(challenge.get("name").asText())) {
                return challenge;
            }
        }
        return null;
    }

    private JsonNode findScoreEntry(JsonNode entries, String name) {
        for (JsonNode entry : entries) {
            if (name.equals(entry.get("name").asText())) {
                return entry;
            }
        }
        return null;
    }

    private boolean jsonArrayContains(JsonNode array, String field, String value) {
        for (JsonNode node : array) {
            if (value.equals(node.get(field).asText())) {
                return true;
            }
        }
        return false;
    }
}
