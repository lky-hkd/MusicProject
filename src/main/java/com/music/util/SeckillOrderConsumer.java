package com.music.util;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.music.config.RabbitMQConfig;
import com.music.dto.SeckillMessage;
import com.music.entity.SeckillOrderTask;
import com.music.mapper.SeckillOrderTaskMapper;
import com.music.service.SeckillOrderPersistService;
import com.rabbitmq.client.Channel;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@Slf4j
@Component
public class SeckillOrderConsumer {
    private static final int MAX_RETRY = 5;

    @Resource
    private SeckillOrderTaskMapper seckillOrderTaskMapper;
    @Resource
    private SeckillOrderPersistService seckillOrderPersistService;

    //监听秒杀订单队列
    @RabbitListener(queues = RabbitMQConfig.SECKILL_ORDER_QUEUE)
    public void onMessage(Message message, Channel channel) throws Exception {
        long tag = message.getMessageProperties().getDeliveryTag();
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        SeckillMessage msg;
        try {
            msg = JSON.parseObject(body, SeckillMessage.class);
        } catch (Exception e) {
            log.error("秒杀消息反序列化失败，进死信: {}", body, e);
            channel.basicNack(tag, false, false);   // 不可重试，直接进死信,手动ack
            return;
        }

        String orderNo = msg.getOrderNo();
        try {
            SeckillOrderTask task = getTask(orderNo);
            if (task != null && task.getStatus() == SeckillOrderTask.STATUS_SUCCESS) {
                channel.basicAck(tag, false);
                return;
            }
            if (msg.getBizType() == SeckillOrderTask.BIZ_PRODUCT) {
                seckillOrderPersistService.persistProduct(msg);
            } else if (msg.getBizType() == SeckillOrderTask.BIZ_VOUCHER) {
                seckillOrderPersistService.persistVoucher(msg);
            } else {
                throw new IllegalArgumentException("未知 bizType: " + msg.getBizType());
            }
            // 消费成功，标记
            markSuccess(orderNo);
            channel.basicAck(tag, false);
            log.info("秒杀订单落库成功 orderNo={}", orderNo);
        } catch (Exception e) {
            log.error("秒杀订单落库失败 orderNo={}", orderNo, e);
            int retry = incrRetry(orderNo, e.getMessage());//重试加1
            if (retry > MAX_RETRY) {
                // 超过上限 -> 标记失败，转人工；进度死信
                markFailed(orderNo, e.getMessage());
                log.error("[需人工处理] 秒杀订单落库超过重试上限 orderNo={}", orderNo);
            }
            // 不重回队列，进死信（避免死循环），由定时扫描 + 人工兜底
            channel.basicNack(tag, false, false);
        }
    }

    private SeckillOrderTask getTask(String orderNo) {
        return seckillOrderTaskMapper.selectOne(new
                LambdaQueryWrapper<SeckillOrderTask>()
                .eq(SeckillOrderTask::getOrderNo, orderNo));
    }

    private int incrRetry(String orderNo, String message) {
        SeckillOrderTask task = getTask(orderNo);
        int retry = (task == null || task.getRetryCount() == null ? 0 : task.getRetryCount()) + 1;
        seckillOrderTaskMapper.update(null, new LambdaUpdateWrapper<SeckillOrderTask>()
                .eq(SeckillOrderTask::getOrderNo, orderNo)
                .set(SeckillOrderTask::getRetryCount, retry)
                .set(SeckillOrderTask::getLastError, truncate(message))
                .set(SeckillOrderTask::getUpdateTime, LocalDateTime.now()));
        return retry;
    }

    private void markSuccess(String orderNo) {
        seckillOrderTaskMapper.update(null,
                new LambdaUpdateWrapper<SeckillOrderTask>()
                        .eq(SeckillOrderTask::getOrderNo, orderNo)
                        .set(SeckillOrderTask::getStatus, SeckillOrderTask.STATUS_SUCCESS)
                        .set(SeckillOrderTask::getUpdateTime, LocalDateTime.now()));
    }

    private void markFailed(String orderNo, String message) {
        seckillOrderTaskMapper.update(null, new LambdaUpdateWrapper<SeckillOrderTask>()
                .eq(SeckillOrderTask::getOrderNo, orderNo)
                .set(SeckillOrderTask::getStatus, SeckillOrderTask.STATUS_FAILED)
                .set(SeckillOrderTask::getLastError, truncate(message))
                .set(SeckillOrderTask::getUpdateTime, LocalDateTime.now()));
    }

    private String truncate(String s) {
        if (s == null) return null;
        return s.length() > 500 ? s.substring(0, 500) : s;
    }
}
