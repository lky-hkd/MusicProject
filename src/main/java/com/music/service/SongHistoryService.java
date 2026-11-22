package com.music.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.music.dto.SongHistoryDTO;
import com.music.entity.SongHistory;

import java.util.List;
import java.util.Map;

public interface SongHistoryService extends IService<SongHistory> {
    List<SongHistoryDTO> getUserHistory(Long userId);
    boolean deleteOne(Long userId,Long songId);
    boolean clearAll(Long userId);
    public int countHistoryByUserId(Long userId);
}
