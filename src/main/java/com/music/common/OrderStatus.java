package com.music.common;

/**
 * 订单状态常量。
 * 与数据库、前端（my-orders.js / merchant-center.js / admin-common.js）保持一致，基数为 0。
 * 0 待付款 / 1 已付款 / 2 已发货 / 3 已完成 / 4 已取消 / 5 已退款
 */
public final class OrderStatus {
    public static final int PENDING_PAY = 0;   // 待付款
    public static final int PAID        = 1;   // 已付款
    public static final int SHIPPED     = 2;   // 已发货
    public static final int COMPLETED   = 3;   // 已完成（已确认收货）
    public static final int CANCELED    = 4;   // 已取消
    public static final int REFUNDED    = 5;   // 已退款
    private OrderStatus() {}
}
