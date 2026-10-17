package com.lzh.service;

import java.math.BigDecimal;

/** 支付成功后的原子履约与权益发放契约。 */
public interface PaymentFulfillmentService {
    void fulfill(String orderNo,
                        Integer expectedPaymentMethod,
                        String transactionId,
                        BigDecimal paidAmount);
}
