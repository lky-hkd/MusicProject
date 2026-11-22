package com.music.controller;

import com.music.common.CurrentUserId;
import com.music.common.Result;
import com.music.service.FollowService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/follow")
@RequiredArgsConstructor
public class FollowController {

    private final FollowService followService;

    // 从登录态中取当前用户 ID，不要从请求参数里信任客户端传入的 followerId
    @PostMapping("/{followeeId}")
    public Result<String> follow(@PathVariable Long followeeId, @CurrentUserId Long currentUserId) {
        followService.follow(currentUserId, followeeId);
        return Result.ok("success");
    }

    @DeleteMapping("/{followeeId}")
    public Result<String> unfollow(@PathVariable Long followeeId, @CurrentUserId Long currentUserId) {
        followService.unfollow(currentUserId, followeeId);
        return Result.ok("success");
    }

    @GetMapping("/following")
    public Result<?> following(@RequestParam(defaultValue = "1") int page,
                               @RequestParam(defaultValue = "20") int size,
                               @CurrentUserId Long currentUserId) {
        return Result.ok(followService.listFollowing(currentUserId, page, size));
    }

    @GetMapping("/followers")
    public Result<?> followers(@RequestParam(defaultValue = "1") int page,
                               @RequestParam(defaultValue = "20") int size,
                               @CurrentUserId Long currentUserId) {
        return Result.ok(followService.listFollowers(currentUserId, page, size));
    }

    /** 与某个用户的关注关系（用于聊天页的关注按钮） */
    @GetMapping("/status/{userId}")
    public Result<?> status(@PathVariable Long userId, @CurrentUserId Long currentUserId) {
        return Result.ok(followService.status(currentUserId, userId));
    }
}
