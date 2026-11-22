package com.music.dto;

import lombok.Data;

@Data
public class ChatSendRequest {
    private Long conversationId;
    private String content;
    /** 0 文本 1 图片 */
    private Integer type;
    /** 客户端消息ID（前端生成），重试时复用同一个，用于幂等 */
    private String clientMsgId;
}
