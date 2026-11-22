package com.music.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.music.dto.OrderConfirmRequest;
import com.music.dto.OrderConfirmResponse;
import com.music.dto.OrderCreateRequest;
import com.music.dto.OrderCreateResult;
import com.music.entity.Order;
import com.music.entity.OrderItem;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface OrderService extends IService<Order> {

//    订单确认页数据
    OrderConfirmResponse confirmOrder(Long orderId, OrderConfirmRequest request);

//     创建订单
    OrderCreateResult createOrder(Long userId, OrderCreateRequest request);
//    模拟支付
    void pay(Long userId,String payNo,String payType);
//    我的订单列表
    List<Order> ListByUserId(Long userId);
//    订单详情（含明细
    Order getDetail(Long userId,String orderNo);
//    订单明细列表
    List<OrderItem> listItems(Long orderId);
//    取消订单
    void cancel(Long userId,String orderNo);

    int scanAndCancelOrders(int minMinutes,int maxMinutes);

    /** 兜底扫描：取消「早于 expireMinutes 分钟仍未付款」的订单（分页 + 索引友好） */
    int scanExpiredOrders(int expireMinutes);

    void cancelTimeoutOrder(String orderNo);
    boolean canCancel(Order order);

    /** 确认收货：已发货(2) -> 已完成(3) */
    void receive(Long userId, String orderNo);

    void paySuccess(String tradeNo,String payNo,java.math.BigDecimal totalAmount);
    List<OrderItem> listItemsByOrderIds(List<Long> orderIds);

    OrderCreateResult seckillCreateOrder(Long userId, OrderCreateRequest request);
}

