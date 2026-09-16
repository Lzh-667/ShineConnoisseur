package com.lzh.service;

import com.lzh.common.Result;

import java.util.Map;

public interface AlipayService {
    Result alipay(String orderNo);

    String notify(Map<String, String> params);
}
