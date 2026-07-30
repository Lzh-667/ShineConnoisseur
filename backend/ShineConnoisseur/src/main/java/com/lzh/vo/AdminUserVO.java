package com.lzh.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminUserVO {
    private Long id;

    private String username;
    private String nickname;
    private String avatar;
    private String email;
    private String phone;

    private Integer reviewCount;
    private Integer followerCount;
    private Integer status;
    private LocalDateTime createTime;
}

