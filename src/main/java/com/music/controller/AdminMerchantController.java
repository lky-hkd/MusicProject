package com.music.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.music.common.BizException;
import com.music.common.Result;
import com.music.entity.Merchant;
import com.music.mapper.MerchantMapper;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 管理端 - 店铺管理
 * 前缀 /api/admin/** 已由 AuthInterceptor 校验 role >= ADMIN
 */
@RestController
@RequestMapping("/api/admin/merchant")
public class AdminMerchantController {

    @Resource
    private MerchantMapper merchantMapper;

    /**
     * 店铺列表
     * POST /api/admin/merchant/list
     * body: { "page":1, "size":10, "keyword":"店名", "status":1 }
     */
    @PostMapping("/list")
    public Result<?> list(@RequestBody(required = false) Map<String, Object> body) {
        int page = body != null && body.get("page") != null ? ((Number) body.get("page")).intValue() : 1;
        int size = body != null && body.get("size") != null ? ((Number) body.get("size")).intValue() : 10;

        LambdaQueryWrapper<Merchant> qw = new LambdaQueryWrapper<>();
        if (body != null && body.get("keyword") != null) {
            String kw = String.valueOf(body.get("keyword")).trim();
            if (!kw.isEmpty()) qw.like(Merchant::getShopName, kw);
        }
        if (body != null && body.get("status") != null) {
            qw.eq(Merchant::getStatus, ((Number) body.get("status")).intValue());
        }
        qw.orderByDesc(Merchant::getCreateTime);

        return Result.ok(merchantMapper.selectPage(new Page<>(page, size), qw));
    }

    /**
     * 启用 / 禁用店铺
     * POST /api/admin/merchant/status
     * body: { "id":1, "status":0 }
     */
    @PostMapping("/status")
    public Result<?> changeStatus(@RequestBody Map<String, Object> body) {
        if (body == null || body.get("id") == null) throw new BizException("店铺 id 不能为空");
        if (body.get("status") == null) throw new BizException("状态不能为空");

        Long id = ((Number) body.get("id")).longValue();
        int status = ((Number) body.get("status")).intValue();
        if (status != 0 && status != 1) throw new BizException("非法状态值");

        int ok = merchantMapper.update(null, new LambdaUpdateWrapper<Merchant>()
                .eq(Merchant::getId, id)
                .set(Merchant::getStatus, status)
                .set(Merchant::getUpdateTime, LocalDateTime.now()));
        if (ok == 0) throw new BizException("店铺不存在或状态未变更");

        return Result.ok(null, status == 1 ? "已启用" : "已禁用");
    }
}