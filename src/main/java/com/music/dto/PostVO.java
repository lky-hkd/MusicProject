package com.music.dto;

import lombok.Data;

import java.util.List;

@Data
public class PostVO {
    private Long id;
    private Long userId;
    private String authorName;
    private String authorAvatar;
    private Integer authorRole;
    private String content;
    private List<String> images;
    private Integer likeCount;
    private Integer commentCount;
    /** 当前登录用户是否点过赞 */
    private Boolean liked;
    /** 是否是当前登录用户发的 */
    private Boolean mine;
    private String createTime;
}
