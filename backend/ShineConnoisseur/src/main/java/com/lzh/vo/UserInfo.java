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

    /** 当前是否为有效 VIP。 */
    private Boolean vip;
}
