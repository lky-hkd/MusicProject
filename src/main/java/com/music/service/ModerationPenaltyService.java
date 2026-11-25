package com.music.service;

import com.music.common.RedisConstants;
import com.music.entity.User;
import com.music.mapper.UserMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 违规惩罚阶梯（私聊场景）：
 * <pre>
 *   10 分钟内 3 次  → 禁言 10 分钟
 *   24 小时内 5 次  → 禁言 24 小时
 *   7 天内 10 次    → 封禁 7 天（同时失效登录态）
 * </pre>
 *
 * 计数与封禁标记放 Redis（拦截器每请求只多一次 hasKey），
 * 封禁截止时间同时落库，便于后台查看和重启后恢复。
 */
@Slf4j
@Service
public class ModerationPenaltyService {

    public static final String MUTE_KEY_PREFIX = "mod:mute:";
    public static final String BAN_KEY_PREFIX = "mod:ban:";
    private static final String HIT_10M = "mod:hit:10m:";
    private static final String HIT_24H = "mod:hit:24h:";
    private static final String HIT_7D = "mod:hit:7d:";

    private static final int MUTE_10M_THRESHOLD = 3;
    private static final int MUTE_24H_THRESHOLD = 5;
    private static final int BAN_THRESHOLD = 10;

    private static final long MUTE_10M_SECONDS = 10 * 60L;
    private static final long MUTE_24H_SECONDS = 24 * 60 * 60L;
    private static final long BAN_DAYS = 7;

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private UserMapper userMapper;

    /** 记录一次违规并执行对应阶梯处罚 */
    public void onViolation(Long userId) {
        if (userId == null) {
            return;
        }
        long in10m = hit(HIT_10M + userId, MUTE_10M_SECONDS);
        long in24h = hit(HIT_24H + userId, MUTE_24H_SECONDS);
        long in7d = hit(HIT_7D + userId, BAN_DAYS * 24 * 60 * 60);

        if (in7d >= BAN_THRESHOLD) {
            ban(userId);
        } else if (in24h >= MUTE_24H_THRESHOLD) {
            mute(userId, MUTE_24H_SECONDS);
        } else if (in10m >= MUTE_10M_THRESHOLD) {
            mute(userId, MUTE_10M_SECONDS);
        }
    }

    public boolean isBanned(Long userId) {
        return userId != null && Boolean.TRUE.equals(
                stringRedisTemplate.hasKey(BAN_KEY_PREFIX + userId));
    }

    public boolean isMuted(Long userId) {
        return userId != null && Boolean.TRUE.equals(
                stringRedisTemplate.hasKey(MUTE_KEY_PREFIX + userId));
    }

    public long muteRemainingMinutes(Long userId) {
        Long seconds = stringRedisTemplate.getExpire(MUTE_KEY_PREFIX + userId);
        return seconds == null || seconds <= 0 ? 0 : (seconds + 59) / 60;
    }

    private long hit(String key, long ttlSeconds) {
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            stringRedisTemplate.expire(key, Duration.ofSeconds(ttlSeconds));
        }
        return count == null ? 0 : count;
    }

    private void mute(Long userId, long seconds) {
        String key = MUTE_KEY_PREFIX + userId;
        Long current = stringRedisTemplate.getExpire(key);
        // 已有更长的禁言时保留
        if (current == null || current < seconds) {
            stringRedisTemplate.opsForValue().set(key, "1", Duration.ofSeconds(seconds));
            log.warn("[moderation] 用户 {} 被禁言 {} 分钟", userId, seconds / 60);
        }
    }

    private void ban(Long userId) {
        LocalDateTime until = LocalDateTime.now().plusDays(BAN_DAYS);
        try {
            User update = new User();
            update.setId(userId);
            update.setBannedUntil(until);
            userMapper.updateById(update);
        } catch (Exception e) {
            log.warn("[moderation] 写入封禁时间失败 userId={}：{}", userId, e.getMessage());
        }
        stringRedisTemplate.opsForValue().set(
                BAN_KEY_PREFIX + userId, "1", Duration.ofDays(BAN_DAYS));
        // 立刻踢下线
        try {
            String token = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_TOKEN_KEY + userId);
            if (token != null) {
                stringRedisTemplate.delete(token);
            }
            stringRedisTemplate.delete(RedisConstants.LOGIN_TOKEN_KEY + userId);
        } catch (Exception e) {
            log.warn("[moderation] 清理登录态失败 userId={}：{}", userId, e.getMessage());
        }
        log.warn("[moderation] 用户 {} 多次违规，封禁至 {}", userId, until);
    }
}
