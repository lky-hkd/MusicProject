package com.music.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.music.common.Result;
import com.music.entity.Song;
import com.music.service.AISearchService;
import com.music.service.SongService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/api/ai")

public class AISearchController {
    @Resource
    private SongService songService;
    @Resource
    private AISearchService aiSearchService;

    @PostMapping("/search")
    public Result<List<Song>> aiSearch(@RequestBody Map<String,String> params) throws Exception {
        String query = params.get("query");
        if (query == null || query.trim().isEmpty()) {
            return Result.fail("请输入搜索内容");
        }
        try {
            return Result.ok(aiSearchService.aiSearch(query.trim()));
        } catch (Exception e) {
            return Result.fail("AI 搜索服务暂时不可用");
        }
    }
}
