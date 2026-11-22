package com.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.music.dto.SongHistoryDTO;
import com.music.entity.SongHistory;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;
import java.util.Map;

public interface SongHistoryMapper extends BaseMapper<SongHistory> {

    // 查询用户播放历史，关联song表拿到歌曲信息，按播放时间倒序
    @Select("SELECT h.id, h.song_id, s.song_name, s.singer, s.cover_url, s.audio_url " +
            "FROM song_history h " +
            "LEFT JOIN song s ON h.song_id = s.id " +
            "WHERE h.user_id = #{userId} " +
            "ORDER BY h.play_time DESC")
    List<SongHistoryDTO> selectUserHistory(@Param("userId") Long userId);

    @Select(" SELECT COUNT(*) FROM song_history  WHERE user_id = #{userId}")
    public int countHistoryByUserId(@Param("userId") Long userId);
}
