package com.music.dto;

import lombok.Data;

/** 优惠券秒杀订单落库快照 */
@Data
public class SeckillVoucherPayload {
    private Long voucherId;
    private Long userId;
}