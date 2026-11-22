package com.music.service.Impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.music.entity.Song;
import com.music.entity.UserCollect;
import com.music.mapper.UserCollectMapper;
import com.music.service.UserCollectService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
public class UserCollectServiceImpl extends ServiceImpl<UserCollectMapper, UserCollect> implements UserCollectService {

    @Resource
    private UserCollectMapper userCollectMapper;

    @Override
    public boolean isCollect(Long userId, Long songId) {
        if(userId == null || songId == null) return false;
        return userCollectMapper.countCollect(userId, songId) > 0;
    }

    @Override
    public boolean addCollect(Long userId, Long songId) {
        if(userId == null || songId == null) return false;
        // 先判断是否已收藏，避免数据库唯一索引报错
        if(isCollect(userId, songId)){
            return false;
        }
        UserCollect collect = new UserCollect();
        collect.setUserId(userId);
        collect.setSongId(songId);
        return save(collect);
    }

    @Override
    public boolean cancelCollect(Long userId, Long songId) {
        if(userId == null || songId == null) return false;
        return userCollectMapper.deleteCollect(userId, songId) > 0;
    }

    @Override
    public List<Song> getCollectSongList(Long userId) {
        // 先查用户收藏记录里所有songId，再批量查询歌曲信息
        return userCollectMapper.selectCollectSongsByUserId(userId);
    }

    @Override
    public int countByUserId(Long userId) {
        return userCollectMapper.countCollectSongsByUserId(userId);
    }
}
