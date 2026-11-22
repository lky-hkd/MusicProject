package com.music.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.music.common.BizException;
import com.music.config.OrderProperties;
import com.music.config.RabbitMQConfig;
import com.music.dto.*;
import com.music.entity.*;
import com.music.mapper.*;
import com.music.service.AddressService;
import com.music.service.OrderService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.music.common.OrderStatus.*;

@Slf4j
@Service
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements OrderService {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    OrderItemMapper orderItemMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private AddressService addressService;

    @Autowired
    private CartMapper cartMapper;

    @Autowired
    private RedisStockService redisStockService;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private com.music.service.OrderCancelService orderCancelService;

    @Autowired
    private com.music.service.VipService vipService;

    @Autowired
    private com.music.mapper.MerchantMapper merchantMapper;

    @Autowired
    private com.music.service.CouponService couponService;

    @Resource
    private SeckillOrderTaskMapper seckillOrderTaskMapper;

    /** 订单已处理标记（已支付 / 已取消）：命中则 MQ 到点消息秒跳过，零 DB 查询 */
    private static final String ORDER_DONE_PREFIX = "order:done:";
    /** 订单库存已回滚标记：SETNX 保证只回滚一次（幂等） */
    private static final String ORDER_ROLLBACK_PREFIX = "order:rollback:";
    /** 标记过期时间（分钟），需大于订单超时时长，留足余量 */
    private static final long ORDER_FLAG_EXPIRE_MINUTES = OrderProperties.FLAG_EXPIRE_MINUTES;
    private static final int SCAN_BATCH_SIZE = 100;
    private static final int SCAN_MAX_ROUNDS = 50;

    /**
     * 订单确认页数据
     *
     * 1. 根据 productId 查商品
     * 2. 判断是否有实物商品 → 决定是否返回地址
     * 3. 计算总金额
     */
    @Override
    public OrderConfirmResponse confirmOrder(Long userId, OrderConfirmRequest request) {
        if(request.getItems()==null || request.getItems().isEmpty()){
            throw new BizException("商品列表为空");
        }

        List<ProductQty> requested = request.getItems().stream()
                .map(i -> new ProductQty(i.getProductId(), i.getQuantity()))
                .toList();

        ProductCheckResult result = buildOrderItems(requested);

        // 防刷单:确认页就拦截商家买自己店铺的商品
        checkNotSelfProduct(userId, result.items);

        OrderConfirmResponse resp = new OrderConfirmResponse();
        List<OrderConfirmResponse.Item> items=new ArrayList<>();
        for (OrderItem oi : result.items) {
            OrderConfirmResponse.Item ri = new OrderConfirmResponse.Item();
            ri.setProductId(oi.getProductId());
            ri.setName(oi.getProductName());
            ri.setCoverUrl(oi.getProductCover());
            ri.setPrice(oi.getPrice());
            ri.setQuantity(oi.getQuantity());
            ri.setSubtotal(oi.getSubtotal());
            ri.setProductType(oi.getProductType());
            items.add(ri);
        }
        resp.setItems(items);
        resp.setTotalAmount(result.total);
        resp.setNeedAddress(result.needAddress);

        if (result.needAddress) {
            resp.setAddresses(addressService.getAddressByUserId(userId));
        }
        return resp;
    }

    /**
     * 热点商品异步下单。
     * 与 createOrder 的区别：只做「校验 + 扣 Redis 库存 + 登记 task + 投 MQ」，不落订单表。
     */
    @Override
    public OrderCreateResult seckillCreateOrder(Long userId, OrderCreateRequest request) {
        if(request.getItems()==null || request.getItems().isEmpty()){
            throw new BizException("商品列表为空");
        }
        List<ProductQty> requested = request.getItems().stream()
                .map(i->new ProductQty(i.getProductId(),i.getQuantity()))
                .toList();
        ProductCheckResult result = buildOrderItems(requested);
        checkNotSelfProduct(userId, result.items);

        // 地址校验（实物）
        Address address = null;
        if (result.needAddress) {
            if (request.getAddressId() == null) throw new BizException("请选择收货地址");
            address = addressService.getById(request.getAddressId());
            if (address == null) throw new BizException("地址不存在");
            if (!address.getUserId().equals(userId)) throw new BizException("无权使用此地址");
        }
        // 1. 预分配 payNo，先核销优惠券（与同步链路一致：先校验券再扣库存）
        String payNo = generateOrderNo();
        BigDecimal couponDiscount = BigDecimal.ZERO;
        if (request.getUserCouponId() != null) {
            couponDiscount = couponService.useForOrder(
                    request.getUserCouponId(), userId, result.total, payNo);
        }
        // 2. 扣 Redis 库存（失败要连带把券还回去）
        List<ProductQty> deducted = new ArrayList<>();
        try{
            for(OrderItem oi : result.items){
                if (!redisStockService.deductStock(oi.getProductId(), oi.getQuantity())) {
                    throw new BizException("库存不足，商品已被抢完：" + oi.getProductId());
                }
                deducted.add(new ProductQty(oi.getProductId(),oi.getQuantity()));
            }
        } catch (RuntimeException e) {
            deducted.forEach(d -> redisStockService.rollbackStock(d.productId, d.quantity));
            couponService.restoreCouponIfBatchCanceled(payNo);
            throw e;
        }
        // 3. 预分配 orderNo
        String orderNo = generateOrderNo();
        // 4. 组装 payload
        SeckillProductPayload payload = new SeckillProductPayload();
        payload.setPayNo(payNo);
        payload.setItems(result.items);
        payload.setDiscountAmount(couponDiscount);
        payload.setRemark(request.getRemark());
        payload.setSource(request.getSource());
        if (address != null) {
            payload.setAddressId(address.getId());
            payload.setReceiver(address.getReceiver());
            payload.setPhone(address.getPhone());
            payload.setAddressDetail(
                    (address.getProvince() == null ? "" : address.getProvince())
                            + (address.getCity() == null ? "" : address.getCity())
                            + (address.getDistrict() == null ? "" : address.getDistrict())
                            + (address.getDetail() == null ? "" : address.getDetail()));
        }
        // 5. 登记 task（权威落库凭证）
        SeckillOrderTask task = new SeckillOrderTask();
        task.setOrderNo(orderNo);
        task.setBizType(SeckillOrderTask.BIZ_PRODUCT);
        task.setUserId(userId);
        task.setPayload(com.alibaba.fastjson2.JSON.toJSONString(payload));
        task.setStatus(SeckillOrderTask.STATUS_CREATING);
        task.setRetryCount(0);
        try{
            seckillOrderTaskMapper.insert(task);
        } catch (Exception e) {
            deducted.forEach(d -> redisStockService.rollbackStock(d.productId, d.quantity));
            couponService.restoreCouponIfBatchCanceled(payNo);
            throw new BizException("系统繁忙，请重试");
        }
        // 6. 投 MQ（失败则回滚库存+删 task+退券）
        try{
            SeckillMessage msg = new SeckillMessage(orderNo, SeckillOrderTask.BIZ_PRODUCT, userId, task.getPayload());
            rabbitTemplate.convertAndSend(RabbitMQConfig.SECKILL_ORDER_EXCHANGE,
                    RabbitMQConfig.SECKILL_ORDER_ROUTING_KEY,
                    com.alibaba.fastjson2.JSON.toJSONString(msg));
        } catch (Exception e) {
            log.error("秒杀消息投递失败 orderNo={}", orderNo, e);
            deducted.forEach(d -> redisStockService.rollbackStock(d.productId, d.quantity));
            seckillOrderTaskMapper.deleteById(task.getId());
            couponService.restoreCouponIfBatchCanceled(payNo);
            throw new BizException("系统繁忙，请重试");
        }
        // 7. 立即返回
        OrderCreateResult res = new OrderCreateResult();
        res.setPayNo(payNo);
        res.setOrderNos(List.of(orderNo));
        res.setTotalAmount(result.total.subtract(couponDiscount));
        return res;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderCreateResult createOrder(Long userId, OrderCreateRequest request) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new BizException("商品列表为空");
        }

        List<ProductQty> requested = request.getItems().stream()
                .map(i -> new ProductQty(i.getProductId(), i.getQuantity()))
                .toList();
        ProductCheckResult result = buildOrderItems(requested);

        // ---------- 0. 防刷单:商家不能购买自己店铺的商品 ----------
        checkNotSelfProduct(userId, result.items);

        // ---------- 1. 商品快照 / 总金额 / 是否需地址 ----------
        List<OrderItem> items = result.items;          // 已合并去重，每个 productId 一条
        BigDecimal total = result.total;
        boolean needAddress = result.needAddress;

        // ---------- 2. 校验地址 ----------
        Address address = null;
        if (needAddress) {
            if (request.getAddressId() == null) {
                throw new BizException("请选择收货地址");
            }
            address = addressService.getById(request.getAddressId());
            if (address == null) throw new BizException("地址不存在");
            if (!address.getUserId().equals(userId)) throw new BizException("无权使用此地址");
        }

        // ---------- 2.5 优惠券核销（先校验再扣库存，避免无效券占用库存）----------
        // payNo 提前生成：券核销时记录批次号，整批订单取消后用于回滚优惠券
        String payNo = generateOrderNo();
        BigDecimal couponDiscount = BigDecimal.ZERO;
        if (request.getUserCouponId() != null) {
            couponDiscount = couponService.useForOrder(
                    request.getUserCouponId(), userId, total, payNo);
        }

        // ---------- 3. 原子扣库存（防超卖，遍历合并后的 items）----------
        List<ProductQty> deducted = new ArrayList<>();

        // 先注册回滚兜底，再扣减：这样循环中途抛异常也能回滚已扣部分
        // 事务回滚兜底：提交失败时回滚已扣的 Redis 库存
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK && !deducted.isEmpty()) {
                    log.warn("订单事务提交失败(回滚)，补回滚 Redis 库存");
                    for (ProductQty it : deducted) {
                        redisStockService.rollbackStock(it.productId, it.quantity);
                    }
                }
            }
        });
        try {
            for (OrderItem oi : result.items) {
                boolean success = redisStockService.deductStock(oi.getProductId(), oi.getQuantity());
                if (!success) {
                    throw new BizException("库存不足，商品已被抢完" + oi.getProductId());
                }
                deducted.add(new ProductQty(oi.getProductId(), oi.getQuantity()));
            }
        } catch (RuntimeException e) {
            throw e;   // 回滚统一交给 afterCompletion
        }


        // ---------- 4. 创建订单主表 ----------
        // merchantId 可能为 null（平台自营 / 历史种子商品），用 0 作为「平台自营」哨兵，
        // 避免 Collectors.groupingBy 遇到 null key 抛 NullPointerException
        Map<Long,List<OrderItem>> byMerchant = items.stream()
                .collect(Collectors.groupingBy(
                        oi -> oi.getMerchantId() == null ? 0L : oi.getMerchantId(),
                        LinkedHashMap::new,Collectors.toList()));

        List<Order> created = new ArrayList<>();
        // 优惠券按拆分后的订单顺序抵扣：先抵满第一单，剩余继续抵下一单
        BigDecimal remainingDiscount = couponDiscount;
        for(Map.Entry<Long,List<OrderItem>> e:byMerchant.entrySet()) {
            Long merchantId = e.getKey();
            List<OrderItem> groupItems = e.getValue();//本店铺

            BigDecimal groupTotal = groupItems.stream()
                    .map(OrderItem::getSubtotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal groupDiscount = BigDecimal.ZERO;
            if (remainingDiscount.compareTo(BigDecimal.ZERO) > 0) {
                groupDiscount = remainingDiscount.min(groupTotal);
                remainingDiscount = remainingDiscount.subtract(groupDiscount);
            }

            Order order = new Order();
            order.setOrderNo(generateOrderNo());
            order.setPayNo(payNo);
            // 哨兵 0 还原为 null：平台自营订单不属于任何店铺
            order.setMerchantId(merchantId == 0L ? null : merchantId);
            order.setUserId(userId);
            order.setTotalAmount(groupTotal);
            order.setPayAmount(groupTotal.subtract(groupDiscount));
            order.setDiscountAmount(groupDiscount);
            order.setStatus(PENDING_PAY);  // 待付款
            order.setRemark(request.getRemark());
            order.setCreateTime(LocalDateTime.now());
            order.setUpdateTime(LocalDateTime.now());

            if (address != null) {
                order.setAddressId(address.getId());
                order.setReceiver(address.getReceiver());
                order.setPhone(address.getPhone());
                order.setAddressDetail(
                        (address.getProvince() != null ? address.getProvince() : "") +
                                (address.getCity() != null ? address.getCity() : "") +
                                (address.getDistrict() != null ? address.getDistrict() : "") +
                                (address.getDetail() != null ? address.getDetail() : "")
                );
            }

            orderMapper.insert(order);

            // ---------- 5. 创建订单明细（只插入本店铺的 groupItems）----------
            for (OrderItem oi : groupItems) {
                oi.setOrderId(order.getId());
                orderItemMapper.insert(oi);
                productMapper.addSales(oi.getProductId(), oi.getQuantity());
            }
            created.add(order);
        }
        // ---------- 6. 购物车来源则删除对应项（遍历合并后的 items）----------
        if ("cart".equals(request.getSource())) {
            for (OrderItem oi : result.items) {
                LambdaQueryWrapper<Cart> cqw = new LambdaQueryWrapper<>();
                cqw.eq(Cart::getUserId, userId);
                cqw.eq(Cart::getProductId, oi.getProductId());
                cartMapper.delete(cqw);
            }
        }

        // ---------- 7. 事务提交后发延迟消息（失败由兜底扫描补）----------
        afterCommit(() -> {
            for(Order o:created) {
                try {
                    rabbitTemplate.convertAndSend(
                            RabbitMQConfig.ORDER_DELAY_EXCHANGE,
                            RabbitMQConfig.ORDER_DELAY_ROUTING_KEY,
                            o.getOrderNo(),
                            message -> {
                                message.getMessageProperties().setContentType("text/plain");
                                return message;
                            }
                    );
                    log.info("延迟消息已发送到 MQ: " + o.getOrderNo());
                } catch (Exception e) {
                    log.error("发送延迟消息失败: " + o.getOrderNo(), e);
                }
            }
        });

        OrderCreateResult res = new OrderCreateResult();
        res.setPayNo(payNo);
        res.setTotalAmount(total.subtract(couponDiscount));
        res.setOrderNos(created.stream().map(Order::getOrderNo).toList());
        return res;
    }

//     模拟支付
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void pay(Long userId, String payNo, String payType) {
        List<Order> orders = this.list(new LambdaQueryWrapper<Order>()
                .eq(Order::getPayNo, payNo)
                .eq(Order::getStatus, PENDING_PAY));
        if(orders.isEmpty()){
            throw new BizException("订单不存在");
        }
        if (!orders.get(0).getUserId().equals(userId)) throw new BizException("无权操作");
        for (Order o : orders) {
            boolean ok = this.update(new LambdaUpdateWrapper<Order>()
                    .eq(Order::getId, o.getId())
                    .eq(Order::getStatus, PENDING_PAY)
                    .set(Order::getStatus, PAID)
                    .set(Order::getPayType, payType != null ? payType : "mock")
                    .set(Order::getPayTime, LocalDateTime.now())
                    .set(Order::getUpdateTime, LocalDateTime.now()));
            if (!ok) throw new BizException("订单状态已变更，支付失败");
        }
        afterCommit(() -> orders.forEach(o -> markOrderDone(o.getOrderNo())));
        afterCommit(() -> deliverVipIfNeeded(orders));

    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void paySuccess(String tradeNo, String payNo, BigDecimal totalAmount) {
        List<Order> orders = this.list(new LambdaQueryWrapper<Order>()
                .eq(Order::getPayNo, payNo));
        if(orders.isEmpty()){
            log.error("回调订单不存在: {}", payNo);
            return;
        }
        // 金额校验：防止伪造回调改金额
        BigDecimal sum = orders.stream().map(Order::getPayAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if(sum.compareTo(totalAmount) != 0){
            log.error("金额不匹配 批次:{}, 应付:{}, 回调:{}", payNo, sum, totalAmount);
            throw new BizException("金额校验失败");
        }

        // 逐单更新：已付款的跳过、CAS 失败的也跳过，不中断整批
        for(Order o : orders){
            if (!Integer.valueOf(PENDING_PAY).equals(o.getStatus())) {
                log.info("订单 {} 已处理, 跳过重复回调", o.getOrderNo(), o.getStatus());
                continue;
            }
            boolean updated=this.update(new LambdaUpdateWrapper<Order>()
                    .eq(Order::getId, o.getId())
                    .eq(Order::getStatus, PENDING_PAY)
                    .set(Order::getStatus, PAID)
                    .set(Order::getPayType, "alipay")
                    .set(Order::getPayTime, LocalDateTime.now())
                    .set(Order::getTradeNo, tradeNo)
                    .set(Order::getUpdateTime, LocalDateTime.now()));
            if(!updated){
                log.info("订单 {} 状态已变更, 忽略本次回调", o.getOrderNo());
                continue;
            }
            afterCommit(() -> markOrderDone(o.getOrderNo()));
            afterCommit(() -> deliverVipIfNeeded(List.of(o)));
        }
    }

    /**
     * 支付成功后发放 VIP:订单明细中 productType=virtual 且 virtualType 以 "vip_" 开头的,
     * 调用 VipService 发放/续费。失败只记日志,不影响支付主流程。
     */
    private void deliverVipIfNeeded(List<Order> orders) {
        try {
            for (Order order : orders) {
                List<OrderItem> items = listItems(order.getId());
                for (OrderItem item : items) {
                    String vt = item.getVirtualType();
                    if ("virtual".equals(item.getProductType()) && vt != null && vt.startsWith("vip_")) {
                        vipService.grantVip(order.getUserId(), vt, item.getVirtualValue());
                    }
                }
            }
        } catch (Exception e) {
            log.error("VIP发放失败(不影响支付流程,需人工核查)", e);
        }
    }
    /** 查询同一 tradeNo 已关联的 payNo（用于防重放） */
    private String getExistingTradeNoPayNo(Order o) {
        if (o.getTradeNo() == null) return null;
        Order exist = this.getOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getTradeNo, o.getTradeNo())
                .last("LIMIT 1"));
        return exist == null ? null : exist.getPayNo();
    }

    @Override
    public List<Order> ListByUserId(Long userId) {
        LambdaQueryWrapper<Order> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Order::getUserId, userId);
        queryWrapper.orderByDesc(Order::getCreateTime);

        return this.list(queryWrapper);
    }

    @Override
    public List<OrderItem> listItemsByOrderIds(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) return List.of();
        return orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().in(OrderItem::getOrderId, orderIds));
    }

    @Override
    public Order getDetail(Long userId, String orderNo) {
        Order order= getByOrderNo(orderNo);
        if (order == null) throw new BizException("订单不存在");
        if (!order.getUserId().equals(userId)) throw new BizException("无权查看此订单");
        return order;
    }

    @Override
    public List<OrderItem> listItems(Long orderId) {
        LambdaQueryWrapper<OrderItem> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(OrderItem::getOrderId, orderId);
        return orderItemMapper.selectList(queryWrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long userId, String orderNo) {
        Order order = getByOrderNo(orderNo);
        if (order == null) throw new BizException("订单不存在");
        if (!order.getUserId().equals(userId)) throw new BizException("无权操作");
//        if (Integer.valueOf(0).equals(order.getStatus())) throw new BizException("只有待付款订单可以取消");

        int status = order.getStatus();
        // 待付款：直接取消 + 回滚库存
        if (status == PENDING_PAY) {
            doCancelPending(order);
            return;
        }

        // 已付款/已发货：不允许直接取消，引导走退款（实物单）
        if (status == PAID || status == SHIPPED) {
            List<OrderItem> items = listItems(order.getId());
            boolean hasPhysical = items.stream().anyMatch(i -> "physical".equals(i.getProductType()));
            if (!hasPhysical) throw new BizException("虚拟商品付款后不支持取消/退款");
            throw new BizException("订单已付款，请通过“申请退款”处理");
        }

        throw new BizException("当前订单状态不可取消");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void receive(Long userId, String orderNo) {
        Order order = getByOrderNo(orderNo);
        if (order == null) throw new BizException("订单不存在");
        if (!order.getUserId().equals(userId)) throw new BizException("无权操作");
        if (Integer.valueOf(COMPLETED).equals(order.getStatus())) return;
        if (!Integer.valueOf(SHIPPED).equals(order.getStatus())) {
            throw new BizException("只有已发货订单可以确认收货");
        }

        LambdaUpdateWrapper<Order> uw = new LambdaUpdateWrapper<>();
        uw.set(Order::getStatus, COMPLETED);
        uw.set(Order::getFinishTime, LocalDateTime.now());
        uw.set(Order::getUpdateTime, LocalDateTime.now());
        uw.eq(Order::getId, order.getId());
        uw.eq(Order::getStatus, SHIPPED);
        if (!this.update(uw)) {
            throw new BizException("订单状态已变更，请刷新后重试");
        }
    }

    private void doCancelPending(Order order){
        //        更新状态
        LambdaUpdateWrapper<Order> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.set(Order::getStatus, CANCELED);
        updateWrapper.set(Order::getUpdateTime, LocalDateTime.now());
        updateWrapper.eq(Order::getId, order.getId());
        updateWrapper.eq(Order::getStatus, PENDING_PAY); // 乐观锁，防止与支付并发
        boolean updated = this.update(updateWrapper);

        if (!updated) {
            throw new BizException("订单状态已变更，请刷新后重试");
        }

        //  提交后：回滚库存（幂等）+ 回滚优惠券 + 标记已处理
        afterCommit(() -> {
            redisStockService.rollbackOrderStockIdempotent(order.getId(), listItems(order.getId()));
            couponService.restoreCouponIfBatchCanceled(order.getPayNo());
            markOrderDone(order.getOrderNo());
        });

    }

    /**
     * 判断订单当前是否可取消（供前端控制按钮显隐）
     */
    @Override
    public boolean canCancel(Order order) {
        if (order == null || order.getStatus() == null) return false;
        int status = order.getStatus();
        if (status == PENDING_PAY) return true;                       // 待付款都可取消
        if (status != PAID && status != SHIPPED) return false;       // 已完成/已取消/已退款不可
        // 已付款/已发货：仅含实物可取消
        return listItems(order.getId()).stream()
                .anyMatch(i -> "physical".equals(i.getProductType()));
    }


    /**
     * 阶梯扫描超时订单
     * @param minMinutes 订单创建时间最小值（分钟前）
     * @param maxMinutes 订单创建时间最大值（分钟前）
     * @return 处理的订单数
     */
    @Override
    public int scanAndCancelOrders(int minMinutes,int maxMinutes) {
         LocalDateTime now = LocalDateTime.now();
        LocalDateTime maxTime = now.minusMinutes(minMinutes);
        LocalDateTime minTime = now.minusMinutes(maxMinutes);
        LambdaQueryWrapper<Order> qw = new LambdaQueryWrapper<>();
        qw.eq(Order::getStatus, PENDING_PAY);
        qw.ge(Order::getCreateTime, minTime);
        qw.lt(Order::getCreateTime, maxTime);

        qw.last("LIMIT 100");
        List<Order> timeoutOrders = orderMapper.selectList(qw);
        return cancelBatch(timeoutOrders, now);

        // 限制一次最多查 100 条，防止极端情况卡死
//        qw.last("LIMIT 100");
//        List<Order> timeoutOrders = orderMapper.selectList(qw);
//        for(Order order:timeoutOrders){
            // 再次确认状态，防止并发支付
//            LambdaUpdateWrapper<Order> uw = new LambdaUpdateWrapper<>();
//            uw.set(Order::getStatus, 4);   //已取消
//            uw.set(Order::getUpdateTime,now);
//            uw.eq(Order::getId, order.getId());
//            uw.eq(Order::getStatus,0);

//            boolean updates = this.update(uw);
//            if(updates){
//                rollbackOrderStock(order.getId()); // 回滚库存
//            }
//        }
//        return timeoutOrders.size();
    }


    /**
     * 兜底扫描：取消「创建时间早于 expireMinutes 分钟、仍未付款」的订单
     * 走 (status, create_time) 索引，分页 LIMIT，按 id 升序推进，避免全表扫。
     */
    @Override
    public int scanExpiredOrders(int expireMinutes) {

        int total = 0;
        for(int round = 0; round < SCAN_MAX_ROUNDS; round++){
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime deadline = now.minusMinutes(expireMinutes);

            LambdaQueryWrapper<Order> qw = new LambdaQueryWrapper<>();
            qw.eq(Order::getStatus, PENDING_PAY);
            qw.lt(Order::getCreateTime, deadline);
            qw.orderByAsc(Order::getId);
            qw.last("LIMIT " + SCAN_BATCH_SIZE);
            List<Order> batch = orderMapper.selectList(qw);

            if(batch.isEmpty()){
                break;
            }
            int canceled = cancelBatch(batch, now);
            total += canceled;
            // 本批全部取到但没取消任何一条（说明这批都被并发支付/取消抢走了）
            // 为避免死循环，若本批“全未成功”且数量达到批量上限，则继续；否则取不满即退出
            if (batch.size() < SCAN_BATCH_SIZE) {
                break;
            }
        }
        return total;
    }

    /**
     * 批量取消：逐单 CAS 改状态，成功才回滚库存 + 标记已处理
     * @return 本轮真正取消成功的订单数
     */
    private int cancelBatch(List<Order> timeoutOrders, LocalDateTime now) {
        int canceled = 0;
        for(Order o : timeoutOrders){
            try{
                if(orderCancelService.cancelOneInNewTx(o.getId(),o.getOrderNo(),now)){
                    canceled++;
                }
            }catch (Exception e){
                // 单条失败只记录，继续处理下一条
                log.error("取消订单失败: {}", o.getOrderNo(), e);
            }
        }
        return canceled;
    }


    /**
     * 处理超时未支付订单
     * MQ 延迟队列 15 分钟后会调用此方法
     */
    @Override
    public void cancelTimeoutOrder(String orderNo) {
        if(isOrderDone(orderNo)){
            log.info("命中已处理标记，跳过：" + orderNo);
            return;
        }
        log.info("======== 进入 cancelTimeoutOrder ========");
        log.info("======== 收到的订单号: " + orderNo);

        // 1. 根据订单号查订单
        LambdaQueryWrapper<Order> qw = new LambdaQueryWrapper<>();
        qw.eq(Order::getOrderNo, orderNo);
        Order order = this.getOne(qw);

        // 2. 如果订单不存在，直接返回
        if (order == null) {
            log.info("[超时处理] 订单不存在：" + orderNo);
            return;
        }

        // 3. 如果订单状态不是 0 (待付款)，说明用户已经支付或者手动取消了，不需要处理
        if (!Integer.valueOf(PENDING_PAY).equals(order.getStatus())) {
            log.info("[超时处理] 订单状态已变更（可能已支付），跳过：" + orderNo);
            return;
        }

        // 4. 更新订单状态为 4 (已取消)
        // 注意：这里加上 status = 0 的条件，防止和用户主动支付产生并发冲突
//        LambdaUpdateWrapper<Order> uw = new LambdaUpdateWrapper<>();
//        uw.set(Order::getStatus, CANCELED);
//        uw.set(Order::getUpdateTime, LocalDateTime.now());
//        uw.eq(Order::getId, order.getId());
//        uw.eq(Order::getStatus, PENDING_PAY); // 乐观锁条件
//        boolean updated = this.update(uw);
        // 5. 如果状态更新成功，回滚库存
//        if (updated) {
//            log.info("[超时处理] 订单已超时取消，注册库存回滚回调：" + orderNo);
//            afterCommit(() -> {
//                redisStockService.rollbackOrderStockIdempotent(order.getId(), listItems(order.getId()));
//                markOrderDone(order.getOrderNo());
//                log.info("[超时处理] 库存回滚完成：" + orderNo);
//            });
//        }
        // 复用独立事务 + CAS + 幂等回滚（内部已含 afterCommit 回滚 + markOrderDone）
        boolean canceled = orderCancelService.cancelOneInNewTx(
                order.getId(), order.getOrderNo(), LocalDateTime.now());
        if (canceled) {
            log.info("[超时处理] 订单已超时取消：{}", orderNo);
        } else {
            // 并发下可能已被用户主动支付/取消，属正常情况
            log.info("[超时处理] 订单已被并发处理，跳过：{}", orderNo);
        }

    }


    private Order getByOrderNo(String orderNo) {
        LambdaQueryWrapper<Order> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Order::getOrderNo, orderNo);
        return this.getOne(queryWrapper);
    }
    /**
     * 生成订单号：时间戳 + 4位随机 + 用户id后2位
     */
    private String generateOrderNo(){
//        long ts=System.currentTimeMillis();
//        int random = ThreadLocalRandom.current().nextInt(1_000_000);
//        String userSuffix = String.format("%02d",userId % 100);
//        return ts + String.format("%06d", random) + userSuffix;
        //雪花 ID 是 Long，转 String 即可，长度固定，不会撞。
        return String.valueOf(com.baomidou.mybatisplus.core.toolkit.IdWorker.getId());
    }


    /**
     * 标记订单已处理（已支付 / 已取消）。
     * 写入 order:done:{orderNo} = "1"，带过期时间（大于订单超时时长，留足余量）。
     * MQ 延迟消息到点时若命中此标记，直接跳过，无需查库。
     */
    private void markOrderDone(String orderNo) {
        try {
            stringRedisTemplate.opsForValue().set(
                    ORDER_DONE_PREFIX + orderNo,
                    "1",
                    Duration.ofMinutes(ORDER_FLAG_EXPIRE_MINUTES)
            );
        } catch (Exception e) {
            // Redis 异常不影响主流程（标记只是加速，DB 仍能兜底）
            log.error("写入订单已处理标记失败：" + orderNo + "，原因：" + e.getMessage());
        }
    }

    /**
     * 判断订单是否已处理（Redis 标记是否存在）。
     * Redis 异常时返回 false，退化为查库逻辑，保证正确性。
     */
    private boolean isOrderDone(String orderNo) {
        try {
            Boolean exists = stringRedisTemplate.hasKey(ORDER_DONE_PREFIX + orderNo);
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            log.error("查询订单已处理标记失败：" + orderNo + "，退化为查库，原因：" + e.getMessage());
            return false;
        }
    }


    private void afterCommit(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { action.run(); }
        });
    }

    /**
     * 防刷单:商家不能购买自己店铺的商品。
     * 用户不是商家或商品全是平台自营(merchantId 为 null)时直接放行。
     */
    private void checkNotSelfProduct(Long userId, List<OrderItem> items) {
        Merchant self = merchantMapper.selectOne(
                new LambdaQueryWrapper<Merchant>().eq(Merchant::getUserId, userId));
        if (self == null) return;   // 不是商家,无自购风险
        for (OrderItem oi : items) {
            if (self.getId().equals(oi.getMerchantId())) {
                throw new BizException("不能购买自己店铺的商品:" + oi.getProductName());
            }
        }
    }

    /** 商品校验结果载体 */
    private static class ProductCheckResult{
        final List<OrderItem> items;
        final BigDecimal total;
        final boolean needAddress;
        ProductCheckResult(List<OrderItem> items, BigDecimal total, boolean needAddress) {
            this.items = items;
            this.total = total;
            this.needAddress = needAddress;
        }
    }

    /** 通用商品项载（避免依赖具体 DTO 的 Item 类） */
    private static class ProductQty {
        final Long productId;
        final Integer quantity;
        ProductQty(Long productId, Integer quantity) {
            this.productId = productId;
            this.quantity = quantity;
        }
    }

    // 避免 N+1，并合并重复 productId 的数量
    private ProductCheckResult buildOrderItems(List<ProductQty> requested) {

        if (requested == null || requested.isEmpty()) {
            throw new BizException("商品列表为空");
        }

        // ---------- 0. 合并相同 productId 的数量（保持插入顺序）----------
        Map<Long, Integer> qtyMap = new java.util.LinkedHashMap<>();
        for (ProductQty q : requested) {
            if (q.quantity == null || q.quantity <= 0) {
                throw new BizException("商品数量非法");
            }
            qtyMap.merge(q.productId, q.quantity, Integer::sum);
        }

        // ---------- 1. 一次批量查商品 ----------
        List<Long> productIds = new ArrayList<>(qtyMap.keySet());
        Map<Long, Product> productMap = productMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        // ---------- 2. 校验商品 + 计算总金额 + 构造快照 ----------
        List<OrderItem> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        boolean needAddress = false;

        for (Map.Entry<Long, Integer> entry : qtyMap.entrySet()) {
            Long productId = entry.getKey();
            Integer quantity = entry.getValue();

            Product p = productMap.get(productId);
            if (p == null) throw new BizException("商品不存在：" + productId);
            if (p.getStatus() == null || p.getStatus() != 1) throw new BizException("商品已下架：" + p.getName());
            if (p.getPrice() == null) throw new BizException("商品价格异常：" + p.getName());

            if ("physical".equals(p.getProductType())) needAddress = true;

            // 构造明细（快照）
            OrderItem oi = new OrderItem();
            oi.setProductId(p.getId());
            oi.setMerchantId(p.getMerchantId());
            oi.setProductName(p.getName());
            oi.setProductCover(p.getCoverUrl());
            oi.setProductType(p.getProductType());
            oi.setVirtualType(p.getVirtualType());
            oi.setVirtualValue(p.getVirtualValue());
            oi.setPrice(p.getPrice());
            oi.setQuantity(quantity);
            oi.setSubtotal(p.getPrice().multiply(new BigDecimal(quantity)));
            items.add(oi);

            total = total.add(oi.getSubtotal());
        }

        return new ProductCheckResult(items, total, needAddress);
    }


}

