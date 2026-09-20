package com.lzh.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PaymentOrderVO {

    private Long id;
    private String orderNo;
    private String requestId;
    private Long userId;
    private Long productId;
    private Integer durationDays;
    private BigDecimal amount;
    private Integer paymentMethod;
    private Integer status;
    private LocalDateTime expireTime;
}
