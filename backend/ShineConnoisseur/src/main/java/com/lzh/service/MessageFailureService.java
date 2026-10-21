package com.lzh.service;

import com.lzh.dto.MessageDTO;
import com.lzh.po.MessageFailure;
import java.io.IOException;
import java.util.List;

/** 保存、查询和回放投递失败的消息。 */
public interface MessageFailureService {
    void capture(String messageId, byte[] body, String queue, boolean invalid, String reason);

    MessageDTO decode(byte[] body) throws IOException;

    List<Long> ready(int limit);

    List<MessageFailure> list(long afterId, int limit);

    MessageFailure find(long id);

    /** 行锁、回放任务写入和状态更新处于同一个数据库事务中。 */
    boolean replay(long id, MessageDTO replacement);
}
