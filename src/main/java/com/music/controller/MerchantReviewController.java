package com.music.controller;

import com.music.common.CurrentUserId;
import com.music.common.Result;
import com.music.dto.ReviewReplyRequest;
import com.music.service.ReviewService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

/**
 * 商家评价管理（/api/merchant/** 已由 AuthInterceptor 校验 role >= MERCHANT）
 */
@RestController
@RequestMapping("/api/merchant/review")
public class MerchantReviewController {

    @Resource
    private ReviewService reviewService;

    @GetMapping("/list")
    public Result<?> list(@RequestParam(defaultValue = "1") Integer page,
                          @RequestParam(defaultValue = "10") Integer size,
                          @CurrentUserId Long userId) {
        return Result.ok(reviewService.merchantReviews(userId, page, size));
    }

    @PostMapping("/{id}/reply")
    public Result<?> reply(@PathVariable Long id,
                           @RequestBody ReviewReplyRequest req,
                           @CurrentUserId Long userId) {
        reviewService.merchantReply(userId, id, req == null ? null : req.getContent());
        return Result.ok("success", "回复成功");
    }
}
