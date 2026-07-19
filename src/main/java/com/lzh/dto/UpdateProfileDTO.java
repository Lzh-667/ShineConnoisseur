package com.lzh.dto;

import lombok.Data;

@Data
public class UpdateProfileDTO {
    private String nickname;
    private String avatar;
    private String bio;
    private Integer gender;
}
