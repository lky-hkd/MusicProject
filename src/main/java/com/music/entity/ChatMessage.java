package com.music.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 聊天消息
 */
@Data
@TableName("chat_message")
public class ChatMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long conversationId;
    private Long senderId;
    private Long receiverId;

    /** 客户端生成的消息ID，发送重试时用于幂等去重 */
    private String clientMsgId;

    /** 文本内容或图片地址 */
    private String content;

    /** 0 文本 1 图片 */
    private Integer type;

    /** 0 未读 1 已读 */
    private Integer isRead;

    private LocalDateTime createTime;
}
