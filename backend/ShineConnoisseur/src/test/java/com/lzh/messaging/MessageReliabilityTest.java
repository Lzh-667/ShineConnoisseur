package com.lzh.messaging;

import com.lzh.service.impl.ConfirmedMessagePublisherImpl;

import com.lzh.service.impl.MessageOutboxServiceImpl;

import com.lzh.service.impl.MessageFailureServiceImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lzh.consumer.MessageConsumer;
import com.lzh.dto.MessageDTO;
import com.lzh.mapper.MessageOutboxMapper;
import com.lzh.mapper.MessageFailureMapper;
import com.lzh.po.MessageOutbox;
import com.lzh.service.*;
import com.lzh.task.MessageOutboxTask;
import com.lzh.utils.MQConstants;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.ImmediateRequeueAmqpException;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import java.util.concurrent.TimeoutException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MessageReliabilityTest {
    private final MessageOutboxMapper mapper = mock(MessageOutboxMapper.class);
    private final RabbitTemplate template = mock(RabbitTemplate.class);
    private final IMessageService messages = mock(IMessageService.class);
    private final MessageOutboxService outbox = mock(MessageOutboxService.class);
    private final MessageFailureMapper failureMapper = mock(MessageFailureMapper.class);
    private final MessageFailureService failures = new MessageFailureServiceImpl(failureMapper, outbox, new ObjectMapper());

    static MessageDTO dto() {
        MessageDTO dto = new MessageDTO();
        dto.setUserId(1L); dto.setFromUserId(2L); dto.setType(1);
        dto.setTargetType(1); dto.setTargetId(3L); dto.setContent("点赞通知");
        return dto;
    }

    private MessageConsumer consumer() {
        return new MessageConsumer(messages, outbox, failures);
    }

    static Message raw(Integer retries) {
        try {
            var properties = new MessageProperties();
            properties.setMessageId("outbox-1");
            properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            if (retries != null) properties.setHeader(MQConstants.RETRY_HEADER, retries);
            return new Message(new ObjectMapper().writeValueAsBytes(dto()), properties);
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    @Test void successfulConsumptionDoesNotRetry() {
        when(messages.save(any())).thenReturn(true);
        consumer().handleMessage(raw(null));
        verifyNoInteractions(outbox);
    }

    @Test void duplicateBusinessKeyIsAcknowledgedWithoutRetry() {
        when(messages.save(any())).thenThrow(new DuplicateKeyException("uk_biz_key"));
        assertDoesNotThrow(() -> consumer().handleMessage(raw(3)));
        verifyNoInteractions(outbox);
    }

    @Test void failedInsertSchedulesDurableRetry() {
        when(messages.save(any())).thenReturn(false);
        consumer().handleMessage(raw(null));
        verify(outbox).retry(eq(dto()), eq(1), anyString());
    }

    @Test void thirdRetryFailureIsDurablyQuarantinedBeforeSuccess() {
        when(messages.save(any())).thenThrow(new IllegalStateException("DB down"));
        assertDoesNotThrow(() -> consumer().handleMessage(raw(3)));
        verify(failureMapper).insert(argThat(f -> "READY".equals(f.getStatus())));
        verifyNoInteractions(outbox);
    }

    @Test void retryPersistenceFailureMustNotReturnSuccess() {
        when(messages.save(any())).thenThrow(new IllegalStateException("DB down"));
        doThrow(new IllegalStateException("outbox down")).when(outbox).retry(any(), anyInt(), anyString());
        assertThrows(ImmediateRequeueAmqpException.class, () -> consumer().handleMessage(raw(1)));
    }

    @Test void retryDelaysArePersistedAndBounded() {
        var service = new MessageOutboxServiceImpl(mapper, new ObjectMapper());
        service.retry(dto(), 1, "delivery"); service.retry(dto(), 2, "delivery"); service.retry(dto(), 3, "delivery");
        verify(mapper).insertOnce(anyString(), eq("message.retry"), eq(1), eq(10), anyString());
        verify(mapper).insertOnce(anyString(), eq("message.retry"), eq(2), eq(60), anyString());
        verify(mapper).insertOnce(anyString(), eq("message.retry"), eq(3), eq(300), anyString());
        assertThrows(IllegalArgumentException.class, () -> service.retry(dto(), 4, "delivery"));
    }

    private MessageOutboxTask task() {
        var row = new MessageOutbox();
        row.setId(5L); row.setPayload("{}"); row.setRoutingKey("message.follow");
        row.setRetryCount(2); row.setAttempts(0);
        when(mapper.findDue()).thenReturn(List.of(row));
        when(mapper.claim(eq(5L), anyString())).thenReturn(1);
        var connectionFactory = mock(CachingConnectionFactory.class);
        when(connectionFactory.getPublisherConnectionFactory()).thenReturn(mock(CachingConnectionFactory.class));
        return new MessageOutboxTask(mapper, new ConfirmedMessagePublisherImpl(template, connectionFactory));
    }

    private void confirm(boolean ack, boolean returned) {
        doAnswer(invocation -> {
            Message sent = invocation.getArgument(2);
            assertEquals(MessageDeliveryMode.PERSISTENT, sent.getMessageProperties().getDeliveryMode());
            assertEquals(Integer.valueOf(2), sent.getMessageProperties().<Integer>getHeader(MQConstants.RETRY_HEADER));
            CorrelationData correlation = invocation.getArgument(3);
            if (returned) correlation.setReturned(new ReturnedMessage(sent, 312, "NO_ROUTE", "message.exchange", "message.follow"));
            correlation.getFuture().complete(new CorrelationData.Confirm(ack, ack ? null : "nack"));
            return null;
        }).when(template).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
    }

    @Test void sentOnlyAfterPositiveConfirm() {
        var task = task(); confirm(true, false); task.dispatch();
        verify(mapper).markSent(eq(5L), anyString());
        verify(mapper, never()).reschedule(anyLong(), anyString(), anyInt(), anyString());
    }

    @Test void ackWithReturnIsNotSuccessfulDelivery() {
        var task = task(); confirm(true, true); task.dispatch();
        verify(mapper, never()).markSent(anyLong(), anyString());
        verify(mapper).reschedule(eq(5L), anyString(), eq(2), anyString());
    }

    @Test void nackIsRetried() {
        var task = task(); confirm(false, false); task.dispatch();
        verify(mapper, never()).markSent(anyLong(), anyString());
        verify(mapper).reschedule(eq(5L), anyString(), eq(2), anyString());
    }

    @Test void timeoutIsRetried() {
        var task = task();
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().completeExceptionally(new TimeoutException());
            return null;
        }).when(template).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        task.dispatch();
        verify(mapper, never()).markSent(anyLong(), anyString());
        verify(mapper).reschedule(eq(5L), anyString(), anyInt(), anyString());
    }

    @Test void competingDispatcherCannotSendWithoutLease() {
        var task = task(); when(mapper.claim(eq(5L), anyString())).thenReturn(0);
        task.dispatch(); verifyNoInteractions(template);
    }
}
