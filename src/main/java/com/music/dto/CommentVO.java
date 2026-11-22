package com.music.dto;

import lombok.Data;

@Data
public class CommentVO {
    private Long id;
    private Long userId;
    private String authorName;
    private String authorAvatar;
    private String content;
    /** 被回复人昵称，为空表示直接评论 */
    private String replyToName;
    private Boolean mine;
    private String createTime;
}
