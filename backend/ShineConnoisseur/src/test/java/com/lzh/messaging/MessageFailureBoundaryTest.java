package com.lzh.messaging;

import com.lzh.service.impl.MessageOutboxServiceImpl;

import com.lzh.service.impl.MessageFailureServiceImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lzh.config.RabbitConfig;
import com.lzh.consumer.MessageConsumer;
import com.lzh.mapper.MessageFailureMapper;
import com.lzh.mapper.MessageOutboxMapper;
import com.lzh.po.MessageFailure;
import com.lzh.service.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.ImmediateRequeueAmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.listener.adapter.HandlerAdapter;
import org.springframework.amqp.rabbit.listener.adapter.MessagingMessageListenerAdapter;
import org.springframework.messaging.handler.annotation.support.DefaultMessageHandlerMethodFactory;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MessageFailureBoundaryTest {
    @Test void realListenerAdapterPreservesMalformedJsonAndIgnoresTypeHeaders() throws Exception {
        var mapper = mock(MessageFailureMapper.class);
        var outbox = mock(MessageOutboxService.class);
        var messages = mock(IMessageService.class);
        var consumer = new MessageConsumer(messages, outbox,
                new MessageFailureServiceImpl(mapper, outbox, new ObjectMapper()));
        var method = MessageConsumer.class.getMethod("handleMessage", Message.class);
        var adapter = new MessagingMessageListenerAdapter(consumer, method);
        adapter.setMessageConverter(new RabbitConfig().rawMessageConverter());
        var methods = new DefaultMessageHandlerMethodFactory();
        methods.afterPropertiesSet();
        adapter.setHandlerAdapter(new HandlerAdapter(methods.createInvocableHandlerMethod(consumer, method)));
        var raw = MessageReliabilityTest.raw(null);
        raw.getMessageProperties().setHeader("__TypeId__", "does.not.Exist");
        Message invalid = new Message(new byte[]{0}, raw.getMessageProperties());
        adapter.onMessage(invalid, mock(com.rabbitmq.client.Channel.class));
        verify(mapper).insert(argThat(row -> "INVALID".equals(row.getStatus()) && row.getBody()[0] == 0));
        verifyNoInteractions(messages, outbox);
    }

    @Test void failedFinalQuarantineRequestsRequeueInsteadOfDlx() {
        var mapper = mock(MessageFailureMapper.class);
        var outbox = mock(MessageOutboxService.class);
        var messages = mock(IMessageService.class);
        when(messages.save(any())).thenThrow(new IllegalStateException("DB down"));
        when(mapper.insert(any())).thenThrow(new IllegalStateException("DB down"));
        var consumer = new MessageConsumer(messages, outbox,
                new MessageFailureServiceImpl(mapper, outbox, new ObjectMapper()));
        assertThrows(ImmediateRequeueAmqpException.class,
                () -> consumer.handleMessage(MessageReliabilityTest.raw(3)));
    }

    @Test void redeliveryUsesExactlyTheSameRetryDedupKey() {
        var mapper = mock(MessageOutboxMapper.class);
        var service = new MessageOutboxServiceImpl(mapper, new ObjectMapper());
        service.retry(MessageReliabilityTest.dto(), 1, "original-1");
        service.retry(MessageReliabilityTest.dto(), 1, "original-1");
        service.retry(MessageReliabilityTest.dto(), 2, "original-1");
        var keys = ArgumentCaptor.forClass(String.class);
        verify(mapper, times(3)).insertOnce(anyString(), anyString(), anyInt(), anyInt(), keys.capture());
        assertEquals(keys.getAllValues().get(0), keys.getAllValues().get(1));
        assertNotEquals(keys.getAllValues().get(0), keys.getAllValues().get(2));
    }

    @Test void replayedFailureIsNotScheduledAgain() {
        var mapper = mock(MessageFailureMapper.class);
        var outbox = mock(MessageOutboxService.class);
        var row = new MessageFailure(); row.setStatus("REPLAYED");
        when(mapper.lock(1)).thenReturn(row);
        assertFalse(new MessageFailureServiceImpl(mapper, outbox, new ObjectMapper()).replay(1, null));
        verifyNoInteractions(outbox);
    }

    @Test void malformedStoredRecordIsExcludedFromFutureBatchReplay() {
        var mapper = mock(MessageFailureMapper.class);
        var outbox = mock(MessageOutboxService.class);
        var row = new MessageFailure(); row.setStatus("READY"); row.setBody(new byte[]{0});
        when(mapper.lock(1)).thenReturn(row);
        assertFalse(new MessageFailureServiceImpl(mapper, outbox, new ObjectMapper()).replay(1, null));
        verify(mapper).markInvalid(eq(1L), anyString());
        verifyNoInteractions(outbox);
    }

    @Test void correctedPayloadCanReplayAnInvalidRecordWithoutOverwritingAuditBody() {
        var mapper = mock(MessageFailureMapper.class);
        var outbox = mock(MessageOutboxService.class);
        var row = new MessageFailure(); row.setStatus("INVALID"); row.setBody(new byte[]{0});
        when(mapper.lock(1)).thenReturn(row); when(mapper.markReplayed(1)).thenReturn(1);
        assertTrue(new MessageFailureServiceImpl(mapper, outbox, new ObjectMapper())
                .replay(1, MessageReliabilityTest.dto()));
        var order = inOrder(outbox, mapper);
        order.verify(outbox).replay(any(), eq("failure:1"));
        order.verify(mapper).markReplayed(1);
        assertArrayEquals(new byte[]{0}, row.getBody());
    }
}
