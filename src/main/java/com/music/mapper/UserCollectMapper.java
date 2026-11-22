package com.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.music.entity.Song;
import com.music.entity.UserCollect;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface UserCollectMapper extends BaseMapper<UserCollect> {

    // 查询用户是否收藏该歌曲
    @Select("SELECT COUNT(*) FROM user_collect WHERE user_id = #{userId} AND song_id = #{songId}")
    int countCollect(@Param("userId") Long userId, @Param("songId") Long songId);

    // 删除收藏
    @Delete("DELETE FROM user_collect WHERE user_id = #{userId} AND song_id = #{songId}")
    int deleteCollect(@Param("userId") Long userId, @Param("songId") Long songId);

    @Select("SELECT s.* FROM user_collect uc " +
            "LEFT JOIN song s ON uc.song_id = s.id " +
            "WHERE uc.user_id = #{userId} ORDER BY uc.create_time DESC")
    List<Song> selectCollectSongsByUserId(@Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM user_collect WHERE user_id = #{userId}")
    int countCollectSongsByUserId(@Param("userId") Long userId);

}
