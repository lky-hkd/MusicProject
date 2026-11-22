package com.music.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.music.common.Result;
import com.music.entity.RefundOrder;
import com.music.mapper.RefundOrderMapper;
import com.music.service.RefundService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/refund")
public class AdminRefundController {

    @Resource
    private RefundService refundService;

    @Resource
    private RefundOrderMapper refundOrderMapper;

    /**
     * 退款单列表
     * POST /api/admin/refund/list
     * body: { "page":1, "size":10, "keyword":"订单号/退款单号", "status":1 }
     */
    @PostMapping("/list")
    public Result<?> list(@RequestBody(required = false) Map<String, Object> body) {
        int page = body != null && body.get("page") != null ? ((Number) body.get("page")).intValue() : 1;
        int size = body != null && body.get("size") != null ? ((Number) body.get("size")).intValue() : 10;

        LambdaQueryWrapper<RefundOrder> qw = new LambdaQueryWrapper<>();
        if (body != null && body.get("keyword") != null) {
            String kw = String.valueOf(body.get("keyword")).trim();
            if (!kw.isEmpty()) {
                qw.and(w -> w.like(RefundOrder::getOrderNo, kw)
                        .or().like(RefundOrder::getRefundNo, kw));
            }
        }
        if (body != null && body.get("status") != null) {
            qw.eq(RefundOrder::getStatus, ((Number) body.get("status")).intValue());
        }
        qw.orderByDesc(RefundOrder::getCreateTime);

        return Result.ok(refundOrderMapper.selectPage(new Page<>(page, size), qw));
    }

    /** 审核通过并发起退款 */
    @PostMapping("/approve")
    public Result<?> approve(@RequestParam Long refundId) {
        refundService.approve(refundId);
        return Result.ok(null, "退款已完成");
    }

    /** 审核拒绝 */
    @PostMapping("/reject")
    public Result<?> reject(@RequestParam Long refundId,
                            @RequestParam String reason) {
        refundService.reject(refundId, reason);
        return Result.ok(null, "已拒绝退款");
    }
}