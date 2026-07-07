package com.lzh.dto;

import lombok.Data;

@Data
public class AdminDTO {
    private Long id;

    private String username;
    private String realName;
    private String avatar;
    private Integer role;
}
