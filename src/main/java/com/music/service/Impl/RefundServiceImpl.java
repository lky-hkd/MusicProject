package com.music.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.music.common.BizException;
import com.music.common.RefundStatus;
import com.music.entity.Order;
import com.music.entity.OrderItem;
import com.music.entity.RefundOrder;
import com.music.mapper.OrderItemMapper;
import com.music.mapper.OrderMapper;
import com.music.mapper.RefundOrderMapper;
import com.music.service.AlipayService;
import com.music.service.RefundService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static com.music.common.OrderStatus.*;

@Slf4j
@Service
public class RefundServiceImpl implements RefundService {

    @Autowired
    private RefundOrderMapper refundOrderMapper;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderItemMapper orderItemMapper;

    @Autowired
    private AlipayService alipayService;

    @Autowired
    private RedisStockService redisStockService;
    private static final BigDecimal AUTO_APPROVE_LIMIT = new BigDecimal("50");

    /** ============ 1. 用户申请退款 ============ */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RefundOrder apply(Long userId, String orderNo, String reason) {
        Order order = getOrderByNo(orderNo);
        if (order == null) throw new BizException("订单不存在");
        if (!order.getUserId().equals(userId)) throw new BizException("无权操作此订单");

        Integer status = order.getStatus();
        if (status == PENDING_PAY) throw new BizException("订单未支付，请直接取消");
        if (status == CANCELED || status == REFUNDED) throw new BizException("订单已取消或已退款");
        if (order.getRefundStatus() != null
                && (order.getRefundStatus() == RefundStatus.APPLYING
                || order.getRefundStatus() == RefundStatus.REFUNDING)) {
            throw new BizException("已有退款申请处理中，请勿重复提交");
        }

        // 虚拟商品付款后不可退
        List<OrderItem> items = listItems(order.getId());
        boolean hasPhysical = items.stream()
                .anyMatch(i -> "physical".equals(i.getProductType()));
        if (!hasPhysical) throw new BizException("虚拟商品不支持退款");

        // 创建退款单（申请中）
        RefundOrder r = new RefundOrder();
        r.setOrderId(order.getId());
        r.setOrderNo(orderNo);
        r.setUserId(userId);
        r.setRefundNo("R" + IdWorker.getId());
        r.setRefundAmount(order.getPayAmount());   // 只退实付
        r.setReason(reason);
        r.setStatus(RefundStatus.APPLYING);
        r.setCreateTime(LocalDateTime.now());
        r.setUpdateTime(LocalDateTime.now());
        refundOrderMapper.insert(r);

        // 订单打上退款标记
        updateOrderRefund(order.getId(), RefundStatus.APPLYING, status);
        log.info("用户申请退款: orderNo={}, refundNo={}", orderNo, r.getRefundNo());
        return r;
    }

    /** ============ 2. 审核通过并执行退款 ============ */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long refundId) {
        RefundOrder r = refundOrderMapper.selectById(refundId);
        if (r == null) throw new BizException("退款单不存在");
        if (r.getStatus() != RefundStatus.APPLYING) {
            throw new BizException("退款单状态不允许审核");
        }

        // 先置“退款中”，防并发重复审核
        LambdaUpdateWrapper<RefundOrder> uw = new LambdaUpdateWrapper<>();
        uw.set(RefundOrder::getStatus, RefundStatus.REFUNDING);
        uw.set(RefundOrder::getUpdateTime, LocalDateTime.now());
        uw.eq(RefundOrder::getId, refundId);
        uw.eq(RefundOrder::getStatus, RefundStatus.APPLYING);
        if (refundOrderMapper.update(null, uw) != 1) {
            throw new BizException("退款单状态已变更，请刷新");
        }

        Order order = getOrderByNo(r.getOrderNo());
        boolean refundOk;
        if ("alipay".equals(order.getPayType()) && order.getTradeNo() != null) {
            refundOk = alipayService.refund(
                    order.getPayNo(), r.getRefundNo(),
                    r.getRefundAmount(), r.getReason());
        } else {
            refundOk = true;   // 模拟支付直接成功
        }

        if (!refundOk) {
            // 退款失败：退回到申请中，允许重试（或置失败由人工处理）
            markRefundFailed(refundId);
            throw new BizException("支付宝退款失败，请稍后重试");
        }

        // 退款成功：更新退款单 + 订单 + 回滚库存
        markRefundSuccess(r, order);
    }

    /** ============ 3. 审核拒绝 ============ */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long refundId, String rejectReason) {
        RefundOrder r = refundOrderMapper.selectById(refundId);
        if (r == null) throw new BizException("退款单不存在");
        if (r.getStatus() != RefundStatus.APPLYING) {
            throw new BizException("退款单状态不允许拒绝");
        }
        LambdaUpdateWrapper<RefundOrder> uw = new LambdaUpdateWrapper<>();
        uw.set(RefundOrder::getStatus, RefundStatus.REJECTED);
        uw.set(RefundOrder::getRejectReason, rejectReason);
        uw.set(RefundOrder::getUpdateTime, LocalDateTime.now());
        uw.eq(RefundOrder::getId, refundId);
        uw.eq(RefundOrder::getStatus, RefundStatus.APPLYING);
        refundOrderMapper.update(null, uw);

        // 订单退款标记也置拒绝（0 无退款可再申请，这里用具 5 拒绝表示）
        LambdaUpdateWrapper<Order> ouw = new LambdaUpdateWrapper<>();
        ouw.set(Order::getRefundStatus, RefundStatus.REJECTED);
        ouw.set(Order::getUpdateTime, LocalDateTime.now());
        ouw.eq(Order::getId, r.getOrderId());
        ouw.in(Order::getStatus, PAID, SHIPPED);
        orderMapper.update(null, ouw);
        log.info("退款单被拒绝: {}", r.getRefundNo());
    }

    // ================= 私有方法 =================

    private void markRefundSuccess(RefundOrder r, Order order) {
        LambdaUpdateWrapper<RefundOrder> ruw = new LambdaUpdateWrapper<>();
        ruw.set(RefundOrder::getStatus, RefundStatus.SUCCESS);
        ruw.set(RefundOrder::getUpdateTime, LocalDateTime.now());
        ruw.eq(RefundOrder::getId, r.getId());
        refundOrderMapper.update(null, ruw);

        // 订单状态置 5 已退款 + 退款信息
        LambdaUpdateWrapper<Order> ouw = new LambdaUpdateWrapper<>();
        ouw.set(Order::getStatus, REFUNDED);
        ouw.set(Order::getRefundStatus, RefundStatus.SUCCESS);
        ouw.set(Order::getRefundAmount, r.getRefundAmount());
        ouw.set(Order::getRefundTime, LocalDateTime.now());
        ouw.set(Order::getUpdateTime, LocalDateTime.now());
        ouw.eq(Order::getId, order.getId());
        orderMapper.update(null, ouw);

        redisStockService.rollbackOrderStockIdempotent(order.getId(), listItems(order.getId()));
        log.info("退款成功: orderNo={}, refundNo={}", order.getOrderNo(), r.getRefundNo());
    }

    private void markRefundFailed(Long refundId) {
        LambdaUpdateWrapper<RefundOrder> uw = new LambdaUpdateWrapper<>();
        uw.set(RefundOrder::getStatus, RefundStatus.FAILED);
        uw.set(RefundOrder::getUpdateTime, LocalDateTime.now());
        uw.eq(RefundOrder::getId, refundId);
        refundOrderMapper.update(null, uw);
    }

    private void updateOrderRefund(Long orderId, int refundStatus, int expectStatus) {
        LambdaUpdateWrapper<Order> ouw = new LambdaUpdateWrapper<>();
        ouw.set(Order::getRefundStatus, refundStatus);
        ouw.set(Order::getUpdateTime, LocalDateTime.now());
        ouw.eq(Order::getId, orderId);
        ouw.eq(Order::getStatus, expectStatus);   // CAS 防并发
        // 关键：只有“当前无退款/已结束”时才能置为申请中，防并发重复申请
        ouw.and(w -> w.isNull(Order::getRefundStatus)
                .or().in(Order::getRefundStatus,
                        RefundStatus.SUCCESS, RefundStatus.REJECTED, RefundStatus.FAILED));
        if (orderMapper.update(null, ouw) != 1) {
            throw new BizException("订单状态已变更，请刷新");
        }
    }

    private Order getOrderByNo(String orderNo) {
        return orderMapper.selectOne(
                new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo));
    }

    private List<OrderItem> listItems(Long orderId) {
        return orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));
    }

    @Override
    public List<RefundOrder> listByOrderNo(String orderNo) {
        return refundOrderMapper.selectList(
                new LambdaQueryWrapper<RefundOrder>()
                        .eq(RefundOrder::getOrderNo, orderNo)
                        .orderByDesc(RefundOrder::getCreateTime));
    }

    @Override
    public List<RefundOrder> listByUserId(Long userId) {
        return refundOrderMapper.selectList(
                new LambdaQueryWrapper<RefundOrder>()
                        .eq(RefundOrder::getUserId, userId)
                        .orderByDesc(RefundOrder::getCreateTime));
    }

    @Override
    public RefundOrder applyAuto(Long userId, String orderNo, String reason) {
        RefundOrder r = this.apply(userId, orderNo, reason);
        if(r.getStatus() == RefundStatus.APPLYING
                && r.getRefundAmount().compareTo(AUTO_APPROVE_LIMIT)<=0) {
            try{
                this.approve(r.getId());
                log.info("小额退款自动审核通过: {}", r.getRefundNo());
                return refundOrderMapper.selectById(r.getId());  // 返回最新状态
            } catch (Exception e) {
                log.warn("自动审核失败，转人工: {}", r.getRefundNo(), e);
            }
        }
        return r;
    }
}
