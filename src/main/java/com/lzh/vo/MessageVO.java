package com.lzh.vo;

import com.lzh.dto.UserDTO;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MessageVO {
    private Long id;
    private Integer type;
    private UserDTO fromUser;
    private String content;
    public Integer targetType;
    private Long targetId;
    private Integer status;
    private LocalDateTime createTime;
}
