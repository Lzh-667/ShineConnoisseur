package com.lzh.vo;

import lombok.Data;

@Data
public class AdminDashboardVO {
    private Long userCount;
    private Long movieCount;
    private Long reviewCount;
    private Long todayReviewCount;
    private Long weekReviewCount;
    public String updateTime;
}
