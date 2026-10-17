package com.lzh.service;

import com.lzh.dto.PaymentOrderQueryResult;
import com.lzh.enums.PaymentCloseResult;
import com.lzh.enums.PaymentRefundResult;

import com.lzh.common.Result;

import java.math.BigDecimal;
import java.util.Map;

public interface AlipayService {
    Result alipay(String orderNo);

    boolean notify(Map<String, String> params);

    PaymentCloseResult closeOrder(String orderNo);

    PaymentOrderQueryResult queryOrder(String orderNo);

    PaymentRefundResult refundOrder(String orderNo, BigDecimal amount, String refundRequestNo);

    PaymentRefundResult queryRefund(String orderNo, String refundRequestNo);
}
