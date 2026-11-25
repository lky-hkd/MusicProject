package com.music.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.music.entity.Song;
import com.music.service.SongService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/song")
public class SongController {
    @Resource
    private SongService songService;

    // 获取热门歌曲
    @GetMapping("/hot")
    public Map<String, Object> getHotSong() {
        Map<String, Object> result = new HashMap<>();
        try {
            List<Song> list = songService.list(new LambdaQueryWrapper<Song>()
                    .eq(Song::getStatus, 0));
            result.put("success", true);
            result.put("data", list);
        } catch (Exception e) {
            result.put("success", false);
            result.put("msg", "查询热门歌曲失败");
        }
        return result;
    }

    /*
     * 歌曲排行榜：按播放量playCount降序，默认取前10条
     * */
    @GetMapping("/rank")
    public Map<String, Object> getSongRank(@RequestParam(defaultValue = "10") Integer limit) {
        Map<String, Object> result = new HashMap<>();
        try {
            // 简单参数校验，防止过大limit
            if (limit < 1 || limit > 50) {
                limit = 10;
            }
            LambdaQueryWrapper<Song> wrapper = new LambdaQueryWrapper<>();
            // 已下架歌曲不参与排行
            wrapper.eq(Song::getStatus, 0);
            // 按播放量降序
            wrapper.orderByDesc(Song::getPlayCount);
            // 使用分页安全替代 last 字符串拼接
            Page<Song> page = new Page<>(1, limit);
            List<Song> list = songService.page(page, wrapper).getRecords();
            result.put("success", true);
            result.put("data", list);
        } catch (Exception e) {
            result.put("success", false);
            result.put("msg", "加载排行榜失败");
        }
        return result;
    }

    /**
     * 根据音乐风格type查询歌曲
     * @param type 音乐分类：流行 / 国风 / 说唱 / 摇滚
     */
    @GetMapping("/type")
    public Map<String, Object> getSongType(@RequestParam String type) {
        Map<String, Object> result = new HashMap<>();
        try {
            LambdaQueryWrapper<Song> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Song::getType, type).eq(Song::getStatus, 0);
            List<Song> list = songService.list(wrapper);
            result.put("success", true);
            result.put("data", list);
        } catch (Exception e) {
            result.put("success", false);
            result.put("msg", "加载分类歌曲失败");
        }
        return result;
    }

    /**
     * 获取所有歌曲分类（用于前端渲染分类标签）
     */
    @GetMapping("/type/list")
    public Map<String, Object> getAllSongType() {
        Map<String, Object> result = new HashMap<>();
        try {
            List<String> typeList = songService.getAllType();
            result.put("success", true);
            result.put("data", typeList);
        } catch (Exception e) {
            result.put("success", false);
            result.put("msg", "查询曲风列表失败");
        }
        return result;
    }

    // ========== 根据id查询歌曲详情 ==========
    @GetMapping("/getById")
    public Map<String, Object> getSongById(@RequestParam Long id) {
        Map<String, Object> result = new HashMap<>();
        try {
            Song song = songService.getById(id);
            if (song == null || Integer.valueOf(1).equals(song.getStatus())) {
                result.put("success", false);
                result.put("msg", "该歌曲不存在");
                return result;
            }
            result.put("success", true);
            result.put("data", song);
        } catch (Exception e) {
            result.put("success", false);
            result.put("msg", "查询歌曲详情失败");
        }
        return result;
    }

    // ========== 传统数据库模糊搜索接口 ==========
    @GetMapping("/search")
    public Map<String, Object> searchSong(@RequestParam String keyword) {
        Map<String, Object> result = new HashMap<>();
        try {
            LambdaQueryWrapper<Song> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Song::getStatus, 0)
                    .and(w -> w.like(Song::getSongName, keyword)
                            .or()
                            .like(Song::getSinger, keyword));
            List<Song> list = songService.list(wrapper);
            result.put("success", true);
            result.put("data", list);
        } catch (Exception e) {
            result.put("success", false);
            result.put("msg", "普通搜索失败");
        }
        return result;
    }

    /**
     * 歌曲播放计数 +1
     * @param id 歌曲id
     */
    @GetMapping("/addPlayCount")
    public Map<String, Object> addPlayCount(@RequestParam Long id){
        Map<String,Object> result = new HashMap<>();
        try {
            boolean success = songService.update(
                    new LambdaUpdateWrapper<Song>()
                            .eq(Song::getId, id)
                            .setSql("play_count = play_count + 1")
            );
            result.put("success",success);
            if(!success){
                result.put("msg","更新失败，无此歌曲");
            }
        }catch (Exception e){
            e.printStackTrace();
            result.put("success",false);
            result.put("msg","更新播放量失败");
        }
        return result;
    }
}
