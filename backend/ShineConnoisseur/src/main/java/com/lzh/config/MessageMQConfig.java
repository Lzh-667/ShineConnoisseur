package com.lzh.config;

import com.lzh.utils.MQConstants;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class MessageMQConfig {

    @Bean
    public TopicExchange messageExchange() {
        return new TopicExchange(MQConstants.MESSAGE_EXCHANGE, true, false);
    }

    /** 死信交换机 */
    @Bean
    public TopicExchange messageDlxExchange() {
        return new TopicExchange(MQConstants.MESSAGE_DLX_EXCHANGE, true, false);
    }

    /** 死信队列 */
    @Bean
    public Queue messageDlxQueue() {
        return new Queue(MQConstants.MESSAGE_DLX_QUEUE, true);
    }

    /** 死信队列绑定到死信交换机 */
    @Bean
    public Binding messageDlxBinding(
            Queue messageDlxQueue,
            TopicExchange messageDlxExchange
    ) {
        return BindingBuilder
                .bind(messageDlxQueue)
                .to(messageDlxExchange)
                .with(MQConstants.MESSAGE_DLX_ROUTING_KEY);
    }

    /** 业务队列 —— 绑定死信交换机，消费失败的消息路由到 DLX */
    @Bean
    public Queue messageQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", MQConstants.MESSAGE_DLX_EXCHANGE);
        args.put("x-dead-letter-routing-key", MQConstants.MESSAGE_DLX_ROUTING_KEY);
        return new Queue(MQConstants.MESSAGE_QUEUE, true, false, false, args);
    }

    @Bean
    public Binding messageBinding(
            Queue messageQueue,
            TopicExchange messageExchange
    ) {
        return BindingBuilder
                .bind(messageQueue)
                .to(messageExchange)
                .with("message.#");
    }
}
