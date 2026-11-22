package com.music.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 商家新增 / 编辑商品的入参
 * 用 DTO 而不是直接收 Product 实体，避免商家伪造 merchantId / sales / 时间等字段
 */
@Data
public class MerchantProductRequest {

    /** 编辑时必传；新增时为空 */
    private Long id;

    private String name;
    private String subtitle;
    private String description;

    /** physical / virtual */
    private String productType;

    private Long categoryId;

    // 虚拟商品
    private String virtualType;
    private Integer virtualValue;

    private BigDecimal price;
    private BigDecimal originalPrice;

    private String coverUrl;
    /** JSON 字符串 */
    private String images;

    private Integer stock;

    /** 0下架 1上架；新增时不传默认 0 */
    private Integer status;
}
