package com.music.controller;

import com.music.common.Result;
import com.music.service.ReviewService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

/**
 * 商品评价读取接口（公开，不需要登录）
 * 挂在 /api/product/** 下，复用免登录规则
 */
@RestController
@RequestMapping("/api/product")
public class ProductReviewController {

    @Resource
    private ReviewService reviewService;

    @GetMapping("/reviews")
    public Result<?> reviews(@RequestParam Long productId,
                             @RequestParam(defaultValue = "1") Integer page,
                             @RequestParam(defaultValue = "10") Integer size) {
        return Result.ok(reviewService.productReviews(productId, page, size));
    }

    @GetMapping("/review-summary")
    public Result<?> summary(@RequestParam Long productId) {
        return Result.ok(reviewService.summary(productId));
    }
}
