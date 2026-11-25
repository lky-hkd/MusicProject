package com.music.service;

import com.music.common.ModerationAction;
import com.music.common.ModerationStage;
import com.music.dto.ModelVerdict;
import com.music.dto.ModerationContext;
import com.music.dto.ModerationResult;
import com.music.dto.WordHit;
import com.music.util.SensitiveWordFilter;
import com.music.util.TextNormalizer;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 异步抽检：对「未命中词库」的内容按采样率送模型复核，命中后自动下架。
 * 跑在 moderationExecutor 线程池，不阻塞发布请求。
 */
@Slf4j
@Component
public class ModerationSampleWorker {

    private static final int MAX_MODEL_CHARS = 1000;

    @Resource
    private ModerationModelClient moderationModelClient;

    @Resource
    private ModerationContentHider contentHider;

    @Resource
    private ModerationAuditRecorder auditRecorder;

    @Resource
    private TextNormalizer textNormalizer;

    @Value("${app.moderation.sample-rate:0.03}")
    private double sampleRate;

    @Value("${app.moderation.sample-min-length:10}")
    private int sampleMinLength;

    @Value("${app.moderation.block-confidence:0.75}")
    private double blockConfidence;

    @Async("moderationExecutor")
    public void sample(ModerationContext ctx) {
        try {
            if (ctx == null || ctx.getContent() == null) {
                return;
            }
            String content = ctx.getContent().trim();
            if (sampleRate <= 0 || content.length() < sampleMinLength) {
                return;
            }
            if (Math.random() > sampleRate) {
                return;
            }

            String normalized = textNormalizer.normalize(content);
            String input = normalized.length() > MAX_MODEL_CHARS
                    ? normalized.substring(0, MAX_MODEL_CHARS)
                    : normalized;
            long start = System.currentTimeMillis();
            ModelVerdict verdict = moderationModelClient.judge(input);
            if (verdict == null) {
                return;
            }

            boolean block = "block".equals(verdict.getDecision())
                    && verdict.getConfidence() >= blockConfidence;
            boolean review = "review".equals(verdict.getDecision());

            // 抽检只关注有风险的结果，allow 不落库，避免审计表被正常内容淹没
            if (!block && !review) {
                return;
            }

            boolean handled = block && contentHider.hide(ctx.getContentType(), ctx.getContentId());

            ModerationResult result = ModerationResult.builder()
                    .action(block ? ModerationAction.BLOCK : ModerationAction.REVIEW)
                    .stage(ModerationStage.MODEL)
                    .hits(List.<WordHit>of())
                    .verdict(verdict)
                    .reason(verdict.getReason())
                    .latencyMs(System.currentTimeMillis() - start)
                    .build();
            auditRecorder.record(ctx, result, true, handled ? 1 : 0);
        } catch (Exception e) {
            log.warn("[moderation] 异步抽检失败：{}", e.getMessage());
        }
    }
}
