package com.lzh.service;

import org.springframework.amqp.core.Message;

/** 发布消息并等待 Broker 确认。 */
public interface ConfirmedMessagePublisher {
    void publish(String routingKey, Message message, String token);
}
