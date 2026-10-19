package com.lzh.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** 在数据库事务提交后删除受影响的缓存，并对临时 Redis 故障进行有限重试。 */
@Service
@Slf4j
public class CacheAfterCommitService {
    private static final int MAX_ATTEMPTS = 3;
    private final StringRedisTemplate redis;
    private final TaskScheduler scheduler;

    public CacheAfterCommitService(StringRedisTemplate redis,
            @Qualifier("taskScheduler") TaskScheduler scheduler) {
        this.redis = redis;
        this.scheduler = scheduler;
    }

    public void evict(String operation, String... keys) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("缓存失效必须在数据库事务中登记");
        }
        List<String> cacheKeys = Arrays.stream(keys)
                .filter(Objects::nonNull)
                .filter(key -> !key.isBlank())
                .distinct()
                .toList();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                evictWithRetry(operation, cacheKeys, 1);
            }
        });
    }

    private void evictWithRetry(String operation, List<String> keys, int attempt) {
        if (keys.isEmpty()) return;
        try {
            redis.delete(keys);
        } catch (RuntimeException e) {
            if (attempt >= MAX_ATTEMPTS) {
                log.error("事务提交后缓存失效失败，等待缓存自然过期: operation={}, keys={}", operation, keys, e);
                return;
            }
            long delaySeconds = attempt == 1 ? 1 : 5;
            try {
                scheduler.schedule(() -> evictWithRetry(operation, keys, attempt + 1),
                        Instant.now().plusSeconds(delaySeconds));
            } catch (RuntimeException scheduleError) {
                log.error("缓存失效重试任务提交失败，等待缓存自然过期: operation={}, keys={}",
                        operation, keys, scheduleError);
            }
        }
    }
}
