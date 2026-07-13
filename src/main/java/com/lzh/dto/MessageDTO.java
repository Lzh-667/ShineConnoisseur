package com.lzh.dto;

import lombok.Data;

@Data
public class MessageDTO {

    private Long userId;

    private Long fromUserId;

    private Integer type;

    private Integer targetType;

    private Long targetId;

    private String content;
}
