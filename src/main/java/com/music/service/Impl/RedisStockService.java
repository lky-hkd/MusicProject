package com.music.service.Impl;

import com.music.common.BizException;
import com.music.common.ProductStatus;
import com.music.entity.OrderItem;
import com.music.entity.Product;
import com.music.mapper.ProductMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.music.common.OrderStatus.PAID;

@Slf4j
@Service
public class RedisStockService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    @Qualifier("seckillScript")
    private DefaultRedisScript<Long> seckillScript;

    private static final String STOCK_KEY_PREFIX = "product:stock:";
    private static final String ORDER_ROLLBACK_PREFIX = "order:rollback:";
    private static final long ROLLBACK_FLAG_EXPIRE_MINUTES = 25;
    @Autowired
    private DefaultRedisScript<Long> rollbackStockScript;

    /**
     * 读取单个商品的 Redis 库存，key 不存在返回 null
     */
    public Integer getRedisStock(Long productId) {
        String key = STOCK_KEY_PREFIX + productId;
        String val = redisTemplate.opsForValue().get(key);
        if (val == null) {
            return null;
        }
        try {
            return Integer.valueOf(val);
        } catch (NumberFormatException e) {
            return null;
        }
    }


    /**
     * 扫描所有 Redis 库存 key，返回 productId（供对账任务使用）
     */
    public Set<String> scanStockKeys() {
        Set<String> keySet = new HashSet<>();   //SCAN会有重复key,HashSet去重
        // 匹配 pattern: stock:*
        String pattern = STOCK_KEY_PREFIX + "*";

        // 构建Scan参数：匹配规则，COUNT 建议设 20~100，只是hint，不保证返回数量
        ScanOptions scanOptions = ScanOptions.scanOptions()
                .match(pattern)
                .count(50)
                .build();

        // 获取游标，0 开始遍历
        try (Cursor<String> cursor = redisTemplate.scan(scanOptions)) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                keySet.add(key);
            }
        }
        return keySet;
    }

    /**
     * ✅ 项目启动时，把数据库库存预热到 Redis
     */
    @PostConstruct
    public void preloadStock(){
        try{
            List<Product> products = productMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Product>()
                            .eq(Product::getStatus, ProductStatus.ON_SALE)
                            .select(Product::getId,Product::getStock));
            for(Product product : products){
                String key = STOCK_KEY_PREFIX + product.getId();
                //预热只在 key 不存在时初始化，绝不覆盖已有实时库存：
                redisTemplate.opsForValue().setIfAbsent(key,String.valueOf(product.getStock()));
            }
            log.info("商品库存预热到Redis完成，共{}个",products.size());
        } catch (Exception e) {
            log.error("库存预热失败，应用继续启动，稍后由对账任务补偿",e);
        }
    }

    /**
     * 扣减 Redis 库存 (Lua 脚本原子操作)
     * @return true 扣减成功，false 库存不足
     */
    public boolean deductStock(Long productId, Integer quantity) {
        String key = STOCK_KEY_PREFIX + productId;

        List<String> keys = Collections.singletonList(key);
        String args = String.valueOf(quantity);
        try {
            // 确保传进去的是字符串
            Long result = redisTemplate.execute(seckillScript, keys, args);
            if (result != null && result == 1L) {
                log.info(" Redis 扣减库存成功: 商品ID=" + productId + ", 数量=" + quantity);
                return true;
            }
            log.warn(" Redis 扣减失败: 库存不足，商品ID=" + productId);
            return false;
        } catch (Exception e) {
            log.error("Redis扣减库存异常:productId={}",productId , e);
            throw new BizException("系统繁忙，请稍后重试");
        }
    }

    /**
     * 幂等回滚某订单占用的库存。
     * 用 SETNX 保证同一订单只回滚一次，重复调用直接跳过。
     *
     * @param orderId   订单 id
     * @param items     订单明细（调用方已查好，避免方法内再查一次）
     */
    public void rollbackOrderStockIdempotent(Long orderId, List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            log.warn("订单无明细，跳过库存回滚：{}", orderId);
            return;
        }
        Boolean first = redisTemplate.opsForValue().setIfAbsent(
                ORDER_ROLLBACK_PREFIX + orderId, "1",
                Duration.ofMinutes(ROLLBACK_FLAG_EXPIRE_MINUTES));
        if (!Boolean.TRUE.equals(first)) {
            log.info("订单库存已回滚过，跳过：{}", orderId);
            return;
        }
        try {
            for (OrderItem oi : items) {
                rollbackStock(oi.getProductId(), oi.getQuantity());
                productMapper.reduceSales(oi.getProductId(), oi.getQuantity());
            }
        } catch (Exception e) {
            // 回滚失败删标记，允许下次重试
            redisTemplate.delete(ORDER_ROLLBACK_PREFIX + orderId);
            throw e;
        }
    }
    /**
     * 回滚 Redis 库存 (取消订单或超时)
     */
    public void rollbackStock(Long productId ,Integer quantity){
        String key = STOCK_KEY_PREFIX + productId;
        /*
        * 如果 key 因任何原因不存在（预热遗漏、被误删、商品新上架未预热），
        * increment 会创建一个等于 quantity 的库存 key，
        * 然后 StockSyncTask 把它回写到 DB → DB 库存凭空多出来。*/
//        if(!Boolean.TRUE.equals(redisTemplate.hasKey(key))){
//           log.info(" 回滚时库存 key 不存在，跳过：" + productId);
//            return;
//        }
//        redisTemplate.opsForValue().increment(key,quantity);
        Long r = redisTemplate.execute(
                rollbackStockScript,
                Collections.singletonList(key),
                String.valueOf(quantity));
        if(r == null || r == 0L){
            log.info("回滚时库存 key 不存在，跳过：{}", productId);
        }
    }

    public Boolean setStockIfAbsent(Long productId, Integer stock) {
        return redisTemplate.opsForValue()
                .setIfAbsent(STOCK_KEY_PREFIX + productId, String.valueOf(stock));
    }

    /**
     * 强制覆盖 Redis 库存（商家手工调整库存时使用）。
     * 与 setStockIfAbsent 不同，这里会覆盖实时值，只应在库存被人工改动时调用。
     */
    public void setStock(Long productId, Integer stock) {
        if (stock == null) return;
        redisTemplate.opsForValue().set(STOCK_KEY_PREFIX + productId, String.valueOf(stock));
    }
}
