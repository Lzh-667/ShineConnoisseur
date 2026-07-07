package com.lzh.dto;

import lombok.Data;

@Data
public class LoginFormDTO {
    private String phone;
    private String username;
    private String code;
    private String password;
}
