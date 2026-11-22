package com.music.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.music.common.OrderStatus;
import com.music.common.Result;
import com.music.common.UserContext;
import com.music.dto.OrderConfirmRequest;
import com.music.dto.OrderConfirmResponse;
import com.music.dto.OrderCreateRequest;
import com.music.dto.OrderCreateResult;
import com.music.entity.Order;
import com.music.entity.OrderItem;
import com.music.entity.ProductReview;
import com.music.mapper.MerchantMapper;
import com.music.mapper.OrderMapper;
import com.music.service.*;
import com.music.service.Impl.RefundServiceImpl;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/order")
public class OrderController {
    @Resource
    private OrderService orderService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private AlipayService alipayService;

    @Autowired
    private RefundService refundService;

    @Resource
    private ReviewService reviewService;

    @Resource
    private MerchantMapper merchantMapper;
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private ProductService productService;

    /**
     * 订单确认页数据
     * POST /api/order/confirm
     * Body: { items: [{productId, quantity}] }
     */
    @PostMapping("/confirm")
    public Result<?> confirm(@Valid @RequestBody OrderConfirmRequest request,
                             @RequestAttribute(UserContext.USER_ID_ATTR) Long userId) {
        try{
            OrderConfirmResponse resp =orderService.confirmOrder(userId, request);
            return Result.ok(resp);
        }catch (Exception e) {
            return Result.fail(e.getMessage());
        }
    }
    /**
     * 提交订单
     * POST /api/order/create
     */
    @PostMapping("/create")
    public Result<?> create(@Valid @RequestBody OrderCreateRequest request,
                            @RequestAttribute(UserContext.USER_ID_ATTR) Long userId) {
        try{
            OrderCreateResult result;
            boolean hot = productService.isHotProduct(request.getItems());
            if(hot){
                result = orderService.seckillCreateOrder(userId, request);
            }else{
                result = orderService.createOrder(userId, request);
            }
            Map<String,Object> data = new HashMap<>();
            data.put("payNo",result.getPayNo());
            data.put("orderNos",result.getOrderNos());
            data.put("payAmount",result.getTotalAmount());
            data.put("orderNo", result.getOrderNos().isEmpty()
                    ? null : result.getOrderNos().get(0));
            return Result.ok(data,"下单成功");
        }catch (Exception e) {
            log.error("下单失败", e);
            return Result.fail(e.getMessage() != null ? e.getMessage() : "下单失败");
        }
    }

    /**
     * 模拟支付
     * POST /api/order/pay?orderNo=xxx&payType=mock
     */
    @PostMapping("/pay")
    public Result<?> pay(@RequestParam String payNo,
                         @RequestParam(defaultValue = "mock") String payType,
                         @RequestAttribute(UserContext.USER_ID_ATTR) Long userId) {
        try{
            orderService.pay(userId, payNo, payType);
            return Result.ok(null, "支付成功");
        }catch (Exception e) {
            return Result.fail(e.getMessage());
        }
    }

    /**
     * 支付页信息：批次金额 + 是否已全部支付（供前端展示/轮询）
     * GET /api/order/pay-info?payNo=xxx
     */
    @GetMapping("/pay-info")
    public Result<?> payInfo(@RequestParam String payNo,
                             @RequestAttribute(UserContext.USER_ID_ATTR) Long userId) {
        List<Order> orders = orderService.list(new LambdaQueryWrapper<Order>()
                .eq(Order::getPayNo, payNo));
        if(orders.isEmpty()) return Result.fail("订单不存在");
        if(!orders.get(0).getUserId().equals(userId)) return Result.fail("无权操作");
        BigDecimal amount = orders.stream().map(Order::getPayAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean paid = orders.stream().allMatch(o -> o.getStatus() != null
                && o.getStatus() != OrderStatus.PENDING_PAY);
        Map<String,Object> data = new HashMap<>();
        data.put("payAmount", amount);
        data.put("paid", paid);
        data.put("orderNos", orders.stream().map(Order::getOrderNo).toList());
        return Result.ok(data);
    }
    /**
     * 发起支付宝支付：返回支付宝支付页面表单 HTML
     * GET /api/order/alipay/page?payNo=xxx
     */
    @GetMapping("/alipay/page")
    public void alipayPage(@RequestParam String payNo,
                           HttpServletResponse response,
                           @RequestAttribute(UserContext.USER_ID_ATTR) Long userId) throws IOException {
        response.setContentType("text/html;charset=utf-8");
        List<Order> orders= orderService.list(new LambdaQueryWrapper<Order>()
                .eq(Order::getPayNo, payNo));

        if (orders.isEmpty()) { response.getWriter().write("<h3>订单不存在</h3>"); return; }
        if (!orders.get(0).getUserId().equals(userId)) {
            response.getWriter().write("<h3>无权操作</h3>"); return;
        }
        if (orders.stream().anyMatch(o -> !Integer.valueOf(OrderStatus.PENDING_PAY).equals(o.getStatus()))) {
            response.getWriter().write("<h3>订单状态不可支付</h3>"); return;
        }
        BigDecimal amount = orders.stream().map(Order::getPayAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        String form = alipayService.bulidPagePayForm(payNo, amount, "音乐平台订单-" + payNo);
        response.getWriter().write(form);
    }

    /**
     * 支付宝异步回调（★ 真正修改订单状态的地方）
     * POST /api/order/alipay/notify
     * 必须返回 "success" 字符串，否则支付宝会重发
     */
    @PostMapping("/alipay/notify")
    public void alipayNotify(@RequestParam Map<String,String> params,
                             HttpServletResponse response) throws Exception {
        PrintWriter out = response.getWriter();
        log.info("收到支付宝异步回调: {}", params);
        try{
            //验签
            boolean signOk = alipayService.verifyNotify(params);
            if(!signOk){
                log.error("支付宝回调验签失败");
                out.write("failure");
                return;
            }
            String tradeStatus = params.get("trade_status");
            String outTradeNo = params.get("out_trade_no");
            String tradeNo = params.get("trade_no");
            String totalAmount = params.get("total_amount");
            if (totalAmount == null || outTradeNo == null || tradeNo == null) {
                log.error("支付宝回调字段缺失: {}", params);
                out.write("failure");
                return;
            }
            if ("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus)) {
                orderService.paySuccess(tradeNo, outTradeNo, new java.math.BigDecimal(totalAmount));
            }
            out.write("success");
        } catch (Exception e) {
            log.error("处理支付宝回调异常", e);
            out.write("failure");   // 让支付宝稍后重试
        }
    }

    /**
     * 支付宝同步返回页（用户付完跳回来，仅用于展示，不要在这里改状态）
     * GET /api/order/alipay/return
     */
    @GetMapping("/alipay/return")
    public void alipayReturn(HttpServletResponse response) throws Exception {
        // 简单跳转到前端订单列表，前端会查询真实状态
        response.sendRedirect("/#/my-orders");
    }

    /**
     * 我的订单列表
     * GET /api/order/list
     */
    @GetMapping("/list")
    public Result<?> list(@RequestAttribute(UserContext.USER_ID_ATTR) Long userId) {
        List<Order> list =orderService.ListByUserId(userId);
        if(list.isEmpty()) return Result.ok(List.of());
        List<Long> orderIds = list.stream().map(Order::getId).toList();
        // 一次查出所有明细，按 orderId 分组
        Map<Long, List<OrderItem>> itemMap = orderService.listItemsByOrderIds(orderIds)
                .stream().collect(Collectors.groupingBy(OrderItem::getOrderId));

        List<Map<String,Object>> data = list.stream().map(o -> {
            Map<String,Object> m = new HashMap<>();
            m.put("orderNo", o.getOrderNo());
            m.put("status", o.getStatus());
            m.put("payAmount", o.getPayAmount());
            m.put("createTime", o.getCreateTime());
            // 用内存中的明细判断，避免 N+1
            m.put("canCancel", canCancelInMemory(o, itemMap.getOrDefault(o.getId(), List.of())));
            return m;
        }).collect(Collectors.toList());

        return Result.ok(data);
    }
    private boolean canCancelInMemory(Order o, List<OrderItem> items) {
        if (o.getStatus() == null) return false;
        if (o.getStatus() == OrderStatus.PENDING_PAY) return true;
        if (o.getStatus() != OrderStatus.PAID && o.getStatus() != OrderStatus.SHIPPED) return false;
        return items.stream().anyMatch(i -> "physical".equals(i.getProductType()));
    }

    /**
     * 订单详情
     * GET /api/order/detail?orderNo=xxx
     */
    @GetMapping("/detail")
    public Result<?> detail(@RequestParam String orderNo,
                            @RequestAttribute(UserContext.USER_ID_ATTR) Long userId) {
       try{
           Order order = orderService.getDetail(userId, orderNo);
           List<OrderItem> items = orderService.listItems(order.getId());

           // 标记每个商品是否已评价
           List<Long> itemIds = items.stream().map(OrderItem::getId).collect(Collectors.toList());
           Map<Long, ProductReview> reviewMap = reviewService.mapByOrderItemIds(itemIds);
           for (OrderItem item : items) {
               ProductReview review = reviewMap.get(item.getId());
               item.setReviewed(review != null);
               if (review != null) {
                   item.setReviewId(review.getId());
                   item.setReviewRating(review.getRating());
               }
           }

           Map<String,Object> data = new HashMap<>();
           data.put("order", order);
           data.put("items", items);
           data.put("canCancel", orderService.canCancel(order));
           data.put("refundStatus", order.getRefundStatus());
           data.put("refunds", refundService.listByOrderNo(orderNo));
           data.put("merchant", order.getMerchantId() == null ? null
                   : merchantMapper.selectById(order.getMerchantId()));
           return Result.ok(data);

       }catch (Exception e) {
           return Result.fail(e.getMessage());
       }
    }
    /**
     * 确认收货
     * POST /api/order/receive?orderNo=xxx
     */
    @PostMapping("/receive")
    public Result<?> receive(@RequestParam String orderNo,
                             @RequestAttribute(UserContext.USER_ID_ATTR) Long userId) {
        try {
            orderService.receive(userId, orderNo);
            return Result.ok(null, "已确认收货");
        } catch (Exception e) {
            return Result.fail(e.getMessage());
        }
    }

    /**
     * 取消订单
     * POST /api/order/cancel?orderNo=xxx
     */
    @PostMapping("/cancel")
    public Result<?> cancel(@RequestParam String orderNo,
                            @RequestAttribute(UserContext.USER_ID_ATTR) Long userId) {
        try{
          orderService.cancel(userId, orderNo);
            return Result.ok(null, "订单已取消");
        }catch (Exception e) {
            return Result.fail(e.getMessage());
        }
    }

    @GetMapping("status/{payNo}")
    public Result<?> queryStatus(@PathVariable String payNo){
        //直接查订单表是否有该 payNo 的订单
        Long cnt = orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                .eq(Order::getPayNo, payNo));
        if(cnt != null && cnt > 0) {
            List<String> orderNos = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                    .eq(Order::getPayNo, payNo)).stream().map(Order::getOrderNo).toList();
            return Result.ok(Map.of("status", "SUCCESS", "orderNos", orderNos));
        }
        //未落库，判断是否超时/失败（可选：查 task 表 last_error）
        return Result.ok(Map.of("status", "CREATING"));
    }
}
