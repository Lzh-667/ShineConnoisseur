package com.lzh.service;

import com.lzh.po.PaymentOrder;

/** 支付订单关单契约。 */
public interface PaymentOrderCloseService {
    public enum CloseOutcome {
        CLOSED,
        IN_PROGRESS,
        STATE_CHANGED
    }

    CloseOutcome requestClose(PaymentOrder order, boolean expired);
}
