package com.lzh.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "修改资料表单")
public class UpdateProfileDTO {
    @Schema(description = "昵称（最长20字）")
    private String nickname;
    @Schema(description = "头像URL")
    private String avatar;
    @Schema(description = "简介（最长200字）")
    private String bio;
    @Schema(description = "性别（0=私密, 1=男, 2=女）")
    private Integer gender;
}
