package com.music.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.music.dto.SongHistoryDTO;
import com.music.entity.SongHistory;
import com.music.mapper.SongHistoryMapper;
import com.music.service.SongHistoryService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class SongHistoryServiceImpl extends ServiceImpl<SongHistoryMapper, SongHistory> implements SongHistoryService {

    @Resource
    private SongHistoryMapper songHistoryMapper;

    @Override
    public List<SongHistoryDTO> getUserHistory(Long userId) {
        return songHistoryMapper.selectUserHistory(userId);
    }

    @Override
    public boolean deleteOne(Long userId, Long songId) {
        LambdaQueryWrapper<SongHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SongHistory::getUserId, userId)
                .eq(SongHistory::getSongId, songId);
        return remove(wrapper);
    }

    @Override
    public boolean clearAll(Long userId) {
        LambdaQueryWrapper<SongHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SongHistory::getUserId, userId);
        return remove(wrapper);
    }

    @Override
    public int countHistoryByUserId(Long userId) {
        return songHistoryMapper.countHistoryByUserId(userId);
    }
}
