package com.music.controller;

import com.music.common.Result;
import com.music.common.UserContext;
import com.music.service.Impl.UserCollectServiceImpl;
import com.music.service.UserCollectService;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/collect")
public class CollectController {

    @Resource
    private UserCollectService userCollectService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 查询当前用户是否收藏该歌曲
     * GET /api/collect/isCollect?songId=xxx
     */
    @GetMapping("/isCollect")
    public Result<Boolean> isCollect(
            @RequestParam Long songId,
            @RequestAttribute(UserContext.USER_ID_ATTR) Long userId
    ) {
        boolean collected = userCollectService.isCollect(userId, songId);
        return Result.ok(collected);
    }

    @GetMapping("/count")
    public Result<Long> count(@RequestAttribute(UserContext.USER_ID_ATTR) Long userId) {

        int count = userCollectService.countByUserId(userId);
        return Result.ok((long) count);
    }

    /**
     * 添加收藏
     * POST /api/collect/add?songId=xxx
     */
    @PostMapping("/add")
    public Result<?> addCollect(
            @RequestParam Long songId,
            @RequestAttribute(UserContext.USER_ID_ATTR) Long userId
    ) {
        boolean success = userCollectService.addCollect(userId, songId);
        if (success) {
            return Result.ok(null, "收藏成功");
        } else {
            return Result.fail("收藏失败，可能已经收藏过了");
        }
    }

    /**
     * 取消收藏
     * POST /api/collect/cancel?songId=xxx
     */
    @PostMapping("/cancel")
    public Result<?> cancelCollect(
            @RequestParam Long songId,
            @RequestAttribute(UserContext.USER_ID_ATTR) Long userId
    ) {

        boolean success = userCollectService.cancelCollect(userId, songId);
        if (success) {
            return Result.ok(null, "取消收藏成功");
        } else {
            return Result.fail("取消收藏失败");
        }
    }

    /**
     * 获取当前用户收藏的歌曲列表
     * GET /api/collect/list
     */
    @GetMapping("/list")
    public Result<?> getCollectList(@RequestAttribute(UserContext.USER_ID_ATTR) Long userId) {
        // 调用service查询该用户收藏的歌曲集合
        return Result.ok(userCollectService.getCollectSongList(userId));
    }

}
