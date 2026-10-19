package com.lzh.mapper;

import com.lzh.po.MessageOutbox;
import org.apache.ibatis.annotations.*;
import java.util.List;

public interface MessageOutboxMapper {
    @Insert("""
        INSERT INTO message_outbox(payload, routing_key, retry_count, next_attempt_at)
        VALUES (#{payload}, #{routingKey}, #{retryCount}, TIMESTAMPADD(SECOND, #{delaySeconds}, NOW(6)))
        """)
    int insert(@Param("payload") String payload, @Param("routingKey") String routingKey,
               @Param("retryCount") int retryCount, @Param("delaySeconds") int delaySeconds);

    @Insert("""
        INSERT INTO message_outbox(payload, routing_key, retry_count, next_attempt_at, dedup_key)
        VALUES (#{payload}, #{routingKey}, #{retryCount}, TIMESTAMPADD(SECOND, #{delaySeconds}, NOW(6)), #{dedupKey})
        ON DUPLICATE KEY UPDATE id = id
        """)
    int insertOnce(@Param("payload") String payload, @Param("routingKey") String routingKey,
                   @Param("retryCount") int retryCount, @Param("delaySeconds") int delaySeconds,
                   @Param("dedupKey") String dedupKey);

    @Select("""
        SELECT id, payload, routing_key, retry_count, attempts FROM message_outbox
        WHERE status = 'PENDING' AND next_attempt_at <= NOW(6)
        ORDER BY next_attempt_at, id LIMIT 20
        """)
    List<MessageOutbox> findDue();

    // 租约配合隔离令牌，使多个应用实例能够安全地并发投递。
    @Update("""
        UPDATE message_outbox SET lease_token = #{token},
          next_attempt_at = TIMESTAMPADD(SECOND, 60, NOW(6)), attempts = attempts + 1
        WHERE id = #{id} AND status = 'PENDING' AND next_attempt_at <= NOW(6)
        """)
    int claim(@Param("id") long id, @Param("token") String token);

    @Update("""
        UPDATE message_outbox SET status = 'SENT', sent_at = NOW(6), lease_token = NULL, last_error = NULL
        WHERE id = #{id} AND lease_token = #{token}
        """)
    int markSent(@Param("id") long id, @Param("token") String token);

    @Update("""
        UPDATE message_outbox SET next_attempt_at = TIMESTAMPADD(SECOND, #{delay}, NOW(6)),
          lease_token = NULL, last_error = #{error}
        WHERE id = #{id} AND lease_token = #{token}
        """)
    int reschedule(@Param("id") long id, @Param("token") String token,
                   @Param("delay") int delay, @Param("error") String error);
}
