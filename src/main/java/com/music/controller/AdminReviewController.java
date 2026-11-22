package com.music.controller;

import com.music.common.Result;
import com.music.service.ReviewService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

/**
 * 管理员隐藏违规评价
 */
@RestController
@RequestMapping("/api/admin/review")
public class AdminReviewController {

    @Resource
    private ReviewService reviewService;

    @DeleteMapping("/{id}")
    public Result<?> hide(@PathVariable Long id) {
        reviewService.hide(id);
        return Result.ok("success", "已隐藏");
    }
}
