package com.lzh.service;

import java.math.BigDecimal;

/** 支付渠道侧订单的权威状态快照。 */
public record PaymentOrderQueryResult(
        State state,
        String transactionId,
        BigDecimal paidAmount
) {
    public enum State {
        PAID,
        UNPAID,
        CLOSED,
        REFUNDED,
        NOT_FOUND,
        UNKNOWN
    }

    public static PaymentOrderQueryResult paid(String transactionId, BigDecimal paidAmount) {
        return new PaymentOrderQueryResult(State.PAID, transactionId, paidAmount);
    }

    public static PaymentOrderQueryResult of(State state) {
        return new PaymentOrderQueryResult(state, null, null);
    }
}
