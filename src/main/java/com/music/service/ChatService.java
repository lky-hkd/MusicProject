package com.music.service;

import com.music.dto.ChatSendRequest;

import java.util.List;
import java.util.Map;

/**
 * 站内消息
 */
public interface ChatService {

    /** 会话列表（含对方信息、最后消息、未读数） */
    List<Map<String, Object>> listConversations(Long userId);

    /** 与指定用户打开（或创建）会话 */
    Map<String, Object> openConversation(Long userId, Long targetId);

    /** 打开平台客服会话 */
    Map<String, Object> openService(Long userId);

    /** 通过商家ID打开会话（商家ID -> 商家所属用户ID） */
    Map<String, Object> openMerchant(Long userId, Long merchantId);

    /** 拉取某个会话的历史消息；afterId 用于断线重连后的增量补拉 */
    Map<String, Object> listMessages(Long userId, Long conversationId, Long beforeId, Long afterId, int size);

    /** 发送消息 */
    Map<String, Object> send(Long userId, ChatSendRequest req);

    /** 会话标记为已读 */
    void markRead(Long userId, Long conversationId);

    /** 未读总数 */
    long unreadTotal(Long userId);

    /** 按昵称/用户名搜索可以聊天的用户 */
    List<Map<String, Object>> searchUsers(Long userId, String keyword);
}
