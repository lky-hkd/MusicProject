package com.music.service.Impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.music.common.Result;
import com.music.config.RabbitMQConfig;
import com.music.config.RedisConfig;
import com.music.dto.SeckillMessage;
import com.music.dto.SeckillVoucherPayload;
import com.music.entity.SeckillOrderTask;
import com.music.entity.SeckillVoucher;
import com.music.entity.VoucherOrder;
import com.music.mapper.SeckillOrderTaskMapper;
import com.music.mapper.SeckillVoucherMapper;
import com.music.mapper.VoucherMapper;
import com.music.mapper.VoucherOrderMapper;
import com.music.service.IVoucherOrderService;
import com.music.service.SeckillVoucherService;
import com.music.util.ILockImpl;
import com.music.util.RedisIdWorker;
import jakarta.annotation.Resource;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@Service
public class IVoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private SeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private SeckillOrderTaskMapper seckillOrderTaskMapper;

    @Resource
    private SeckillVoucherMapper seckillVoucherMapper;

    @Resource
    @Qualifier("voucherSeckillScript")
    private DefaultRedisScript<Long> voucherSeckillScript;

    private static final String VOUCHER_STOCK_KEY = "seckill:voucher:stock:";
    private static final String VOUCHER_USERS_KEY = "seckill:voucher:users:";
    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Override
    public Result seckillVoucher(Long voucherId, Long userId) {
        if (userId == null) {
            return Result.fail("请先登录");
        }
        SeckillVoucher sv = seckillVoucherService.getById(voucherId);
        if (sv == null) {
            return Result.fail("暂无秒杀券");
        }
        LocalDateTime now = LocalDateTime.now();

        if (sv.getBeginTime().isAfter(now)) {
            return Result.fail("暂未开始");
        }
        if (sv.getEndTime().isBefore(now)) {
            return Result.fail("秒杀活动已结束");
        }
        if (sv.getStock() < 1) {
            return Result.fail("库存不足");
        }
        // 预热库存
        String stockKey = VOUCHER_STOCK_KEY + voucherId;
        String usersKey = VOUCHER_USERS_KEY + voucherId;
        stringRedisTemplate.opsForValue().setIfAbsent(stockKey, String.valueOf(sv.getStock()));

        // Lua 原子扣减 + 一人一券
        Long r = stringRedisTemplate.execute(
                voucherSeckillScript,
                Arrays.asList(stockKey, usersKey),
                String.valueOf(userId));
        if (r == null)                 return Result.fail("系统繁忙");
        if (r == 0L)                   return Result.fail("已抢完");
        if (r == 2L)                   return Result.fail("每用户仅限一张");
        if (r == -1L)                  return Result.fail("库存未预热");
        // 预分配 orderNo + 登记 task + 投 MQ
        long orderNo = redisIdWorker.nextId("order");
        SeckillVoucherPayload payload = new SeckillVoucherPayload();
        payload.setVoucherId(voucherId);
        payload.setUserId(userId);

        SeckillOrderTask task = new SeckillOrderTask();
        task.setOrderNo(String.valueOf(orderNo));
        task.setBizType(SeckillOrderTask.BIZ_VOUCHER);
        task.setUserId(userId);
        task.setPayload(JSON.toJSONString(payload));
        task.setStatus(SeckillOrderTask.STATUS_CREATING);
        task.setRetryCount(0);
        seckillOrderTaskMapper.insert(task);
        try{
            SeckillMessage msg = new SeckillMessage(String.valueOf(orderNo), SeckillOrderTask.BIZ_VOUCHER, userId, task.getPayload());
            rabbitTemplate.convertAndSend(RabbitMQConfig.SECKILL_ORDER_EXCHANGE,
                    RabbitMQConfig.SECKILL_ORDER_ROUTING_KEY,JSON.toJSONString(msg));

        } catch (Exception e) {
            stringRedisTemplate.opsForValue().increment(stockKey, 1);
            stringRedisTemplate.opsForSet().remove(usersKey, String.valueOf(userId));
            seckillOrderTaskMapper.deleteById(task.getId());
            return Result.fail("系统繁忙，请重试");
        }
        return Result.ok(String.valueOf(orderNo));   // 返回 orderNo，前端轮询
    }
//        ILockImpl iLock = new ILockImpl("order:" + userId, stringRedisTemplate);
//        RLock iLock = redissonClient.getLock("lock:order:" + userId);
        //toString底层是把long转化为String,即使是相同id也是不同对象，所以要去常量池中intern()
//        boolean isLock = iLock.tryLock();
//        if(!isLock){
//            return Result.fail("下单处理中，请勿重复提交");
//        }
//        try {
//            //获取代理对象，保证同一个对象
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            //实现一人只能一张
//            return proxy.createSeckillVoucher(voucherId, userId);
//        }finally {
//
//            iLock.unlock();
//        }

    //
    @Transactional(rollbackFor = Exception.class)
    public Result createSeckillVoucher(Long voucherId, Long userId) {
        //实现一人只能一张
        Long count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count > 0) {
            return Result.fail("每用户仅限一张");
        }

        boolean success = seckillVoucherService.update()
                .setSql("stock=stock-1")
                .gt("stock", 0)
                .eq("voucher_id", voucherId).update();

        if (!success) {
            return Result.fail("库存不足");
        }

        VoucherOrder voucherOrder = new VoucherOrder();
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setStatus(1);              // 1 待支付
        voucherOrder.setCreateTime(LocalDateTime.now());
        save(voucherOrder);

        return Result.ok(orderId);
    }
}
