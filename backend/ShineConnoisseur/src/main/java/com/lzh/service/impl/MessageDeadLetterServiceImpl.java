package com.lzh.service.impl;

import com.lzh.service.MessageDeadLetterService;
import com.lzh.service.MessageOutboxService;
import com.lzh.service.MessageFailureService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lzh.dto.MessageDTO;
import com.lzh.utils.MQConstants;
import com.lzh.utils.MessageIdentity;
import com.rabbitmq.client.GetResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Qualifier;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class MessageDeadLetterServiceImpl implements MessageDeadLetterService {
    private final RabbitTemplate rabbitTemplate;
    private final MessageOutboxService outboxService;
    private final ObjectMapper objectMapper;
    private final MessageFailureService failures;

    public MessageDeadLetterServiceImpl(@Qualifier("deadLetterRabbitTemplate") RabbitTemplate rabbitTemplate,
            MessageOutboxService outboxService, ObjectMapper objectMapper, MessageFailureService failures) {
        this.rabbitTemplate = rabbitTemplate;
        this.outboxService = outboxService;
        this.objectMapper = objectMapper;
        this.failures = failures;
    }

    /** 手动拉取死信，仅在持久化 Outbox 事务提交后确认原消息。 */
    @Override
    public ReplayResult replay(int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("limit 必须在 1 到 100 之间");
        int databaseReplayed = 0;
        int databaseInvalid = 0;
        int databaseProcessed = 0;
        for (long id : failures.ready(limit)) {
            databaseProcessed++;
            try {
                if (failures.replay(id, null)) databaseReplayed++;
                else {
                    var record = failures.find(id);
                    if (record != null && "INVALID".equals(record.getStatus())) databaseInvalid++;
                }
            } catch (RuntimeException e) {
                log.error("数据库死信重放失败，事务保留原记录: id={}", id, e);
                return new ReplayResult(databaseReplayed, databaseInvalid, true);
            }
        }
        final int restored = databaseReplayed;
        final int invalid = databaseInvalid;
        final int processed = databaseProcessed;
        if (processed >= limit) return new ReplayResult(restored, invalid, false);
        AtomicInteger replayedCount = new AtomicInteger(restored);
        AtomicInteger quarantinedCount = new AtomicInteger(invalid);
        try {
            ReplayResult result = rabbitTemplate.execute(channel -> {
                for (int attempted = processed; attempted < limit; attempted++) {
                    GetResponse response = channel.basicGet(MQConstants.MESSAGE_DLX_QUEUE, false);
                    if (response == null) break;
                    long tag = response.getEnvelope().getDeliveryTag();
                    try {
                        MessageDTO dto;
                        try {
                            dto = objectMapper.readValue(response.getBody(), MessageDTO.class);
                            MessageOutboxService.validate(dto);
                        } catch (IOException | IllegalArgumentException malformed) {
                            failures.capture(response.getProps().getMessageId(), response.getBody(),
                                    MQConstants.MESSAGE_DLX_QUEUE, true, malformed.toString());
                            quarantinedCount.incrementAndGet();
                            channel.basicAck(tag, false);
                            continue;
                        }
                        outboxService.replay(dto, "broker:" + MessageIdentity.delivery(
                                response.getProps().getMessageId(), response.getBody()));
                        replayedCount.incrementAndGet();
                    } catch (RuntimeException e) {
                        channel.basicNack(tag, false, true);
                        log.error("死信重放失败，原消息保留在队列中", e);
                        return new ReplayResult(replayedCount.get(), quarantinedCount.get(), true);
                    }
                    channel.basicAck(tag, false);
                }
                log.info("管理员重放死信消息: count={}", replayedCount.get());
                return new ReplayResult(replayedCount.get(), quarantinedCount.get(), false);
            });
            return result == null
                    ? new ReplayResult(replayedCount.get(), quarantinedCount.get(), true)
                    : result;
        } catch (RuntimeException e) {
            log.error("RabbitMQ 死信队列不可用，本批已完成的回放结果已保留", e);
            return new ReplayResult(replayedCount.get(), quarantinedCount.get(), true);
        }
    }
}
