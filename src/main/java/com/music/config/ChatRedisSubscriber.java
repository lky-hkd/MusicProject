package com.music.config;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 站内消息跨实例广播订阅者
 *
 * 发送方实例把推送内容发到 Redis 频道，每个实例都会收到，
 * 但只有真正持有该用户 WebSocket 连接的实例会真正发出去。
 */
@Slf4j
@Component
public class ChatRedisSubscriber implements MessageListener {

    /** 广播频道：多实例共用同一个 */
    public static final String CHANNEL = "chat:push";

    @Resource
    private ChatWebSocketHandler chatWebSocketHandler;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            JSONObject envelope = JSON.parseObject(body);
            Long userId = envelope.getLong("userId");
            String payload = envelope.getString("payload");
            if (userId != null && payload != null) {
                // 只推本实例持有的连接，不会再次广播，避免循环
                chatWebSocketHandler.sendToUserLocal(userId, payload);
            }
        } catch (Exception e) {
            log.warn("[chat] 解析广播消息失败：{}", e.getMessage());
        }
    }
}
