package com.lzh.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lzh.common.Result;
import com.lzh.po.Message;

public interface IMessageService extends IService<Message> {
    Result listAll(Long current, Integer type);

    Result unreadCount();

    Result read(Long id);

    Result readAll();
}
