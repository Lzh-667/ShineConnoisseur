package com.lzh.task;

import com.lzh.mapper.MessageOutboxMapper;
import com.lzh.po.MessageOutbox;
import com.lzh.utils.MQConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import com.lzh.service.ConfirmedMessagePublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class MessageOutboxTask {
    private final MessageOutboxMapper mapper;
    private final ConfirmedMessagePublisher publisher;

    @Scheduled(scheduler = "messageOutboxScheduler", fixedDelayString = "${message.outbox.poll-delay-ms:1000}",
               initialDelayString = "${message.outbox.initial-delay-ms:10000}")
    public void dispatch() {
        for (MessageOutbox row : mapper.findDue()) {
            String token = UUID.randomUUID().toString();
            if (mapper.claim(row.getId(), token) != 1) continue;
            try {
                MessageProperties properties = new MessageProperties();
                properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
                properties.setContentEncoding(StandardCharsets.UTF_8.name());
                properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                properties.setMessageId("outbox-" + row.getId());
                properties.setHeader(MQConstants.RETRY_HEADER, row.getRetryCount());
                publisher.publish(row.getRoutingKey(),
                        new Message(row.getPayload().getBytes(StandardCharsets.UTF_8), properties), token);
                mapper.markSent(row.getId(), token);
            } catch (Exception e) {
                // 持续重试，单次间隔最长五分钟；进程崩溃后通过租约到期恢复。
                int delay = (int) Math.min(300, 1L << Math.min(row.getAttempts() + 1, 9));
                String error = e.getClass().getSimpleName() + ": " + e.getMessage();
                mapper.reschedule(row.getId(), token, delay, error.substring(0, Math.min(1000, error.length())));
                log.error("Outbox 投递失败，稍后重试: id={}", row.getId(), e);
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
            }
        }
    }
}
