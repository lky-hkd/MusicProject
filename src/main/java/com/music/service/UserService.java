package com.music.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.music.common.Result;
import com.music.dto.LoginFormDTO;
import com.music.dto.ProfileUpdateRequest;
import com.music.entity.User;

public interface UserService extends IService<User> {

    /** 发送短信验证码（演示环境：验证码随响应返回并打印到日志） */
    Result sendCode(String phone);

    /** 验证码登录：验证码校验通过后返回用户，不存在则自动注册 */
    Result<User> loginbaseCode(LoginFormDTO loginUser);

    /** 忘记密码：校验短信验证码后重置登录密码 */
    Result resetPassword(LoginFormDTO form);

    /** 修改个人资料 */
    Result<?> updateProfile(Long userId, ProfileUpdateRequest req);

    /** 登录状态下修改密码（需要原密码） */
    Result<?> changePassword(Long userId, String oldPassword, String newPassword);

    Result<?> sign(Long userId);

    Result<?> signCount(Long userId);

    /** 签到状态：今日是否已签、连续/本月/累计天数、可兑换状态 */
    Result<?> signStatus(Long userId);

    /** 累计签到满 30 天兑换 7 天 VIP，每满 30 天可兑换一次 */
    Result<?> redeemSignVip(Long userId);

}
