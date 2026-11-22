package com.music.dto;

import lombok.Data;

/**
 * 商家商品列表查询入参
 */
@Data
public class MerchantProductQuery {
    private Integer page = 1;
    private Integer size = 10;
    /** 0下架 1上架，null=全部 */
    private String keyword;
    private Integer status;
}
