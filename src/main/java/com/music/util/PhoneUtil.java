package com.music.util;

import java.util.regex.Pattern;

/**
 * 手机号校验工具类
 */
public class PhoneUtil {

    // 国内手机号正则
    private static final String PHONE_REGEX = "^1[3-9]\\d{9}$";
    private static final Pattern PHONE_PATTERN = Pattern.compile(PHONE_REGEX);

    /**
     * 校验手机号是否合法
     *
     * @param phone 手机号
     * @return true 合法，false 不合法
     */
    public static boolean isValidPhone(String phone) {
        // 空值直接返回false
        if (phone == null || phone.isBlank()) {
            return false;
        }
        return PHONE_PATTERN.matcher(phone).matches();
    }
}