package com.music.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.music.entity.UserVip;
import com.music.mapper.UserVipMapper;
import com.music.service.VipService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
public class VipServiceImpl extends ServiceImpl<UserVipMapper, UserVip> implements VipService {

    private static final String TYPE_FOREVER = "vip_forever";

    @Override
    public UserVip getVipStatus(Long userId) {
        return this.getOne(new LambdaQueryWrapper<UserVip>().eq(UserVip::getUserId, userId));
    }

    @Override
    public void grantVip(Long userId, String virtualType, Integer virtualValue) {
        UserVip exist = getVipStatus(userId);

        // 已是永久 VIP:不做减法,直接跳过
        if (exist != null && Integer.valueOf(1).equals(exist.getIsForever())) {
            log.info("用户 {} 已是永久VIP,跳过发放({})", userId, virtualType);
            return;
        }

        UserVip vip = exist != null ? exist : new UserVip();
        vip.setUserId(userId);
        LocalDateTime now = LocalDateTime.now();
        if (exist == null) {
            vip.setCreateTime(now);
        }
        vip.setUpdateTime(now);

        if (TYPE_FOREVER.equals(virtualType)) {
            vip.setIsForever(1);
            vip.setExpireTime(null);
        } else {
            int days = resolveDays(virtualType, virtualValue);
            vip.setIsForever(0);
            // 顺延:当前仍有效则在原到期时间上累加,否则从现在开始
            LocalDateTime base = vip.isValid() ? vip.getExpireTime() : LocalDateTime.now();
            vip.setExpireTime(base.plusDays(days));
        }

        if (exist == null) {
            this.save(vip);
        } else {
            this.updateById(vip);
        }
        log.info("VIP发放成功: userId={}, type={}, 永久={}, 到期={}",
                userId, virtualType, vip.getIsForever() == 1, vip.getExpireTime());
    }

    private int resolveDays(String virtualType, Integer virtualValue) {
        if (virtualValue != null && virtualValue > 0) return virtualValue;
        // 商品没配天数时的兜底默认值
        return switch (virtualType == null ? "" : virtualType) {
            case "vip_month" -> 30;
            case "vip_quarter" -> 90;
            case "vip_year" -> 365;
            default -> 30;
        };
    }
}
