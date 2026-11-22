package com.music.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 聊天会话（两人一个会话，user_a_id 恒小于 user_b_id）
 */
@Data
@TableName("chat_conversation")
public class ChatConversation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userAId;
    private Long userBId;

    /** 0 私聊 1 平台客服 */
    private Integer type;

    private String lastMsg;
    private LocalDateTime lastMsgTime;
    private LocalDateTime createTime;
}
