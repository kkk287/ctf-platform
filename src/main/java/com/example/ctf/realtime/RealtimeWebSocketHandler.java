package com.example.ctf.realtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 处理客户端订阅/退订/心跳消息。鉴权在握手阶段完成（见 JwtHandshakeInterceptor）。 */
@Component
public class RealtimeWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(RealtimeWebSocketHandler.class);

    private final RealtimeHub hub;
    private final ObjectMapper mapper;

    public RealtimeWebSocketHandler(RealtimeHub hub, ObjectMapper mapper) {
        this.hub = hub;
        this.mapper = mapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        hub.register(session);
        Map<String, Object> ready = Map.of(
                "type", "ready",
                "username", String.valueOf(session.getAttributes().getOrDefault("username", "")),
                "role", String.valueOf(session.getAttributes().getOrDefault("role", "")));
        hub.send(session, ready);
        log.debug("WS 连接建立 session={} user={}", session.getId(),
                session.getAttributes().get("username"));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            JsonNode node = mapper.readTree(message.getPayload());
            String action = node.path("action").asText("");
            switch (action) {
                case "subscribe" -> hub.subscribe(session, readTopics(node));
                case "unsubscribe" -> hub.unsubscribe(session, readTopics(node));
                case "ping" -> hub.pong(session);
                default -> hub.error(session, "未知指令：" + action);
            }
        } catch (Exception ex) {
            hub.error(session, "消息格式错误：" + ex.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        hub.remove(session);
        log.debug("WS 连接关闭 session={} status={}", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.debug("WS 传输异常 session={}: {}", session.getId(), exception.getMessage());
        hub.remove(session);
    }

    private List<String> readTopics(JsonNode node) {
        List<String> topics = new ArrayList<>();
        JsonNode array = node.path("topics");
        if (array.isArray()) {
            for (JsonNode item : array) {
                topics.add(item.asText());
            }
        }
        return topics;
    }
}
