package com.lzh.service;

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

@Slf4j
@Service
public class MessageDeadLetterService {
    private final RabbitTemplate rabbitTemplate;
    private final MessageOutboxService outboxService;
    private final ObjectMapper objectMapper;
    private final MessageFailureService failures;

    public MessageDeadLetterService(@Qualifier("deadLetterRabbitTemplate") RabbitTemplate rabbitTemplate,
            MessageOutboxService outboxService, ObjectMapper objectMapper, MessageFailureService failures) {
        this.rabbitTemplate = rabbitTemplate;
        this.outboxService = outboxService;
        this.objectMapper = objectMapper;
        this.failures = failures;
    }

    public record ReplayResult(int replayed, int quarantined, boolean stoppedOnFailure) {}

    /** Manual basicGet: acknowledge only after a durable outbox transaction has committed. */
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
        return rabbitTemplate.execute(channel -> {
            int replayed = restored;
            int quarantined = invalid;
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
                        channel.basicAck(tag, false);
                        quarantined++;
                        continue;
                    }
                    outboxService.replay(dto, "broker:" + MessageIdentity.delivery(
                            response.getProps().getMessageId(), response.getBody()));
                } catch (RuntimeException e) {
                    channel.basicNack(tag, false, true);
                    log.error("死信重放失败，原消息保留在队列中", e);
                    return new ReplayResult(replayed, quarantined, true);
                }
                channel.basicAck(tag, false);
                replayed++;
            }
            log.info("管理员重放死信消息: count={}", replayed);
            return new ReplayResult(replayed, quarantined, false);
        });
    }
}
