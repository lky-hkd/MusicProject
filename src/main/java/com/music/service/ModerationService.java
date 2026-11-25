package com.music.service;

import com.music.common.BizException;
import com.music.common.ModerationAction;
import com.music.common.ModerationContentType;
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
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 内容审核编排：
 * <pre>
 *   高危词命中 → 直接拦截（不调用模型）
 *   可疑词命中 → 同步调用模型复核
 *   无命中     → 直接放行（后续在此处接异步抽检）
 * </pre>
 *
 * 模型不可用/超时时：可疑内容返回 REVIEW（转人工/待审），不默认放行。
 */
@Slf4j
@Service
public class ModerationService {

    /** 送给模型的最大字符数，避免超长内容拖慢推理 */
    private static final int MAX_MODEL_CHARS = 1000;

    @Resource
    private SensitiveWordFilter sensitiveWordFilter;

    @Resource
    private ModerationModelClient moderationModelClient;

    @Resource
    private TextNormalizer textNormalizer;

    @Resource
    private ModerationAuditRecorder auditRecorder;

    /** 模型判定 block 时要求的最低置信度，低于该值降为 REVIEW */
    @Value("${app.moderation.block-confidence:0.75}")
    private double blockConfidence;

    /**
     * 执行一次完整审核，只返回结论，不抛异常。
     */
    public ModerationResult moderate(String content) {
        long start = System.currentTimeMillis();

        List<WordHit> hits = sensitiveWordFilter.findAll(content);
        WordHit blockHit = hits.stream()
                .filter(h -> h.getTier() == SensitiveWordFilter.TIER_BLOCK)
                .findFirst()
                .orElse(null);

        // 1. 高危词：直接拦截，零模型开销
        if (blockHit != null) {
            log.warn("[moderation] 高危词拦截 word={}", blockHit.getWord());
            return ModerationResult.builder()
                    .action(ModerationAction.BLOCK)
                    .stage(ModerationStage.RULE)
                    .hits(hits)
                    .reason("命中高危词：" + blockHit.getWord())
                    .latencyMs(latency(start))
                    .build();
        }

        // 2. 可疑词：交给模型复核
        if (!hits.isEmpty()) {
            String modelInput = prepareModelInput(content);
            ModelVerdict verdict = moderationModelClient.judge(modelInput);

            if (verdict == null) {
                log.warn("[moderation] 模型不可用，可疑内容转人工 hits={}", words(hits));
                return ModerationResult.builder()
                        .action(ModerationAction.REVIEW)
                        .stage(ModerationStage.FALLBACK)
                        .hits(hits)
                        .reason("模型不可用或超时，可疑内容转人工复核")
                        .latencyMs(latency(start))
                        .build();
            }

            ModerationAction action = decideByVerdict(verdict);
            return ModerationResult.builder()
                    .action(action)
                    .stage(ModerationStage.MODEL)
                    .hits(hits)
                    .verdict(verdict)
                    .reason(verdict.getReason())
                    .latencyMs(latency(start))
                    .build();
        }

        // 3. 无命中：放行
        // TODO 异步抽检：按采样率投递到审核队列，命中后下架/通知
        return ModerationResult.builder()
                .action(ModerationAction.ALLOW)
                .stage(ModerationStage.RULE)
                .hits(List.of())
                .reason("未命中敏感词")
                .latencyMs(latency(start))
                .build();
    }

    /**
     * 供发布/评论等入口直接调用：拦截时抛业务异常，其余返回审核结论。
     * 调用方可根据返回值把 REVIEW 内容落为「待审」状态（status=2）。
     */
    public ModerationAction check(String content) {
        return check(ModerationContext.builder()
                .contentType(ModerationContentType.TEXT)
                .content(content)
                .build());
    }

    /**
     * 带内容归属的审核入口：BLOCK / REVIEW 会写入审计表。
     *
     * @return ALLOW 或 REVIEW；BLOCK 会直接抛异常
     */
    public ModerationAction check(ModerationContext ctx) {
        if (ctx == null || ctx.getContent() == null) {
            return ModerationAction.ALLOW;
        }
        ModerationResult result = moderate(ctx.getContent());
        if (result.getAction() == ModerationAction.BLOCK) {
            auditRecorder.record(ctx, result);
            throw new BizException("内容包含违规信息，请修改后重试");
        }
        if (result.getAction() == ModerationAction.REVIEW) {
            auditRecorder.record(ctx, result);
            log.warn("[moderation] 可疑内容待复核 type={} id={} hits={} reason={}",
                    ctx.getContentType(), ctx.getContentId(),
                    words(result.getHits()), result.getReason());
        }
        return result.getAction();
    }

    private ModerationAction decideByVerdict(ModelVerdict verdict) {
        String decision = verdict.getDecision();
        if ("block".equals(decision)) {
            return verdict.getConfidence() >= blockConfidence
                    ? ModerationAction.BLOCK
                    : ModerationAction.REVIEW;
        }
        if ("review".equals(decision)) {
            return ModerationAction.REVIEW;
        }
        return ModerationAction.ALLOW;
    }

    private String prepareModelInput(String content) {
        String normalized = textNormalizer.normalize(content);
        return normalized.length() > MAX_MODEL_CHARS
                ? normalized.substring(0, MAX_MODEL_CHARS)
                : normalized;
    }

    private List<String> words(List<WordHit> hits) {
        return hits.stream().map(WordHit::getWord).toList();
    }

    private long latency(long start) {
        return System.currentTimeMillis() - start;
    }
}
