package com.music.controller;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.music.mapper.OrderItemMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.music.common.MerchantContext;
import com.music.common.RefundStatus;
import com.music.common.Result;
import com.music.common.UserContext;
import com.music.entity.Product;
import com.music.entity.RefundOrder;
import com.music.mapper.RefundOrderMapper;
import com.music.service.ProductService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
/**
 * 商家 - 数据看板
 */
@RestController
@RequestMapping("/api/merchant/dashboard")
public class MerchantDashborderController {
    private static final DateTimeFormatter LABEL_FMT = DateTimeFormatter.ofPattern("MM-dd");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int CACHE_MINUTES = 5;

    @Resource
    private MerchantContext merchantContext;

    @Resource
    private ProductService productService;

    @Resource
    private OrderItemMapper orderItemMapper;

    @Resource
    private RefundOrderMapper refundOrderMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 看板汇总
     * POST /api/merchant/dashboard/summary
     */
    @PostMapping("/summary")
    public Result<?> summary(@RequestAttribute(UserContext.USER_ID_ATTR) Long userId) {
        Long merchantId = merchantContext.requireMerchantId(userId);

        long totalProducts = productService.count(
                new LambdaQueryWrapper<Product>().eq(Product::getMerchantId, merchantId));
        long onSaleProducts = productService.count(
                new LambdaQueryWrapper<Product>()
                        .eq(Product::getMerchantId, merchantId)
                        .eq(Product::getStatus, 1));

        Long pendingShip = orderItemMapper.countPendingShip(merchantId);

        Long pendingRefund = refundOrderMapper.selectCount(
                new LambdaQueryWrapper<RefundOrder>()
                        .inSql(RefundOrder::getOrderId,
                                "SELECT DISTINCT order_id FROM order_item WHERE merchant_id = " + merchantId)
                        .eq(RefundOrder::getStatus, RefundStatus.APPLYING));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalProducts", totalProducts);
        data.put("onSaleProducts", onSaleProducts);
        data.put("pendingShip", pendingShip == null ? 0 : pendingShip);
        data.put("pendingRefund", pendingRefund == null ? 0 : pendingRefund);
        data.put("totalSales", orderItemMapper.sumSales(merchantId));
        return Result.ok(data);
    }

    /**
     * 销售统计图数据（Redis 缓存 5 分钟，refresh=1 强制刷新）
     * POST /api/merchant/dashboard/chart?range=7&refresh=0
     */
    @PostMapping("/chart")
    public Result<?> chart(@RequestParam(defaultValue = "7") Integer range,
                           @RequestParam(defaultValue = "0") Integer refresh,
                           @RequestAttribute(UserContext.USER_ID_ATTR) Long userId) {
        Long merchantId = merchantContext.requireMerchantId(userId);
        int days = (range != null && range == 30) ? 30 : 7;
        String key = "merchant:chart:" + merchantId + ":" + days;

        boolean force = refresh != null && refresh == 1;
        if (force) {
            try {
                stringRedisTemplate.delete(key);
            } catch (Exception ignored) {
                // Redis 不可用时降级直查
            }
        } else {
            try {
                String cache = stringRedisTemplate.opsForValue().get(key);
                if (cache != null) {
                    JSONObject cached = JSON.parseObject(cache);
                    cached.put("cached", true);
                    return Result.ok(cached);
                }
            } catch (Exception ignored) {
                // 缓存异常不影响正常统计
            }
        }

        Map<String, Object> data = buildChartData(merchantId, days);
        try {
            stringRedisTemplate.opsForValue().set(key, JSON.toJSONString(data), Duration.ofMinutes(CACHE_MINUTES));
        } catch (Exception ignored) {
            // 写缓存失败忽略
        }
        data.put("cached", false);
        return Result.ok(data);
    }

    private Map<String, Object> buildChartData(Long merchantId, int days) {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(days - 1L);
        LocalDateTime startTime = start.atStartOfDay();
        LocalDateTime endTime = end.plusDays(1).atStartOfDay();

        List<Map<String, Object>> dailyRows = orderItemMapper.selectDailySales(merchantId, startTime, endTime);
        Map<String, double[]> daily = new HashMap<>();
        for (Map<String, Object> row : dailyRows) {
            Object dateObj = row.get("d");
            if (dateObj == null) continue;
            String date = dateObj.toString();
            if (date.length() > 10) date = date.substring(0, 10);
            double amount = row.get("amt") == null ? 0 : ((Number) row.get("amt")).doubleValue();
            double count = row.get("cnt") == null ? 0 : ((Number) row.get("cnt")).doubleValue();
            daily.put(date, new double[]{amount, count});
        }

        List<String> labels = new ArrayList<>();
        List<Double> sales = new ArrayList<>();
        List<Long> orders = new ArrayList<>();
        double totalSales = 0;
        for (int i = 0; i < days; i++) {
            LocalDate date = start.plusDays(i);
            double[] values = daily.getOrDefault(date.toString(), new double[]{0, 0});
            labels.add(date.format(LABEL_FMT));
            sales.add(round2(values[0]));
            orders.add((long) values[1]);
            totalSales += values[0];
        }
        totalSales = round2(totalSales);
        final double total = totalSales;

        List<Map<String, Object>> topProducts = new ArrayList<>();
        List<Map<String, Object>> topRows = orderItemMapper.selectTopProducts(merchantId, startTime, endTime);
        for (Map<String, Object> row : topRows) {
            double amount = row.get("amount") == null ? 0 : ((Number) row.get("amount")).doubleValue();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", row.get("name") == null ? "未知商品" : row.get("name"));
            item.put("amount", round2(amount));
            item.put("ratio", total <= 0 ? 0 : round2(amount / total * 100) / 100);
            topProducts.add(item);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("range", days);
        data.put("labels", labels);
        data.put("sales", sales);
        data.put("orders", orders);
        data.put("totalSales", totalSales);
        data.put("topProducts", topProducts);
        data.put("updatedAt", LocalDateTime.now().format(TIME_FMT));
        return data;
    }

    private double round2(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
