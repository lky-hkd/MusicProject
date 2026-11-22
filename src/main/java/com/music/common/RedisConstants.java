package com.music.common;

public class RedisConstants {
    public static final String LOGIN_CODE_KEY = "login:code:";
    public static final String LOGIN_CODE_COOLDOWN_KEY = "login:code:cooldown:";
    public static  final String LOGIN_TOKEN_KEY="login:token:";


    public static final Long LOGIN_CODE_TTL = 5L;
    public static final Long LOGIN_TOKEN_TTL = 30L;
    public static final Long SMS_COOLDOWN_TTL = 60L;

    public static final String USER_SIGN_KEY = "sign:";
    /** 用户累计签到天数（跨月累加，仅首次签到当天 +1） */
    public static final String SIGN_TOTAL_KEY = "sign:total:";

}
