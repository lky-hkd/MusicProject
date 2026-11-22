package com.music.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("refund_order")
public class RefundOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private String orderNo;
    private Long userId;
    private String refundNo;
    private BigDecimal refundAmount;
    private String reason;
    private Integer status;          // 1申请中 2退款中 3成功 4失败 5拒绝
    private String rejectReason;
    private String alipayTradeNo;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}