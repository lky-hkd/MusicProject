package com.music.service.Impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.music.common.ModerationAction;
import com.music.common.BizException;
import com.music.common.ModerationContentType;
import com.music.common.Role;
import com.music.config.ChatWebSocketHandler;
import com.music.dto.ChatSendRequest;
import com.music.dto.ModerationContext;
import com.music.entity.ChatConversation;
import com.music.entity.ChatMessage;
import com.music.entity.Merchant;
import com.music.entity.User;
import com.music.mapper.ChatConversationMapper;
import com.music.mapper.ChatMessageMapper;
import com.music.mapper.MerchantMapper;
import com.music.mapper.UserMapper;
import com.music.service.ChatService;
import com.music.service.ChatModerationWorker;
import com.music.service.ModerationPenaltyService;
import com.music.service.ModerationService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ChatServiceImpl implements ChatService {

    private static final int TYPE_PRIVATE = 0;
    private static final int TYPE_SERVICE = 1;
    private static final int MSG_TEXT = 0;
    private static final int MSG_IMAGE = 1;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Resource
    private ChatConversationMapper conversationMapper;
    @Resource
    private ChatMessageMapper messageMapper;
    @Resource
    private UserMapper userMapper;
    @Resource
    private MerchantMapper merchantMapper;
    @Resource
    private ChatWebSocketHandler webSocketHandler;
    @Resource
    private ModerationService moderationService;
    @Resource
    private ModerationPenaltyService penaltyService;
    @Resource
    private ChatModerationWorker chatModerationWorker;

    @Override
    public List<Map<String, Object>> listConversations(Long userId) {
        List<ChatConversation> list = conversationMapper.selectList(new QueryWrapper<ChatConversation>()
                .and(w -> w.eq("user_a_id", userId).or().eq("user_b_id", userId))
                .orderByDesc("last_msg_time")
                .orderByDesc("id"));
        if (list.isEmpty()) return Collections.emptyList();

        // 未读数：一次分组查询
        Map<Long, Long> unreadMap = new HashMap<>();
        List<Map<String, Object>> counts = messageMapper.selectMaps(new QueryWrapper<ChatMessage>()
                .select("conversation_id AS cid", "COUNT(*) AS cnt")
                .eq("receiver_id", userId)
                .eq("is_read", 0)
                .groupBy("conversation_id"));
        for (Map<String, Object> row : counts) {
            Object cid = row.get("cid");
            Object cnt = row.get("cnt");
            if (cid != null && cnt != null) {
                unreadMap.put(((Number) cid).longValue(), ((Number) cnt).longValue());
            }
        }

        // 批量查对方信息
        Set<Long> peerIds = new HashSet<>();
        for (ChatConversation c : list) {
            peerIds.add(peerOf(c, userId));
        }
        Map<Long, User> userMap = userMapper.selectBatchIds(peerIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<Map<String, Object>> result = new ArrayList<>();
        for (ChatConversation c : list) {
            Long peerId = peerOf(c, userId);
            User peer = userMap.get(peerId);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("type", c.getType());
            m.put("peerId", peerId);
            m.put("peerName", peer == null ? "用户已注销" : (peer.getNickname() != null ? peer.getNickname() : peer.getUsername()));
            m.put("peerAvatar", peer == null ? "" : peer.getAvatar());
            m.put("peerRole", peer == null ? 0 : peer.getRole());
            m.put("lastMsg", c.getLastMsg());
            m.put("lastMsgTime", c.getLastMsgTime() == null ? "" : c.getLastMsgTime().format(TIME_FMT));
            m.put("unread", unreadMap.getOrDefault(c.getId(), 0L));
            result.add(m);
        }
        // 最后消息新的排前面
        result.sort((a, b) -> String.valueOf(b.get("lastMsgTime")).compareTo(String.valueOf(a.get("lastMsgTime"))));
        return result;
    }

    @Override
    public Map<String, Object> openConversation(Long userId, Long targetId) {
        return openInternal(userId, targetId, TYPE_PRIVATE);
    }

    @Override
    public Map<String, Object> openService(Long userId) {
        User admin = userMapper.selectOne(new QueryWrapper<User>()
                .eq("role", Role.ADMIN)
                .last("limit 1"));
        // 兼容：没有 role=2 的账号时，退回到用户名为 admin 的账号
        if (admin == null) {
            admin = userMapper.selectOne(new QueryWrapper<User>()
                    .eq("username", "admin")
                    .last("limit 1"));
        }
        if (admin == null) throw new RuntimeException("平台暂未开通客服");
        if (admin.getId().equals(userId)) throw new RuntimeException("你已经是客服账号了");
        return openInternal(userId, admin.getId(), TYPE_SERVICE);
    }

    @Override
    public Map<String, Object> openMerchant(Long userId, Long merchantId) {
        if (merchantId == null) throw new RuntimeException("商家不存在");
        Merchant merchant = merchantMapper.selectById(merchantId);
        if (merchant == null || merchant.getUserId() == null) throw new RuntimeException("商家不存在");
        return openInternal(userId, merchant.getUserId(), TYPE_PRIVATE);
    }

    private Map<String, Object> openInternal(Long userId, Long targetId, int type) {
        if (targetId == null) throw new RuntimeException("请选择聊天对象");
        if (targetId.equals(userId)) throw new RuntimeException("不能和自己聊天");
        User target = userMapper.selectById(targetId);
        if (target == null) throw new RuntimeException("用户不存在");

        ChatConversation conv = findByPair(userId, targetId);
        if (conv == null) {
            conv = new ChatConversation();
            conv.setUserAId(Math.min(userId, targetId));
            conv.setUserBId(Math.max(userId, targetId));
            conv.setType(type);
            conv.setCreateTime(LocalDateTime.now());
            try {
                conversationMapper.insert(conv);
            } catch (org.springframework.dao.DuplicateKeyException e) {
                // 并发下被另一个请求抢先创建，直接复用
                conv = findByPair(userId, targetId);
                if (conv == null) throw e;
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", conv.getId());
        m.put("type", conv.getType());
        m.put("peerId", targetId);
        m.put("peerName", target.getNickname() != null ? target.getNickname() : target.getUsername());
        m.put("peerAvatar", target.getAvatar());
        return m;
    }

    @Override
    public Map<String, Object> listMessages(Long userId, Long conversationId, Long beforeId, Long afterId, int size) {
        ChatConversation conv = requireMember(userId, conversationId);
        int limit = Math.min(Math.max(size, 1), 50);
        boolean incremental = afterId != null && afterId > 0;

        QueryWrapper<ChatMessage> qw = new QueryWrapper<ChatMessage>()
                .eq("conversation_id", conversationId);
        if (incremental) {
            // 断线重连：只取比本地最新一条更新的消息，正序返回
            qw.gt("id", afterId).orderByAsc("id").last("limit " + limit);
        } else {
            if (beforeId != null && beforeId > 0) {
                qw.lt("id", beforeId);
            }
            qw.orderByDesc("id").last("limit " + limit);
        }
        List<ChatMessage> list = messageMapper.selectList(qw);
        if (!incremental) {
            Collections.reverse(list);
        }

        Long peerId = peerOf(conv, userId);
        User peer = userMapper.selectById(peerId);

        Map<String, Object> peerMap = new LinkedHashMap<>();
        peerMap.put("id", peerId);
        peerMap.put("name", peer == null ? "用户已注销" : (peer.getNickname() != null ? peer.getNickname() : peer.getUsername()));
        peerMap.put("avatar", peer == null ? "" : peer.getAvatar());
        peerMap.put("role", peer == null ? 0 : peer.getRole());

        List<Map<String, Object>> msgs = list.stream().map(m -> messageView(m, userId)).collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("peer", peerMap);
        result.put("messages", msgs);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> send(Long userId, ChatSendRequest req) {
        if (req == null || req.getConversationId() == null) throw new RuntimeException("会话不存在");
        String content = req.getContent() == null ? "" : req.getContent().trim();
        if (content.isEmpty()) throw new RuntimeException("消息不能为空");
        int type = req.getType() == null ? MSG_TEXT : req.getType();
        if (type == MSG_TEXT && content.length() > 500) throw new RuntimeException("消息不能超过500字");

        ChatConversation conv = requireMember(userId, req.getConversationId());
        Long peerId = peerOf(conv, userId);

        String clientMsgId = req.getClientMsgId() == null ? null : req.getClientMsgId().trim();
        if (clientMsgId != null && clientMsgId.isEmpty()) {
            clientMsgId = null;
        }

        // 幂等：同一个 clientMsgId 重复提交（网络超时重试），直接返回已写入的那条
        if (clientMsgId != null) {
            ChatMessage exists = findByClientMsgId(userId, clientMsgId);
            if (exists != null) {
                return messageView(exists, userId);
            }
        }

        // 私聊文本：高危词直接不发送；可疑词先发后审，模型判不安全后异步撤回
        ModerationAction action = ModerationAction.ALLOW;
        if (type == MSG_TEXT) {
            if (penaltyService.isBanned(userId)) {
                throw new BizException("账号已被封禁，请联系管理员");
            }
            if (penaltyService.isMuted(userId)) {
                throw new BizException("你已被临时禁言，请 "
                        + penaltyService.muteRemainingMinutes(userId) + " 分钟后再试");
            }
            try {
                action = moderationService.check(ModerationContext.builder()
                        .contentType(ModerationContentType.CHAT)
                        .userId(userId)
                        .content(content)
                        .build());
            } catch (BizException e) {
                penaltyService.onViolation(userId);
                throw e;
            }
        }

        ChatMessage msg = new ChatMessage();
        msg.setConversationId(conv.getId());
        msg.setSenderId(userId);
        msg.setReceiverId(peerId);
        msg.setClientMsgId(clientMsgId);
        msg.setContent(content);
        msg.setType(type);
        msg.setIsRead(0);
        msg.setCreateTime(LocalDateTime.now());
        try {
            messageMapper.insert(msg);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // 并发重试：另一个请求已先写入，复用已存在的消息
            // 用当前读（for update），避免 REPEATABLE READ 快照看不到刚提交的那条
            ChatMessage exists = clientMsgId == null ? null : findByClientMsgIdForUpdate(userId, clientMsgId);
            if (exists != null) {
                return messageView(exists, userId);
            }
            throw e;
        }

        conv.setLastMsg(type == MSG_IMAGE ? "[图片]" : truncate(content, 200));
        conv.setLastMsgTime(msg.getCreateTime());
        conversationMapper.updateById(conv);

        Map<String, Object> sendView = messageView(msg, userId);
        sendView.put("event", "message");
        sendView.put("conversationId", conv.getId());

        // 事务提交后再推送：避免事务回滚了，对方却已收到消息
        Long conversationId = conv.getId();
        final ModerationAction decidedAction = action;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                pushToBothSides(conversationId, msg, peerId, userId);
                if (decidedAction == ModerationAction.REVIEW) {
                    chatModerationWorker.review(msg.getId(), conversationId, userId, content);
                } else if (decidedAction == ModerationAction.ALLOW) {
                    chatModerationWorker.sample(msg.getId(), conversationId, userId, content);
                }
            }
        });
        return sendView;
    }

    /** 分别按收到方 / 发送方视角推送，保证 mine 字段正确 */
    private void pushToBothSides(Long conversationId, ChatMessage msg, Long peerId, Long userId) {
        try {
            Map<String, Object> recvView = messageView(msg, peerId);
            recvView.put("event", "message");
            recvView.put("conversationId", conversationId);
            webSocketHandler.sendToUser(peerId, JSON.toJSONString(recvView));

            Map<String, Object> sendView = messageView(msg, userId);
            sendView.put("event", "message");
            sendView.put("conversationId", conversationId);
            webSocketHandler.sendToUser(userId, JSON.toJSONString(sendView));
        } catch (Exception e) {
            // 消息已提交，推送失败不影响数据，调用方可从历史接口补齐
            log.warn("[chat] 消息推送失败，conversationId={}, msgId={}：{}",
                    conversationId, msg.getId(), e.getMessage());
        }
    }

    private ChatMessage findByClientMsgId(Long userId, String clientMsgId) {
        return messageMapper.selectOne(new QueryWrapper<ChatMessage>()
                .eq("sender_id", userId)
                .eq("client_msg_id", clientMsgId)
                .last("limit 1"));
    }

    private ChatMessage findByClientMsgIdForUpdate(Long userId, String clientMsgId) {
        return messageMapper.selectOne(new QueryWrapper<ChatMessage>()
                .eq("sender_id", userId)
                .eq("client_msg_id", clientMsgId)
                .last("limit 1 for update"));
    }

    @Override
    public void markRead(Long userId, Long conversationId) {
        requireMember(userId, conversationId);
        ChatMessage update = new ChatMessage();
        update.setIsRead(1);
        messageMapper.update(update, new QueryWrapper<ChatMessage>()
                .eq("conversation_id", conversationId)
                .eq("receiver_id", userId)
                .eq("is_read", 0));
    }

    @Override
    public long unreadTotal(Long userId) {
        return messageMapper.selectCount(new QueryWrapper<ChatMessage>()
                .eq("receiver_id", userId)
                .eq("is_read", 0));
    }

    @Override
    public List<Map<String, Object>> searchUsers(Long userId, String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) return Collections.emptyList();
        String kw = keyword.trim();
        List<User> users = userMapper.selectList(new QueryWrapper<User>()
                .ne("id", userId)
                .and(w -> w.like("nickname", kw).or().like("username", kw))
                .last("limit 10"));
        return users.stream().map(u -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("name", u.getNickname() != null ? u.getNickname() : u.getUsername());
            m.put("avatar", u.getAvatar());
            m.put("role", u.getRole());
            return m;
        }).collect(Collectors.toList());
    }

    // ---------------- 内部工具 ----------------

    private ChatConversation findByPair(Long userId, Long targetId) {
        return conversationMapper.selectOne(new QueryWrapper<ChatConversation>()
                .eq("user_a_id", Math.min(userId, targetId))
                .eq("user_b_id", Math.max(userId, targetId))
                .last("limit 1"));
    }

    private ChatConversation requireMember(Long userId, Long conversationId) {
        ChatConversation conv = conversationMapper.selectById(conversationId);
        if (conv == null) throw new RuntimeException("会话不存在");
        if (!conv.getUserAId().equals(userId) && !conv.getUserBId().equals(userId)) {
            throw new RuntimeException("无权访问该会话");
        }
        return conv;
    }

    private Long peerOf(ChatConversation conv, Long userId) {
        return conv.getUserAId().equals(userId) ? conv.getUserBId() : conv.getUserAId();
    }

    private Map<String, Object> messageView(ChatMessage m, Long userId) {
        boolean recalled = m.getStatus() != null && m.getStatus() == 1;
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", m.getId());
        map.put("conversationId", m.getConversationId());
        map.put("senderId", m.getSenderId());
        map.put("content", recalled ? "该消息因违规已被拦截" : m.getContent());
        map.put("type", m.getType());
        map.put("status", recalled ? 1 : 0);
        map.put("createTime", m.getCreateTime() == null ? "" : m.getCreateTime().format(TIME_FMT));
        map.put("mine", m.getSenderId().equals(userId));
        return map;
    }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
