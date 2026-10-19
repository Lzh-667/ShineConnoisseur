package com.lzh.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lzh.service.*;
import com.lzh.utils.MQConstants;
import com.lzh.mapper.MessageFailureMapper;
import com.rabbitmq.client.*;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.*;
import org.springframework.amqp.AmqpException;
import java.io.IOException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MessageDeadLetterTest {
    @Test void acknowledgesOnlyAfterOutboxPersistence() throws Exception {
        var fixture = new Fixture();
        assertEquals(1, fixture.service.replay(1).replayed());
        var order = inOrder(fixture.outbox, fixture.channel);
        order.verify(fixture.outbox).replay(any(), anyString());
        order.verify(fixture.channel).basicAck(7L, false);
    }

    @Test void persistenceFailurePreservesOriginalDeadLetter() throws Exception {
        var fixture = new Fixture();
        doThrow(new IllegalStateException("DB down")).when(fixture.outbox).replay(any(), anyString());
        assertTrue(fixture.service.replay(1).stoppedOnFailure());
        verify(fixture.channel).basicNack(7L, false, true);
        verify(fixture.channel, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test void invalidPayloadIsNotDiscarded() throws Exception {
        var fixture = new Fixture();
        when(fixture.channel.basicGet(anyString(), eq(false))).thenReturn(
                new GetResponse(new Envelope(7L, false, "", ""), new AMQP.BasicProperties(), new byte[]{0}, 0));
        var result = fixture.service.replay(1);
        assertFalse(result.stoppedOnFailure());
        assertEquals(1, result.quarantined());
        verifyNoInteractions(fixture.outbox);
        var order = inOrder(fixture.failures, fixture.channel);
        order.verify(fixture.failures).insert(argThat(f -> "INVALID".equals(f.getStatus())));
        order.verify(fixture.channel).basicAck(7L, false);
    }

    @Test void poisonMessageDoesNotBlockNextValidDeadLetter() throws Exception {
        var fixture = new Fixture();
        when(fixture.channel.basicGet(anyString(), eq(false))).thenReturn(
                new GetResponse(new Envelope(7L, false, "", ""), new AMQP.BasicProperties(), new byte[]{0}, 1),
                new GetResponse(new Envelope(8L, false, "", ""), new AMQP.BasicProperties(),
                        new ObjectMapper().writeValueAsBytes(MessageReliabilityTest.dto()), 0));
        var result = fixture.service.replay(2);
        assertEquals(1, result.quarantined());
        assertEquals(1, result.replayed());
        assertFalse(result.stoppedOnFailure());
        verify(fixture.channel).basicAck(7L, false);
        verify(fixture.channel).basicAck(8L, false);
    }

    @Test void failedQuarantineDoesNotAckOrDiscardPoisonMessage() throws Exception {
        var fixture = new Fixture();
        when(fixture.channel.basicGet(anyString(), eq(false))).thenReturn(
                new GetResponse(new Envelope(7L, false, "", ""), new AMQP.BasicProperties(), new byte[]{0}, 0));
        doThrow(new IllegalStateException("DB down")).when(fixture.failures).insert(any());
        assertTrue(fixture.service.replay(1).stoppedOnFailure());
        verify(fixture.channel).basicNack(7L, false, true);
        verify(fixture.channel, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test void brokerFailureReturnsCompletedDatabaseReplayCount() {
        RabbitTemplate template = mock(RabbitTemplate.class);
        MessageOutboxService outbox = mock(MessageOutboxService.class);
        MessageFailureService failures = mock(MessageFailureService.class);
        when(failures.ready(2)).thenReturn(List.of(1L));
        when(failures.replay(1L, null)).thenReturn(true);
        when(template.execute(any())).thenThrow(new AmqpException("broker offline"));
        var service = new MessageDeadLetterService(template, outbox, new ObjectMapper(), failures);

        var result = service.replay(2);

        assertEquals(1, result.replayed());
        assertEquals(0, result.quarantined());
        assertTrue(result.stoppedOnFailure());
    }

    @Test void brokerFailureReturnsCompletedQueueReplayCount() throws Exception {
        RabbitTemplate template = mock(RabbitTemplate.class);
        Channel channel = mock(Channel.class);
        MessageOutboxService outbox = mock(MessageOutboxService.class);
        MessageFailureService failures = mock(MessageFailureService.class);
        ObjectMapper json = new ObjectMapper();
        GetResponse valid = new GetResponse(new Envelope(7L, false, "", ""),
                new AMQP.BasicProperties(), json.writeValueAsBytes(MessageReliabilityTest.dto()), 1);
        when(channel.basicGet(MQConstants.MESSAGE_DLX_QUEUE, false))
                .thenReturn(valid)
                .thenThrow(new IOException("connection lost"));
        when(template.execute(any())).thenAnswer(invocation -> {
            ChannelCallback<?> callback = invocation.getArgument(0);
            try {
                return callback.doInRabbit(channel);
            } catch (Exception e) {
                throw new AmqpException("broker offline", e);
            }
        });
        var service = new MessageDeadLetterService(template, outbox, json, failures);

        var result = service.replay(2);

        assertEquals(1, result.replayed());
        assertTrue(result.stoppedOnFailure());
        verify(channel).basicAck(7L, false);
    }

    private static class Fixture {
        final Channel channel = mock(Channel.class);
        final MessageOutboxService outbox = mock(MessageOutboxService.class);
        final MessageFailureMapper failures = mock(MessageFailureMapper.class);
        final MessageDeadLetterService service;
        Fixture() throws Exception {
            RabbitTemplate template = mock(RabbitTemplate.class);
            ObjectMapper json = new ObjectMapper();
            when(template.execute(any())).thenAnswer(invocation -> {
                ChannelCallback<?> callback = invocation.getArgument(0);
                return callback.doInRabbit(channel);
            });
            when(channel.basicGet(MQConstants.MESSAGE_DLX_QUEUE, false)).thenReturn(
                    new GetResponse(new Envelope(7L, false, "", ""), new AMQP.BasicProperties(),
                            json.writeValueAsBytes(MessageReliabilityTest.dto()), 0));
            service = new MessageDeadLetterService(template, outbox, json, new MessageFailureService(failures, outbox, json));
        }
    }
}
