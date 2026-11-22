package com.music.controller;

import com.music.common.Result;
import com.music.common.UserContext;
import com.music.entity.RefundOrder;
import com.music.service.RefundService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/refund")
public class RefundController {

    @Resource
    private RefundService refundService;

    /** 用户申请退款 */
    @PostMapping("/apply")
    public Result<?> apply(@RequestParam String orderNo,
                           @RequestParam(required = false) String reason,
                           @RequestAttribute(UserContext.USER_ID_ATTR) Long userId) {
        RefundOrder r = refundService.apply(userId, orderNo, reason);
        return Result.ok(r, "退款申请已提交，等待审核");
    }

    /** 用户查看某订单的退款进度 */
    @GetMapping("/list")
    public Result<List<RefundOrder>> list(@RequestParam String orderNo,
                                          @RequestAttribute(UserContext.USER_ID_ATTR) Long userId) {
        return Result.ok(refundService.listByOrderNo(orderNo));
    }

    /** 我的所有退款单 */
    @GetMapping("/my")
    public Result<List<RefundOrder>> my(@RequestAttribute(UserContext.USER_ID_ATTR) Long userId) {
        return Result.ok(refundService.listByUserId(userId));
    }
}