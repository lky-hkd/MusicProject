package com.music.service;

import java.time.LocalDateTime;

/**
 * 订单取消（供批量扫描/超时取消使用，逐条独立事务）
 */
public interface OrderCancelService {

    /**
     * 在独立事务中取消一条待付款订单（CAS 改状态成功 → 提交后回滚库存）。
     * @return true 表示本次真正取消了该订单
     */
    boolean cancelOneInNewTx(Long orderId, String orderNo, LocalDateTime now);
}