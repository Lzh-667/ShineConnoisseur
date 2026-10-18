package com.lzh.po;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class MessageFailure {
    private Long id;
    private String sourceKey;
    private String sourceQueue;
    private String messageId;
    private byte[] body;
    private String status;
    private String reason;
    private LocalDateTime createdAt;
    private LocalDateTime replayedAt;
}
