package com.music.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.music.common.*;
import com.music.dto.MerchantOrderQuery;
import com.music.dto.MerchantShipRequest;
import com.music.entity.Order;
import com.music.entity.OrderItem;
import com.music.mapper.OrderItemMapper;
import com.music.service.OrderService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 商家 - 订单管理
 * 数据隔离：order 表无 merchant_id，必须经 order_item 反查
 */
@RestController
@RequestMapping("/api/merchant/order")
public class MerchantOrderController {
    @Resource
    private MerchantContext merchantContext;

    @Resource
    private OrderService orderService;

    @Resource
    private OrderItemMapper orderItemMapper;

    //我的订单列表（只含本店商品）
    @PostMapping("/list")
    public Result<?> list(@RequestAttribute(UserContext.USER_ID_ATTR) Long userId,
                          @RequestBody(required = false) MerchantOrderQuery query) {
        Long merchantId = merchantContext.requireMerchantId(userId);
        int page = (query == null || query.getPage() == null) ? 1 : query.getPage();
        int size = (query == null || query.getSize() == null) ? 10 : query.getSize();

        LambdaQueryWrapper<Order> qw = new LambdaQueryWrapper<>();
        //子查询限定本店订单
        qw.eq(Order::getMerchantId, merchantId);
        if (query != null && query.getStatus() != null) {
            qw.eq(Order::getStatus, query.getStatus());
        }
        if (query != null && query.getKeyword() != null && !query.getKeyword().trim().isEmpty()) {
            String kw = query.getKeyword().trim();
            qw.and(w -> w.like(Order::getOrderNo, kw).or().like(Order::getReceiver, kw));
        }
        qw.orderByDesc(Order::getCreateTime);
        Page<Order> result = orderService.page(new Page<>(page, size), qw);

        //每个订单中属于本店的明细
        List<Long> orderIds = result.getRecords().stream().map(Order::getId).toList();
        Map<Long, List<OrderItem>> itemMap = orderIds.isEmpty() ? Collections.emptyMap()
                : orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                        .in(OrderItem::getOrderId, orderIds)
                        .eq(OrderItem::getMerchantId, merchantId))
                .stream().collect(Collectors.groupingBy(OrderItem::getOrderId));
        List<Map<String,Object>> records = new ArrayList<>();
        for(Order o: result.getRecords()){
            Map<String,Object> vo = new LinkedHashMap<>();
            vo.put("order", o);
            vo.put("items", itemMap.getOrDefault(o.getId(), List.of()));
            records.add(vo);
        }
        Map<String,Object> data = new LinkedHashMap<>();
        data.put("records", records);
        data.put("total", result.getTotal());
        data.put("page", page);
        data.put("size", size);
        return Result.ok(data);
    }

    //订单详情
    @PostMapping("/detail")
    public Result<?> detail(@RequestAttribute(UserContext.USER_ID_ATTR) Long userId,
                            @RequestBody Map<String,String> body){
        Long merchantId = merchantContext.requireMerchantId(userId);
        String orderNo = body.get("orderNo");
        if(orderNo == null || orderNo.trim().isEmpty()){
            throw new BizException("订单号不能为空");
        }

        Order order = orderService.getOne(new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo));
        if(order == null ) throw new BizException("订单不存在");
        List<OrderItem> myItems = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getMerchantId, merchantId)
                .eq(OrderItem::getOrderId,order.getId()));
        if(myItems.isEmpty())  throw new BizException("无权查看此订单");

        Map<String,Object> vo = new LinkedHashMap<>();
        vo.put("order",order);
        vo.put("items",myItems);
        return Result.ok(vo);
    }

//发货
@PostMapping("/ship")
public Result<?> ship(@RequestBody MerchantShipRequest req,
                      @RequestAttribute(UserContext.USER_ID_ATTR) Long userId) {
        Long merchantId = merchantContext.requireMerchantId(userId);
        if(req.getOrderNo() == null || req.getOrderNo().trim().isEmpty()){
            throw new BizException("订单号不能为空");
        }
        if (req.getExpressNo() == null || req.getExpressNo().trim().isEmpty())
            throw new BizException("快递单号不能为空");
        Order order = orderService.getOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getOrderNo, req.getOrderNo().trim())
                .eq(Order::getMerchantId,merchantId));
        if(order == null ) throw new BizException("订单不存在");
         if (order.getStatus() != OrderStatus.PAID) throw new BizException("只有已付款订单可以发货");


    // CAS 更新，防并发重复发货
       boolean ok = orderService.update(new LambdaUpdateWrapper<Order>()
            .eq(Order::getId, order.getId())
            .eq(Order::getStatus, OrderStatus.PAID)
            .set(Order::getStatus, OrderStatus.SHIPPED)
            .set(Order::getExpressNo, req.getExpressNo().trim())
            .set(Order::getShipTime, LocalDateTime.now())
            .set(Order::getUpdateTime, LocalDateTime.now()));
       if (!ok) throw new BizException("发货失败，订单状态已变更，请刷新重试");

    return Result.ok(null, "发货成功");
    }
}