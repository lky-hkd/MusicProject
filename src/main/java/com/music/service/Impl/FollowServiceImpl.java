package com.music.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.music.common.BizException;
import com.music.dto.FollowUserDTO;
import com.music.entity.User;
import com.music.entity.UserFollow;
import com.music.mapper.UserFollowMapper;
import com.music.mapper.UserMapper;
import com.music.service.FollowService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class FollowServiceImpl extends ServiceImpl<UserFollowMapper, UserFollow> implements FollowService {

    @Resource
    private UserMapper userMapper;

    @Resource
    private UserFollowMapper userFollowMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void follow(Long followerId, Long followeeId) {
        if (followerId.equals(followeeId)) {
            throw new BizException("不能关注自己");
        }
        // 校验被关注者是否存在（不是校验自己）
        if (userMapper.selectById(followeeId) == null) {
            throw new BizException("用户不存在");
        }
        if (userFollowMapper.exists(followerId, followeeId) > 0) {
            throw new BizException("已关注");
        }

        UserFollow userFollow = new UserFollow();
        userFollow.setFollowerId(followerId);
        userFollow.setFolloweeId(followeeId);
        userFollow.setCreatedAt(LocalDateTime.now());
        try {
            userFollowMapper.insert(userFollow);
        } catch (DuplicateKeyException e) {
            // 并发下重复点击，唯一键兜底
            throw new BizException("已关注");
        }

        userMapper.incrFollowingCount(followerId);
        userMapper.incrFollowerCount(followeeId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unfollow(Long followerId, Long followeeId) {
        if (followerId.equals(followeeId)) {
            throw new BizException("不能取关自己");
        }

        int deleted = userFollowMapper.delete(
                new LambdaQueryWrapper<UserFollow>()
                        .eq(UserFollow::getFollowerId, followerId)
                        .eq(UserFollow::getFolloweeId, followeeId)
        );

        // 只有确实删除了关系，才减计数，保证幂等
        if (deleted > 0) {
            userMapper.decrFollowingCount(followerId);
            userMapper.decrFollowerCount(followeeId);
        }
    }

    @Override
    public List<FollowUserDTO> listFollowing(Long userId, int page, int size) {
        int limit = Math.min(Math.max(size, 1), 50);
        int offset = (Math.max(page, 1) - 1) * limit;
        return userFollowMapper.listFollowing(userId, offset, limit);
    }

    @Override
    public List<FollowUserDTO> listFollowers(Long userId, int page, int size) {
        int limit = Math.min(Math.max(size, 1), 50);
        int offset = (Math.max(page, 1) - 1) * limit;
        return userFollowMapper.listFollowers(userId, offset, limit);
    }

    @Override
    public Map<String, Object> status(Long viewerId, Long targetId) {
        User target = userMapper.selectById(targetId);
        if (target == null) {
            throw new BizException("用户不存在");
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("self", viewerId.equals(targetId));
        map.put("following", userFollowMapper.exists(viewerId, targetId) > 0);
        map.put("followedBy", userFollowMapper.exists(targetId, viewerId) > 0);
        map.put("followingCount", target.getFollowingCount());
        map.put("followerCount", target.getFollowerCount());
        return map;
    }
}
