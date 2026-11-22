package com.music.config;

import jakarta.annotation.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * 站内消息 Redis 广播监听容器
 *
 * 每个实例启动时订阅 chat:push，收到广播后推本机 WebSocket 会话。
 * 这样多实例部署时，用户连在哪个实例都能收到消息。
 */
@Configuration
public class ChatRedisConfig {

    @Resource
    private ChatRedisSubscriber chatRedisSubscriber;

    @Bean
    public RedisMessageListenerContainer chatPushListenerContainer(RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(chatRedisSubscriber, new ChannelTopic(ChatRedisSubscriber.CHANNEL));
        return container;
    }
}
