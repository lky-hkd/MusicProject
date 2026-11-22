package com.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.music.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    //关注数 +1
    @Update("UPDATE user SET following_count = following_count +1 WHERE id = #{userId}")
    int incrFollowingCount(@Param("userId") Long userId);

    //粉丝数 +1
    @Update("UPDATE user SET follower_count = follower_count + 1 where id = #{userId}")
    int incrFollowerCount(@Param("userId") Long userId);

    // 关注数 -1（用 GREATEST 防止减成负数）
    @Update("UPDATE user SET following_count = GREATEST(following_count-1,0) where id = #{userId}")
    int decrFollowingCount(@Param("userId") Long userId);

    // 粉丝数 -1
    @Update("UPDATE `user` SET follower_count = GREATEST(follower_count - 1, 0) WHERE id = #{userId}")
    int decrFollowerCount(@Param("userId") Long userId);

}

