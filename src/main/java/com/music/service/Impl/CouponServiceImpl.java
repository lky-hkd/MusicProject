package com.music.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.music.common.BizException;
import com.music.common.OrderStatus;
import com.music.entity.Order;
import com.music.entity.SeckillVoucher;
import com.music.entity.UserCoupon;
import com.music.entity.Voucher;
import com.music.mapper.OrderMapper;
import com.music.mapper.SeckillVoucherMapper;
import com.music.mapper.UserCouponMapper;
import com.music.mapper.VoucherMapper;
import com.music.service.CouponService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CouponServiceImpl implements CouponService {

    private static final String STOCK_KEY_PREFIX = "seckill:voucher:stock:";
    private static final String USER_KEY_PREFIX = "seckill:voucher:users:";
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    @Resource
    private VoucherMapper voucherMapper;

    @Resource
    private SeckillVoucherMapper seckillVoucherMapper;

    @Resource
    private UserCouponMapper userCouponMapper;

    @Resource
    private OrderMapper orderMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource(name = "voucherSeckillScript")
    private DefaultRedisScript<Long> voucherSeckillScript;

    // ======================= 活动中心 =======================

    @Override
    public Map<String, Object> listActivity(Long userId) {
        List<Voucher> vouchers = voucherMapper.selectList(
                new LambdaQueryWrapper<Voucher>()
                        .eq(Voucher::getStatus, 1)
                        .orderByAsc(Voucher::getType)
                        .orderByDesc(Voucher::getId));

        Set<Long> receivedIds = Collections.emptySet();
        if (userId != null) {
            receivedIds = userCouponMapper.selectList(
                            new LambdaQueryWrapper<UserCoupon>().eq(UserCoupon::getUserId, userId))
                    .stream().map(UserCoupon::getVoucherId).collect(Collectors.toSet());
        }

        List<Long> seckillIds = vouchers.stream()
                .filter(v -> Integer.valueOf(1).equals(v.getType()))
                .map(Voucher::getId).toList();
        Map<Long, SeckillVoucher> seckillMap = seckillIds.isEmpty()
                ? Collections.emptyMap()
                : seckillVoucherMapper.selectBatchIds(seckillIds).stream()
                        .collect(Collectors.toMap(SeckillVoucher::getVoucherId, s -> s));

        List<Map<String, Object>> list = new ArrayList<>();
        for (Voucher v : vouchers) {
            Map<String, Object> item = baseItem(v);
            item.put("received", receivedIds.contains(v.getId()));
            item.put("stock", null);
            item.put("beginTime", null);
            item.put("endTime", null);
            item.put("soldOut", false);

            if (Integer.valueOf(1).equals(v.getType())) {
                SeckillVoucher sv = seckillMap.get(v.getId());
                if (sv != null) {
                    item.put("stock", sv.getStock());
                    item.put("beginTime", sv.getBeginTime());
                    item.put("endTime", sv.getEndTime());
                    item.put("soldOut", sv.getStock() == null || sv.getStock() <= 0);
                } else {
                    item.put("soldOut", true);
                }
            }
            list.add(item);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("logged", userId != null);
        data.put("list", list);
        return data;
    }

    // ======================= 领取普通券 =======================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void receiveNormal(Long voucherId, Long userId) {
        Voucher v = voucherMapper.selectById(voucherId);
        if (v == null || !Integer.valueOf(1).equals(v.getStatus())) {
            throw new BizException("优惠券不存在或已下架");
        }
        if (Integer.valueOf(1).equals(v.getType())) {
            throw new BizException("秒杀券需要参与抢购");
        }
        if (hasReceived(voucherId, userId)) {
            throw new BizException("您已领取过该券");
        }
        insertUserCoupon(v, userId);
    }

    // ======================= 秒杀券 =======================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void seckill(Long voucherId, Long userId) {
        Voucher v = voucherMapper.selectById(voucherId);
        if (v == null || !Integer.valueOf(1).equals(v.getStatus())) {
            throw new BizException("优惠券不存在或已下架");
        }
        if (!Integer.valueOf(1).equals(v.getType())) {
            throw new BizException("该券无需抢购，请直接领取");
        }
        SeckillVoucher sv = seckillVoucherMapper.selectById(voucherId);
        if (sv == null) {
            throw new BizException("秒杀活动不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        if (sv.getBeginTime() != null && now.isBefore(sv.getBeginTime())) {
            throw new BizException("秒杀还未开始");
        }
        if (sv.getEndTime() != null && now.isAfter(sv.getEndTime())) {
            throw new BizException("秒杀活动已结束");
        }
        if (hasReceived(voucherId, userId)) {
            throw new BizException("您已领取过该券");
        }

        String stockKey = STOCK_KEY_PREFIX + voucherId;
        String userKey = USER_KEY_PREFIX + voucherId;
        ensureStockPreheated(stockKey, sv);

        Long result = executeSeckill(stockKey, userKey, userId);
        if (result != null && result == -1L) {
            // 库存 key 被删（例如 Redis 重启后首次抢购），重新预热再抢一次
            ensureStockPreheated(stockKey, sv);
            result = executeSeckill(stockKey, userKey, userId);
        }
        if (result == null) {
            throw new BizException("系统繁忙，请稍后重试");
        }
        if (result == 2L) {
            throw new BizException("您已领取过该券");
        }
        if (result == 0L || result == -1L) {
            throw new BizException("手慢了，已被抢光");
        }

        try {
            // DB 兜底扣减：防止 Redis 与 DB 不一致时超卖
            boolean stockOk = seckillVoucherMapper.update(null,
                    new LambdaUpdateWrapper<SeckillVoucher>()
                            .eq(SeckillVoucher::getVoucherId, voucherId)
                            .gt(SeckillVoucher::getStock, 0)
                            .setSql("stock = stock - 1")) > 0;
            if (!stockOk) {
                throw new BizException("手慢了，已被抢光");
            }
            insertUserCoupon(v, userId);
        } catch (RuntimeException e) {
            // 落库失败：把 Redis 预扣的库存和用户标记补回去
            stringRedisTemplate.opsForValue().increment(stockKey);
            stringRedisTemplate.opsForSet().remove(userKey, String.valueOf(userId));
            throw e;
        }
    }

    private Long executeSeckill(String stockKey, String userKey, Long userId) {
        return stringRedisTemplate.execute(voucherSeckillScript,
                List.of(stockKey, userKey), String.valueOf(userId));
    }

    private void ensureStockPreheated(String stockKey, SeckillVoucher sv) {
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(stockKey))) {
            return;
        }
        int stock = sv.getStock() == null ? 0 : sv.getStock();
        stringRedisTemplate.opsForValue().setIfAbsent(stockKey, String.valueOf(stock));
    }

    private boolean hasReceived(Long voucherId, Long userId) {
        Long count = userCouponMapper.selectCount(
                new LambdaQueryWrapper<UserCoupon>()
                        .eq(UserCoupon::getUserId, userId)
                        .eq(UserCoupon::getVoucherId, voucherId));
        return count != null && count > 0;
    }

    private void insertUserCoupon(Voucher v, Long userId) {
        UserCoupon uc = new UserCoupon();
        uc.setUserId(userId);
        uc.setVoucherId(v.getId());
        uc.setStatus(0);
        LocalDateTime now = LocalDateTime.now();
        uc.setReceiveTime(now);
        if (v.getValidDays() != null && v.getValidDays() > 0) {
            uc.setExpireTime(now.plusDays(v.getValidDays()));
        }
        try {
            userCouponMapper.insert(uc);
        } catch (DuplicateKeyException e) {
            throw new BizException("您已领取过该券");
        }
    }

    // ======================= 我的优惠券 =======================

    @Override
    public List<Map<String, Object>> myCoupons(Long userId) {
        expireUserCoupons(userId);
        List<UserCoupon> coupons = userCouponMapper.selectList(
                new LambdaQueryWrapper<UserCoupon>()
                        .eq(UserCoupon::getUserId, userId)
                        .orderByAsc(UserCoupon::getStatus)
                        .orderByDesc(UserCoupon::getReceiveTime));
        if (coupons.isEmpty()) {
            return List.of();
        }
        Map<Long, Voucher> voucherMap = loadVoucherMap(coupons);

        List<Map<String, Object>> list = new ArrayList<>();
        for (UserCoupon uc : coupons) {
            Voucher v = voucherMap.get(uc.getVoucherId());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", uc.getId());
            item.put("voucherId", uc.getVoucherId());
            item.put("status", uc.getStatus());
            item.put("receiveTime", uc.getReceiveTime());
            item.put("expireTime", uc.getExpireTime());
            item.put("useTime", uc.getUseTime());
            item.put("title", v != null ? v.getTitle() : "优惠券");
            item.put("rules", v != null ? v.getRules() : null);
            item.put("discount", fen2yuan(v != null ? v.getActualValue() : null));
            item.put("threshold", fen2yuan(v != null ? v.getThreshold() : null));
            list.add(item);
        }
        return list;
    }

    @Override
    public List<Map<String, Object>> availableForOrder(Long userId, BigDecimal totalAmount) {
        if (totalAmount == null) {
            totalAmount = BigDecimal.ZERO;
        }
        expireUserCoupons(userId);
        List<UserCoupon> coupons = userCouponMapper.selectList(
                new LambdaQueryWrapper<UserCoupon>()
                        .eq(UserCoupon::getUserId, userId)
                        .eq(UserCoupon::getStatus, 0));
        if (coupons.isEmpty()) {
            return List.of();
        }
        Map<Long, Voucher> voucherMap = loadVoucherMap(coupons);

        List<Map<String, Object>> list = new ArrayList<>();
        for (UserCoupon uc : coupons) {
            Voucher v = voucherMap.get(uc.getVoucherId());
            if (v == null || !Integer.valueOf(1).equals(v.getStatus())) {
                continue;
            }
            BigDecimal threshold = fen2yuan(v.getThreshold());
            if (totalAmount.compareTo(threshold) < 0) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", uc.getId());
            item.put("title", v.getTitle());
            item.put("discount", fen2yuan(v.getActualValue()));
            item.put("threshold", threshold);
            item.put("expireTime", uc.getExpireTime());
            list.add(item);
        }
        return list;
    }

    // ======================= 下单核销 / 回滚 =======================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal useForOrder(Long userCouponId, Long userId, BigDecimal totalAmount, String payNo) {
        UserCoupon uc = userCouponMapper.selectById(userCouponId);
        if (uc == null || !Objects.equals(uc.getUserId(), userId)) {
            throw new BizException("优惠券不存在");
        }
        if (!Integer.valueOf(0).equals(uc.getStatus())) {
            throw new BizException("优惠券不可用");
        }
        if (uc.getExpireTime() != null && uc.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new BizException("优惠券已过期");
        }
        Voucher v = voucherMapper.selectById(uc.getVoucherId());
        if (v == null || !Integer.valueOf(1).equals(v.getStatus())) {
            throw new BizException("优惠券已下架");
        }
        BigDecimal threshold = fen2yuan(v.getThreshold());
        if (totalAmount.compareTo(threshold) < 0) {
            throw new BizException("订单未满 " + threshold + " 元，无法使用该券");
        }
        BigDecimal discount = fen2yuan(v.getActualValue()).min(totalAmount);

        int rows = userCouponMapper.update(null,
                new LambdaUpdateWrapper<UserCoupon>()
                        .eq(UserCoupon::getId, userCouponId)
                        .eq(UserCoupon::getUserId, userId)
                        .eq(UserCoupon::getStatus, 0)
                        .set(UserCoupon::getStatus, 1)
                        .set(UserCoupon::getUseTime, LocalDateTime.now())
                        .set(UserCoupon::getPayNo, payNo)
                        .set(UserCoupon::getDiscountAmount, discount.multiply(HUNDRED).longValue()));
        if (rows == 0) {
            throw new BizException("优惠券已被使用");
        }
        return discount;
    }

    @Override
    public void restoreCouponIfBatchCanceled(String payNo) {
        if (payNo == null || payNo.isEmpty()) {
            return;
        }
        Long remain = orderMapper.selectCount(
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getPayNo, payNo)
                        .ne(Order::getStatus, OrderStatus.CANCELED));
        if (remain != null && remain > 0) {
            return; // 同批次还有订单未取消，优惠券仍被占用
        }
        UserCoupon uc = userCouponMapper.selectOne(
                new LambdaQueryWrapper<UserCoupon>()
                        .eq(UserCoupon::getPayNo, payNo)
                        .eq(UserCoupon::getStatus, 1)
                        .last("LIMIT 1"));
        if (uc == null) {
            return;
        }
        boolean expired = uc.getExpireTime() != null
                && uc.getExpireTime().isBefore(LocalDateTime.now());
        userCouponMapper.update(null,
                new LambdaUpdateWrapper<UserCoupon>()
                        .eq(UserCoupon::getId, uc.getId())
                        .eq(UserCoupon::getStatus, 1)
                        .set(UserCoupon::getStatus, expired ? 2 : 0)
                        .set(UserCoupon::getUseTime, null)
                        .set(UserCoupon::getPayNo, null)
                        .set(UserCoupon::getDiscountAmount, null));
    }

    // ======================= 工具方法 =======================

    private void expireUserCoupons(Long userId) {
        userCouponMapper.update(null,
                new LambdaUpdateWrapper<UserCoupon>()
                        .eq(UserCoupon::getUserId, userId)
                        .eq(UserCoupon::getStatus, 0)
                        .isNotNull(UserCoupon::getExpireTime)
                        .lt(UserCoupon::getExpireTime, LocalDateTime.now())
                        .set(UserCoupon::getStatus, 2));
    }

    private Map<Long, Voucher> loadVoucherMap(List<UserCoupon> coupons) {
        List<Long> ids = coupons.stream().map(UserCoupon::getVoucherId).distinct().toList();
        return voucherMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Voucher::getId, v -> v));
    }

    private Map<String, Object> baseItem(Voucher v) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", v.getId());
        item.put("title", v.getTitle());
        item.put("subTitle", v.getSubTitle());
        item.put("rules", v.getRules());
        item.put("type", v.getType() == null ? 0 : v.getType());
        item.put("discount", fen2yuan(v.getActualValue()));
        item.put("threshold", fen2yuan(v.getThreshold()));
        item.put("validDays", v.getValidDays() == null ? 0 : v.getValidDays());
        item.put("payValue", fen2yuan(v.getPayValue()));
        return item;
    }

    /** 分 -> 元 */
    private BigDecimal fen2yuan(Long fen) {
        if (fen == null || fen <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(fen).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }
}
