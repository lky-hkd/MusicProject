package com.music.task;

import com.music.config.OrderProperties;
import com.music.service.OrderService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 订单超时取消定时任务
 *
 * 每 30 秒扫一次数据库，找创建超过 15 分钟还未付款的订单，自动取消并回滚库存
 */
@Slf4j
@Component
public class OrderTimeoutTask {

    @Resource
    private OrderService orderService;

    /**
     * 每 30 秒执行一次
     * 只做精准的阶梯扫描，极大降低数据库压力
     */
    @Scheduled(fixedDelay = 30_000L)
    public void scanTimeOrders(){
            try {
                // 扫描 [15分钟前 到 无穷大] 的未付款订单
                int count = orderService.scanExpiredOrders(OrderProperties.TIMEOUT_MINUTES);
                if (count > 0) {
                    log.info("[定时任务] 清理了已超时(>15分钟)的订单: " + count + " 单");
                }
            } catch (Exception e) {
                log.error("[定时任务] 扫描订单异常:{}" , e.getMessage());
            }
    }

}
