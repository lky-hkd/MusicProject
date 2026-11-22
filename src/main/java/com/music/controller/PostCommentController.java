package com.music.controller;

import com.music.common.CurrentUserId;
import com.music.common.Result;
import com.music.common.UserContext;
import com.music.service.PostService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

/**
 * 动态评论的删除（评论创建在 PostController）
 */
@RestController
@RequestMapping("/api/comment")
public class PostCommentController {

    @Resource
    private PostService postService;

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id,
                            @CurrentUserId Long userId,
                            @RequestAttribute(value = UserContext.ROLE_ATTR, required = false) Integer role) {
        postService.deleteComment(userId, role == null ? 0 : role, id);
        return Result.ok("success", "已删除");
    }
}
