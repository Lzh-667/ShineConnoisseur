package com.lzh.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CacheAfterCommitServiceTest {
    @AfterEach
    void clearTransactionState() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void evictsOnlyAfterCommit() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        TaskScheduler scheduler = mock(TaskScheduler.class);
        var service = new CacheAfterCommitService(redis, scheduler);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();

        service.evict("测试", "cache:a", "cache:b", "cache:a");
        verifyNoInteractions(redis);

        TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCommit());
        verify(redis).delete(List.of("cache:a", "cache:b"));
    }

    @Test
    void schedulesRetryWhenRedisIsTemporarilyUnavailable() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        TaskScheduler scheduler = mock(TaskScheduler.class);
        var service = new CacheAfterCommitService(redis, scheduler);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        doThrow(new IllegalStateException("redis down")).when(redis).delete(List.of("cache:a"));

        service.evict("测试", "cache:a");
        TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCommit());

        verify(scheduler).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    void rejectsRegistrationOutsideTransaction() {
        var service = new CacheAfterCommitService(mock(StringRedisTemplate.class), mock(TaskScheduler.class));
        assertThrows(IllegalStateException.class, () -> service.evict("测试", "cache:a"));
    }
}
