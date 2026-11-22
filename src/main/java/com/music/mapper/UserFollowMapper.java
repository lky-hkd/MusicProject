package com.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.music.dto.FollowUserDTO;
import com.music.entity.UserFollow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface UserFollowMapper extends BaseMapper<UserFollow> {

    //判断是否已关注
    @Select("SELECT count(1) from user_follow where follower_id = #{followerId} and followee_id = #{followeeId}")
    int exists(@Param("followerId") Long followerId, @Param("followeeId") Long followeeId);

    // 我的关注列表（按关注时间倒序），followed 表示当前用户是否已关注对方
    @Select("SELECT u.id, u.username, u.nickname, u.avatar, u.role, " +
            "u.following_count AS followingCount, u.follower_count AS followerCount, " +
            "EXISTS(SELECT 1 FROM user_follow x WHERE x.follower_id = #{userId} AND x.followee_id = u.id) AS followed " +
            "FROM user_follow f JOIN `user` u ON u.id = f.followee_id " +
            "WHERE f.follower_id = #{userId} ORDER BY f.created_at DESC, f.id DESC LIMIT #{offset}, #{limit}")
    List<FollowUserDTO> listFollowing(@Param("userId") Long userId,
                                      @Param("offset") int offset,
                                      @Param("limit") int limit);

    // 我的粉丝列表，followed 表示我是否已回关
    @Select("SELECT u.id, u.username, u.nickname, u.avatar, u.role, " +
            "u.following_count AS followingCount, u.follower_count AS followerCount, " +
            "EXISTS(SELECT 1 FROM user_follow x WHERE x.follower_id = #{userId} AND x.followee_id = u.id) AS followed " +
            "FROM user_follow f JOIN `user` u ON u.id = f.follower_id " +
            "WHERE f.followee_id = #{userId} ORDER BY f.created_at DESC, f.id DESC LIMIT #{offset}, #{limit}")
    List<FollowUserDTO> listFollowers(@Param("userId") Long userId,
                                      @Param("offset") int offset,
                                      @Param("limit") int limit);
}
