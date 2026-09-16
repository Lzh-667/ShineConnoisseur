package com.lzh.service;

import com.lzh.common.Result;

public interface WechatPayService {
    Result wechat(String orderNo);
    String notify(String body,
                  String signature,
                  String timestamp,
                  String nonce,
                  String serialNumber);
}
