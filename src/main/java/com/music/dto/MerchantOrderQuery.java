package com.music.dto;

import lombok.Data;

@Data
public class MerchantOrderQuery {

    private Integer page;
    private Integer size;
    //订单状态，null=全部
    private Integer status;
    //订单号 / 收件人 模糊搜索
    private String keyword;

}
