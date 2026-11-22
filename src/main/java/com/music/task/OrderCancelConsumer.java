package com.music.task;

import com.music.config.RabbitMQConfig;
import com.music.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.rabbitmq.client.Channel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class OrderCancelConsumer {

    @Autowired
    private OrderService orderService;

    /**
     * 监听死信队列：处理超时未支付的订单
     */
    @RabbitListener(queues = RabbitMQConfig.ORDER_CANCEL_QUEUE)
    public void handleOrderCancel(Message message, Channel channel) throws IOException {
        long tag = message.getMessageProperties().getDeliveryTag();


        // 转成字符串
        String orderNo = new String(message.getBody(), StandardCharsets.UTF_8);

        // 如果转出来是带双引号的（比如 "1792..."），把双引号去掉
        if (orderNo.startsWith("\"") && orderNo.endsWith("\"")) {
            orderNo = orderNo.substring(1, orderNo.length() - 1);
        }

        log.info("======== ⏰ 收到 MQ 延迟消息，订单号: [" + orderNo + "] ========");

        try {
            orderService.cancelTimeoutOrder(orderNo);
            channel.basicAck(tag,false);
        } catch (Exception e) {
            log.error("💥 处理超时订单异常: " + e.getMessage());
            channel.basicNack(tag, false, false);
        }
    }
}