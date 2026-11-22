package com.music.service;

import com.music.dto.FollowUserDTO;

import java.util.List;
import java.util.Map;

public interface FollowService {

    /**
     * 关注。校验、插入关系、更新双方计数在同一事务内完成。
     */
    void follow(Long followerId, Long followeeId);

    /**
     * 取关。先删关系，根据影响行数决定是否减计数，保证幂等。
     */
    void unfollow(Long followerId, Long followeeId);

    /** 我关注的人 */
    List<FollowUserDTO> listFollowing(Long userId, int page, int size);

    /** 关注我的人 */
    List<FollowUserDTO> listFollowers(Long userId, int page, int size);

    /** 当前用户与目标用户的关注关系及计数 */
    Map<String, Object> status(Long viewerId, Long targetId);
}
