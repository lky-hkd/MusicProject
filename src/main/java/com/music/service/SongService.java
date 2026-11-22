package com.music.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.music.entity.Song;

import java.util.List;

public interface SongService extends IService<Song> {
    List<String> getAllType();
}
