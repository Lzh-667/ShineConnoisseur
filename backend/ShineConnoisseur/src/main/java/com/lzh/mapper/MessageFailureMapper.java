package com.lzh.mapper;

import com.lzh.po.MessageFailure;
import org.apache.ibatis.annotations.*;
import java.util.List;

public interface MessageFailureMapper {
    @Insert("""
        INSERT INTO message_failure(source_key, source_queue, message_id, body, status, reason)
        VALUES (#{sourceKey}, #{sourceQueue}, #{messageId}, #{body}, #{status}, #{reason})
        ON DUPLICATE KEY UPDATE id = id
        """)
    int insert(MessageFailure failure);

    @Select("SELECT id FROM message_failure WHERE status = 'READY' ORDER BY id LIMIT #{limit}")
    List<Long> findReady(int limit);

    @Select("SELECT * FROM message_failure WHERE id = #{id} FOR UPDATE")
    MessageFailure lock(long id);

    @Select("SELECT * FROM message_failure WHERE id = #{id}")
    MessageFailure find(long id);

    @Select("""
        SELECT id, source_key, source_queue, message_id, status, reason, created_at, replayed_at
        FROM message_failure WHERE id > #{afterId} ORDER BY id LIMIT #{limit}
        """)
    List<MessageFailure> list(@Param("afterId") long afterId, @Param("limit") int limit);

    @Update("UPDATE message_failure SET status = 'REPLAYED', replayed_at = NOW(6) WHERE id = #{id}")
    int markReplayed(long id);

    @Update("UPDATE message_failure SET status = 'INVALID', reason = #{reason} WHERE id = #{id}")
    int markInvalid(@Param("id") long id, @Param("reason") String reason);
}
