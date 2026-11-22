package com.music.dto;

import com.music.entity.Address;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 响应：订单确认页需要的数据
 */
@Data
public class OrderConfirmResponse {

    /** 商品清单（含商品信息快照预览） */
    private List<Item> items;

    /** 是否需要收货地址（有实物商品就要） */
    private Boolean needAddress;

    /** 用户的收货地址列表（needAddress=true 时返回） */
    private List<Address> addresses;

    /** 总金额 */
    private BigDecimal totalAmount;

    @Data
    public static class Item {
        private Long productId;
        private String name;
        private String coverUrl;
        private BigDecimal price;
        private Integer quantity;
        private BigDecimal subtotal;
        private String productType;
    }
}