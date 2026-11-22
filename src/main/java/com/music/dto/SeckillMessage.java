package com.music.dto;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 秒杀异步落库消息。
 * payload 里放落库所需的完整快照 JSON，消费者无需再查商品/券。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeckillMessage implements Serializable {
    private String orderNo;
    private Integer bizType;   // SeckillOrderTask.BIZ_PRODUCT / BIZ_VOUCHER
    private Long userId;
    private String payload;    // JSON
}
