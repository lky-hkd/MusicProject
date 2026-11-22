package com.music.dto;

import com.music.entity.OrderItem;
import lombok.Data;

import java.util.List;

/** 商品秒杀订单落库快照 */
@Data
public class SeckillProductPayload {
    private String payNo;              // 整批支付号
    /** 优惠券抵扣总额（拆单时按订单顺序分摊） */
    private java.math.BigDecimal discountAmount;
    private List<OrderItem> items;     // 已合并、已算好小计的明细快照
    private String remark;
    /** 地址快照（实物才有，可为 null） */
    private Long addressId;
    private String receiver;
    private String phone;
    private String addressDetail;
    /** 是否来自购物车 */
    private String source;
}
