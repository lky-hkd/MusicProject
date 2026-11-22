package com.music.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.cookie")
public class CookieProperties {
    /** 是否仅 https 传输：本地 false，上线 true */
    private boolean secure = false;
    /** 是否禁止 JS 读取 */
    private boolean httpOnly = true;
    /** 有效期（天） */
    private int maxAgeDays = 7;
}