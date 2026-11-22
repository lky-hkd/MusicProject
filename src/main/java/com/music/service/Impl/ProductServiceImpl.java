package com.music.service.Impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.music.dto.OrderCreateRequest;
import com.music.entity.Product;
import com.music.mapper.ProductMapper;
import com.music.service.ProductService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 商品 Service 实现
 *
 * 继承 ServiceImpl<ProductMapper, Product> 后：
 *   - 自动实现 IService<Product> 里的所有方法
 *   - 内部通过 ProductMapper 操作数据库
 *
 * 如果将来有复杂业务（比如"商品上下架要发通知"），
 * 可以在这里重写或新增方法。
 */
@Slf4j
@Service
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product> implements ProductService {

    /** 每个商品每秒一个计数桶：qps:order:product:{productId}:{epochSecond} */
    private static final String HOT_QPS_KEY_PREFIX = "qps:order:product:";

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Value("${app.seckill.hot-product-qps:20}")
    private int hotProductQps;

    @Value("${app.seckill.hot-product-window-seconds:3}")
    private int hotProductWindowSeconds;

    @Override
    public boolean isHotProduct(List<OrderCreateRequest.Item> items) {
        if (items == null || items.isEmpty() || hotProductQps <= 0) {
            return false;
        }
        try {
            Set<Long> productIds = items.stream()
                    .map(OrderCreateRequest.Item::getProductId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            if (productIds.isEmpty()) {
                return false;
            }
            long nowSecond = System.currentTimeMillis() / 1000;
            long threshold = (long) hotProductQps * hotProductWindowSeconds;
            for (Long productId : productIds) {
                String bucketPrefix = HOT_QPS_KEY_PREFIX + productId + ":";
                // 当前这一秒先计数，保证本次请求也被统计进去
                String currentKey = bucketPrefix + nowSecond;
                Long current = stringRedisTemplate.opsForValue().increment(currentKey);
                if (current != null && current == 1L) {
                    stringRedisTemplate.expire(currentKey, Duration.ofSeconds(hotProductWindowSeconds + 1));
                }
                long count = current == null ? 0L : current;
                // 汇总前几个秒桶
                for (int i = 1; i < hotProductWindowSeconds; i++) {
                    String val = stringRedisTemplate.opsForValue().get(bucketPrefix + (nowSecond - i));
                    if (val != null) {
                        count += Long.parseLong(val);
                    }
                }
                if (count >= threshold) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            // 热点判定只是分流开关，任何异常都退回同步下单，不能影响下单主流程
            log.warn("热点商品判定失败，走同步下单", e);
            return false;
        }
    }
}
