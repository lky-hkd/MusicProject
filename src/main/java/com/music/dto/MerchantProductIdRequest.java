package com.music.dto;

import lombok.Data;
/**
 * 只带商品 id 的入参（详情 / 删除共用）
 */
@Data
public class MerchantProductIdRequest {
    private Long id;
}
