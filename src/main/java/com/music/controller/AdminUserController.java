package com.music.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.music.common.Result;
import com.music.common.Role;
import com.music.entity.User;
import com.music.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/user")
public class AdminUserController {

    @Autowired
    private StringRedisTemplate redis;
    @Autowired private UserService userService;

    /**
     * 用户列表（分页 + 关键词 + 角色筛选）
     * POST /api/admin/user/list
     * body: { "page":1, "size":10, "keyword":"用户名/昵称", "role":0 }
     */
    @PostMapping("/list")
    public Result<?> list(@RequestBody(required = false) Map<String, Object> body) {
        int page = body != null && body.get("page") != null ? ((Number) body.get("page")).intValue() : 1;
        int size = body != null && body.get("size") != null ? ((Number) body.get("size")).intValue() : 10;

        LambdaQueryWrapper<User> qw = new LambdaQueryWrapper<>();
        if (body != null && body.get("keyword") != null) {
            String kw = String.valueOf(body.get("keyword")).trim();
            if (!kw.isEmpty()) {
                qw.and(w -> w.like(User::getUsername, kw).or().like(User::getNickname, kw));
            }
        }
        if (body != null && body.get("role") != null) {
            qw.eq(User::getRole, ((Number) body.get("role")).intValue());
        }
        qw.orderByDesc(User::getId);

        Page<User> result = userService.page(new Page<>(page, size), qw);
        result.getRecords().forEach(u -> u.setPassword(null));
        return Result.ok(result);
    }

    /** 改角色 */
    @PostMapping("/changeRole")
    public Result<?> changeRole(@RequestParam Long userId,
                                @RequestParam Integer role,
                                @RequestParam(defaultValue = "false") boolean forceLogout) {
        if (role == null || (role != Role.USER && role != Role.MERCHANT && role != Role.ADMIN)) {
            return Result.fail("非法的角色值");
        }
        User exist = userService.getById(userId);
        if (exist == null) return Result.fail("用户不存在");

        User u = new User();
        u.setId(userId);
        u.setRole(role);
        userService.updateById(u);

        // token 里缓存了 userId:role，改角色后必须同步，否则用户要重新登录才生效
        String pointerKey = com.music.common.RedisConstants.LOGIN_TOKEN_KEY + userId;
        String token = redis.opsForValue().get(pointerKey);
        if (token != null) {
            Long ttl = redis.getExpire(token);
            if (forceLogout || ttl == null || ttl == -2) {
                redis.delete(token);
                redis.delete(pointerKey);
            } else if (ttl > 0) {
                redis.opsForValue().set(token, userId + ":" + role, ttl, java.util.concurrent.TimeUnit.SECONDS);
            } else {
                // -1 表示没有过期时间，保持原样
                redis.opsForValue().set(token, userId + ":" + role);
            }
        }
        return Result.ok(null, "角色已更新" + (forceLogout ? "，该用户需重新登录" : ""));
    }
}