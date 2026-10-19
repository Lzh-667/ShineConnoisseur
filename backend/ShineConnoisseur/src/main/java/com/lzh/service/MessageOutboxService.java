package com.lzh.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lzh.dto.MessageDTO;
import com.lzh.mapper.MessageOutboxMapper;
import com.lzh.utils.MessageIdentity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MessageOutboxService {
    private final MessageOutboxMapper mapper;
    private final ObjectMapper objectMapper;

    /** 必须加入业务事务，写入失败时回滚整个业务事务。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(MessageDTO dto, String routingKey) {
        persist(dto, routingKey, 0, 0, null);
    }

    /** 确认原消息前，先提交持久化的延迟重试任务。 */
    @Transactional
    public void retry(MessageDTO dto, int retryCount, String sourceKey) {
        if (retryCount < 1 || retryCount > 3) {
            throw new IllegalArgumentException("Invalid retry count");
        }
        requireSourceKey(sourceKey);
        persist(dto, "message.retry", retryCount, new int[]{10, 60, 300}[retryCount - 1],
                MessageIdentity.hash("retry:" + sourceKey + ":" + retryCount));
    }

    @Transactional
    public void replay(MessageDTO dto, String sourceKey) {
        requireSourceKey(sourceKey);
        persist(dto, "message.replay", 0, 0, MessageIdentity.hash("replay:" + sourceKey));
    }

    public static void validate(MessageDTO dto) {
        if (dto == null || dto.getUserId() == null || dto.getFromUserId() == null
                || dto.getType() == null || dto.getTargetType() == null || dto.getTargetId() == null) {
            throw new IllegalArgumentException("消息业务键不能为空");
        }
    }

    private static void requireSourceKey(String sourceKey) {
        if (sourceKey == null || sourceKey.isBlank()) throw new IllegalArgumentException("缺少投递标识");
    }

    private void persist(MessageDTO dto, String routingKey, int retryCount, int delay, String dedupKey) {
        validate(dto);
        try {
            String payload = objectMapper.writeValueAsString(dto);
            if (dedupKey != null) {
                mapper.insertOnce(payload, routingKey, retryCount, delay, dedupKey);
            } else if (mapper.insert(payload, routingKey, retryCount, delay) != 1) {
                throw new IllegalStateException("写入消息 Outbox 失败");
            }
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("消息序列化失败", e);
        }
    }
}
