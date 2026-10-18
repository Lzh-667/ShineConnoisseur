package com.lzh.service;

import com.lzh.utils.MQConstants;
import com.rabbitmq.client.BlockedListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Service
@Slf4j
public class ConfirmedMessagePublisher {
    private final RabbitTemplate template;
    private final CachingConnectionFactory publisherFactory;
    private final AtomicReference<com.rabbitmq.client.Connection> active = new AtomicReference<>();
    private final AtomicBoolean blocked = new AtomicBoolean();

    public ConfirmedMessagePublisher(RabbitTemplate template, ConnectionFactory connectionFactory) {
        this.template = template;
        if (!(connectionFactory.getPublisherConnectionFactory() instanceof CachingConnectionFactory publisher)) {
            throw new IllegalStateException("Outbox requires an isolated caching publisher connection");
        }
        this.publisherFactory = publisher;
        publisher.setCloseTimeout(1000);
        publisher.addConnectionListener(connection -> {
            var delegate = connection.getDelegate();
            active.set(delegate);
            blocked.set(false);
            delegate.addBlockedListener(new BlockedListener() {
                @Override public void handleBlocked(String reason) {
                    if (active.get() == delegate) {
                        blocked.set(true);
                        log.warn("RabbitMQ 发布连接被阻塞: {}", reason);
                    }
                }
                @Override public void handleUnblocked() {
                    if (active.get() == delegate) blocked.set(false);
                }
            });
        });
    }

    public void publish(String routingKey, Message message, String token) {
        if (blocked.get()) throw new AmqpException("RabbitMQ publisher connection is blocked");
        CorrelationData correlation = new CorrelationData(token);
        try {
            template.send(MQConstants.MESSAGE_EXCHANGE, routingKey, message, correlation);
            var confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
            var returned = correlation.getReturned();
            if (returned != null) {
                throw new AmqpException("Broker returned message: " + returned.getReplyCode() + " " + returned.getReplyText());
            }
            if (!confirm.isAck()) throw new AmqpException("Broker NACK: " + confirm.getReason());
        } catch (Exception e) {
            // NIO bounds enqueueing and channel RPC. Abort timed-out queued writes before the next attempt.
            // Only the publisher connection is reset; consumer deliveries and ACKs stay on their connection.
            abortPublisher();
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new AmqpException("Confirmed message publish failed: " + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    private void abortPublisher() {
        var connection = active.getAndSet(null);
        try {
            if (connection != null) connection.abort(1000);
        } catch (RuntimeException e) {
            log.warn("中止发布连接失败", e);
        } finally {
            blocked.set(false);
            publisherFactory.resetConnection();
        }
    }
}
