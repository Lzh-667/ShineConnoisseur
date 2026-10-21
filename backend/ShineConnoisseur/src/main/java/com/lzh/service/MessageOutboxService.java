package com.lzh.service;

import com.lzh.dto.MessageDTO;

/** 持久化消息发送、延迟重试和回放任务。 */
public interface MessageOutboxService {
    /** 必须加入业务事务，写入失败时回滚整个业务事务。 */
    void enqueue(MessageDTO dto, String routingKey);

    /** 确认原消息前，先提交持久化的延迟重试任务。 */
    void retry(MessageDTO dto, int retryCount, String sourceKey);

    void replay(MessageDTO dto, String sourceKey);

    static void validate(MessageDTO dto) {
        if (dto == null || dto.getUserId() == null || dto.getFromUserId() == null
                || dto.getType() == null || dto.getTargetType() == null || dto.getTargetId() == null) {
            throw new IllegalArgumentException("消息业务键不能为空");
        }
    }
}
