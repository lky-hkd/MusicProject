package com.music.controller;

import com.music.common.Result;
import com.music.common.UserContext;
import com.music.service.CouponService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * 优惠券 / 活动中心
 */
@RestController
@RequestMapping("/api/coupon")
public class CouponController {

    @Resource
    private CouponService couponService;

    /** 活动中心列表（未登录也可浏览） */
    @GetMapping("/activity")
    public Result<?> activity(@RequestAttribute(value = UserContext.USER_ID_ATTR, required = false) Long userId) {
        return Result.ok(couponService.listActivity(userId));
    }

    /** 领取普通券 */
    @PostMapping("/receive/{voucherId}")
    public Result<?> receive(@PathVariable("voucherId") Long voucherId,
                             @RequestAttribute(UserContext.USER_ID_ATTR) Long userId) {
        couponService.receiveNormal(voucherId, userId);
        return Result.ok(null, "领取成功");
    }

    /** 秒杀券抢购 */
    @PostMapping("/seckill/{voucherId}")
    public Result<?> seckill(@PathVariable("voucherId") Long voucherId,
                             @RequestAttribute(UserContext.USER_ID_ATTR) Long userId) {
        couponService.seckill(voucherId, userId);
        return Result.ok(null, "抢购成功");
    }

    /** 我的优惠券 */
    @GetMapping("/my")
    public Result<?> my(@RequestAttribute(UserContext.USER_ID_ATTR) Long userId) {
        return Result.ok(couponService.myCoupons(userId));
    }

    /** 当前订单金额可用的优惠券 */
    @GetMapping("/available")
    public Result<?> available(@RequestParam(value = "amount", defaultValue = "0") BigDecimal amount,
                               @RequestAttribute(UserContext.USER_ID_ATTR) Long userId) {
        return Result.ok(couponService.availableForOrder(userId, amount));
    }
}
