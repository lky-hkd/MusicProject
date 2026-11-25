package com.music.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.music.common.Result;
import com.music.entity.Song;
import com.music.service.SongService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;

/**
 * 后台音乐信息管理：列表 / 新增 / 修改 / 上下架。
 * 路径以 /api/admin 开头，由鉴权拦截器统一校验管理员角色。
 */
@RestController
@RequestMapping("/api/admin/song")
public class AdminSongController {

    @Resource
    private SongService songService;

    @PostMapping("list")
    public Result<?> list(@RequestBody(required = false) SongQuery query) {
        int page = query == null || query.getPage() == null ? 1 : Math.max(1, query.getPage());
        int size = query == null || query.getSize() == null ? 10 : Math.min(Math.max(1, query.getSize()), 100);

        LambdaQueryWrapper<Song> wrapper = new LambdaQueryWrapper<>();
        String keyword = query == null ? null : query.getKeyword();
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(Song::getSongName, kw).or().like(Song::getSinger, kw));
        }
        if (query != null && query.getStatus() != null) {
            wrapper.eq(Song::getStatus, query.getStatus());
        }
        wrapper.orderByDesc(Song::getId);
        return Result.ok(songService.page(new Page<>(page, size), wrapper));
    }

    /** 新增或修改：有 id 为修改，无 id 为新增 */
    @PostMapping("save")
    public Result<?> save(@RequestBody Song song) {
        if (song == null) {
            return Result.fail("参数错误");
        }
        if (song.getSongName() == null || song.getSongName().isBlank()) {
            return Result.fail("歌曲名不能为空");
        }
        if (song.getSinger() == null || song.getSinger().isBlank()) {
            return Result.fail("歌手不能为空");
        }
        song.setSongName(song.getSongName().trim());
        song.setSinger(song.getSinger().trim());
        if (song.getStatus() == null) {
            song.setStatus(0);
        }
        if (song.getIsVip() == null) {
            song.setIsVip(0);
        }
        if (song.getId() == null) {
            song.setPlayCount(0);
            song.setCreateTime(new Date());
            songService.save(song);
        } else {
            // 播放量和创建时间不允许被表单覆盖
            song.setPlayCount(null);
            song.setCreateTime(null);
            if (songService.getById(song.getId()) == null) {
                return Result.fail("歌曲不存在");
            }
            songService.updateById(song);
        }
        return Result.ok(song.getId());
    }

    /** 上下架：0 上架 / 1 下架 */
    @PostMapping("status")
    public Result<?> status(@RequestParam Long id, @RequestParam Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            return Result.fail("状态只能是 0（上架）或 1（下架）");
        }
        if (songService.getById(id) == null) {
            return Result.fail("歌曲不存在");
        }
        Song update = new Song();
        update.setId(id);
        update.setStatus(status);
        songService.updateById(update);
        return Result.ok(Boolean.TRUE);
    }

    public static class SongQuery {
        private Integer page;
        private Integer size;
        private String keyword;
        private Integer status;

        public Integer getPage() { return page; }
        public void setPage(Integer page) { this.page = page; }
        public Integer getSize() { return size; }
        public void setSize(Integer size) { this.size = size; }
        public String getKeyword() { return keyword; }
        public void setKeyword(String keyword) { this.keyword = keyword; }
        public Integer getStatus() { return status; }
        public void setStatus(Integer status) { this.status = status; }
    }
}
