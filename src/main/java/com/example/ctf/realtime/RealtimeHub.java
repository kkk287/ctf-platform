package com.example.ctf.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实时推送中心：维护 WebSocket 会话与主题订阅，向指定主题广播事件。
 *
 * <p>协议（纯文本 JSON）：
 * <pre>
 * 客户端 → 服务端
 *   {"action":"subscribe","topics":["scoreboard:&lt;eventId&gt;","solves:&lt;eventId&gt;","announcement"]}
 *   {"action":"unsubscribe","topics":[...]}
 *   {"action":"ping"}
 * 服务端 → 客户端
 *   {"type":"ready","username":"...","topics":[...]}
 *   {"type":"subscribed","topics":[...]}
 *   {"type":"event","kind":"SOLVE|SCOREBOARD_CHANGED|ANNOUNCEMENT_CHANGED","topic":"...","data":{...}}
 *   {"type":"pong"}
 *   {"type":"error","message":"..."}
 * </pre>
 */
@Component
public class RealtimeHub {

    private static final Logger log = LoggerFactory.getLogger(RealtimeHub.class);
    private static final int MAX_TOPICS_PER_SESSION = 20;

    private final ObjectMapper mapper;
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> topicsBySession = new ConcurrentHashMap<>();

    public RealtimeHub(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public void register(WebSocketSession session) {
        sessions.put(session.getId(), session);
        topicsBySession.put(session.getId(), ConcurrentHashMap.newKeySet());
    }

    public void remove(WebSocketSession session) {
        sessions.remove(session.getId());
        topicsBySession.remove(session.getId());
    }

    public Set<String> topicsOf(WebSocketSession session) {
        return topicsBySession.getOrDefault(session.getId(), Set.of());
    }

    public void subscribe(WebSocketSession session, List<String> topics) {
        Set<String> current = topicsBySession.computeIfAbsent(session.getId(), k -> ConcurrentHashMap.newKeySet());
        for (String topic : topics) {
            if (topic == null || topic.isBlank()) {
                continue;
            }
            if (current.size() >= MAX_TOPICS_PER_SESSION) {
                break;
            }
            current.add(topic.trim());
        }
        send(session, Map.of("type", "subscribed", "topics", new ArrayList<>(current)));
    }

    public void unsubscribe(WebSocketSession session, List<String> topics) {
        Set<String> current = topicsBySession.get(session.getId());
        if (current == null) {
            return;
        }
        for (String topic : topics) {
            if (topic != null) {
                current.remove(topic.trim());
            }
        }
        send(session, Map.of("type", "subscribed", "topics", new ArrayList<>(current)));
    }

    public void pong(WebSocketSession session) {
        send(session, Map.of("type", "pong"));
    }

    public void error(WebSocketSession session, String message) {
        send(session, Map.of("type", "error", "message", message));
    }

    /** 事务提交后再广播，避免客户端收到事件后立刻回查却读不到未提交数据。 */
    public void broadcastAfterCommit(String topic, String kind, Object payload) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    broadcast(topic, kind, payload);
                }
            });
        } else {
            broadcast(topic, kind, payload);
        }
    }

    /** 向订阅了该主题的所有会话推送事件。 */
    public void broadcast(String topic, String kind, Object payload) {
        if (topic == null || topic.isBlank()) {
            return;
        }
        String json = serialize(Map.of(
                "type", "event",
                "kind", kind == null ? "EVENT" : kind,
                "topic", topic,
                "data", payload == null ? Map.of() : payload));
        if (json == null) {
            return;
        }
        for (Map.Entry<String, Set<String>> entry : topicsBySession.entrySet()) {
            if (!entry.getValue().contains(topic)) {
                continue;
            }
            WebSocketSession session = sessions.get(entry.getKey());
            if (session != null && session.isOpen()) {
                sendRaw(session, json);
            }
        }
    }

    public void send(WebSocketSession session, Map<String, Object> message) {
        String json = serialize(message);
        if (json != null) {
            sendRaw(session, json);
        }
    }

    private void sendRaw(WebSocketSession session, String json) {
        try {
            // Spring 的 WebSocketSession 非线程安全，同一会话的写入串行化
            synchronized (session) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(json));
                }
            }
        } catch (Exception ex) {
            log.debug("WS 推送失败 session={}: {}", session.getId(), ex.getMessage());
        }
    }

    private String serialize(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception ex) {
            log.warn("WS 消息序列化失败: {}", ex.getMessage());
            return null;
        }
    }
}
