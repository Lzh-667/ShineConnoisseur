package com.lzh.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "注册表单")
public class RegisterFormDTO {
    @Schema(description = "用户名")
    private String username;
    @Schema(description = "密码")
    private String password;
    @Schema(description = "验证码")
    private String code;
    @Schema(description = "邮箱（选填）")
    private String email;
    @Schema(description = "手机号")
    private String phone;
    @Schema(description = "确认密码")
    private String confirmPassword;
}
