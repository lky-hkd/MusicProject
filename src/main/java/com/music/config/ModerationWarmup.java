package com.music.config;

import com.music.service.ModerationModelClient;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 启动预热：应用就绪后异步触发一次模型加载，避免首个可疑内容请求遇到 60 秒冷加载而超时降级。
 * 放在审核线程池里执行，不拖慢启动和 Web 端口监听。
 */
@Slf4j
@Component
public class ModerationWarmup {

    @Resource
    private ModerationModelClient moderationModelClient;

    @Value("${app.moderation.enabled:true}")
    private boolean enabled;

    @Value("${app.moderation.warmup-enabled:true}")
    private boolean warmupEnabled;

    @EventListener(ApplicationReadyEvent.class)
    @Async("moderationExecutor")
    public void warmup() {
        if (!enabled || !warmupEnabled) {
            return;
        }
        log.info("[moderation] 开始预热审核模型");
        moderationModelClient.warmup();
    }
}
