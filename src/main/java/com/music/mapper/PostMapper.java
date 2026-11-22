package com.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.music.entity.Post;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface PostMapper extends BaseMapper<Post> {

    @Update("UPDATE post SET like_count = like_count + 1 WHERE id = #{id}")
    int incrLike(@Param("id") Long id);

    @Update("UPDATE post SET like_count = GREATEST(like_count - 1, 0) WHERE id = #{id}")
    int decrLike(@Param("id") Long id);

    @Update("UPDATE post SET comment_count = comment_count + 1 WHERE id = #{id}")
    int incrComment(@Param("id") Long id);

    @Update("UPDATE post SET comment_count = GREATEST(comment_count - 1, 0) WHERE id = #{id}")
    int decrComment(@Param("id") Long id);

    /** 广场：全站最新 */
    @Select("SELECT * FROM post WHERE status = 0 AND id < #{lastId} ORDER BY id DESC LIMIT #{limit}")
    List<Post> selectSquare(@Param("lastId") long lastId, @Param("limit") int limit);

    /** 关注流：自己和已关注的人的动态 */
    @Select("SELECT p.* FROM post p " +
            "LEFT JOIN user_follow f ON f.followee_id = p.user_id AND f.follower_id = #{userId} " +
            "WHERE p.status = 0 AND p.id < #{lastId} " +
            "AND (p.user_id = #{userId} OR f.id IS NOT NULL) " +
            "ORDER BY p.id DESC LIMIT #{limit}")
    List<Post> selectFollowing(@Param("userId") Long userId,
                               @Param("lastId") long lastId,
                               @Param("limit") int limit);
}
