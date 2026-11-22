package com.music.config;

import jakarta.annotation.PostConstruct;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitMQConfig {
    public static final String ORDER_DELAY_EXCHANGE="order.delay.exchange";
    public static final String ORDER_DELAY_QUEUE="order.delay.queue";
    public static final String ORDER_DELAY_ROUTING_KEY="order.delay.routing.key";

    public static final String ORDER_CANCEL_EXCHANGE="order.cancel.exchange";
    public static final String ORDER_CANCEL_QUEUE="order.cancel.queue";
    public static final String ORDER_CANCEL_ROUTING_KEY="order.cancel.routing.key";


    // ===================== 秒杀异步落库队列 =====================
    public static final String SECKILL_ORDER_EXCHANGE     = "seckill.order.exchange";
    public static final String SECKILL_ORDER_QUEUE        = "seckill.order.queue";
    public static final String SECKILL_ORDER_ROUTING_KEY  = "seckill.order.routing.key";
    public static final String SECKILL_ORDER_DLX          = "seckill.order.dlx";
    public static final String SECKILL_ORDER_DLQ          = "seckill.order.dlq";
    public static final String SECKILL_ORDER_DLQ_ROUTING  = "seckill.order.dlq.routing";


    // 1. 声明死信交换机 (接收超时消息)
    @Bean
    public DirectExchange orderCancelExchange() {
        return new DirectExchange(ORDER_CANCEL_EXCHANGE);
    }

    // 2. 声明死信队列
    @Bean
    public Queue orderCancelQueue() {
        return new Queue(ORDER_CANCEL_QUEUE);
    }

    // 3. 绑定死信队列
    @Bean
    public Binding orderCancelBinding() {
        return BindingBuilder.bind(orderCancelQueue()).to(orderCancelExchange()).with(ORDER_CANCEL_ROUTING_KEY);
    }

    // 4. 声明延迟交换机
    @Bean
    public DirectExchange orderDelayExchange() {
        return new DirectExchange(ORDER_DELAY_EXCHANGE);
    }

    // 5. 声明延迟队列（并设置死信路由）
    @Bean
    public Queue orderDelayQueue() {
        Map<String,Object> args=new HashMap<String,Object>();
        // 消息过期后，转发到死信交换机
        args.put("x-dead-letter-exchange", ORDER_CANCEL_EXCHANGE);
        args.put("x-dead-letter-routing-key", ORDER_CANCEL_ROUTING_KEY);
        // 队列所有消息统一延迟 15 分钟 (900000 毫秒)
        args.put("x-message-ttl", OrderProperties.TIMEOUT_MILLIS);
        return new Queue(ORDER_DELAY_QUEUE,true,false,false,args);
    }

    // 6. 绑定延迟队列

    @Bean
    public Binding orderDelayBinding() {
        return BindingBuilder.bind(orderDelayQueue())
                .to(orderDelayExchange()).with(ORDER_DELAY_ROUTING_KEY);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new SimpleMessageConverter();
    }


    @Bean
    public DirectExchange seckillOrderExchange(){
        return new DirectExchange(SECKILL_ORDER_EXCHANGE,true,false);
    }

    @Bean
    public Queue seckillOrderQueue(){
        Map<String,Object> args=new HashMap<>();
        // 消费失败 -> 死信（由定时扫描 + 人工兜底）
        args.put("x-dead-letter-exchange", SECKILL_ORDER_DLX);
        args.put("x-dead-letter-routing-key", SECKILL_ORDER_DLQ_ROUTING);
        return new Queue(SECKILL_ORDER_QUEUE,true,false,false,args);
    }

    @Bean
    public Binding seckillOrderBinding() {
        return BindingBuilder.bind(seckillOrderQueue())
                .to(seckillOrderExchange()).with(SECKILL_ORDER_ROUTING_KEY);
    }

    @Bean
    public DirectExchange seckillOrderDlx(){
        return new DirectExchange(SECKILL_ORDER_DLX,true,false);
    }

    @Bean
    public Queue seckillOrderDlq(){
        return new Queue(SECKILL_ORDER_DLQ,true);
    }

    @Bean
    public Binding seckillOrderDlqBinding() {
        return BindingBuilder.bind(seckillOrderDlq()).to(seckillOrderDlx()).with(SECKILL_ORDER_DLQ_ROUTING);
    }

}
