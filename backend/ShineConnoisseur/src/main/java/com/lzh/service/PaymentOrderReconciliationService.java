package com.lzh.service;

import com.lzh.po.PaymentOrder;

/** 支付订单对账补偿契约。 */
public interface PaymentOrderReconciliationService {
    public enum Outcome {
        FULFILLED,
        REFUNDED,
        REMOTE_CLOSED,
        UNPAID,
        NOT_FOUND,
        UNKNOWN
    }

    Outcome reconcile(PaymentOrder order);
}
