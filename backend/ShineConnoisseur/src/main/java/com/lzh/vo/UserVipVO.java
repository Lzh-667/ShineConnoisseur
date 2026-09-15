package com.lzh.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserVipVO {

    private Long id;
    private boolean isVip;
    private LocalDateTime startTime;
    private LocalDateTime expireTime;
}