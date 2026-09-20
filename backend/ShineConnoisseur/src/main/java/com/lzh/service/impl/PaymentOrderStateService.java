package com.lzh.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lzh.mapper.PaymentOrderMapper;
import com.lzh.po.PaymentOrder;
import com.lzh.utils.SystemConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.time.LocalDateTime;

/** 支付订单状态的原子迁移操作。 */
@Service
@RequiredArgsConstructor
public class PaymentOrderStateService {

    private final PaymentOrderMapper paymentOrderMapper;

    public boolean transition(Long orderId, Integer fromStatus, Integer toStatus) {
        return paymentOrderMapper.update(
                null,
                new LambdaUpdateWrapper<PaymentOrder>()
                        .eq(PaymentOrder::getId, orderId)
                        .eq(PaymentOrder::getStatus, fromStatus)
                        .set(PaymentOrder::getStatus, toStatus)
        ) == 1;
    }

    public boolean completeInitiation(Long orderId, String paymentPayload) {
        return paymentOrderMapper.update(
                null,
                new LambdaUpdateWrapper<PaymentOrder>()
                        .eq(PaymentOrder::getId, orderId)
                        .eq(PaymentOrder::getStatus, SystemConstants.ORDER_STATUS_INITIATING)
                        .set(PaymentOrder::getStatus, SystemConstants.ORDER_STATUS_WAIT_PAY)
                        .set(PaymentOrder::getPaymentPayload, paymentPayload)
        ) == 1;
    }

    public boolean resetInitiation(Long orderId) {
        return transition(
                orderId,
                SystemConstants.ORDER_STATUS_INITIATING,
                SystemConstants.ORDER_STATUS_PAYING
        );
    }

    public boolean transitionFromAny(Long orderId, List<Integer> sourceStatuses, Integer targetStatus) {
        return paymentOrderMapper.update(
                null,
                new LambdaUpdateWrapper<PaymentOrder>()
                        .eq(PaymentOrder::getId, orderId)
                        .in(PaymentOrder::getStatus, sourceStatuses)
                        .set(PaymentOrder::getStatus, targetStatus)
        ) == 1;
    }

    public boolean markRefunding(Long orderId, String transactionId) {
        return paymentOrderMapper.update(
                null,
                new LambdaUpdateWrapper<PaymentOrder>()
                        .eq(PaymentOrder::getId, orderId)
                        .in(PaymentOrder::getStatus,
                                SystemConstants.ORDER_STATUS_PAYING,
                                SystemConstants.ORDER_STATUS_INITIATING,
                                SystemConstants.ORDER_STATUS_WAIT_PAY,
                                SystemConstants.ORDER_STATUS_CLOSING,
                                SystemConstants.ORDER_STATUS_CLOSE)
                        .set(PaymentOrder::getStatus, SystemConstants.ORDER_STATUS_REFUNDING)
                        .set(PaymentOrder::getTransactionId, transactionId)
        ) == 1;
    }

    /** 记录本轮已处理时间，让批量任务在订单很多时能够公平轮询而不饿死后续订单。 */
    public void touch(Long orderId, Integer expectedStatus, LocalDateTime checkedAt) {
        paymentOrderMapper.update(
                null,
                new LambdaUpdateWrapper<PaymentOrder>()
                        .eq(PaymentOrder::getId, orderId)
                        .eq(PaymentOrder::getStatus, expectedStatus)
                        .set(PaymentOrder::getUpdateTime, checkedAt)
        );
    }
}
