package com.lzh.consumer;

import com.lzh.dto.MessageDTO;
import com.lzh.po.Message;
import com.lzh.service.IMessageService;
import com.lzh.service.MessageOutboxService;
import com.lzh.service.MessageFailureService;
import com.lzh.utils.MQConstants;
import com.lzh.utils.MessageIdentity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.amqp.ImmediateRequeueAmqpException;
import java.io.IOException;

@Component
@Slf4j
@RequiredArgsConstructor
public class MessageConsumer {
    private final IMessageService messageService;
    private final MessageOutboxService outboxService;
    private final MessageFailureService failures;

    @RabbitListener(queues = MQConstants.MESSAGE_QUEUE)
    public void handleMessage(org.springframework.amqp.core.Message raw) {
        try {
            consume(raw);
        } catch (RuntimeException e) {
            // Advice backs off before requeueing. No durable successor means no ACK or DLX handoff.
            throw new ImmediateRequeueAmqpException("消息处理/隔离记录未持久化，保留原消息", e);
        }
    }

    private void consume(org.springframework.amqp.core.Message raw) {
        String messageId = raw.getMessageProperties().getMessageId();
        MessageDTO dto;
        int retries;
        try {
            dto = failures.decode(raw.getBody());
            Object header = raw.getMessageProperties().getHeaders().get(MQConstants.RETRY_HEADER);
            retries = header == null ? 0 : Integer.parseInt(header.toString());
            if (retries < 0 || retries > 3) throw new IllegalArgumentException("Invalid retry count");
        } catch (IOException | IllegalArgumentException e) {
            failures.capture(messageId, raw.getBody(), MQConstants.MESSAGE_QUEUE, true, e.toString());
            return;
        }
        Message message = new Message();
        BeanUtils.copyProperties(dto, message);
        message.setStatus(0);
        try {
            if (!messageService.save(message)) {
                throw new IllegalStateException("消息写入失败");
            }
        } catch (DuplicateKeyException e) {
            log.debug("业务重复消息，已忽略: messageId={}", messageId);
        } catch (RuntimeException e) {
            if (retries >= 3) {
                failures.capture(messageId, raw.getBody(), MQConstants.MESSAGE_QUEUE, false, e.toString());
            } else {
                outboxService.retry(dto, retries + 1, MessageIdentity.delivery(messageId, raw.getBody()));
            }
        }
    }
}
