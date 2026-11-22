package com.music.service;

import com.music.entity.RefundOrder;

import java.util.List;

public interface RefundService {
    /** 用户申请退款，返回退款单 */
    RefundOrder apply(Long userId, String orderNo, String reason);

    /** 审核通过并执行退款（商家/管理员或自动） */
    void approve(Long refundId);

    /** 审核拒绝 */
    void reject(Long refundId, String rejectReason);

    /** 查询某订单的退款单 */
    List<RefundOrder> listByOrderNo(String orderNo);

    /** 查询用户的退款单列表 */
    List<RefundOrder> listByUserId(Long userId);

    /**
     * 用户申请退款，若命中"小额自动审核"规则则直接审核通过。
     * @return 退款单（可能已是 SUCCESS 状态，需重新查询）
     */
    RefundOrder applyAuto(Long userId, String orderNo, String reason);
}
