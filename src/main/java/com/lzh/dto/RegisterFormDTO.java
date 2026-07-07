package com.lzh.dto;

import lombok.Data;

@Data
public class RegisterFormDTO {
    private String username;
    private String password;
    private String code;
    private String email;
    private String phone;
    private String confirmPassword;
}
