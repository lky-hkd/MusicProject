package com.music.service;

import com.music.dto.ModerationContext;
import com.music.dto.ModerationResult;
import com.music.dto.WordHit;
import com.music.entity.ModerationAudit;
import com.music.mapper.ModerationAuditMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

/**
 * 审核结果落库。写失败只告警，不影响主流程。
 *
 * 用独立事务（REQUIRES_NEW）写入：拦截时业务事务会因 BizException 回滚，
 * 若审计与业务同事务，拦截记录会被一起回滚，导致「拦截了却查不到」。
 */
@Slf4j
@Component
public class ModerationAuditRecorder {

    private static final int SNIPPET_LENGTH = 40;

    @Resource
    private ModerationAuditMapper auditMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void record(ModerationContext ctx, ModerationResult result, boolean sampled, int handled) {
        try {
            ModerationAudit audit = new ModerationAudit();
            audit.setContentType(ctx.getContentType() == null ? "TEXT" : ctx.getContentType().name());
            audit.setContentId(ctx.getContentId());
            audit.setUserId(ctx.getUserId());
            audit.setContentHash(sha256(ctx.getContent()));
            audit.setSnippet(snippet(ctx.getContent()));
            audit.setStage(result.getStage() == null ? null : result.getStage().name());
            audit.setAction(result.getAction() == null ? null : result.getAction().name());
            audit.setHits(hits(result));
            if (result.getVerdict() != null) {
                audit.setModelDecision(result.getVerdict().getDecision());
                audit.setConfidence(result.getVerdict().getConfidence());
            }
            audit.setReason(result.getReason());
            audit.setLatencyMs((int) Math.min(Integer.MAX_VALUE, result.getLatencyMs()));
            audit.setSampled(sampled ? 1 : 0);
            audit.setHandled(handled);
            audit.setCreateTime(LocalDateTime.now());
            auditMapper.insert(audit);
        } catch (Exception e) {
            log.warn("[moderation] 审计写入失败：{}", e.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void record(ModerationContext ctx, ModerationResult result) {
        record(ctx, result, false, 0);
    }

    private String hits(ModerationResult result) {
        if (result.getHits() == null || result.getHits().isEmpty()) {
            return null;
        }
        return result.getHits().stream()
                .map(WordHit::getWord)
                .collect(Collectors.joining(","));
    }

    /**
     * 脱敏片段：只保留首尾各 2 字，中间用 *** 代替。
     * 审计表不存用户明文内容（私聊、评价都属于隐私数据），需要原文时按 content_hash 追溯。
     */
    private String snippet(String content) {
        if (content == null) {
            return null;
        }
        String s = content.trim();
        if (s.length() > SNIPPET_LENGTH) {
            s = s.substring(0, SNIPPET_LENGTH);
        }
        if (s.length() <= 4) {
            return "***";
        }
        return s.substring(0, 2) + "***" + s.substring(s.length() - 2);
    }

    private String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((content == null ? "" : content).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
