package com.music.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.music.common.ProductStatus;
import com.music.entity.Product;
import com.music.mapper.ProductMapper;
import com.music.service.Impl.RedisStockService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

import static com.music.common.OrderStatus.PAID;

/**
 * 库存对账任务：
 * Redis 是高并发的实时库存来源，DB 的 stock 由本任务定期回写，保证最终一致。
 * 主链路（下单/取消）完全不碰 DB 库存热点行。
 */
@Slf4j
@Component
public class StockSyncTask {

    @Autowired
    private RedisStockService redisStockService;

    @Autowired
    private ProductMapper productMapper;

    private static final String STOCK_KEY_PREFIX = "product:stock:";

    /**
     * 每 5 分钟把 Redis 库存同步回 DB。
     * fixedDelay: 上一次执行结束后再等 5 分钟，避免任务堆积。
     */
    @Scheduled(fixedDelay = 5 * 60 * 1000L, initialDelay = 1 * 60 * 1000L)
    public void syncStockToDb() {
        Set<String> keys = redisStockService.scanStockKeys();
        if (keys == null || keys.isEmpty()) {
            return;
        }

        int success = 0;
        int skip = 0;

        for (String key : keys) {
            try {
                // 从 key("product:stock:123") 截出 productId
                String idStr = key.substring(STOCK_KEY_PREFIX.length());
                Long productId = Long.valueOf(idStr);

                Integer redisStock = redisStockService.getRedisStock(productId);
                if (redisStock == null || redisStock < 0) {
                    skip++;
                    continue;
                }

                productMapper.syncStock(productId, redisStock);
                success++;
            } catch (Exception e) {
                // 单个商品失败不影响整体，记录后继续
                log.error("库存回写失败, key=" + key + ", err=" + e.getMessage());
            }
        }

        log.info("🔄 [库存对账] Redis->DB 同步完成, 成功=" + success + ", 跳过=" + skip);
    }

    /**
     * 兜底：把 DB 里有、但 Redis 里缺失的库存 key 补回来。
     * 仅补“缺失”的，不覆盖 Redis 里已有的实时值，避免冲刷掉刚扣的库存。
     */
    @Scheduled(fixedDelay = 10 * 60 * 1000L, initialDelay = 3 * 60 * 1000L)
    public void restoreMissingStock() {
        List<Product> products = productMapper.selectList(
                new LambdaQueryWrapper<Product>()
                        .eq(Product::getStatus, ProductStatus.ON_SALE)
                        .select(Product::getId, Product::getStock));
        int restored = 0;
        for (Product p : products) {
            String key = STOCK_KEY_PREFIX + p.getId();
            Boolean set = redisStockService.setStockIfAbsent(p.getId(), p.getStock());
            if (Boolean.TRUE.equals(set)) {
                restored++;
                log.warn("⚠️ 库存 key 缺失已从 DB 恢复：productId={}, stock={}", p.getId(), p.getStock());
            }
        }
        if (restored > 0) {
            log.warn("🔄 [库存兜底] 共恢复 {} 个缺失的库存 key，请排查 Redis 是否非正常丢失", restored);
        }
    }
}