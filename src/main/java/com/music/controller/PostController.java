package com.music.controller;

import com.music.common.CurrentUserId;
import com.music.common.Result;
import com.music.common.UserContext;
import com.music.dto.CommentCreateRequest;
import com.music.dto.PostCreateRequest;
import com.music.service.PostService;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * 动态（微博式讨论发言）
 */
@RestController
@RequestMapping("/api/post")
public class PostController {

    @Resource
    private PostService postService;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    /** 动态流：square 广场 / following 关注 */
    @GetMapping("/feed")
    public Result<?> feed(@RequestParam(defaultValue = "square") String tab,
                          @RequestParam(required = false) Long lastId,
                          @RequestParam(defaultValue = "10") Integer size,
                          @CurrentUserId Long userId) {
        return Result.ok(postService.feed(userId, tab, lastId, size));
    }

    /** 动态详情 */
    @GetMapping("/{id}")
    public Result<?> detail(@PathVariable Long id, @CurrentUserId Long userId) {
        return Result.ok(postService.detail(userId, id));
    }

    /** 发布动态 */
    @PostMapping
    public Result<?> create(@RequestBody PostCreateRequest req, @CurrentUserId Long userId) {
        return Result.ok(postService.create(userId, req), "发布成功");
    }

    /** 删除动态：作者本人或管理员 */
    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id,
                            @CurrentUserId Long userId,
                            @RequestAttribute(value = UserContext.ROLE_ATTR, required = false) Integer role) {
        postService.delete(userId, role == null ? 0 : role, id);
        return Result.ok("success", "已删除");
    }

    /** 点赞 */
    @PostMapping("/{id}/like")
    public Result<?> like(@PathVariable Long id, @CurrentUserId Long userId) {
        postService.like(userId, id);
        return Result.ok("success");
    }

    /** 取消点赞 */
    @DeleteMapping("/{id}/like")
    public Result<?> unlike(@PathVariable Long id, @CurrentUserId Long userId) {
        postService.unlike(userId, id);
        return Result.ok("success");
    }

    /** 评论列表 */
    @GetMapping("/{id}/comments")
    public Result<?> comments(@PathVariable Long id,
                              @RequestParam(required = false) Long lastId,
                              @RequestParam(defaultValue = "20") Integer size,
                              @CurrentUserId Long userId) {
        return Result.ok(postService.comments(userId, id, lastId, size));
    }

    /** 发表评论 / 回复 */
    @PostMapping("/{id}/comments")
    public Result<?> addComment(@PathVariable Long id,
                                @RequestBody CommentCreateRequest req,
                                @CurrentUserId Long userId) {
        return Result.ok(postService.addComment(userId, id, req), "评论成功");
    }

    /** 发帖图片上传 */
    @PostMapping("/upload")
    public Result<?> upload(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) throw new RuntimeException("请选择图片");
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new RuntimeException("只支持图片文件");
        }
        if (file.getSize() > 5 * 1024 * 1024) throw new RuntimeException("图片不能超过5MB");

        String ext = ".png";
        String original = file.getOriginalFilename();
        if (original != null && original.contains(".")) {
            ext = original.substring(original.lastIndexOf('.')).toLowerCase();
        }
        Path dir = Paths.get(uploadDir, "post").toAbsolutePath();
        Files.createDirectories(dir);
        String filename = UUID.randomUUID().toString().replace("-", "") + ext;
        file.transferTo(dir.resolve(filename).toFile());
        return Result.ok("/uploads/post/" + filename);
    }
}
