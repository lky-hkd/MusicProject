package com.music.dto;

import lombok.Data;

@Data
public class MerchantRefundQuery {

    private Integer page;
    private Integer size;
    private Integer status;
}
