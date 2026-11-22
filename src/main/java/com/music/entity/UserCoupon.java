package com.music.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户优惠券（领取记录）
 * 状态：0 未使用 / 1 已使用 / 2 已过期
 */
@Data
@TableName("user_coupon")
public class UserCoupon {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long voucherId;

    /** 0 未使用 / 1 已使用 / 2 已过期 */
    private Integer status;

    private LocalDateTime receiveTime;

    /** 过期时间，null 表示不限 */
    private LocalDateTime expireTime;

    private LocalDateTime useTime;

    /** 核销时所在支付批次号，取消订单时用于回滚 */
    private String payNo;

    /** 本次实际抵扣金额（分） */
    private Long discountAmount;
}
