package com.music.config;

public class OrderProperties {
    /** 订单超时时长（分钟）：未支付超过此时间自动取消 */
    public static final int TIMEOUT_MINUTES = 15;

    /** 订单超时时长（毫秒），供 MQ x-message-ttl 使用 */
    public static final long TIMEOUT_MILLIS = TIMEOUT_MINUTES * 60L * 1000L;

    /** 支付宝支付窗口（分钟）：用户可完成支付的有效期，与订单超时保持一致 */
    public static final int PAY_WINDOW_MINUTES = TIMEOUT_MINUTES;

    /** 标记类 Redis key 额外冗余（分钟）：需大于订单超时，留足兜底扫描/时钟漂移余量 */
    public static final long FLAG_EXPIRE_EXTRA_MINUTES = 10;

    /** 标记类 Redis key 过期时间（分钟）= 订单超时 + 冗余，需严格大于 TIMEOUT_MINUTES */
    public static final long FLAG_EXPIRE_MINUTES = TIMEOUT_MINUTES + FLAG_EXPIRE_EXTRA_MINUTES;

    private OrderProperties() {}
}
