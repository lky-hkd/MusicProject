package com.music.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.music.common.Result;
import com.music.common.CurrentUserId;
import com.music.common.Role;
import com.music.common.UserContext;
import com.music.config.CookieProperties;
import com.music.dto.LoginFormDTO;
import com.music.dto.MerchantApplyRequest;
import com.music.dto.PasswordChangeRequest;
import com.music.dto.ProfileUpdateRequest;
import com.music.entity.Merchant;
import com.music.entity.User;
import com.music.service.MerchantService;
import com.music.service.UserService;
import com.music.util.PhoneUtil;
import jakarta.annotation.Resource;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/user")
public class UserController {
    @Resource
    private UserService userService;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private CookieProperties cookieProperties;

    private static final String LOGIN_FAIL_PREFIX = "login:fail:";
    private static final int MAX_FAIL_COUNT = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(5);
    @Resource
    private MerchantService merchantService;

    @Resource
    private com.music.service.VipService vipService;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    //签到
   @PostMapping("/sign")
   public Result<?> sign(@CurrentUserId Long userId){
       return userService.sign(userId);
   }

   @GetMapping("/sign/count")
   public Result<?> signCount(@CurrentUserId Long userId){
       return userService.signCount(userId);
   }

   /** 签到状态：今日是否已签、连续/本月/累计天数、是否可兑换 VIP */
   @GetMapping("/sign/status")
   public Result<?> signStatus(@CurrentUserId Long userId){
       return userService.signStatus(userId);
   }

   /** 累计签到满 30 天兑换 7 天 VIP */
   @PostMapping("/sign/vip")
   public Result<?> redeemSignVip(@CurrentUserId Long userId){
       return userService.redeemSignVip(userId);
   }


    //发送短信验证码
    @PostMapping("/code")
    public Result<?> sendCode(@RequestParam("phone") String phone) {
        return userService.sendCode(phone);
    }

    // 注册接口
    @PostMapping("/register")
    public Result<?> register(@RequestBody User registerUser) {
        if (registerUser == null || !PhoneUtil.isValidPhone(registerUser.getPhone())) {
            return Result.fail("手机号格式错误！");
        }
        if (registerUser.getPassword() == null || registerUser.getPassword().isBlank()) {
            return Result.fail("密码不能为空！");
        }
        User existUser = userService.getOne(new LambdaQueryWrapper<User>().eq(User::getPhone, registerUser.getPhone()));
        if (existUser != null) {
            return Result.fail("该手机号已注册！");
        }
        registerUser.setUsername(registerUser.getUsername() == null || registerUser.getUsername().isBlank()
                ? registerUser.getPhone() : registerUser.getUsername());
        registerUser.setPassword(passwordEncoder.encode(registerUser.getPassword()));
        registerUser.setRole(Role.USER);
        if (registerUser.getCreateTime() == null) {
            registerUser.setCreateTime(java.time.LocalDateTime.now());
        }
        userService.save(registerUser);
        return Result.ok(null, "注册成功！");
    }

    // 登录接口，密码登录
    @PostMapping("/login")
    public Result<?> login(@RequestBody User loginUser,
                           HttpServletResponse response,
                           HttpServletRequest request) {
        if (loginUser.getPhone() == null || loginUser.getPhone().isBlank()
                || loginUser.getPassword() == null || loginUser.getPassword().isBlank()) {
            return Result.fail("手机号和密码不能为空！");
        }
        String phone = loginUser.getPhone();
        String ip = getClientIp(request);
        // 组合键：账号 + IP，防止单账号被锁后换 IP 继续，也防止同 IP 撞库
//        String failKey = LOGIN_FAIL_PREFIX + username + ":" + ip;
        String failKey = LOGIN_FAIL_PREFIX + phone + ":" + ip;

        // 1. 先看是否被锁
        String failStr = stringRedisTemplate.opsForValue().get(failKey);
        if (failStr != null && Integer.parseInt(failStr) >= MAX_FAIL_COUNT) {
            Long ttl = stringRedisTemplate.getExpire(failKey, TimeUnit.SECONDS);
            return Result.fail("失败次数过多，请 " + (ttl != null ? ttl / 60 + 1 : 5) + " 分钟后再试");
        }

        User dbUser = userService.getOne(new LambdaQueryWrapper<User>().eq(User::getPhone, loginUser.getPhone()));
        if (dbUser != null && dbUser.getBannedUntil() != null
                && dbUser.getBannedUntil().isAfter(java.time.LocalDateTime.now())) {
            return Result.fail("账号已被封禁，请联系管理员");
        }
        if (dbUser != null && Integer.valueOf(0).equals(dbUser.getPasswordSet())) {
            return Result.fail("该账号尚未设置密码，请使用短信登录或找回密码");
        }
        if (dbUser == null || !passwordEncoder.matches(loginUser.getPassword(), dbUser.getPassword())) {
            Long failCount = stringRedisTemplate.opsForValue().increment(failKey, 1);
            if(failCount!=null && failCount == 1L){
                stringRedisTemplate.expire(failKey,LOCK_DURATION);
            }
            return Result.fail("账号或密码错误！");
        }
        // 登录成功，清除失败计数
        stringRedisTemplate.delete(failKey);
        // 生成 token 并写入 Cookie
        issueToken(dbUser, response);
        return Result.ok(null, "登录成功！");
    }

    /**
     * 生成 token：Redis 存 "userId:role"，同时写 HttpOnly Cookie
     */
    private void issueToken(User user, HttpServletResponse response) {
        String token = UUID.randomUUID().toString().replace("-", "");
        int role = (user.getRole() == 0) ? Role.USER : user.getRole();
        stringRedisTemplate.opsForValue().set(
                token,
                user.getId() + ":" + role,     // 存 "userId:role"
                7, TimeUnit.DAYS);
        // 记录 userId -> token 的映射，便于后台改角色时同步 token
        stringRedisTemplate.opsForValue().set(
                com.music.common.RedisConstants.LOGIN_TOKEN_KEY + user.getId(),
                token, 7, TimeUnit.DAYS);

        Cookie cookie = new Cookie("token", token);
        cookie.setPath("/");
        cookie.setMaxAge(cookieProperties.getMaxAgeDays() * 24 * 60 * 60);
        cookie.setHttpOnly(cookieProperties.isHttpOnly());
        cookie.setSecure(cookieProperties.isSecure()); // localhost本地调试false；上线https改为true
        response.addCookie(cookie);
    }

    /** 获取真实客户端 IP（考虑 nginx 反向代理场景） */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // X-Forwarded-For 可能是 "客户端IP, 代理1, 代理2"，取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip == null ? "unknown" : ip;
    }
    //短信验证码登录
    @PostMapping("/loginbaseCode")
    public Result<?> loginBaseCode(@RequestBody LoginFormDTO loginUser,
                                   HttpServletResponse response) {
        Result<User> result = userService.loginbaseCode(loginUser);
        if (!result.isSuccess()) {
            return Result.fail(result.getMsg());
        }
        // 校验通过，签发与密码登录一致的 token
        issueToken(result.getData(), response);
        return Result.ok(null, "登录成功！");
    }

    //忘记密码：校验短信验证码后重置密码
    @PostMapping("/resetPassword")
    public Result<?> resetPassword(@RequestBody LoginFormDTO form) {
        return userService.resetPassword(form);
    }

    /*
     * 获取当前用户信息
     * */
    @GetMapping("/getUserInfo")
    public Result<User> getUserInfo(@RequestAttribute(UserContext.USER_ID_ATTR) Long userId,
                                    @CookieValue(value = "token",defaultValue = "false") String token) {

        User user = userService.getById(Long.valueOf(userId));
        if (user == null) {
            // token 有效但用户已被删除（或数据异常），清掉脏 token
            stringRedisTemplate.delete(token);
            return Result.fail("用户不存在，请重新登录");
        }
        user.setPassword(null);
        // 填充 VIP 状态(瞬态字段)
        com.music.entity.UserVip vipRecord = vipService.getVipStatus(userId);
        boolean valid = vipRecord != null && vipRecord.isValid();
        user.setVip(valid);
        if (valid) {
            user.setVipExpireTime(vipRecord.getExpireTime());
            user.setIsVipForever(Integer.valueOf(1).equals(vipRecord.getIsForever()));
        }
        return Result.ok(user, "获取成功");
    }

    /*
     * 个人资料
     * */
    @GetMapping("/profile")
    public Result<?> profile(@CurrentUserId Long userId) {
        User user = userService.getById(userId);
        if (user == null) return Result.fail("用户不存在");
        user.setPassword(null);
        return Result.ok(user);
    }

    @PostMapping("/profile/update")
    public Result<?> updateProfile(@RequestBody ProfileUpdateRequest req,
                                   @CurrentUserId Long userId) {
        return userService.updateProfile(userId, req);
    }

    @PostMapping("/avatar")
    public Result<?> uploadAvatar(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) return Result.fail("请选择图片");
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return Result.fail("只支持图片文件");
        }
        if (file.getSize() > 5 * 1024 * 1024) return Result.fail("头像不能超过5MB");

        String ext = ".png";
        String original = file.getOriginalFilename();
        if (original != null && original.contains(".")) {
            ext = original.substring(original.lastIndexOf('.')).toLowerCase();
        }
        if (!ext.matches("\\.(jpg|jpeg|png|gif|webp)")) {
            return Result.fail("只支持 jpg/png/gif/webp 图片");
        }

        Path dir = Paths.get(uploadDir, "avatar").toAbsolutePath();
        Files.createDirectories(dir);
        String filename = UUID.randomUUID().toString().replace("-", "") + ext;
        file.transferTo(dir.resolve(filename).toFile());
        return Result.ok("/uploads/avatar/" + filename);
    }

    @PostMapping("/changePassword")
    public Result<?> changePassword(@RequestBody PasswordChangeRequest req,
                                    @CurrentUserId Long userId,
                                    HttpServletResponse response) {
        Result<?> result = userService.changePassword(userId, req.getOldPassword(), req.getNewPassword());
        if (result.isSuccess()) {
            // 旧 token 已在服务层删除，这里同步清掉浏览器 Cookie
            Cookie cookie = new Cookie("token", null);
            cookie.setPath("/");
            cookie.setMaxAge(0);
            cookie.setHttpOnly(cookieProperties.isHttpOnly());
            cookie.setSecure(cookieProperties.isSecure());
            response.addCookie(cookie);
        }
        return result;
    }

    /*
     * 退出登录
     * */
    @PostMapping("/logout")
    public Result<?> logout(@CookieValue(value = "token", required = false) String token, HttpServletResponse response) {
        if (token != null) {
            stringRedisTemplate.delete(token);
        }
        Cookie cookie = new Cookie("token", null);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
        return Result.ok(null, "退出成功！");
    }

    /**
     * 申请开店：普通用户 -> 商家
     * 成功后必须刷新 Redis 中 token 的 role
     */
    @PostMapping("/applyMerchant")
    public Result<?> applyMerchant(@RequestBody MerchantApplyRequest req,
                                   @RequestAttribute(UserContext.USER_ID_ATTR) Long userId,
                                   @CookieValue(value="token",required = false) String token){
        if(req.getShopName() == null || req.getShopName().trim().isEmpty()){
            return Result.fail("店铺名称不能为空");
        }

        Merchant m = merchantService.applyShop(userId,req);
        if(token != null && !token.isEmpty()){
            String val = stringRedisTemplate.opsForValue().get(token);
            if(val != null){
                String[] parts = val.split(":");
                Long uid = Long.valueOf(parts[0]);
                int oldRole = parts.length > 1 ? Integer.parseInt(parts[1]) : Role.USER;
                int newRole = Math.max(oldRole,Role.MERCHANT);
                stringRedisTemplate.opsForValue().set(token, uid+":"+newRole,7,TimeUnit.DAYS);
            }
        }
        return Result.ok(m, "开店成功，欢迎入驻！");
    }
}
