package com.music.service;

import com.music.entity.Song;

import java.util.List;

public interface UserCollectService {
    // 判断是否收藏
    boolean isCollect(Long userId, Long songId);
    // 添加收藏
    boolean addCollect(Long userId, Long songId);
    // 取消收藏
    boolean cancelCollect(Long userId, Long songId);
    List<Song> getCollectSongList(Long userId);
    public int countByUserId(Long userId);
}
