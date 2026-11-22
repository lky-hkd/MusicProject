package com.music.service;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.music.common.OrderStatus;
import com.music.dto.SeckillMessage;
import com.music.dto.SeckillProductPayload;
import com.music.dto.SeckillVoucherPayload;
import com.music.entity.Cart;
import com.music.entity.Order;
import com.music.entity.OrderItem;
import com.music.entity.SeckillVoucher;
import com.music.entity.VoucherOrder;
import com.music.mapper.CartMapper;
import com.music.mapper.OrderItemMapper;
import com.music.mapper.OrderMapper;
import com.music.mapper.ProductMapper;
import com.music.mapper.VoucherOrderMapper;
import com.music.service.SeckillVoucherService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 秒杀订单落库服务。
 *
 * 单独抽成 Bean 的原因：MQ 消费者内部直接调用自身的 @Transactional 方法属于自调用，
 * Spring 代理不生效，事务不会开启。放到独立 Bean 后由容器注入代理对象调用，
 * 保证「一个订单批次要么全部落库、要么全部回滚」。
 */
@Slf4j
@Service
public class SeckillOrderPersistService {

    @Resource
    private OrderMapper orderMapper;

    @Resource
    private OrderItemMapper orderItemMapper;

    @Resource
    private VoucherOrderMapper voucherOrderMapper;

    @Resource
    private ProductMapper productMapper;

    @Resource
    private CartMapper cartMapper;

    @Resource
    private SeckillVoucherService seckillVoucherService;

    /**
     * 商品订单落库：按商家拆单，每个商家一个订单。
     * 幂等按「每个商家自己的 orderNo」判断，避免多商家批次只落了一部分就被当成已完成。
     */
    @Transactional(rollbackFor = Exception.class)
    public void persistProduct(SeckillMessage msg) {
        SeckillProductPayload p = JSON.parseObject(msg.getPayload(), SeckillProductPayload.class);
        List<OrderItem> items = p.getItems();
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("秒杀订单明细为空");
        }
        LinkedHashMap<Long, List<OrderItem>> byMerchant = items.stream().collect(Collectors.groupingBy(
                oi -> oi.getMerchantId() == null ? 0L : oi.getMerchantId(),
                LinkedHashMap::new, Collectors.toList()));

        BigDecimal remainingDiscount = p.getDiscountAmount() == null
                ? BigDecimal.ZERO : p.getDiscountAmount();

        // 第一个商家沿用预分配的 orderNo，其余商家追加后缀保证唯一
        boolean first = true;
        for (Map.Entry<Long, List<OrderItem>> e : byMerchant.entrySet()) {
            String orderNo = first ? msg.getOrderNo() : msg.getOrderNo() + "_" + e.getKey();
            first = false;

            List<OrderItem> groupItems = e.getValue();
            BigDecimal groupTotal = groupItems.stream().map(OrderItem::getSubtotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            // 优惠券按拆单顺序抵扣：先抵满前一单，剩余继续抵下一单（与同步链路口径一致）
            BigDecimal groupDiscount = BigDecimal.ZERO;
            if (remainingDiscount.compareTo(BigDecimal.ZERO) > 0) {
                groupDiscount = remainingDiscount.min(groupTotal);
                remainingDiscount = remainingDiscount.subtract(groupDiscount);
            }

            Long existed = orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                    .eq(Order::getOrderNo, orderNo));
            if (existed != null && existed > 0) {
                log.info("秒杀订单已存在，跳过 orderNo={}", orderNo);
                continue;
            }
            Order order = new Order();
            order.setOrderNo(orderNo);
            order.setPayNo(p.getPayNo());
            order.setMerchantId(e.getKey() == 0L ? null : e.getKey());
            order.setUserId(msg.getUserId());
            order.setTotalAmount(groupTotal);
            order.setPayAmount(groupTotal.subtract(groupDiscount));
            order.setDiscountAmount(groupDiscount);
            order.setStatus(OrderStatus.PENDING_PAY);
            order.setRemark(p.getRemark());
            order.setAddressId(p.getAddressId());
            order.setReceiver(p.getReceiver());
            order.setPhone(p.getPhone());
            order.setAddressDetail(p.getAddressDetail());
            order.setCreateTime(LocalDateTime.now());
            order.setUpdateTime(LocalDateTime.now());
            orderMapper.insert(order);
            for (OrderItem oi : groupItems) {
                oi.setOrderId(order.getId());
                orderItemMapper.insert(oi);
                productMapper.addSales(oi.getProductId(), oi.getQuantity());
            }
        }

        // 购物车来源：删除已下单的商品项
        if ("cart".equals(p.getSource())) {
            Set<Long> productIds = items.stream().map(OrderItem::getProductId)
                    .collect(Collectors.toSet());
            for (Long productId : productIds) {
                cartMapper.delete(new LambdaQueryWrapper<Cart>()
                        .eq(Cart::getUserId, msg.getUserId())
                        .eq(Cart::getProductId, productId));
            }
        }
    }

    /**
     * 秒杀券订单落库，orderNo 即券订单主键，按主键幂等。
     */
    @Transactional(rollbackFor = Exception.class)
    public void persistVoucher(SeckillMessage msg) {
        Long voucherOrderId = Long.valueOf(msg.getOrderNo());
        VoucherOrder existed = voucherOrderMapper.selectById(voucherOrderId);
        if (existed != null) {
            return;
        }
        SeckillVoucherPayload p = JSON.parseObject(msg.getPayload(), SeckillVoucherPayload.class);
        VoucherOrder vo = new VoucherOrder();
        vo.setId(voucherOrderId);
        vo.setUserId(p.getUserId());
        vo.setVoucherId(p.getVoucherId());
        vo.setStatus(1);        // 待支付
        vo.setCreateTime(LocalDateTime.now());
        voucherOrderMapper.insert(vo);

        // DB 库存回写：Redis 已在抢购时预扣，这里把 seckill_voucher.stock 同步扣掉。
        // 以 Redis 为准，DB 库存已为 0 时不阻断建单，只告警（可能存在 Redis/DB 漂移）。
        boolean stockWritten = seckillVoucherService.update(new LambdaUpdateWrapper<SeckillVoucher>()
                .eq(SeckillVoucher::getVoucherId, p.getVoucherId())
                .gt(SeckillVoucher::getStock, 0)
                .setSql("stock = stock - 1"));
        if (!stockWritten) {
            log.warn("券订单已生成，但 DB 库存回写失败（库存为0或券不存在） voucherId={} orderNo={}",
                    p.getVoucherId(), msg.getOrderNo());
        }
    }
}
