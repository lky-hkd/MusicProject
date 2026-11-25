package com.music.service.Impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.music.common.Role;
import com.music.common.Result;
import com.music.dto.LoginFormDTO;
import com.music.dto.ProfileUpdateRequest;
import com.music.entity.SignVipRedeem;
import com.music.entity.User;
import com.music.entity.UserVip;
import com.music.common.ModerationAction;
import com.music.common.ModerationContentType;
import com.music.dto.ModerationContext;
import com.music.mapper.SignVipRedeemMapper;
import com.music.mapper.UserMapper;
import com.music.service.ModerationPendingService;
import com.music.service.ModerationService;
import com.music.service.UserService;
import com.music.service.VipService;
import com.music.util.PhoneUtil;
import com.music.util.RandomNumberUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.music.common.RedisConstants.*;
import static com.music.common.UserContext.USER_NAME_DEFAULT;

@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Resource
    StringRedisTemplate stringRedisTemplate;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Resource
    private ModerationService moderationService;
    @Resource
    private ModerationPendingService moderationPendingService;

    @Resource
    private SignVipRedeemMapper signVipRedeemMapper;

    @Resource
    private VipService vipService;

    /** 兑换 VIP 所需的累计签到天数 */
    private static final int SIGN_VIP_MILESTONE_DAYS = 30;
    /** 每档奖励的 VIP 天数 */
    private static final int SIGN_VIP_REWARD_DAYS = 7;

    @Override
    public Result<?> sign(Long userId) {
        //2.获取日期
        LocalDateTime now = LocalDateTime.now();
        //3.拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyy-MM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        //4.今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();

        //5.写入redis；返回旧值，true 表示今天已经签过
        Boolean signedBefore = stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
        if (Boolean.TRUE.equals(signedBefore)) {
            return Result.ok("今日已签到");
        }
        //6.当天首次签到才累加总天数（跨月累计，用于兑换 VIP）
        stringRedisTemplate.opsForValue().increment(SIGN_TOTAL_KEY + userId);
        return Result.ok("签到成功");
    }

    @Override
    public Result<?> signCount(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        String key = USER_SIGN_KEY + userId + now.format(DateTimeFormatter.ofPattern(":yyyy-MM"));
        long num = readSignBits(key, now.getDayOfMonth());
        int count = 0;
        // 从今天往前数，连续为 1 的天数（今天没签则为 0）
        while ((num & 1) == 1) {
            count++;
            num >>>= 1;
        }
        return Result.ok(count);
    }

    /**
     * 签到状态：供签到页展示「今日是否已签 / 连续天数 / 本月天数 / 累计进度 / 是否可兑换」。
     */
    @Override
    public Result<?> signStatus(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        String key = USER_SIGN_KEY + userId + now.format(DateTimeFormatter.ofPattern(":yyyy-MM"));
        int dayOfMonth = now.getDayOfMonth();

        long num = readSignBits(key, dayOfMonth);
        boolean signedToday = (num & 1) == 1;
        int monthDays = Long.bitCount(num);
        int continuousDays = 0;
        long tmp = num;
        while ((tmp & 1) == 1) {
            continuousDays++;
            tmp >>>= 1;
        }

        int totalDays = getSignTotalDays(userId);
        int reachedMilestone = totalDays / SIGN_VIP_MILESTONE_DAYS * SIGN_VIP_MILESTONE_DAYS;
        boolean redeemable = reachedMilestone > 0 && !hasRedeemedSignVip(userId, reachedMilestone);

        Map<String, Object> data = new HashMap<>();
        data.put("signedToday", signedToday);
        data.put("continuousDays", continuousDays);
        data.put("monthDays", monthDays);
        data.put("totalDays", totalDays);
        // 距离下一档还差多少天：累计 23 天 -> 7；刚好 30 天 -> 30（下一档 60）
        data.put("progressDays", totalDays - reachedMilestone);
        data.put("nextMilestone", reachedMilestone + SIGN_VIP_MILESTONE_DAYS);
        data.put("milestoneDays", SIGN_VIP_MILESTONE_DAYS);
        data.put("rewardVipDays", SIGN_VIP_REWARD_DAYS);
        data.put("redeemable", redeemable);
        return Result.ok(data);
    }

    /** 读取本月签到位图，返回前 dayOfMonth 位（bit0 为今天） */
    private long readSignBits(String key, int dayOfMonth) {
        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key, BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0));
        if (result == null || result.isEmpty() || result.get(0) == null) {
            return 0L;
        }
        return result.get(0);
    }

    private boolean hasRedeemedSignVip(Long userId, int milestone) {
        Long count = signVipRedeemMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SignVipRedeem>()
                        .eq(SignVipRedeem::getUserId, userId)
                        .eq(SignVipRedeem::getMilestone, milestone));
        return count != null && count > 0;
    }

    /** 累计签到满 30 天兑换 7 天 VIP，每满 30 天可兑换一次 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<?> redeemSignVip(Long userId) {
        int totalDays = getSignTotalDays(userId);
        int milestone = (totalDays / SIGN_VIP_MILESTONE_DAYS) * SIGN_VIP_MILESTONE_DAYS;
        if (milestone <= 0) {
            return Result.fail("累计签到未满 " + SIGN_VIP_MILESTONE_DAYS + " 天，暂时不能兑换");
        }
        SignVipRedeem record = new SignVipRedeem();
        record.setUserId(userId);
        record.setMilestone(milestone);
        record.setVipDays(SIGN_VIP_REWARD_DAYS);
        record.setCreateTime(LocalDateTime.now());
        try {
            // (user_id, milestone) 唯一索引兜底并发重复兑换
            signVipRedeemMapper.insert(record);
        } catch (DuplicateKeyException e) {
            return Result.fail("该阶段签到奖励已兑换过了");
        }
        // 与兑换记录同一事务，发放失败会一起回滚
        vipService.grantVip(userId, "vip_sign", SIGN_VIP_REWARD_DAYS);
        UserVip vip = vipService.getVipStatus(userId);
        Map<String, Object> data = new HashMap<>();
        data.put("totalDays", totalDays);
        data.put("milestone", milestone);
        data.put("vipDays", SIGN_VIP_REWARD_DAYS);
        data.put("vipExpireTime", vip == null ? null : vip.getExpireTime());
        return Result.ok(data, "兑换成功，VIP 已到账");
    }

    private int getSignTotalDays(Long userId) {
        String val = stringRedisTemplate.opsForValue().get(SIGN_TOTAL_KEY + userId);
        if (val == null) {
            return 0;
        }
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    //发送短信
    @Override
    public Result sendCode(String phone) {
        // 手机号格式校验（注意这里必须是取反，格式不合法才拦截）
        if (!PhoneUtil.isValidPhone(phone)) {
            return Result.fail("手机号格式错误！");
        }
        // 60 秒冷却，防止短信接口被刷
        String cooldownKey = LOGIN_CODE_COOLDOWN_KEY + phone;
        Boolean acquired = stringRedisTemplate.opsForValue()
                .setIfAbsent(cooldownKey, "1", SMS_COOLDOWN_TTL, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(acquired)) {
            Long ttl = stringRedisTemplate.getExpire(cooldownKey, TimeUnit.SECONDS);
            long wait = (ttl != null && ttl > 0) ? ttl : SMS_COOLDOWN_TTL;
            return Result.fail("操作过于频繁，请 " + wait + " 秒后再试");
        }

        //生成验证码
        String code = RandomNumberUtil.generateNumStrWithZero(6);
        //保存验证码
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);

        //模拟发送短信：真实项目应替换成短信服务商 SDK，且不要把验证码返回给前端
        log.info("模拟发送短信验证码 phone={} code={}", phone, code);
        return Result.ok(code, "验证码已发送");
    }

    //短信登录
    @Override
    public Result<User> loginbaseCode(LoginFormDTO loginUser) {
        if (loginUser == null || isBlank(loginUser.getPhone()) || isBlank(loginUser.getCode())) {
            return Result.fail("手机号或验证码不能为空！");
        }
        String phone = loginUser.getPhone().trim();
        String inputCode = loginUser.getCode().trim();

        // 手机号校验
        if (!PhoneUtil.isValidPhone(phone)) {
            return Result.fail("手机号格式错误！");
        }

        // 从 Redis 取验证码
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        if (cacheCode == null) {
            return Result.fail("验证码已失效，请重新获取");
        }
        if (!cacheCode.equals(inputCode)) {
            return Result.fail("验证码错误！");
        }

        // 校验通过立即销毁，防止同一个验证码重复使用
        stringRedisTemplate.delete(LOGIN_CODE_KEY + phone);

        // 查询用户，不存在则自动注册
        User user = query().eq("phone", phone).one();
        if (user == null) {
            user = createUser(phone);
        }
        return Result.ok(user, "登录成功！");
    }

    /**
     * 忘记密码：校验短信验证码后重置登录密码
     */
    @Override
    public Result resetPassword(LoginFormDTO form) {
        if (form == null || isBlank(form.getPhone()) || isBlank(form.getCode()) || isBlank(form.getPassword())) {
            return Result.fail("手机号、验证码和新密码不能为空！");
        }
        String phone = form.getPhone().trim();
        String inputCode = form.getCode().trim();
        String newPassword = form.getPassword();

        if (!PhoneUtil.isValidPhone(phone)) {
            return Result.fail("手机号格式错误！");
        }
        if (newPassword.length() < 6) {
            return Result.fail("密码长度至少 6 位");
        }

        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        if (cacheCode == null) {
            return Result.fail("验证码已失效，请重新获取");
        }
        if (!cacheCode.equals(inputCode)) {
            return Result.fail("验证码错误！");
        }
        // 一次性使用
        stringRedisTemplate.delete(LOGIN_CODE_KEY + phone);

        User user = query().eq("phone", phone).one();
        if (user == null) {
            return Result.fail("该手机号尚未注册");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setPasswordSet(1);
        updateById(user);
        return Result.ok(null, "密码修改成功，请重新登录");
    }

    @Override
    public Result<?> updateProfile(Long userId, ProfileUpdateRequest req) {
        if (req == null) return Result.fail("参数错误");
        User user = getById(userId);
        if (user == null) return Result.fail("用户不存在");

        LambdaUpdateWrapper<User> update = new LambdaUpdateWrapper<>();
        update.eq(User::getId, userId);
        boolean pending = false;

        if (req.getNickname() != null) {
            String nickname = req.getNickname().trim();
            if (nickname.length() < 2 || nickname.length() > 20) {
                return Result.fail("昵称长度需要 2-20 个字符");
            }
            ModerationAction action = moderationService.check(ModerationContext.builder()
                    .contentType(ModerationContentType.NICKNAME)
                    .userId(userId)
                    .content(nickname)
                    .build());
            if (action == ModerationAction.REVIEW) {
                // 可疑昵称不直接生效，交后台复核
                moderationPendingService.submit(ModerationContentType.NICKNAME, userId, nickname);
                pending = true;
            } else {
                update.set(User::getNickname, nickname);
            }
        }
        if (req.getAvatar() != null) {
            String avatar = req.getAvatar().trim();
            if (!avatar.isEmpty()
                    && !avatar.startsWith("/uploads/avatar/")
                    && !avatar.startsWith("/img/")) {
                return Result.fail("头像地址不合法");
            }
            update.set(User::getAvatar, avatar.isEmpty() ? null : avatar);
        }
        if (req.getGender() != null) {
            int gender = req.getGender();
            if (gender < 0 || gender > 2) return Result.fail("性别参数不正确");
            update.set(User::getGender, gender);
        }
        if (req.getSignature() != null) {
            String signature = req.getSignature().trim();
            if (signature.length() > 100) return Result.fail("个性签名不能超过100字");
            if (!signature.isEmpty()) {
                ModerationAction action = moderationService.check(ModerationContext.builder()
                        .contentType(ModerationContentType.SIGNATURE)
                        .userId(userId)
                        .content(signature)
                        .build());
                if (action == ModerationAction.REVIEW) {
                    moderationPendingService.submit(ModerationContentType.SIGNATURE, userId, signature);
                    pending = true;
                } else {
                    update.set(User::getSignature, signature.isEmpty() ? null : signature);
                }
            } else {
                update.set(User::getSignature, null);
            }
        }
        if (req.getBirthday() != null) {
            String birthday = req.getBirthday().trim();
            if (birthday.isEmpty()) {
                update.set(User::getBirthday, null);
            } else {
                try {
                    LocalDate date = LocalDate.parse(birthday);
                    if (date.isBefore(LocalDate.of(1900, 1, 1)) || date.isAfter(LocalDate.now())) {
                        return Result.fail("生日日期不正确");
                    }
                    update.set(User::getBirthday, date);
                } catch (DateTimeParseException e) {
                    return Result.fail("生日格式不正确");
                }
            }
        }

        String setSql = update.getSqlSet();
        if (setSql != null && !setSql.isBlank()) {
            update(update);
        }
        return pending
                ? Result.ok(null, "部分内容需要人工审核，审核通过后自动生效")
                : Result.ok(null, "保存成功");
    }

    @Override
    public Result<?> changePassword(Long userId, String oldPassword, String newPassword) {
        if (oldPassword == null || oldPassword.isBlank()
                || newPassword == null || newPassword.isBlank()) {
            return Result.fail("原密码和新密码不能为空");
        }
        User user = getById(userId);
        if (user == null) return Result.fail("用户不存在");
        if (Integer.valueOf(0).equals(user.getPasswordSet())) {
            return Result.fail("该账号还没有设置密码，请先通过短信验证设置密码");
        }
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            return Result.fail("原密码不正确");
        }
        if (newPassword.length() < 6 || newPassword.length() > 20) {
            return Result.fail("新密码长度需要 6-20 位");
        }
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            return Result.fail("新密码不能与原密码相同");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setPasswordSet(1);
        updateById(user);

        // 修改密码后让旧登录态立即失效
        String token = stringRedisTemplate.opsForValue().get(LOGIN_TOKEN_KEY + userId);
        if (token != null) {
            stringRedisTemplate.delete(token);
            stringRedisTemplate.delete(LOGIN_TOKEN_KEY + userId);
        }
        return Result.ok(null, "密码修改成功，请重新登录");
    }

    /**
     * 手机号首次登录时自动创建账号
     */
    private User createUser(String phone) {
        User user = new User();
        user.setUsername(USER_NAME_DEFAULT + phone);
        // 自动注册的用户不使用密码，这里给一个加密后的随机密码占位
        user.setPassword(passwordEncoder.encode(RandomNumberUtil.generateNumStrWithZero(10)));
        user.setPasswordSet(0);
        user.setPhone(phone);
        user.setRole(Role.USER);
        user.setCreateTime(LocalDateTime.now());
        save(user);
        return user;
    }

    private boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }

}
