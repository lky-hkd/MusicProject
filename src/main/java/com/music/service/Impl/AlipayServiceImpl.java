package com.music.service.Impl;

import com.alibaba.fastjson2.JSONObject;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.domain.AlipayTradePagePayModel;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayTradeRefundResponse;
import com.music.config.AlipayConfig;
import com.music.config.OrderProperties;
import com.music.service.AlipayService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

@Slf4j
@Service
public class AlipayServiceImpl implements AlipayService {

    @Resource
    private AlipayClient alipayClient;

    @Resource
    private AlipayConfig alipayConfig;

    @Value("${alipay.notify-url}")
    private String notifyUrl;

    @Value("${alipay.return-url}")
    private String returnUrl;

    @Override
    public String bulidPagePayForm(String orderNo, BigDecimal amount, String subject) {
        AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
        request.setNotifyUrl(notifyUrl);
        request.setReturnUrl(returnUrl);

        AlipayTradePagePayModel model = new AlipayTradePagePayModel();
        model.setOutTradeNo(orderNo);
        model.setTotalAmount(amount.toString());
        model.setSubject(subject);
        model.setProductCode("FAST_INSTANT_TRADE_PAY");
        model.setTimeoutExpress(OrderProperties.PAY_WINDOW_MINUTES + "m");

        request.setBizModel(model);
        try{
            return alipayClient.pageExecute(request).getBody();
        }catch (AlipayApiException e) {
            log.error("支付宝下单失败", e);
            throw new RuntimeException("发起支付宝支付失败: " + e.getErrMsg());
        }
    }

    @Override
    public boolean verifyNotify(Map<String, String> params) {
        try{
            return AlipaySignature.rsaCheckV1(params, alipayConfig.getAlipayPublicKey(),
                    alipayConfig.getCharset(), alipayConfig.getSignType());
        } catch (AlipayApiException e) {
            log.error("支付宝验签异常", e);
            return false;
        }

    }

    @Override
    public boolean refund(String payNo, String outRequestNo,
                          java.math.BigDecimal amount, String reason) {
        try {
            AlipayTradeRefundRequest req = new AlipayTradeRefundRequest();
            JSONObject biz = new JSONObject();
            biz.put("out_trade_no", payNo);         // 原支付订单号
            biz.put("refund_amount", amount.toPlainString());
            biz.put("out_request_no", outRequestNo);      // 退款请求号 —— 幂等键，重复调用不会重复退
            biz.put("refund_reason", reason == null ? "用户申请退款" : reason);
            req.setBizContent(biz.toJSONString());

            AlipayTradeRefundResponse resp = alipayClient.execute(req);
            if (resp.isSuccess()) {
                log.info("支付宝退款成功, orderNo={}, refundNo={}, amount={}",
                       payNo, outRequestNo, amount);
                return true;
            }
            // 失败原因：可能余额不足、订单不存在、已全额退过等
            log.error("支付宝退款失败, orderNo={}, refundNo={}, code={}, msg={}",
                    payNo, outRequestNo, resp.getSubCode(), resp.getSubMsg());
            return false;
        } catch (Exception e) {
            log.error("支付宝退款异常, orderNo={}, refundNo={}", payNo, outRequestNo, e);
            return false;
        }
    }
}
