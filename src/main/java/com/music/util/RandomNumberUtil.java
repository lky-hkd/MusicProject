package com.music.util;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public class RandomNumberUtil {
    private static final Random RANDOM = new Random();

    /**
     * 生成指定长度数字字符串，首位不为0
     * @param length 长度，必须大于0
     * @return 数字字符串
     */
    public static String generateNumStr(int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("长度必须大于0");
        }
        StringBuilder sb = new StringBuilder(length);
        sb.append(ThreadLocalRandom.current().nextInt(9) + 1);
        for (int i = 1; i < length; i++) {
            sb.append(ThreadLocalRandom.current().nextInt(10));
        }
        return sb.toString();
    }

    /**
     * 生成指定长度数字字符串，允许首位为0（验证码）
     * @param length 长度
     * @return 数字字符串
     */
    public static String generateNumStrWithZero(int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("长度必须大于0");
        }
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }
}
