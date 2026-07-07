package com.lzh.dto;

import lombok.Data;

@Data
public class UserDTO {
    private Long id;

    /**基础信息*/
    private String username;
    private String nickname;
    private String avatar;

}
