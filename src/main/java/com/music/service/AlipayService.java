package com.music.service;

import java.math.BigDecimal;
import java.util.Map;

public interface AlipayService {

    //电脑网站支付：返回支付宝支付页面的完整 HTML 表单
    String bulidPagePayForm(String orderNo,java.math.BigDecimal amount, String subject);

    //异步回调验签 + 校验，返回 true 表示合法
    boolean verifyNotify(Map<String,String> params);

    /** 支付宝退款，返回是否成功 */
    boolean refund(String outTradeNo, String refundNo, BigDecimal amount, String reason);
}
