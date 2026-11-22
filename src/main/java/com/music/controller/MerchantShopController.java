package com.music.controller;

import com.music.common.MerchantContext;
import com.music.common.Result;
import com.music.common.UserContext;
import com.music.dto.MerchantShopUpdateRequest;
import com.music.entity.Merchant;
import com.music.service.MerchantService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

/**
 * 商家 - 店铺管理
 * 前缀 /api/merchant/** 已由 AuthInterceptor 校验 role >= MERCHANT
 */
@RestController
@RequestMapping("/api/merchant/shop")
public class MerchantShopController {

    @Resource
    private MerchantService merchantService;

    @Resource
    MerchantContext merchantContext;

    //店铺信息
    @PostMapping("/info")
    public Result<?> info(@RequestAttribute(UserContext.USER_ID_ATTR) Long userId) {
        Merchant m = merchantContext.requireMerchant(userId);
        return Result.ok(m);
    }

    //编辑店铺信息
    @PostMapping("/update")
    public Result<?> updateshopinfo(@RequestBody MerchantShopUpdateRequest req,
                                    @RequestAttribute(UserContext.USER_ID_ATTR) Long userId) {
        Merchant m = merchantService.updateShop(userId,req);
        return Result.ok(m, "店铺信息已更新");
    }
}
