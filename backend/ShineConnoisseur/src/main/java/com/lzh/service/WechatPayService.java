package com.lzh.service;

import com.lzh.common.Result;

import java.math.BigDecimal;

public interface WechatPayService {
    Result wechat(String orderNo);
    boolean notify(String body,
                   String signature,
                   String timestamp,
                   String nonce,
                   String serialNumber);

    PaymentCloseResult closeOrder(String orderNo);

    PaymentOrderQueryResult queryOrder(String orderNo);

    PaymentRefundResult refundOrder(String orderNo, BigDecimal amount, String refundRequestNo);

    PaymentRefundResult queryRefund(String refundRequestNo);
}
