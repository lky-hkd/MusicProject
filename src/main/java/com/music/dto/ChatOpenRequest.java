package com.music.dto;

import lombok.Data;

@Data
public class ChatOpenRequest {
    /** 聊天对象用户ID */
    private Long targetId;
}
