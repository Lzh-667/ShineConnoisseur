package com.lzh.po;

import lombok.Data;

@Data
public class MessageOutbox {
    private Long id;
    private String payload;
    private String routingKey;
    private Integer retryCount;
    private Integer attempts;
}
