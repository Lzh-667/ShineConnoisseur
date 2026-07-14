package com.lzh.vo;

import lombok.Data;

@Data
public class UserInfo {
    private Long id;

    private String username;
    private String nickname;
    private String avatar;
    private String bio;
    private Integer gender;

    private Integer reviewCount;
    private Integer followingCount;
    private Integer followerCount;
}
