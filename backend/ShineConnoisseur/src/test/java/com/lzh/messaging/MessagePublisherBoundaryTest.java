package com.lzh.messaging;

import com.lzh.service.impl.ConfirmedMessagePublisherImpl;

import com.lzh.service.ConfirmedMessagePublisher;
import com.rabbitmq.client.BlockedListener;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MessagePublisherBoundaryTest {
    @Test void absentConfirmReallyTimesOutAndAbortsOnlyPublisherConnection() {
        var fixture = new Fixture();
        // 保持确认结果未完成，以真实等待超时，而不是直接注入超时异常。
        assertTimeoutPreemptively(Duration.ofSeconds(10), () ->
                assertThrows(AmqpException.class, () -> fixture.publisher.publish("message.test",
                        MessageReliabilityTest.raw(0), "attempt")));
        verify(fixture.nativeConnection).abort(1000);
        verify(fixture.publisherFactory).resetConnection();
        verify(fixture.consumerFactory, never()).resetConnection();
    }

    @Test void blockedPublisherDoesNotEnqueueMoreMessagesAndResumesAfterUnblock() throws Exception {
        var fixture = new Fixture();
        fixture.blocked.handleBlocked("disk alarm");
        assertThrows(AmqpException.class, () -> fixture.publisher.publish("message.test",
                MessageReliabilityTest.raw(0), "attempt"));
        verifyNoInteractions(fixture.template);
        fixture.blocked.handleUnblocked();
        doAnswer(invocation -> {
            CorrelationData cd = invocation.getArgument(3);
            cd.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(fixture.template).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        fixture.publisher.publish("message.test", MessageReliabilityTest.raw(0), "attempt-2");
        verify(fixture.template).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        verify(fixture.nativeConnection, never()).abort(anyInt());
    }

    @Test void enqueueFailureAbortsConnectionAndKeepsExceptionVisibleToOutbox() {
        var fixture = new Fixture();
        doThrow(new AmqpException("NIO write enqueue timed out")).when(fixture.template)
                .send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        assertThrows(AmqpException.class, () -> fixture.publisher.publish("message.test",
                MessageReliabilityTest.raw(0), "attempt"));
        verify(fixture.nativeConnection).abort(1000);
        verify(fixture.publisherFactory).resetConnection();
    }

    @Test void closedBlockedConnectionDoesNotPreventRecovery() throws Exception {
        var fixture = new Fixture();
        fixture.blocked.handleBlocked("disk alarm");
        when(fixture.nativeConnection.isOpen()).thenReturn(false);
        fixture.connectionListener.onClose(fixture.springConnection);
        doAnswer(invocation -> {
            CorrelationData cd = invocation.getArgument(3);
            cd.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(fixture.template).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));

        assertDoesNotThrow(() -> fixture.publisher.publish("message.test",
                MessageReliabilityTest.raw(0), "recovered-attempt"));
        verify(fixture.template).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
    }

    private static class Fixture {
        final RabbitTemplate template = mock(RabbitTemplate.class);
        final CachingConnectionFactory consumerFactory = mock(CachingConnectionFactory.class);
        final CachingConnectionFactory publisherFactory = mock(CachingConnectionFactory.class);
        final com.rabbitmq.client.Connection nativeConnection = mock(com.rabbitmq.client.Connection.class);
        final ConfirmedMessagePublisher publisher;
        final BlockedListener blocked;
        final ConnectionListener connectionListener;
        final Connection springConnection = mock(Connection.class);
        Fixture() {
            when(consumerFactory.getPublisherConnectionFactory()).thenReturn(publisherFactory);
            publisher = new ConfirmedMessagePublisherImpl(template, consumerFactory);
            var listener = ArgumentCaptor.forClass(ConnectionListener.class);
            verify(publisherFactory).addConnectionListener(listener.capture());
            when(springConnection.getDelegate()).thenReturn(nativeConnection);
            when(nativeConnection.isOpen()).thenReturn(true);
            connectionListener = listener.getValue();
            connectionListener.onCreate(springConnection);
            var blockedListener = ArgumentCaptor.forClass(BlockedListener.class);
            verify(nativeConnection).addBlockedListener(blockedListener.capture());
            blocked = blockedListener.getValue();
        }
    }
}
