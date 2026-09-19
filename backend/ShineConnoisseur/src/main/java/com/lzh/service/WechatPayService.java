package com.lzh.service;

import com.lzh.common.Result;

public interface WechatPayService {
    Result wechat(String orderNo);
    boolean notify(String body,
                   String signature,
                   String timestamp,
                   String nonce,
                   String serialNumber);

    boolean closeOrder(String orderNo);
}
