package com.example.ctf.integration;

import com.example.ctf.repository.EventRepository;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P2 黑灯模式集成测试：赛事开启黑灯开关后，普通选手榜单仅可见自身、解题动态隐藏；
 * 管理员/裁判不受限；开关关闭后恢复全量可见。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BlackoutIntegrationTest {

    private static final MediaType JSON = MediaType.APPLICATION_JSON;

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private EventRepository eventRepository;

    private String adminToken;
    private String eventId;
    private String eventName;
    private String startAt;
    private String endAt;
    private String challengeA;
    private String challengeB;

    @BeforeEach
    void seed() throws Exception {
        adminToken = login("admin", "Admin@123");
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        eventName = "黑灯测试赛-" + suffix;
        startAt = LocalDateTime.now().minusHours(1).withNano(0).toString();
        endAt = LocalDateTime.now().plusDays(1).withNano(0).toString();
        MvcResult event = postJson("/api/admin/events", adminToken, Map.of(
                        "name", eventName,
                        "type", "COMPETITION",
                        "startAt", startAt,
                        "endAt", endAt,
                        "blackoutMode", true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.blackoutMode").value(true))
                .andReturn();
        eventId = data(event).get("id").asText();
        challengeA = createChallenge("黑灯题目A", "flag{blackout_a}");
        challengeB = createChallenge("黑灯题目B", "flag{blackout_b}");
    }

    @AfterEach
    void cleanup() {
        // 赛事删除级联清理题目/解题/提交记录
        if (eventId != null) {
            eventRepository.findById(eventId).ifPresent(eventRepository::delete);
        }
    }

    @Test
    void blackout_playerSeesOnlySelfAndNoRecent() throws Exception {
        String p1 = register("bo_p1_");
        String p2 = register("bo_p2_");
        String p1Id = dataGet("/api/auth/me", p1).get("id").asText();
        submit(challengeA, p1);
        submit(challengeB, p2);

        // 个人榜：仅自身条目，且 me 正常返回
        JsonNode board = dataGet("/api/scoreboard?eventId=" + eventId + "&type=USER&limit=50", p1);
        assertThat(board.get("entries")).hasSize(1);
        assertThat(board.get("entries").get(0).get("id").asText()).isEqualTo(p1Id);
        assertThat(board.get("me")).isNotNull();
        assertThat(board.get("me").get("id").asText()).isEqualTo(p1Id);
        assertThat(board.get("me").get("totalPoints").asLong()).isPositive();

        // 解题动态：对选手隐藏
        JsonNode recent = dataGet("/api/scoreboard/recent?eventId=" + eventId + "&limit=10", p1);
        assertThat(recent).isEmpty();

        // 赛事详情携带黑灯开关，供前端切换展示
        JsonNode eventDto = dataGet("/api/events/" + eventId, p1);
        assertThat(eventDto.get("blackoutMode").asBoolean()).isTrue();
    }

    @Test
    void blackout_adminAndJudgeUnaffected() throws Exception {
        String p1 = register("bo_p3_");
        String p2 = register("bo_p4_");
        submit(challengeA, p1);
        submit(challengeB, p2);

        // 管理员：榜单与动态全量可见
        JsonNode adminBoard = dataGet("/api/scoreboard?eventId=" + eventId + "&type=USER&limit=50", adminToken);
        assertThat(adminBoard.get("entries").size()).isGreaterThanOrEqualTo(2);
        JsonNode adminRecent = dataGet("/api/scoreboard/recent?eventId=" + eventId + "&limit=10", adminToken);
        assertThat(adminRecent.size()).isGreaterThanOrEqualTo(2);

        // 裁判：调整为 JUDGE 后重新登录（角色写在 JWT claim 中）
        String judgeName = "bo_judge_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        registerExact(judgeName);
        String judgeId = dataGet("/api/admin/users?page=0&size=50&keyword=" + judgeName, adminToken)
                .get("items").get(0).get("id").asText();
        mvc.perform(patch("/api/admin/users/" + judgeId + "/role").contentType(JSON)
                        .content(mapper.writeValueAsString(Map.of("role", "JUDGE")))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.role").value("JUDGE"));
        String judgeToken = login(judgeName, "Pass123456");

        JsonNode judgeBoard = dataGet("/api/scoreboard?eventId=" + eventId + "&type=USER&limit=50", judgeToken);
        assertThat(judgeBoard.get("entries").size()).isGreaterThanOrEqualTo(2);
        JsonNode judgeRecent = dataGet("/api/scoreboard/recent?eventId=" + eventId + "&limit=10", judgeToken);
        assertThat(judgeRecent.size()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void blackout_teamBoardShowsOnlyOwnTeam() throws Exception {
        String p1 = register("bo_p5_");
        String p2 = register("bo_p6_");
        String team1 = "黑灯一队-" + UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        String team2 = "黑灯二队-" + UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        postJson("/api/teams", p1, Map.of("name", team1))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        postJson("/api/teams", p2, Map.of("name", team2))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        submit(challengeA, p1);
        submit(challengeB, p2);

        JsonNode teamBoard = dataGet("/api/scoreboard?eventId=" + eventId + "&type=TEAM&limit=50", p1);
        assertThat(teamBoard.get("entries")).hasSize(1);
        assertThat(teamBoard.get("entries").get(0).get("name").asText()).isEqualTo(team1);
        assertThat(teamBoard.get("me").get("name").asText()).isEqualTo(team1);
    }

    @Test
    void blackout_disabledRestoresFullVisibility() throws Exception {
        String p1 = register("bo_p7_");
        String p2 = register("bo_p8_");
        submit(challengeA, p1);
        submit(challengeB, p2);

        // 关闭黑灯（PUT 为全量更新，需携带完整赛事字段）
        mvc.perform(put("/api/admin/events/" + eventId).contentType(JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "name", eventName,
                                "type", "COMPETITION",
                                "startAt", startAt,
                                "endAt", endAt,
                                "blackoutMode", false)))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.blackoutMode").value(false));

        JsonNode board = dataGet("/api/scoreboard?eventId=" + eventId + "&type=USER&limit=50", p1);
        assertThat(board.get("entries").size()).isGreaterThanOrEqualTo(2);
        JsonNode recent = dataGet("/api/scoreboard/recent?eventId=" + eventId + "&limit=10", p1);
        assertThat(recent.size()).isGreaterThanOrEqualTo(2);
    }

    // ---------- 工具 ----------

    private String createChallenge(String name, String flag) throws Exception {
        MvcResult result = postJson("/api/admin/challenges", adminToken, Map.of(
                        "eventId", eventId,
                        "name", name,
                        "description", "黑灯测试题",
                        "category", "MISC",
                        "difficulty", "EASY",
                        "initialPoints", 100,
                        "flag", flag))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return data(result).get("id").asText();
    }

    private void submit(String challengeId, String token) throws Exception {
        mvc.perform(post("/api/challenges/" + challengeId + "/submit").contentType(JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(mapper.writeValueAsString(Map.of("flag",
                                challengeId.equals(challengeA) ? "flag{blackout_a}" : "flag{blackout_b}"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.correct").value(true));
    }

    private String register(String prefix) throws Exception {
        return registerExact(prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
    }

    /** 指定完整用户名注册（角色变更后需按用户名重新登录） */
    private String registerExact(String username) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/register").contentType(JSON)
                        .content(mapper.writeValueAsString(Map.of("username", username, "password", "Pass123456"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return data(result).get("token").asText();
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login").contentType(JSON)
                        .content(mapper.writeValueAsString(Map.of("username", username, "password", password))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return data(result).get("token").asText();
    }

    private ResultActions postJson(String path, String token, Object body) throws Exception {
        MockHttpServletRequestBuilder builder = post(path).contentType(JSON)
                .characterEncoding(StandardCharsets.UTF_8.name())
                .content(mapper.writeValueAsString(body));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return mvc.perform(builder);
    }

    private JsonNode dataGet(String path, String token) throws Exception {
        MvcResult result = mvc.perform(get(path).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        return data(result);
    }

    private JsonNode data(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
    }
}
