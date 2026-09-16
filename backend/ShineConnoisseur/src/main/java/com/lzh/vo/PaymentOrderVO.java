package com.lzh.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PaymentOrderVO {

    private String orderNo;
    private BigDecimal amount;
    private Integer paymentMethod;
    private LocalDateTime expireTime;
}