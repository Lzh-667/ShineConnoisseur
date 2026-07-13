package com.lzh.consumer;

import com.lzh.dto.MessageDTO;
import com.lzh.po.Message;
import com.lzh.service.IMessageService;
import com.lzh.utils.MQConstants;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class MessageConsumer {

    @Resource
    private IMessageService messageService;

    @RabbitListener(queues = MQConstants.MESSAGE_QUEUE)
    public void handleMessage(MessageDTO dto) {
        log.info("收到消息通知: {}", dto);
        Message message = new Message();
        BeanUtils.copyProperties(dto, message);
        message.setStatus(0);
        try {
            messageService.save(message);
        } catch (Exception e) {
            log.error("消息消费失败",e);
            throw e;
        }
    }
}
