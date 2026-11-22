package com.music.config;

import com.alibaba.fastjson2.JSONObject;
import com.music.common.UserContext;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 站内消息 WebSocket 推送
 * 一个用户可能同时开多个标签页，所以用 userId -> 多个 session 的映射
 */
@Slf4j
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final Map<Long, Set<WebSocketSession>> SESSIONS = new ConcurrentHashMap<>();

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long userId = (Long) session.getAttributes().get(UserContext.USER_ID_ATTR);
        if (userId == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }
        SESSIONS.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(session);
        log.debug("[chat] 用户 {} 建立 WebSocket 连接，当前连接数 {}", userId, SESSIONS.get(userId).size());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // 前端定时发 ping 保活，这里回 pong
        if ("ping".equals(message.getPayload())) {
            session.sendMessage(new TextMessage("pong"));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        Long userId = (Long) session.getAttributes().get(UserContext.USER_ID_ATTR);
        if (userId == null) return;
        Set<WebSocketSession> set = SESSIONS.get(userId);
        if (set != null) {
            set.remove(session);
            if (set.isEmpty()) SESSIONS.remove(userId);
        }
        log.debug("[chat] 用户 {} 断开 WebSocket 连接", userId);
    }

    /**
     * 向指定用户推送 JSON 文本。
     * 先广播到 Redis，所有实例（包括自己）收到后只推本机连接；
     * Redis 不可用时降级为只推本实例，保证单机仍可用。
     */
    public void sendToUser(Long userId, String json) {
        try {
            JSONObject envelope = new JSONObject();
            envelope.put("userId", userId);
            envelope.put("payload", json);
            stringRedisTemplate.convertAndSend(ChatRedisSubscriber.CHANNEL, envelope.toJSONString());
        } catch (Exception e) {
            log.warn("[chat] Redis 广播失败，降级为本地推送：{}", e.getMessage());
            sendToUserLocal(userId, json);
        }
    }

    /** 只推本实例持有的连接，供广播订阅者调用 */
    public void sendToUserLocal(Long userId, String json) {
        Set<WebSocketSession> set = SESSIONS.get(userId);
        if (set == null || set.isEmpty()) return;
        for (WebSocketSession s : set) {
            if (!s.isOpen()) continue;
            try {
                synchronized (s) {
                    s.sendMessage(new TextMessage(json));
                }
            } catch (IOException e) {
                log.warn("[chat] 推送消息给用户 {} 失败：{}", userId, e.getMessage());
            }
        }
    }
}
