package com.example.ctf.integration;

import com.example.ctf.repository.EventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P1 安全升级集成测试：审计日志、角色体系（OBSERVER 只读）、赛事命名空间唯一性、登出令牌吊销。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityUpgradeIntegrationTest {

    private static final MediaType JSON = MediaType.APPLICATION_JSON;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private EventRepository eventRepository;

    /** 本测试类创建的额外赛事/用户，测试结束后清理防止污染后续测试 */
    private final List<String> createdEventIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        for (String id : createdEventIds) {
            eventRepository.findById(id).ifPresent(eventRepository::delete);
        }
        createdEventIds.clear();
    }

    // ---------- 审计与命名空间 ----------

    @Test
    void adminActionsAreAuditedAndNamespaceIsUnique() throws Exception {
        String adminToken = login("admin", "Admin@123");

        postJson("/api/admin/events", adminToken, Map.of(
                        "name", "审计验证赛事",
                        "namespace", "audit-it",
                        "description", "验证审计落库与命名空间",
                        "type", "PRACTICE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.namespace").value("audit-it"))
                .andExpect(jsonPath("$.data.id").exists())
                .andDo(r -> createdEventIds.add(data(r).get("id").asText()));

        // 审计落库：EVENT_CREATE 可被查询
        JsonNode logs = dataGet("/api/admin/audit-logs?action=EVENT_CREATE&size=20", adminToken);
        assertThat(logs.get("total").asLong()).isPositive();
        boolean found = false;
        for (JsonNode item : logs.get("items")) {
            if ("审计验证赛事".equals(item.get("target").asText())) {
                found = true;
                assertThat(item.get("actorName").asText()).isEqualTo("admin");
                assertThat(item.get("actorRole").asText()).isEqualTo("ADMIN");
                assertThat(item.get("detail").asText()).contains("audit-it");
            }
        }
        assertThat(found).as("审计日志中应包含 审计验证赛事 的 EVENT_CREATE 记录").isTrue();

        // 普通用户不可访问审计日志
        String userToken = register("it_audit_denied", "AuditPass123");
        getJson("/api/admin/audit-logs", userToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(4030));

        // 命名空间唯一：重复命名空间 → 409
        postJson("/api/admin/events", adminToken, Map.of(
                        "name", "审计验证赛事二",
                        "namespace", "audit-it",
                        "type", "PRACTICE"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(4090));

        // 命名空间格式校验：非法字符 → 400
        postJson("/api/admin/events", adminToken, Map.of(
                        "name", "非法命名空间赛事",
                        "namespace", "Bad_NS",
                        "type", "PRACTICE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4002));
    }

    // ---------- 角色体系 ----------

    @Test
    void observerIsReadOnlyAndRoleChangeIsAudited() throws Exception {
        String adminToken = login("admin", "Admin@123");
        String observerToken = register("it_observer", "Observer123");

        JsonNode users = dataGet("/api/admin/users?page=0&size=20&keyword=it_observer", adminToken);
        String observerId = users.get("items").get(0).get("id").asText();

        patchJson("/api/admin/users/" + observerId + "/role", adminToken, Map.of("role", "OBSERVER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("OBSERVER"))
                .andExpect(jsonPath("$.data.roleLabel").value("观摩"));

        // 角色变更留痕
        JsonNode roleLogs = dataGet("/api/admin/audit-logs?action=USER_ROLE_CHANGE&size=20", adminToken);
        boolean roleLogged = false;
        for (JsonNode item : roleLogs.get("items")) {
            if ("it_observer".equals(item.get("target").asText())) {
                roleLogged = true;
            }
        }
        assertThat(roleLogged).as("审计日志中应包含 it_observer 的 USER_ROLE_CHANGE 记录").isTrue();

        // 观摩账号不可组队
        postJson("/api/teams", observerToken, Map.of("name", "Observer Team"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(4030));

        // 观摩账号不可提交 flag（使用进行中的演示赛事题目）
        String competitionId = null;
        for (JsonNode e : dataGet("/api/events", observerToken)) {
            if ("秋季夺旗赛 2026".equals(e.get("name").asText())) {
                competitionId = e.get("id").asText();
            }
        }
        assertThat(competitionId).isNotNull();
        String welcomeId = null;
        for (JsonNode c : dataGet("/api/challenges?eventId=" + competitionId, observerToken).get("challenges")) {
            if ("欢迎来到 CTF".equals(c.get("name").asText())) {
                welcomeId = c.get("id").asText();
            }
        }
        assertThat(welcomeId).isNotNull();

        mvc.perform(post("/api/challenges/" + welcomeId + "/submit")
                        .contentType(JSON)
                        .header("Authorization", "Bearer " + observerToken)
                        .content(mapper.writeValueAsString(Map.of("flag", "flag{observer_try}"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(4030));

        // 角色保护：不能修改自己 / 不能降级管理员
        patchJson("/api/admin/users/u-admin/role", adminToken, Map.of("role", "USER"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(4001));
    }

    // ---------- 登出吊销 ----------

    @Test
    void logoutRevokesTokenImmediately() throws Exception {
        String token = register("it_logout", "LogoutPass123");
        getJson("/api/auth/me", token).andExpect(status().isOk());

        postJson("/api/auth/logout", token, Map.of())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 吊销后旧令牌立即失效
        getJson("/api/auth/me", token)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(4010));

        // 重新登录可正常获取新令牌
        login("it_logout", "LogoutPass123");
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
                .characterEncoding(StandardCharsets.UTF_8.name())
                .content(mapper.writeValueAsString(body));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return mvc.perform(builder);
    }

    private ResultActions patchJson(String path, String token, Object body) throws Exception {
        MockHttpServletRequestBuilder builder = patch(path).contentType(JSON)
                .characterEncoding(StandardCharsets.UTF_8.name())
                .content(mapper.writeValueAsString(body));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return mvc.perform(builder);
    }

    private JsonNode data(MvcResult result) throws Exception {
        // MockMvc 默认按 ISO-8859-1 读取响应，需显式用 UTF-8，否则中文会乱码
        return mapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
    }
}
