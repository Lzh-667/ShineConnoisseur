package com.lzh.config;

import com.lzh.utils.MQConstants;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MessageMQConfig {
    @Bean
    public TopicExchange messageExchange(){
        return new TopicExchange(
                MQConstants.MESSAGE_EXCHANGE,
                true,
                false
        );
    }
    @Bean
    public Queue messageQueue(){

        return new Queue(
                MQConstants.MESSAGE_QUEUE,
                true
        );
    }
    @Bean
    public Binding messageBinding(
            Queue messageQueue,
            TopicExchange messageExchange
    ){

        return BindingBuilder
                .bind(messageQueue)
                .to(messageExchange)
                .with("message.#");

    }
}
