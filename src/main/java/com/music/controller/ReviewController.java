package com.music.controller;

import com.music.common.CurrentUserId;
import com.music.common.Result;
import com.music.dto.ReviewCreateRequest;
import com.music.service.ReviewService;
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
 * 商品评价写入（需登录）
 */
@RestController
@RequestMapping("/api/review")
public class ReviewController {

    @Resource
    private ReviewService reviewService;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @PostMapping
    public Result<?> create(@RequestBody ReviewCreateRequest req, @CurrentUserId Long userId) {
        return Result.ok(reviewService.create(userId, req), "评价成功");
    }

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
        Path dir = Paths.get(uploadDir, "review").toAbsolutePath();
        Files.createDirectories(dir);
        String filename = UUID.randomUUID().toString().replace("-", "") + ext;
        file.transferTo(dir.resolve(filename).toFile());
        return Result.ok("/uploads/review/" + filename);
    }
}
