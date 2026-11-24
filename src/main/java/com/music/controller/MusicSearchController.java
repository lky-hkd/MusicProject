package com.music.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.music.entity.Song;
import com.music.service.AISearchService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin // 开发环境开启跨域，本地前端访问不会CORS报错
public class MusicSearchController {

    @Resource
    private AISearchService aiSearchService;

    @PostMapping("/searchLyric")
    @ResponseBody
    public Map<String,Object> searchLyric(@RequestBody Map<String,String> param){
        Map<String,Object> resp = new HashMap<>();
        try{
            String query = param.get("query");
            List<Song> songList = aiSearchService.aiSearch(query);
            resp.put("success", true);
            resp.put("data", songList);
        }catch (Exception e){
            e.printStackTrace();
            resp.put("success",false);
            resp.put("msg","搜索失败："+e.getMessage());
        }
        return resp;
    }

}
