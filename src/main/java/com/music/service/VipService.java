package com.music.service;

import com.music.entity.UserVip;

public interface VipService {

    /** 查询用户 VIP 记录,无记录返回 null */
    UserVip getVipStatus(Long userId);

    /**
     * 发放/续费 VIP。
     * 已是永久 VIP 直接跳过;有期限的在 max(expireTime, now) 基础上顺延。
     *
     * @param virtualType  vip_month / vip_quarter / vip_year / vip_forever
     * @param virtualValue 天数,为空按类型默认(month=30/quarter=90/year=365)
     */
    void grantVip(Long userId, String virtualType, Integer virtualValue);
}
