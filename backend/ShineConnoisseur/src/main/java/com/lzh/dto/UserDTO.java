package com.lzh.dto;

import lombok.Data;

@Data
public class UserDTO {
    private Long id;

    /**基础信息*/
    private String username;
    private String nickname;
    private String avatar;

    /** 当前是否为有效 VIP。 */
    private Boolean vip;

}
