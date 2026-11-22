package com.music.service.Impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.music.entity.Song;
import com.music.mapper.SongMapper;
import com.music.service.SongService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SongServiceImpl extends ServiceImpl<SongMapper, Song> implements SongService {
    @Override
    public List<String> getAllType() {
        // 查询所有不重复的歌曲分类
        return baseMapper.selectDistinctType();
    }
}
