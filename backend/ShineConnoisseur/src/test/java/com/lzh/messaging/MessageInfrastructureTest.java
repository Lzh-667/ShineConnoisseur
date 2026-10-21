package com.lzh.messaging;

import com.lzh.service.impl.MessageDeadLetterServiceImpl;

import com.lzh.service.impl.ConfirmedMessagePublisherImpl;

import com.lzh.service.impl.MessageOutboxServiceImpl;

import com.lzh.service.impl.MessageFailureServiceImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lzh.config.MessageMQConfig;
import com.lzh.config.RabbitConfig;
import com.lzh.consumer.MessageConsumer;
import com.lzh.mapper.MessageFailureMapper;
import com.lzh.mapper.MessageOutboxMapper;
import com.lzh.service.*;
import com.lzh.task.MessageOutboxTask;
import com.lzh.utils.MQConstants;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.MethodRabbitListenerEndpoint;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.boot.autoconfigure.amqp.RabbitTemplateConfigurer;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.messaging.handler.annotation.support.DefaultMessageHandlerMethodFactory;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 需显式启用并启动 docker/compose.message-it.yml，严禁连接应用数据库或应用虚拟主机。 */
@EnabledIfSystemProperty(named = "message.integration", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MessageInfrastructureTest {
    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Transactions {}

    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbc;
    private DataSourceTransactionManager txManager;
    private AnnotationConfigApplicationContext context;
    private MessageOutboxMapper outboxMapper;
    private MessageOutboxService outbox;
    private MessageFailureService failures;
    private RabbitConfig config;
    private CachingConnectionFactory connectionFactory;
    private RabbitTemplate producer;
    private RabbitTemplate receiver;
    private RabbitAdmin admin;
    private ConfirmedMessagePublisher publisher;
    private MessageDeadLetterService replay;

    @BeforeAll void connectToIsolatedServices() throws Exception {
        dataSource = new DriverManagerDataSource("jdbc:mysql://127.0.0.1:23306/shine_message_it?useUnicode=true&characterEncoding=utf8",
                "message_it", "message_it_only");
        jdbc = new JdbcTemplate(dataSource);
        assertEquals("shine_message_it", jdbc.queryForObject("SELECT DATABASE()", String.class));
        if (jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='message'", Integer.class) == 0) {
            String schema = new ClassPathResource("db/create_message.sql").getContentAsString(StandardCharsets.UTF_8)
                    .replace("use shineconnoisseur;", "");
            try (var connection = dataSource.getConnection()) {
                ScriptUtils.executeSqlScript(connection, new EncodedResource(new ByteArrayResource(schema.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8));
            }
        }
        migrate();
        txManager = new DataSourceTransactionManager(dataSource);
        var mybatis = new org.apache.ibatis.session.Configuration();
        mybatis.setMapUnderscoreToCamelCase(true);
        mybatis.addMapper(MessageOutboxMapper.class);
        mybatis.addMapper(MessageFailureMapper.class);
        var factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource); factory.setConfiguration(mybatis);
        var sessions = new SqlSessionTemplate(factory.getObject());
        context = new AnnotationConfigApplicationContext();
        context.register(Transactions.class, MessageOutboxServiceImpl.class, MessageFailureServiceImpl.class);
        context.registerBean(DataSourceTransactionManager.class, () -> txManager);
        context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
        context.registerBean(MessageOutboxMapper.class, () -> sessions.getMapper(MessageOutboxMapper.class));
        context.registerBean(MessageFailureMapper.class, () -> sessions.getMapper(MessageFailureMapper.class));
        context.refresh();
        outboxMapper = context.getBean(MessageOutboxMapper.class);
        outbox = context.getBean(MessageOutboxService.class);
        failures = context.getBean(MessageFailureService.class);

        config = new RabbitConfig();
        var nativeFactory = new com.rabbitmq.client.ConnectionFactory();
        nativeFactory.setHost("127.0.0.1"); nativeFactory.setPort(25672);
        nativeFactory.setUsername("message_it"); nativeFactory.setPassword("message_it_only");
        nativeFactory.setVirtualHost("/shine-message-it"); nativeFactory.setAutomaticRecoveryEnabled(false);
        config.boundedRabbitIo().customize(nativeFactory);
        connectionFactory = new CachingConnectionFactory(nativeFactory);
        connectionFactory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        connectionFactory.setPublisherReturns(true);
        producer = config.rabbitTemplate(connectionFactory, config.messageConverter(), new RabbitTemplateConfigurer(new RabbitProperties()));
        receiver = config.deadLetterRabbitTemplate(connectionFactory);
        publisher = new ConfirmedMessagePublisherImpl(producer, connectionFactory);
        admin = new RabbitAdmin(connectionFactory);
        var topology = new MessageMQConfig();
        admin.declareExchange(topology.messageExchange());
        admin.declareExchange(topology.messageDlxExchange());
        admin.declareQueue(topology.messageQueue());
        admin.declareQueue(topology.messageDlxQueue());
        admin.declareBinding(topology.messageBinding(topology.messageQueue(), topology.messageExchange()));
        admin.declareBinding(topology.messageDlxBinding(topology.messageDlxQueue(), topology.messageDlxExchange()));
        replay = new MessageDeadLetterServiceImpl(receiver, outbox, new ObjectMapper(), failures);
    }

    @BeforeEach void clearOnlyIsolatedFixtures() {
        assertEquals("shine_message_it", jdbc.queryForObject("SELECT DATABASE()", String.class));
        assertEquals("/shine-message-it", connectionFactory.getVirtualHost());
        jdbc.update("DELETE FROM message_outbox"); jdbc.update("DELETE FROM message_failure"); jdbc.update("DELETE FROM message");
        admin.purgeQueue(MQConstants.MESSAGE_QUEUE, false);
        admin.purgeQueue(MQConstants.MESSAGE_DLX_QUEUE, false);
    }

    @AfterAll void closeClients() {
        if (connectionFactory != null) connectionFactory.destroy();
        if (context != null) context.close();
    }

    private void migrate() throws Exception {
        try (var connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new EncodedResource(new ClassPathResource("db/migrate_message_outbox.sql"), StandardCharsets.UTF_8));
        }
    }

    @Test void realBusinessAndOutboxWritesRollbackTogetherAndMigrationIsRepeatable() throws Exception {
        // 模拟已经安装第一版 Outbox 的数据库。
        jdbc.update("ALTER TABLE message_outbox DROP INDEX uk_outbox_dedup, DROP COLUMN dedup_key");
        migrate();
        migrate();
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='message_outbox' AND index_name='uk_outbox_dedup' AND non_unique=0", Integer.class));
        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(txManager).execute(status -> {
            jdbc.update("INSERT INTO message(user_id,from_user_id,type,target_type,target_id) VALUES(1,2,1,1,3)");
            outbox.enqueue(MessageReliabilityTest.dto(), "message.test");
            throw new IllegalStateException("rollback");
        }));
        assertEquals(0, count("message")); assertEquals(0, count("message_outbox"));
    }

    @Test void concurrentRetriesDeduplicateAndExpiredLeaseFencesOldOwner() throws Exception {
        try (var pool = Executors.newFixedThreadPool(8)) {
            List<Callable<Void>> inserts = new ArrayList<>();
            for (int i = 0; i < 16; i++) inserts.add(() -> { outbox.retry(MessageReliabilityTest.dto(), 1, "same-delivery"); return null; });
            for (var result : pool.invokeAll(inserts)) result.get();
            assertEquals(1, count("message_outbox"));
            jdbc.update("UPDATE message_outbox SET next_attempt_at=TIMESTAMPADD(SECOND,-1,NOW(6))");
            long id = outboxMapper.findDue().getFirst().getId();
            List<Callable<Integer>> claims = new ArrayList<>();
            for (int i = 0; i < 16; i++) claims.add(() -> outboxMapper.claim(id, UUID.randomUUID().toString()));
            int won = 0; for (var result : pool.invokeAll(claims)) won += result.get();
            assertEquals(1, won);
            String oldToken = jdbc.queryForObject("SELECT lease_token FROM message_outbox WHERE id=?", String.class, id);
            jdbc.update("UPDATE message_outbox SET next_attempt_at=TIMESTAMPADD(SECOND,-1,NOW(6)) WHERE id=?", id);
            assertEquals(1, outboxMapper.claim(id, "new-owner"));
            assertEquals(0, outboxMapper.markSent(id, oldToken));
            assertEquals(1, outboxMapper.markSent(id, "new-owner"));
        }
    }

    @Test void realReturnKeepsOutboxPendingThenRoutedConfirmMarksSent() {
        new TransactionTemplate(txManager).executeWithoutResult(status -> outbox.enqueue(MessageReliabilityTest.dto(), "no-matching-binding"));
        var task = new MessageOutboxTask(outboxMapper, publisher);
        task.dispatch();
        assertEquals("PENDING", jdbc.queryForObject("SELECT status FROM message_outbox", String.class));
        jdbc.update("UPDATE message_outbox SET routing_key='message.test', next_attempt_at=TIMESTAMPADD(SECOND,-1,NOW(6))");
        task.dispatch();
        assertEquals("SENT", jdbc.queryForObject("SELECT status FROM message_outbox", String.class));
        assertNotNull(receiver.receive(MQConstants.MESSAGE_QUEUE, 5000));
    }

    @Test void brokerRedeliveryAfterLostAckCreatesOnlyOneRetryTask() throws Exception {
        var messages = mock(IMessageService.class);
        when(messages.save(any())).thenThrow(new IllegalStateException("transient business failure"));
        var consumer = new MessageConsumer(messages, outbox, failures);
        publisher.publish("message.test", MessageReliabilityTest.raw(0), "attempt");
        receiver.execute(channel -> {
            var first = channel.basicGet(MQConstants.MESSAGE_QUEUE, false);
            assertNotNull(first);
            consumer.handleMessage(envelope(first));
            channel.basicNack(first.getEnvelope().getDeliveryTag(), false, true);
            // 重试任务提交后，模拟原消息因确认丢失而再次投递。
            var again = channel.basicGet(MQConstants.MESSAGE_QUEUE, false);
            assertNotNull(again);
            consumer.handleMessage(envelope(again));
            channel.basicAck(again.getEnvelope().getDeliveryTag(), false);
            return null;
        });
        assertEquals(1, count("message_outbox"));
    }

    @Test void actualListenerDeduplicatesNotifications() throws Exception {
        var messages = mock(IMessageService.class);
        AtomicInteger attempts = new AtomicInteger();
        when(messages.save(any())).thenAnswer(invocation -> {
            attempts.incrementAndGet();
            return jdbc.update("INSERT INTO message(user_id,from_user_id,type,target_type,target_id) VALUES(1,2,1,1,3)") == 1;
        });
        var container = start(new MessageConsumer(messages, outbox, failures));
        try {
            publisher.publish("message.test", MessageReliabilityTest.raw(0), "a");
            publisher.publish("message.test", MessageReliabilityTest.raw(0), "b");
            await(() -> attempts.get() == 2 && count("message") == 1);
            assertEquals(0, count("message_outbox"));
        } finally { container.stop(); }
    }

    @Test void exhaustedMessagePersistsEvenWithoutDlxBinding() throws Exception {
        var topology = new MessageMQConfig();
        var binding = topology.messageDlxBinding(topology.messageDlxQueue(), topology.messageDlxExchange());
        admin.removeBinding(binding);
        var messages = mock(IMessageService.class);
        when(messages.save(any())).thenThrow(new IllegalStateException("persistent business failure"));
        var container = start(new MessageConsumer(messages, outbox, failures));
        try {
            publisher.publish("message.test", MessageReliabilityTest.raw(3), "last-attempt");
            await(() -> count("message_failure") == 1);
            assertEquals("READY", jdbc.queryForObject("SELECT status FROM message_failure", String.class));
        } finally { container.stop(); admin.declareBinding(binding); }
    }

    @Test void poisonDeadLetterIsSavedAndNextMessageReplays() throws Exception {
        var valid = MessageReliabilityTest.raw(0);
        var invalid = new Message(new byte[]{0}, new MessageProperties());
        for (var body : List.of(invalid, valid)) {
            var correlation = new CorrelationData(UUID.randomUUID().toString());
            producer.send(MQConstants.MESSAGE_DLX_EXCHANGE, MQConstants.MESSAGE_DLX_ROUTING_KEY, body, correlation);
            assertTrue(correlation.getFuture().get(5, TimeUnit.SECONDS).isAck());
        }
        var result = replay.replay(2);
        assertEquals(1, result.replayed()); assertEquals(1, result.quarantined());
        assertFalse(result.stoppedOnFailure());
        assertEquals(1, count("message_outbox")); assertEquals(1, count("message_failure"));
        assertArrayEquals(new byte[]{0}, jdbc.queryForObject("SELECT body FROM message_failure", byte[].class));
        assertNull(receiver.receive(MQConstants.MESSAGE_DLX_QUEUE));
    }

    private int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class); }

    private Message envelope(com.rabbitmq.client.GetResponse response) {
        var properties = new MessageProperties();
        properties.setMessageId(response.getProps().getMessageId());
        if (response.getProps().getHeaders() != null) properties.getHeaders().putAll(response.getProps().getHeaders());
        return new Message(response.getBody(), properties);
    }

    private SimpleMessageListenerContainer start(MessageConsumer consumer) throws Exception {
        var methods = new DefaultMessageHandlerMethodFactory(); methods.afterPropertiesSet();
        var endpoint = new MethodRabbitListenerEndpoint();
        endpoint.setId("message-infrastructure-test"); endpoint.setQueueNames(MQConstants.MESSAGE_QUEUE);
        endpoint.setBean(consumer); endpoint.setMethod(MessageConsumer.class.getMethod("handleMessage", Message.class));
        endpoint.setMessageHandlerMethodFactory(methods);
        var container = config.rabbitListenerContainerFactory(connectionFactory, config.rawMessageConverter()).createListenerContainer(endpoint);
        container.afterPropertiesSet(); container.start();
        return container;
    }

    private void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(50);
        assertTrue(condition.getAsBoolean(), "Timed out waiting for broker/database state");
    }
}
