package com.lzh.config;

import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;

import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.amqp.RabbitTemplateConfigurer;
import org.springframework.boot.autoconfigure.amqp.ConnectionFactoryCustomizer;
import org.springframework.context.annotation.Primary;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.retry.ImmediateRequeueMessageRecoverer;
import com.rabbitmq.client.impl.nio.NioParams;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

@Configuration
public class RabbitConfig {
    @Bean
    public ConnectionFactoryCustomizer boundedRabbitIo() {
        return factory -> {
            factory.useNio();
            factory.setNioParams(new NioParams().setWriteQueueCapacity(256).setWriteEnqueuingTimeoutInMs(1000));
            factory.setConnectionTimeout(1000);
            factory.setHandshakeTimeout(5000);
            factory.setChannelRpcTimeout(5000);
            factory.setShutdownTimeout(1000);
        };
    }
    @Bean
    public org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler taskScheduler() {
        var scheduler = new org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("scheduled-");
        return scheduler;
    }

    @Bean
    public org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler messageOutboxScheduler() {
        var scheduler = new org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("message-outbox-");
        return scheduler;
    }

    @Bean
    @Primary
    public MessageConverter messageConverter(){
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public MessageConverter rawMessageConverter() {
        return new MessageConverter() {
            @Override public Object fromMessage(Message message) { return message.getBody(); }
            @Override public Message toMessage(Object body, MessageProperties properties) {
                if (body instanceof byte[] bytes) return new Message(bytes, properties);
                throw new IllegalArgumentException("Raw listener only supports byte arrays");
            }
        };
    }
    @Bean
    @Primary
    public RabbitTemplate rabbitTemplate(
            ConnectionFactory connectionFactory,
            MessageConverter converter,
            RabbitTemplateConfigurer configurer
    ){
        RabbitTemplate template =
                new RabbitTemplate(connectionFactory);
        configurer.configure(template, connectionFactory);
        template.setMandatory(true);
        template.setUsePublisherConnection(true);
        template.setMessageConverter(converter);
        return template;
    }

    @Bean
    public RabbitTemplate deadLetterRabbitTemplate(ConnectionFactory connectionFactory) {
        // basicGet/ACK use the consumer connection, never the blocked publisher connection.
        return new RabbitTemplate(connectionFactory);
    }
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            @Qualifier("rawMessageConverter") MessageConverter converter
    ){
        SimpleRabbitListenerContainerFactory factory =
                new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(converter);
        factory.setAcknowledgeMode(org.springframework.amqp.core.AcknowledgeMode.AUTO);
        factory.setDefaultRequeueRejected(false);
        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                .maxAttempts(3).backOffOptions(1000, 2, 5000)
                .recoverer(new ImmediateRequeueMessageRecoverer()).build());
        return factory;
    }
}
