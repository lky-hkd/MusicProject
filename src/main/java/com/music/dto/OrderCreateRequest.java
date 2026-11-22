package com.music.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

/**
 * 请求：提交订单
 */
@Data
public class OrderCreateRequest {

    @NotEmpty(message = "商品列表不能为空")
    @Valid   // 关键：让集合内元素也参与校验
    private List<Item> items;

    /** 商品项 */
    /** 收货地址 id（有实物商品时必填） */
    private Long addressId;

    /** 备注（可选） */
    private String remark;

    /** 来源：cart / direct */
    private String source;

    /** 使用的用户优惠券 id（可选） */
    private Long userCouponId;

    @Data
    public static class Item {
        @NotNull(message = "商品ID不能为空")
        private Long productId;

        @NotNull(message = "商品数量不能为空")
        @Min(value = 1, message = "商品数量必须大于0")
        private Integer quantity;
    }
}