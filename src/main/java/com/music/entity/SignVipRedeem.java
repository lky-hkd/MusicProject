package com.music.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 签到兑换 VIP 记录。
 * (user_id, milestone) 唯一，保证同一阶段奖励只能兑换一次。
 */
@Data
@TableName("sign_vip_redeem")
public class SignVipRedeem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 兑换时达到的累计签到天数里程碑（30 / 60 / 90 ...） */
    private Integer milestone;

    /** 本次奖励的 VIP 天数 */
    private Integer vipDays;

    private LocalDateTime createTime;
}
