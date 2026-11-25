package com.music.config;

import com.music.common.Result;
import com.music.common.Role;
import com.music.common.UserContext;
import com.alibaba.fastjson2.JSON;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;

@Slf4j
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final Duration TOKEN_TTL = Duration.ofDays(7);
    /** 可匿名访问、但登录后仍能识别身份的接口 */
    private static final String OPTIONAL_AUTH_PREFIX = "/api/coupon/activity";

    private boolean isOptionalAuth(String uri) {
        return uri != null && uri.startsWith(OPTIONAL_AUTH_PREFIX);
    }

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token = null;
        if (request.getCookies() != null) {
            for (var c : request.getCookies()) {
                if ("token".equals(c.getName())) { token = c.getValue(); break; }
            }
        }
        if (token == null || token.isEmpty()) {
            // 活动中心允许游客浏览
            if (isOptionalAuth(request.getRequestURI())) return true;
            return reject(response, "请先登录");
        }

        String val = stringRedisTemplate.opsForValue().get(token);
        if (val == null) {
            if (isOptionalAuth(request.getRequestURI())) return true;
            return reject(response, "登录已过期");
        }

        // 解析 "userId:role"，兼容老数据（只有 userId 没冒号）
        String[] parts = val.split(":");
        Long userId;
        int role;
        try {
            userId = Long.valueOf(parts[0]);
            role = parts.length > 1 ? Integer.parseInt(parts[1]) : Role.USER;
        } catch (NumberFormatException e) {
            stringRedisTemplate.delete(token);
            return reject(response, "登录状态异常，请重新登录");
        }

        stringRedisTemplate.expire(token, TOKEN_TTL);   // 滑动续期
        // userId -> token 映射同步续期，保证后台改角色时还能找到该用户会话
        stringRedisTemplate.expire(
                com.music.common.RedisConstants.LOGIN_TOKEN_KEY + userId, TOKEN_TTL);

        // 封禁用户直接拒绝（多次违规触发，见 ModerationPenaltyService）
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(
                com.music.service.ModerationPenaltyService.BAN_KEY_PREFIX + userId))) {
            return reject(response, "账号已被封禁，请联系管理员");
        }

        request.setAttribute(UserContext.USER_ID_ATTR, userId);
        request.setAttribute(UserContext.ROLE_ATTR, role);   // 新增

        // ---- 按 URL 前缀做角色校验 ----
        String uri = request.getRequestURI();
        if (uri.startsWith("/api/admin/") && role < Role.ADMIN) {
            return reject(response, "无管理员权限");
        }
        if (uri.startsWith("/api/merchant/") && role < Role.MERCHANT) {
            return reject(response, "无商家权限");
        }
        return true;
//        String userIdStr = stringRedisTemplate.opsForValue().get(token);
//        if (userIdStr == null) {
//            return reject(response, "登录已过期");
//        }
//      //滑动过期，每次访问刷新过期时间
//        stringRedisTemplate.expire(token,TOKEN_TTL);
//        request.setAttribute(UserContext.USER_ID_ATTR, Long.valueOf(userIdStr));
//        return true;
    }

    private boolean reject(HttpServletResponse response, String msg) throws Exception {
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(JSON.toJSONString(Result.fail(msg)));
        return false;
    }
}