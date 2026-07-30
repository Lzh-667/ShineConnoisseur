package com.lzh.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "管理员登录表单")
public class AdminLoginDTO {
    @Schema(description = "管理员用户名")
    private String username;
    @Schema(description = "密码")
    private String password;
}
