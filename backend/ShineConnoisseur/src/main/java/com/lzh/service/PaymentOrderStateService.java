package com.lzh.service;

import java.util.List;
import java.time.LocalDateTime;

/** 支付订单状态原子迁移契约。 */
public interface PaymentOrderStateService {
    boolean transition(Long orderId, Integer fromStatus, Integer toStatus);

    boolean completeInitiation(Long orderId, String paymentPayload);

    boolean resetInitiation(Long orderId);

    boolean transitionFromAny(Long orderId, List<Integer> sourceStatuses, Integer targetStatus);

    boolean markRefunding(Long orderId, String transactionId);

    void touch(Long orderId, Integer expectedStatus, LocalDateTime checkedAt);
}
