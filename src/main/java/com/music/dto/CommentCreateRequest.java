package com.music.dto;

import lombok.Data;

@Data
public class CommentCreateRequest {
    private String content;
    /** 回复的目标用户，可选 */
    private Long replyToUserId;
}
