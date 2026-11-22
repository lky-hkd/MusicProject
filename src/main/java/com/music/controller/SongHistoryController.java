package com.music.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.music.common.Result;
import com.music.common.UserContext;
import com.music.dto.SongHistoryDTO;
import com.music.entity.SongHistory;
import com.music.service.SongHistoryService;
import com.music.service.UserCollectService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/song_history")
public class SongHistoryController {

    @Resource
    private SongHistoryService songHistoryService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // GET /api/song_history/list 查询播放历史
    @GetMapping("/list")
    public Result<List<SongHistoryDTO>> list(@RequestAttribute(UserContext.USER_ID_ATTR) Long userId) {
       List<SongHistoryDTO> data = songHistoryService.getUserHistory(userId);
       return Result.ok(data);
    }
//POST /api/song_history/delete
    @PostMapping("/delete")
    public Result<?> delete(@RequestParam Long songId,
                            @RequestAttribute(UserContext.USER_ID_ATTR) Long userId) {
        boolean ok=songHistoryService.deleteOne(userId,songId);
        return ok ? Result.ok(null,"删除成功") : Result.fail("删除失败");
    }
//   POST /api/song_history/clearAll
    @PostMapping("/clearAll")
    public Result<?> clearAll(@RequestAttribute(UserContext.USER_ID_ATTR) Long userId) {
        boolean ok=songHistoryService.clearAll(userId);
        return ok ? Result.ok(null,"清空成功") : Result.fail("清空失败");
    }

//    POST /api/song_history/add 添加播放历史（播放歌曲时调用）
    @PostMapping("/add")
    public Result<?> addSongHistory(
            @RequestParam Long songId,
            @RequestAttribute(UserContext.USER_ID_ATTR) Long userId){

        LambdaQueryWrapper<SongHistory> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SongHistory::getSongId,songId);
        SongHistory oldRecord = songHistoryService.getOne(queryWrapper);
        if(oldRecord != null){
            oldRecord.setPlayTime(new java.util.Date());
            songHistoryService.updateById(oldRecord);
        }else{
            SongHistory songHistory = new SongHistory();
            songHistory.setUserId(userId);
            songHistory.setSongId(songId);
            songHistory.setPlayTime(new java.util.Date());
            songHistoryService.save(songHistory);
        }
        return Result.ok(null,"记录播放成功");
    }

    @GetMapping("/count")
    public Result<Long> count(@RequestAttribute(UserContext.USER_ID_ATTR) Long userId) {

        int count = songHistoryService.countHistoryByUserId(userId);
        return Result.ok((long) count);
    }

}
