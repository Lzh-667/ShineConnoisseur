package com.lzh.service;

import com.lzh.common.Result;

import java.util.Map;

public interface AlipayService {
    Result alipay(String orderNo);

    boolean notify(Map<String, String> params);

    boolean closeOrder(String orderNo);
}
