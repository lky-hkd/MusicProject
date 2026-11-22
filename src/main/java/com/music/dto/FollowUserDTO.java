package com.music.dto;

import lombok.Data;

/**
 * 关注/粉丝列表中的用户信息
 */
@Data
public class FollowUserDTO {
    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    private Integer role;
    private Integer followingCount;
    private Integer followerCount;
    /** 当前登录用户是否已关注该用户 */
    private Boolean followed;
}
