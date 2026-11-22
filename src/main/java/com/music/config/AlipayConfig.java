package com.music.config;

import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class AlipayConfig {
    @Value("${alipay.app-id}")
    private String appId;

    @Value("${alipay.private-key}")
    private String privateKey;

    @Value("${alipay.alipay-public-key}")
    private String alipayPublicKey;

    @Value("${alipay.gateway-url}")
    private String gatewayUrl;

    @Value("${alipay.sign-type}")
    private String signType;

    @Value("${alipay.charset}")
    private String charset;

    @Bean
    public AlipayClient alipayClient(){
        // 兼容从控制台/环境变量复制过来的密钥：去掉换行、空格和 PEM 头尾
        String cleanPrivateKey = cleanKey(privateKey);
        String cleanAlipayPublicKey = cleanKey(alipayPublicKey);

        // 启动时打印实际生效的配置（脱敏），便于核对是否和沙箱控制台一致
        log.info("支付宝配置生效 -> appId={}, gateway={}, signType={}, charset={}",
                appId, gatewayUrl, signType, charset);
        log.info("支付宝密钥生效 -> privateKey(len={}, head={}), alipayPublicKey(len={}, head={})",
                cleanPrivateKey.length(), mask(cleanPrivateKey), cleanAlipayPublicKey.length(), mask(cleanAlipayPublicKey));

        return new DefaultAlipayClient(
                gatewayUrl, appId, cleanPrivateKey,
                "json", charset, cleanAlipayPublicKey, signType);
    }

    /** 去掉空白字符以及 -----BEGIN/END xxx----- 头尾 */
    private String cleanKey(String key) {
        if (key == null) return null;
        return key.replaceAll("-----[^-]+-----", "")
                .replaceAll("\\s", "");
    }

    /** 只显示前 8 位 + 长度，避免密钥泄漏 */
    private String mask(String key) {
        if (key == null || key.isEmpty()) return "EMPTY";
        return key.substring(0, Math.min(8, key.length())) + "...";
    }

    // 供回调验签用
    public String getAlipayPublicKey() { return cleanKey(alipayPublicKey); }
    public String getSignType() { return signType; }
    public String getCharset() { return charset; }
}
