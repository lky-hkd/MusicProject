package com.music.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

//申请开店
@Data
public class MerchantApplyRequest {

    @NotBlank(message = "店铺名称不能为空")
    @Size(max=30, message = "店铺名称最多 30 字")
    private String shopName;

    private String logo;

    @Size(max = 200,message = "店铺简介最多 200 字")
    private String description;

    private String contactPhone;
}
