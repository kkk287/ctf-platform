package com.example.ctf.integration;

import com.example.ctf.domain.*;
import com.example.ctf.repository.ChallengeRepository;
import com.example.ctf.repository.EventRepository;
import com.example.ctf.repository.SolveRepository;
import com.example.ctf.repository.UserRepository;
import com.example.ctf.repository.WriteupRepository;
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
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P2 Writeup 集成测试：仅解题者可提交、非赛后自己可见、赛后公开、管理端审核。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WriteupIntegrationTest {

    private static final MediaType JSON = MediaType.APPLICATION_JSON;

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private EventRepository eventRepository;
    @Autowired private ChallengeRepository challengeRepository;
    @Autowired private SolveRepository solveRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private WriteupRepository writeupRepository;

    private String adminToken;
    private String solverToken;
    private String solverId;
    private String viewerToken;
    private String eventId;
    private String challengeId;

    @BeforeEach
    void seed() throws Exception {
        adminToken = loginOrRegister("admin", "Admin@123");
        // 用练习区（始终 ONGOING）模拟"比赛进行中"
        JsonNode events = dataGet("/api/events", adminToken);
        eventId = events.get(0).get("id").asText();
        JsonNode challenges = dataGet("/api/challenges?eventId=" + eventId, adminToken);
        challengeId = challenges.get("challenges").get(0).get("id").asText();

        solverToken = loginOrRegister("wu_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10),
                "Pass123456");
        viewerToken = loginOrRegister("wv_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10),
                "Pass123456");
        solverId = dataGet("/api/auth/me", solverToken).get("id").asText();

        // 直接插一条 Solve 记录，绕过 REST flag 提交（challenge 明文不出接口）
        User user = userRepository.findById(solverId).orElseThrow();
        Challenge challenge = challengeRepository.findById(challengeId).orElseThrow();
        Solve solve = new Solve();
        solve.setUser(user);
        solve.setChallenge(challenge);
        solve.setBasePoints(challenge.getInitialPoints());
        solve.setFinalPoints(challenge.getInitialPoints());
        solve.setBloodRank(BloodRank.NONE);
        solve.setSolvedAt(LocalDateTime.now().minusMinutes(5));
        solveRepository.save(solve);
    }

    @AfterEach
    void cleanup() {
        writeupRepository.deleteAll();
        solveRepository.deleteAll();
        // 不删 event/challenge——DemoDataInitializer 只启动时跑一次
    }

    // ---------- 提交权限 ----------

    @Test
    void create_requiresSolve() throws Exception {
        Map<String, Object> body = Map.of("challengeId", challengeId, "title", "未解题 Writeup", "markdown", "# 内容");
        mvc.perform(post("/api/events/" + eventId + "/writeups").contentType(JSON)
                        .content(mapper.writeValueAsString(body))
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(4030));
    }

    @Test
    void create_solverCanSubmit() throws Exception {
        Map<String, Object> body = Map.of("challengeId", challengeId, "title", "我的 Writeup",
                "markdown", "## 步骤\n1. 信息收集\n2. 注入 payload");
        JsonNode created = dataPost("/api/events/" + eventId + "/writeups", body, solverToken);
        assertThat(created.get("status").asText()).isEqualTo("SUBMITTED");
        assertThat(created.get("markdown").asText()).contains("信息收集");
    }

    @Test
    void create_duplicateRejected() throws Exception {
        Map<String, Object> body = Map.of("challengeId", challengeId, "title", "重复 Writeup", "markdown", "# A");
        dataPost("/api/events/" + eventId + "/writeups", body, solverToken);
        mvc.perform(post("/api/events/" + eventId + "/writeups").contentType(JSON)
                        .content(mapper.writeValueAsString(body))
                        .header("Authorization", "Bearer " + solverToken))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(4090));
    }

    // ---------- 可见性：非赛后 ----------

    @Test
    void list_duringEvent_onlyOwnVisible() throws Exception {
        String writeupId = createWriteup(solverToken).get("id").asText();

        // solver 能看到自己的
        JsonNode solverList = dataGet("/api/events/" + eventId + "/writeups", solverToken);
        assertThat(solverList).hasSize(1);
        assertThat(solverList.get(0).get("id").asText()).isEqualTo(writeupId);

        // viewer 看不到别人的（空列表）
        JsonNode viewerList = dataGet("/api/events/" + eventId + "/writeups", viewerToken);
        assertThat(viewerList).isEmpty();
    }

    @Test
    void detail_duringEvent_forbiddenToOthers() throws Exception {
        String writeupId = createWriteup(solverToken).get("id").asText();

        mvc.perform(get("/api/writeups/" + writeupId).header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(4030));

        mvc.perform(get("/api/writeups/" + writeupId).header("Authorization", "Bearer " + solverToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));

        mvc.perform(get("/api/writeups/" + writeupId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
    }

    // ---------- 赛后公开 ----------

    @Test
    void afterEvent_allCanSee() throws Exception {
        // 创建已结束赛事（COMPETITION，endAt 在过去）
        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> eventBody = Map.of("name", "已结束测试赛",
                "type", "COMPETITION",
                "startAt", now.minusDays(3).toString(),
                "endAt", now.minusDays(1).toString());
        JsonNode event = dataPost("/api/admin/events", eventBody, adminToken);
        String endedEventId = event.get("id").asText();

        // 在该赛事下创建题目
        Map<String, Object> chal = Map.of("eventId", endedEventId, "name", "赛后题",
                "description", "test", "category", "MISC", "difficulty", "EASY",
                "initialPoints", 100, "visible", true, "flag", "flag{ended_test}");
        JsonNode chalNode = dataPost("/api/admin/challenges", chal, adminToken);
        String chalId = chalNode.get("id").asText();

        // admin 解出并提交 Writeup
        User adminUser = userRepository.findByUsername("admin").orElseThrow();
        Challenge c = challengeRepository.findById(chalId).orElseThrow();
        Solve adminSolve = new Solve();
        adminSolve.setUser(adminUser);
        adminSolve.setChallenge(c);
        adminSolve.setBasePoints(100);
        adminSolve.setFinalPoints(100);
        adminSolve.setBloodRank(BloodRank.NONE);
        adminSolve.setSolvedAt(LocalDateTime.now().minusMinutes(1));
        solveRepository.save(adminSolve);

        Map<String, Object> wBody = Map.of("challengeId", chalId, "title", "赛后 Writeup",
                "markdown", "# 赛后可见");
        JsonNode w = dataPost("/api/events/" + endedEventId + "/writeups", wBody, adminToken);
        String writeupId = w.get("id").asText();

        // viewer 可以看到（已结束）
        JsonNode viewerList = dataGet("/api/events/" + endedEventId + "/writeups", viewerToken);
        assertThat(viewerList).hasSize(1);
        assertThat(viewerList.get(0).get("id").asText()).isEqualTo(writeupId);

        mvc.perform(get("/api/writeups/" + writeupId).header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.markdown").value("# 赛后可见"));

        // 清理该测试创建的 event/challenge，防止污染后续测试的 events.get(0)
        challengeRepository.delete(c);
        eventRepository.delete(eventRepository.findById(endedEventId).orElseThrow());
    }

    // ---------- 管理端审核 ----------

    @Test
    void moderate_setsAcceptedStatus() throws Exception {
        String writeupId = createWriteup(solverToken).get("id").asText();

        JsonNode moderated = dataPut("/api/admin/writeups/" + writeupId + "/moderate",
                Map.of("status", "ACCEPTED", "note", "质量很好"), adminToken);
        assertThat(moderated.get("status").asText()).isEqualTo("ACCEPTED");
        assertThat(moderated.get("accepted").asBoolean()).isTrue();

        JsonNode rejected = dataPut("/api/admin/writeups/" + writeupId + "/moderate",
                Map.of("status", "REJECTED"), adminToken);
        assertThat(rejected.get("status").asText()).isEqualTo("REJECTED");
        assertThat(rejected.get("accepted").asBoolean()).isFalse();

        long publicCount = writeupRepository.findPublicByEvent(eventId,
                List.of(WriteupStatus.SUBMITTED, WriteupStatus.ACCEPTED)).size();
        assertThat(publicCount).isEqualTo(0);
    }

    // ---------- 工具 ----------

    private JsonNode createWriteup(String token) throws Exception {
        Map<String, Object> body = Map.of("challengeId", challengeId,
                "title", "Writeup-" + UUID.randomUUID().toString().substring(0, 6),
                "markdown", "# 解题思路\n\n## 第一步\n...");
        return dataPost("/api/events/" + eventId + "/writeups", body, token);
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

    private JsonNode dataPut(String uri, Object body, String token) throws Exception {
        MvcResult result = mvc.perform(put(uri).contentType(JSON)
                        .content(mapper.writeValueAsString(body))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("data");
    }
}
