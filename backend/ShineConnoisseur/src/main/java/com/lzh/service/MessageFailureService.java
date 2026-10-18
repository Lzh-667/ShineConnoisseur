package com.lzh.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lzh.dto.MessageDTO;
import com.lzh.mapper.MessageFailureMapper;
import com.lzh.po.MessageFailure;
import com.lzh.utils.MessageIdentity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MessageFailureService {
    private final MessageFailureMapper mapper;
    private final MessageOutboxService outbox;
    private final ObjectMapper json;

    @Transactional
    public void capture(String messageId, byte[] body, String queue, boolean invalid, String reason) {
        var failure = new MessageFailure();
        failure.setSourceKey(MessageIdentity.delivery(messageId, body));
        failure.setMessageId(messageId);
        failure.setSourceQueue(queue);
        failure.setBody(body);
        failure.setStatus(invalid ? "INVALID" : "READY");
        failure.setReason(shortReason(reason));
        mapper.insert(failure);
    }

    public MessageDTO decode(byte[] body) throws IOException {
        MessageDTO dto = json.readValue(body, MessageDTO.class);
        MessageOutboxService.validate(dto);
        return dto;
    }

    public List<Long> ready(int limit) { return mapper.findReady(limit); }
    public List<MessageFailure> list(long afterId, int limit) { return mapper.list(afterId, limit); }
    public MessageFailure find(long id) { return mapper.find(id); }

    /** Row lock, replay insertion and status update share the database transaction. */
    @Transactional
    public boolean replay(long id, MessageDTO replacement) {
        MessageFailure failure = mapper.lock(id);
        if (failure == null || "REPLAYED".equals(failure.getStatus())) return false;
        MessageDTO dto = replacement;
        if (dto == null) {
            if (!"READY".equals(failure.getStatus())) return false;
            try {
                dto = decode(failure.getBody());
            } catch (IOException | IllegalArgumentException e) {
                mapper.markInvalid(id, shortReason(e.toString()));
                return false;
            }
        }
        MessageOutboxService.validate(dto);
        outbox.replay(dto, "failure:" + id);
        if (mapper.markReplayed(id) != 1) throw new IllegalStateException("更新死信状态失败");
        return true;
    }

    private static String shortReason(String reason) {
        return reason == null ? "unknown" : reason.substring(0, Math.min(1000, reason.length()));
    }
}
