package com.example.ctf.integration;

import com.example.ctf.domain.*;
import com.example.ctf.dto.ChallengeDtos;
import com.example.ctf.repository.*;
import com.example.ctf.service.DynamicFlagService;
import com.example.ctf.service.InstanceService;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P2 动态靶机集成测试：使用 NoopOrchestrator（默认配置 enabled=false），
 * 覆盖 InstanceService.start/reset/stop/TTL sweep 与 DynamicFlagService 判分链路。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RangeOrchestratorIntegrationTest {

    private static final MediaType JSON = MediaType.APPLICATION_JSON;
    private static final List<InstanceStatus> ACTIVE = List.of(InstanceStatus.RUNNING, InstanceStatus.PENDING);

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private InstanceRepository instanceRepository;
    @Autowired private DynamicFlagRepository dynamicFlagRepository;
    @Autowired private ChallengeRepository challengeRepository;
    @Autowired private InstanceService instanceService;
    @Autowired private DynamicFlagService dynamicFlagService;

    private String ownerId;
    private String challengeId;
    private Challenge seededChallenge;

    @BeforeEach
    void seedChallenge() throws Exception {
        String adminToken = loginOrRegister("admin", "Admin@123");
        JsonNode events = dataGet("/api/events", adminToken);  // 返回 List<EventDto>
        String eventId = events.get(0).get("id").asText();
        JsonNode challenges = dataGet("/api/challenges?eventId=" + eventId, adminToken);
        challengeId = challenges.get("challenges").get(0).get("id").asText();

        // 管理员把它改成动态靶机题目（混合类型 Map → 全部 Object）
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", eventId);
        body.put("name", "靶机集成测试题");
        body.put("description", "P2 集成测试专用");
        body.put("category", "PWN");
        body.put("difficulty", "HARD");
        body.put("initialPoints", 200);
        body.put("visible", true);
        body.put("flag", "flag{static_baseline}");
        body.put("instanceImage", "nginx:alpine");
        body.put("instancePort", 80);
        body.put("instanceTtlMinutes", 10);
        body.put("instanceDynamicFlag", true);
        mvc.perform(put("/api/admin/challenges/" + challengeId).contentType(JSON)
                        .content(mapper.writeValueAsString(body))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        seededChallenge = challengeRepository.findById(challengeId).orElseThrow();

        String userToken = loginOrRegister("range_it_user", "Pass123456");
        ownerId = dataGet("/api/auth/me", userToken).get("id").asText();
    }

    @AfterEach
    void cleanup() {
        if (ownerId != null) {
            instanceRepository.findAll().stream()
                    .filter(i -> ownerId.equals(i.getOwnerId()))
                    .forEach(i -> instanceRepository.delete(i));
        }
        if (seededChallenge != null) {
            seededChallenge.setInstanceDynamicFlag(false);
            seededChallenge.setInstanceImage(null);
            seededChallenge.setInstancePort(null);
            seededChallenge.setInstanceTtlMinutes(null);
            challengeRepository.save(seededChallenge);
        }
    }

    // ---------- Instance 生命周期 ----------

    @Test
    void noopOrchestrator_startStopReset() {
        ChallengeDtos.InstanceStatusDto started = instanceService.start(challengeId, ownerId);
        assertThat(started.status()).isEqualTo("RUNNING");
        assertThat(started.host()).isNotNull();
        assertThat(started.port()).isNotNull();
        assertThat(started.dynamicFlag()).isTrue();
        assertThat(started.expiresAt()).isAfter(LocalDateTime.now());

        Instance instance = instanceRepository.findById(started.instanceId()).orElseThrow();
        assertThat(instance.getOwnerId()).isEqualTo(ownerId);
        assertThat(instance.getContainerId()).startsWith("noop-");

        // 复用：同 owner/challenge 再调 start 应该延长 TTL，不新建实例
        LocalDateTime firstExpires = started.expiresAt();
        ChallengeDtos.InstanceStatusDto reused = instanceService.start(challengeId, ownerId);
        assertThat(reused.instanceId()).isEqualTo(started.instanceId());
        assertThat(reused.expiresAt()).isAfter(firstExpires);

        // reset：销毁旧实例，拉起新实例
        ChallengeDtos.InstanceStatusDto reset = instanceService.reset(challengeId, ownerId);
        assertThat(reset.instanceId()).isNotEqualTo(started.instanceId());
        assertThat(reset.status()).isEqualTo("RUNNING");

        // stop
        instanceService.stop(challengeId, ownerId);
        Instance stopped = instanceRepository.findById(reset.instanceId()).orElseThrow();
        assertThat(stopped.getStatus()).isEqualTo(InstanceStatus.STOPPED);
        assertThat(stopped.getStoppedAt()).isNotNull();
    }

    @Test
    void dynamicFlag_generatedAndStoredOnStart() {
        instanceService.start(challengeId, ownerId);

        Instance instance = instanceRepository
                .findByOwnerIdAndChallengeIdAndStatusIn(ownerId, challengeId, ACTIVE).get(0);
        DynamicFlag df = dynamicFlagRepository.findByInstanceId(instance.getId()).orElseThrow();
        assertThat(df.getOwnerId()).isEqualTo(ownerId);
        assertThat(df.getChallenge().getId()).isEqualTo(challengeId);
        assertThat(df.getFlagHash()).isNotNull();
        assertThat(df.getFlagSalt()).isNotNull();

        String plaintext = dynamicFlagService.generatePlaintext(challengeId, ownerId);
        assertThat(dynamicFlagService.matches(plaintext, df)).isTrue();
        assertThat(dynamicFlagService.matches("wrong-flag", df)).isFalse();
    }

    @Test
    void dynamicFlagService_md5Format() {
        String plaintext = dynamicFlagService.generatePlaintext("c1", "u1");
        assertThat(plaintext).startsWith("flag{");
        assertThat(plaintext).endsWith("}");
        assertThat(plaintext.length()).isEqualTo("flag{}".length() + 32);  // md5 hex

        String other = dynamicFlagService.generatePlaintext("c2", "u1");
        assertThat(other).isNotEqualTo(plaintext);
    }

    // ---------- TTL sweep ----------

    @Test
    void ttlSweep_expiresRunningInstances() {
        Challenge challenge = challengeRepository.findById(challengeId).orElseThrow();
        challenge.setInstanceTtlMinutes(1);
        challengeRepository.save(challenge);

        ChallengeDtos.InstanceStatusDto started = instanceService.start(challengeId, ownerId);
        Instance instance = instanceRepository.findById(started.instanceId()).orElseThrow();
        instance.setExpiresAt(LocalDateTime.now().minusMinutes(5));
        instanceRepository.save(instance);

        assertThat(instance.getStatus()).isEqualTo(InstanceStatus.RUNNING);

        instanceService.sweepExpired();

        Instance expired = instanceRepository.findById(instance.getId()).orElseThrow();
        assertThat(expired.getStatus()).isEqualTo(InstanceStatus.EXPIRED);
        assertThat(expired.getStoppedAt()).isNotNull();
    }

    // ---------- 工具 ----------

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
}
