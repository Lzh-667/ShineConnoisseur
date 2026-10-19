package com.lzh.service;

import com.lzh.utils.MQConstants;
import com.rabbitmq.client.BlockedListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionListener;
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
        publisher.addConnectionListener(new ConnectionListener() {
            @Override
            public void onCreate(Connection connection) {
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
            }

            @Override
            public void onClose(Connection connection) {
                clearConnection(connection.getDelegate());
            }

            @Override
            public void onShutDown(com.rabbitmq.client.ShutdownSignalException signal) {
                Object reference = signal.getReference();
                if (reference instanceof com.rabbitmq.client.Connection connection) clearConnection(connection);
                else clearClosedConnection();
            }
        });
    }

    public void publish(String routingKey, Message message, String token) {
        if (blocked.get()) {
            var connection = active.get();
            if (connection == null || !connection.isOpen()) clearConnection(connection);
            else throw new AmqpException("RabbitMQ publisher connection is blocked");
        }
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
            // NIO 会限制写入排队和信道调用时间；下次尝试前中止已经超时的待写数据。
            // 这里只重置发布连接，消费投递和消息确认继续使用各自的连接。
            abortPublisher();
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new AmqpException("Confirmed message publish failed: " + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    private void clearClosedConnection() {
        var connection = active.get();
        if (connection != null && !connection.isOpen()) clearConnection(connection);
    }

    private void clearConnection(com.rabbitmq.client.Connection connection) {
        if (connection == null) {
            if (active.get() == null) blocked.set(false);
        } else if (active.compareAndSet(connection, null)) {
            blocked.set(false);
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
