package com.lzh.messaging;

import com.lzh.service.impl.MessageOutboxServiceImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lzh.mapper.MessageOutboxMapper;
import com.lzh.service.MessageOutboxService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.sql.Connection;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MessageOutboxTransactionTest {
    @Configuration
    @EnableTransactionManagement
    static class Config {}

    @Test void enqueueRequiresBusinessTransactionAndRollsBackWithIt() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        var manager = new DataSourceTransactionManager(dataSource);
        var mapper = mock(MessageOutboxMapper.class);
        when(mapper.insert(anyString(), anyString(), anyInt(), anyInt())).thenReturn(1);
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(Config.class, MessageOutboxServiceImpl.class);
            context.registerBean(DataSourceTransactionManager.class, () -> manager);
            context.registerBean(MessageOutboxMapper.class, () -> mapper);
            context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
            context.refresh();
            var outbox = context.getBean(MessageOutboxService.class);
            assertThrows(IllegalTransactionStateException.class,
                    () -> outbox.enqueue(MessageReliabilityTest.dto(), "message.follow"));
            verifyNoInteractions(mapper);
            assertThrows(IllegalStateException.class, () -> new TransactionTemplate(manager).execute(status -> {
                outbox.enqueue(MessageReliabilityTest.dto(), "message.follow");
                throw new IllegalStateException("business transaction failed");
            }));
            verify(connection).rollback();
            verify(connection, never()).commit();
            verify(mapper).insert(anyString(), eq("message.follow"), eq(0), eq(0));
        }
    }
}
