package com.music.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.music.config.OrderProperties;
import com.music.entity.Order;
import com.music.entity.OrderItem;
import com.music.mapper.OrderItemMapper;
import com.music.mapper.OrderMapper;
import com.music.service.OrderCancelService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static com.music.common.OrderStatus.CANCELED;
import static com.music.common.OrderStatus.PENDING_PAY;

@Slf4j
@Service
public class OrderCancelServiceImpl extends ServiceImpl<OrderMapper, Order> implements OrderCancelService {

    @Autowired
    private OrderItemMapper orderItemMapper;

    @Autowired
    private RedisStockService redisStockService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private com.music.service.CouponService couponService;

    private static final String ORDER_DONE_PREFIX = "order:done:";
    private static final String ORDER_ROLLBACK_PREFIX = "order:rollback:";
    private static final long ORDER_FLAG_EXPIRE_MINUTES = OrderProperties.FLAG_EXPIRE_MINUTES;

    /**
     * 把每条订单做成独立的事务
     */


    @Override
    @Transactional(rollbackFor = Exception.class,propagation = Propagation.REQUIRES_NEW)
    public boolean cancelOneInNewTx(Long orderId, String orderNo, LocalDateTime now) {

        Order order = this.getById(orderId);
        String payNo = order != null ? order.getPayNo() : null;

        LambdaUpdateWrapper<Order> uw = new LambdaUpdateWrapper<>();
        uw.set(Order::getStatus,CANCELED);
        uw.set(Order::getUpdateTime,now);
        uw.eq(Order::getId,orderId);
        uw.eq(Order::getStatus,PENDING_PAY);//乐观锁

        boolean updated = this.update(uw);
        if(!updated){
            return false;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit(){
                rollbackOrderStock(orderId);
                couponService.restoreCouponIfBatchCanceled(payNo);
                markOrderDone(orderNo);
            }
        });


        return true;
    }

    private void markOrderDone(String orderNo) {
        try{
            stringRedisTemplate.opsForValue().set(
                    ORDER_DONE_PREFIX + orderNo,"1",
                    Duration.ofMinutes(ORDER_FLAG_EXPIRE_MINUTES));
        } catch (Exception e) {
            log.error("写入订单已处理标记失败：{}，原因：{}", orderNo, e.getMessage());
        }

    }

    private void rollbackOrderStock(Long orderId) {
        LambdaQueryWrapper<OrderItem> qw = new LambdaQueryWrapper<>();
        qw.eq(OrderItem::getOrderId,orderId);
        List<OrderItem> items = orderItemMapper.selectList(qw);

        if (items == null || items.isEmpty()) {
            log.warn("订单无明细，跳过库存回滚：{}", orderId);
            return;
        }
//        Boolean first = stringRedisTemplate.opsForValue().setIfAbsent(
//                ORDER_ROLLBACK_PREFIX+orderId,"1",
//                Duration.ofMinutes(ORDER_FLAG_EXPIRE_MINUTES));
//        if(!Boolean.TRUE.equals(first)){
//            log.info("订单库存已回滚过，跳过：{}", orderId);
//            return;
//        }
        try{
            redisStockService.rollbackOrderStockIdempotent(orderId, items);
        } catch (Exception e) {
            // 回滚失败要删除标记，允许下次重试
            stringRedisTemplate.delete(ORDER_ROLLBACK_PREFIX + orderId);
            throw new RuntimeException(e);
        }
    }


}
