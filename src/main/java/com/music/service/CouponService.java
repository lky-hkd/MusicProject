package com.music.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 优惠券 / 活动中心
 */
public interface CouponService {

    /** 活动中心：普通券 + 秒杀券（含库存、是否已领） */
    Map<String, Object> listActivity(Long userId);

    /** 领取普通券 */
    void receiveNormal(Long voucherId, Long userId);

    /** 秒杀券抢购（Redis 预扣库存 + Lua） */
    void seckill(Long voucherId, Long userId);

    /** 我的优惠券 */
    List<Map<String, Object>> myCoupons(Long userId);

    /** 当前订单金额可用的优惠券 */
    List<Map<String, Object>> availableForOrder(Long userId, BigDecimal totalAmount);

    /**
     * 下单时核销优惠券，返回实际抵扣金额（元）。
     * 由 OrderServiceImpl 在下单事务内调用，失败抛 BizException。
     */
    BigDecimal useForOrder(Long userCouponId, Long userId, BigDecimal totalAmount, String payNo);

    /** 同一支付批次订单全部取消后，回滚优惠券 */
    void restoreCouponIfBatchCanceled(String payNo);
}
