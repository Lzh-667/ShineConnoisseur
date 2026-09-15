package com.lzh.vo;

import lombok.Data;

import java.math.BigDecimal;


@Data
public class VipProductVO {

    private Long id;
    private String name;
    private Integer durationDays;
    private BigDecimal price;
}