package com.campus.sportsbuddy.websocket;

import com.campus.sportsbuddy.service.RealtimePresenceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
public class UserNotifier extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final RealtimePresenceService presenceService;
    private final Map<Long, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    public UserNotifier(ObjectMapper objectMapper, RealtimePresenceService presenceService) {
        this.objectMapper = objectMapper;
        this.presenceService = presenceService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId != null) {
            presenceService.markActive(userId);
            sessions.computeIfAbsent(userId, ignored -> new CopyOnWriteArraySet<>()).add(session);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId != null) {
            presenceService.markActive(userId);
        }
        try {
            if ("PING".equals(objectMapper.readTree(message.getPayload()).path("type").asText())) {
                send(session, toJson("PONG", "pong", null));
            }
        } catch (IOException | RuntimeException ignored) {
            // 非 JSON 消息忽略，连接本身仍用于接收服务端实时通知。
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId == null) {
            return;
        }
        Set<WebSocketSession> userSessions = sessions.get(userId);
        if (userSessions != null) {
            userSessions.remove(session);
            if (userSessions.isEmpty()) {
                sessions.remove(userId);
            }
        }
    }

    public void sendToUser(Long userId, String type, String message, Object data) {
        Set<WebSocketSession> userSessions = sessions.get(userId);
        if (userSessions == null || userSessions.isEmpty()) {
            return;
        }
        String payload = toJson(type, message, data);
        userSessions.removeIf(session -> !send(session, payload));
    }

    public void broadcast(String type, String message, Object data) {
        String payload = toJson(type, message, data);
        sessions.values().forEach(userSessions ->
                userSessions.removeIf(session -> !send(session, payload)));
    }

    private boolean send(WebSocketSession session, String payload) {
        if (!session.isOpen()) {
            return false;
        }
        try {
            synchronized (session) {
                session.sendMessage(new TextMessage(payload));
            }
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    private String toJson(String type, String message, Object data) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", type);
        payload.put("message", message);
        payload.put("data", data);
        payload.put("createdAt", Instant.now().toString());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (IOException exception) {
            throw new IllegalStateException("通知序列化失败", exception);
        }
    }
}
