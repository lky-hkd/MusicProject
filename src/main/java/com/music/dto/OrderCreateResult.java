package com.music.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class OrderCreateResult {
    private String payNo;
    private BigDecimal totalAmount;
    private List<String> orderNos;
}
