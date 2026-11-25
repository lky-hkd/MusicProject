package com.music.service;

import com.alibaba.fastjson2.JSON;
import com.music.common.ModerationAction;
import com.music.common.ModerationContentType;
import com.music.common.ModerationStage;
import com.music.config.ChatWebSocketHandler;
import com.music.dto.ModelVerdict;
import com.music.dto.ModerationContext;
import com.music.dto.ModerationResult;
import com.music.entity.ChatConversation;
import com.music.entity.ChatMessage;
import com.music.mapper.ChatConversationMapper;
import com.music.mapper.ChatMessageMapper;
import com.music.util.TextNormalizer;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 私聊可疑消息的异步复核：
 * 消息先投递，模型判定不安全后撤回（status=1），并推送 recall 事件。
 * 撤回不影响发送方当时看到的消息，但双方刷新或收到事件后都显示占位文案。
 */
@Slf4j
@Component
public class ChatModerationWorker {

    private static final int MAX_MODEL_CHARS = 500;
    private static final String RECALL_TEXT = "该消息因违规已被拦截";

    @Resource
    private ModerationModelClient moderationModelClient;
    @Resource
    private ModerationPenaltyService penaltyService;
    @Resource
    private ModerationAuditRecorder auditRecorder;
    @Resource
    private ChatMessageMapper messageMapper;
    @Resource
    private ChatConversationMapper conversationMapper;
    @Resource
    private ChatWebSocketHandler webSocketHandler;
    @Resource
    private TextNormalizer textNormalizer;

    @Value("${app.moderation.block-confidence:0.75}")
    private double blockConfidence;

    @Value("${app.moderation.sample-rate:0.03}")
    private double sampleRate;

    @Value("${app.moderation.sample-min-length:10}")
    private int sampleMinLength;

    /**
     * 未命中词库的消息按采样率抽检：新骗术、新话术词库覆盖不到，靠模型兜底。
     * 抽中后走与可疑消息相同的复核 + 撤回逻辑。
     */
    @Async("moderationExecutor")
    public void sample(Long messageId, Long conversationId, Long senderId, String content) {
        if (sampleRate <= 0 || content == null || content.length() < sampleMinLength) {
            return;
        }
        if (Math.random() > sampleRate) {
            return;
        }
        review(messageId, conversationId, senderId, content);
    }

    @Async("moderationExecutor")
    public void review(Long messageId, Long conversationId, Long senderId, String content) {
        try {
            String normalized = textNormalizer.normalize(content);
            if (normalized.length() > MAX_MODEL_CHARS) {
                normalized = normalized.substring(0, MAX_MODEL_CHARS);
            }
            long start = System.currentTimeMillis();
            ModelVerdict verdict = moderationModelClient.judge(normalized);
            if (verdict == null) {
                return;
            }
            boolean block = "block".equals(verdict.getDecision())
                    && verdict.getConfidence() >= blockConfidence;
            if (!block) {
                return;
            }

            ChatMessage msg = messageMapper.selectById(messageId);
            if (msg == null || (msg.getStatus() != null && msg.getStatus() != 0)) {
                return;
            }
            ChatMessage update = new ChatMessage();
            update.setId(messageId);
            update.setStatus(1);
            messageMapper.updateById(update);

            // 会话列表的最后一条如果就是这条，换成占位文案
            ChatConversation conv = conversationMapper.selectById(conversationId);
            if (conv != null && content != null && content.equals(conv.getLastMsg())) {
                ChatConversation convUpdate = new ChatConversation();
                convUpdate.setId(conversationId);
                convUpdate.setLastMsg("[违规消息已拦截]");
                conversationMapper.updateById(convUpdate);
            }

            pushRecall(messageId, conversationId, msg.getSenderId(), msg.getReceiverId());
            penaltyService.onViolation(senderId);
            auditRecorder.record(ModerationContext.builder()
                    .contentType(ModerationContentType.CHAT)
                    .contentId(messageId)
                    .userId(senderId)
                    .content(content)
                    .build(), ModerationResult.builder()
                    .action(ModerationAction.BLOCK)
                    .stage(ModerationStage.MODEL)
                    .hits(List.of())
                    .verdict(verdict)
                    .reason(verdict.getReason())
                    .latencyMs(System.currentTimeMillis() - start)
                    .build());
            log.warn("[moderation] 私聊消息已撤回 messageId={} senderId={}", messageId, senderId);
        } catch (Exception e) {
            log.warn("[moderation] 私聊异步复核失败 messageId={}：{}", messageId, e.getMessage());
        }
    }

    private void pushRecall(Long messageId, Long conversationId, Long senderId, Long receiverId) {
        try {
            push(senderId, messageId, conversationId, true);
            push(receiverId, messageId, conversationId, false);
        } catch (Exception e) {
            log.warn("[moderation] 撤回事件推送失败 messageId={}：{}", messageId, e.getMessage());
        }
    }

    private void push(Long userId, Long messageId, Long conversationId, boolean mine) {
        if (userId == null) {
            return;
        }
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("event", "recall");
        event.put("id", messageId);
        event.put("conversationId", conversationId);
        event.put("content", RECALL_TEXT);
        event.put("mine", mine);
        webSocketHandler.sendToUser(userId, JSON.toJSONString(event));
    }
}
