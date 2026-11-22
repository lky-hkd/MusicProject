package com.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.music.entity.Song;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SongMapper extends BaseMapper<Song> {
    @Select("SELECT DISTINCT type FROM song WHERE type IS NOT NULL AND type != ''")
    List<String> selectDistinctType();
}
