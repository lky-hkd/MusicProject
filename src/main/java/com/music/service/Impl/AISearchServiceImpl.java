package com.music.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.music.entity.Song;
import com.music.mapper.SongMapper;
import com.music.service.AISearchService;
import com.music.util.LlmUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class AISearchServiceImpl implements AISearchService {

    @Resource
    private SongMapper songMapper;

    @Resource
    private LlmUtil llmUtil;

    @Override
    public List<Song> aiSearch(String userQuery) throws Exception {
        // 1. 入参校验：空查询直接返回空（不调用 LLM，也不降级）
        if (userQuery == null || userQuery.trim().isEmpty()) {
            return new ArrayList<>();
        }
        String query = userQuery.trim();
        String keyword;
        try {
            // 调用大模型提取关键词
            keyword = llmUtil.extractKeyword(query);
            log.info("【大模型提取关键词】：{}" , keyword);
        } catch (Exception e) {
            // LLM调用失败，降级：直接使用原始输入搜索
            log.warn("大模型调用/解析失败，降级用原始输入搜索：{}" , e.getMessage());
            keyword = query;
        }

        // 兜底：关键词为空，返回空集合，防止全表扫描
        if (keyword == null || keyword.trim().isEmpty()) {
            log.warn("关键词为空，返回空结果，避免全表扫描");
            return new ArrayList<>();
        }
        keyword = keyword.trim();

        // 数据库模糊查询：歌名、歌手、歌词
        final String finalKeyword = keyword;
        LambdaQueryWrapper<Song> wrapper = new LambdaQueryWrapper<>();
        // 把多个or条件打包到一组，防止后续新增条件破坏逻辑
        wrapper.and(wr -> wr.like(Song::getSongName, finalKeyword)
                .or().like(Song::getSinger, finalKeyword)
                .or().like(Song::getLyric, finalKeyword))
        ;

        return songMapper.selectList(wrapper);
    }
}
