package com.music.config;

import com.music.common.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AiRateLimitInterceptor implements HandlerInterceptor {
    private static final int LIMIT = 10;   // 每分钟最多 10 次
    private static final String PREFIX = "ai:limit:";
    private static final int WINDOW_SECONDS = 60;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    @Qualifier("rateLimitScript")
    private DefaultRedisScript<Long> rateLimitScript;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Long userId = (Long)request.getAttribute(UserContext.USER_ID_ATTR);
        if(userId == null) {
            return reject(response, "请先登录");
        }

        String key= PREFIX + userId;
        Long count = redis.execute(
                rateLimitScript,
                java.util.Collections.singletonList(key),
                String.valueOf(WINDOW_SECONDS));

        if (count != null && count > LIMIT) {
            return reject(response, "AI 搜索太频繁，请稍后再试");
        }
        return true;
    }

    private boolean reject(HttpServletResponse response, String msg) throws Exception {
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"success\":false,\"msg\":\"" + msg + "\"}");
        return false;
    }

}
