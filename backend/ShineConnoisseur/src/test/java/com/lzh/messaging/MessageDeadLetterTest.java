package com.lzh.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lzh.service.*;
import com.lzh.utils.MQConstants;
import com.lzh.mapper.MessageFailureMapper;
import com.rabbitmq.client.*;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.*;
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
