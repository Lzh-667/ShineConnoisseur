package com.lzh.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "登录表单")
public class LoginFormDTO {
    @Schema(description = "手机号（验证码登录）")
    private String phone;
    @Schema(description = "用户名（密码登录）")
    private String username;
    @Schema(description = "验证码")
    private String code;
    @Schema(description = "密码")
    private String password;
}
