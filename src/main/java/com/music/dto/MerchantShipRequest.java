package com.music.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

//发货入参
@Data
public class MerchantShipRequest {
    @NotBlank(message = "订单号不能为空")
    private String orderNo;

    @NotBlank(message = "快递单号不能为空")
    private String expressNo;
}
