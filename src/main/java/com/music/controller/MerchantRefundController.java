package com.music.controller;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.music.common.*;
import com.music.dto.MerchantRefundQuery;
import com.music.dto.MerchantRefundReviewRequest;
import com.music.entity.Order;
import com.music.entity.OrderItem;
import com.music.entity.RefundOrder;
import com.music.mapper.OrderItemMapper;
import com.music.mapper.OrderMapper;
import com.music.mapper.RefundOrderMapper;
import com.music.service.RefundService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

//商家 - 退款审核
@RestController
@RequestMapping("/api/merchant/refund")
public class MerchantRefundController {
    @Resource
    private MerchantContext merchantContext;
    @Resource
    private RefundService refundService;

    @Resource
    private RefundOrderMapper refundOrderMapper;

    @Resource
    OrderItemMapper orderItemMapper;
    @Resource
    private OrderMapper orderMapper;

    //我的退款单列表
    @PostMapping("/list")
    public Result<?> list(@RequestAttribute(UserContext.USER_ID_ATTR) Long userId,
                          @RequestBody(required = false)MerchantRefundQuery query){
        Long merchantId = merchantContext.requireMerchantId(userId);
        int page = (query == null || query.getPage() == null) ? 1 : query.getPage();
        int size = (query == null || query.getSize() == null) ? 10 : query.getSize();

        LambdaQueryWrapper<RefundOrder> qw = new LambdaQueryWrapper<>();
        qw.inSql(RefundOrder::getOrderId,
                "SELECT DISTINCT order_id FROM order_item WHERE merchant_id = " + merchantId);
        if (query != null && query.getStatus() != null) {
            qw.eq(RefundOrder::getStatus, query.getStatus());
        }
        qw.orderByDesc(RefundOrder::getCreateTime);

        Page<RefundOrder> result = refundOrderMapper.selectPage(new Page<>(page, size), qw);
        return Result.ok(result);
    }

    //审核退款（同意走支付宝退款；拒绝只改状态）
    @PostMapping("/review")
    public Result<?> review(@RequestBody MerchantRefundReviewRequest req,
                            @RequestAttribute(UserContext.USER_ID_ATTR) Long userId) {
        Long merchantId = merchantContext.requireMerchantId(userId);
        if (req.getRefundId() == null) throw new BizException("退款单 id 不能为空");
        if (req.getApprove() == null) throw new BizException("请选择处理结果");

        RefundOrder ro = refundOrderMapper.selectById(req.getRefundId());
        if(ro == null ) throw new BizException("退款单不存在");
        // 归属校验：该退款单对应的订单必须含本店商品
        Order order = orderMapper.selectById(ro.getOrderId());
        if (order == null) throw new BizException("订单不存在");
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, order.getId()));
        boolean mine = items.stream().anyMatch(i -> merchantId.equals(i.getMerchantId()));
        if (!mine) throw new BizException("无权处理此退款单");

        //状态校验：只能处理"申请中"
        if (ro.getStatus() == null || ro.getStatus() != RefundStatus.APPLYING) {
            throw new BizException("该退款单已处理，请勿重复操作");
        }
        if (Boolean.TRUE.equals(req.getApprove())) {
            refundService.approve(ro.getId());
            return Result.ok(null, "已同意退款");
        } else {
            if (req.getRejectReason() == null || req.getRejectReason().trim().isEmpty()) {
                throw new BizException("拒绝退款时必须填写理由");
            }
            refundService.reject(ro.getId(), req.getRejectReason().trim());
            return Result.ok(null, "已拒绝退款");
        }
    }
}
