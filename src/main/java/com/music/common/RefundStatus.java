package com.music.common;

public final class RefundStatus {
    public static final int APPLYING = 1;   // 申请中
    public static final int REFUNDING = 2;  // 退款中
    public static final int SUCCESS  = 3;   // 成功
    public static final int FAILED   = 4;   // 失败
    public static final int REJECTED = 5;   // 拒绝
    private RefundStatus() {}
}