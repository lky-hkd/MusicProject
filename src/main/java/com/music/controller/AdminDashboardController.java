package com.music.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.music.common.RefundStatus;
import com.music.common.Result;
import com.music.entity.Merchant;
import com.music.entity.RefundOrder;
import com.music.mapper.MerchantMapper;
import com.music.mapper.RefundOrderMapper;
import com.music.service.OrderService;
import com.music.service.UserService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 管理端 - 数据看板
 * 前缀 /api/admin/** 已由 AuthInterceptor 校验 role >= ADMIN
 */
@RestController
@RequestMapping("/api/admin/dashboard")
public class AdminDashboardController {

    @Resource
    private UserService userService;

    @Resource
    private MerchantMapper merchantMapper;

    @Resource
    private OrderService orderService;

    @Resource
    private RefundOrderMapper refundOrderMapper;

    /**
     * 平台总览
     * POST /api/admin/dashboard/summary
     */
    @PostMapping("/summary")
    public Result<?> summary() {
        long totalUsers = userService.count();
        long totalMerchants = merchantMapper.selectCount(null);
        long enabledMerchants = merchantMapper.selectCount(
                new LambdaQueryWrapper<Merchant>().eq(Merchant::getStatus, 1));
        long totalOrders = orderService.count();
        long pendingRefund = refundOrderMapper.selectCount(
                new LambdaQueryWrapper<RefundOrder>().eq(RefundOrder::getStatus, RefundStatus.APPLYING));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalUsers", totalUsers);
        data.put("totalMerchants", totalMerchants);
        data.put("enabledMerchants", enabledMerchants);
        data.put("totalOrders", totalOrders);
        data.put("pendingRefund", pendingRefund);
        return Result.ok(data);
    }
}