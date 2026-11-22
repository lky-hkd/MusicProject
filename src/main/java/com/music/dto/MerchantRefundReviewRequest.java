package com.music.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

//商家审核退款入参
@Data
public class MerchantRefundReviewRequest {
    @NotBlank(message = "退款单 id 不能为空")
    private Long refundId;

    @NotBlank(message = "请选择处理结果")
    private Boolean approve;

    //拒绝时必填
    private String rejectReason;
}
