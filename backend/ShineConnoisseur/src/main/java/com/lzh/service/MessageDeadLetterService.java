package com.lzh.service;

/** 回放数据库和 RabbitMQ 中的死信消息。 */
public interface MessageDeadLetterService {
    record ReplayResult(int replayed, int quarantined, boolean stoppedOnFailure) {}

    /** 仅在持久化 Outbox 事务提交后确认原消息。 */
    ReplayResult replay(int limit);
}
