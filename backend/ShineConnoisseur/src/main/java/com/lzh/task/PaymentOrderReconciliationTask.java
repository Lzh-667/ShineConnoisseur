package com.lzh.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lzh.mapper.PaymentOrderMapper;
import com.lzh.po.PaymentOrder;
import com.lzh.service.impl.PaymentOrderReconciliationService;
import com.lzh.service.impl.PaymentOrderStateService;
import com.lzh.utils.SystemConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/** 主动查单，弥补回调丢失、渠道调用结果未知以及退款处理中断。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentOrderReconciliationTask {

    private final PaymentOrderMapper paymentOrderMapper;
    private final PaymentOrderReconciliationService reconciliationService;
    private final PaymentOrderStateService stateService;

    @Scheduled(
            initialDelayString = "${payment.reconciliation-initial-delay-ms:45000}",
            fixedDelayString = "${payment.reconciliation-scan-delay-ms:60000}"
    )
    public void reconcileOrders() {
        LocalDateTime now = LocalDateTime.now();
        List<PaymentOrder> activeOrders = paymentOrderMapper.selectList(
                new LambdaQueryWrapper<PaymentOrder>()
                        .in(PaymentOrder::getStatus,
                                SystemConstants.ORDER_STATUS_INITIATING,
                                SystemConstants.ORDER_STATUS_WAIT_PAY,
                                SystemConstants.ORDER_STATUS_CLOSING,
                                SystemConstants.ORDER_STATUS_REFUNDING)
                        .lt(PaymentOrder::getUpdateTime, now.minusSeconds(30))
                        .orderByAsc(PaymentOrder::getUpdateTime)
                        .last("LIMIT 100")
        );
        activeOrders.forEach(order -> reconcileOne(order, now));

        // 兼容关单与支付回调极端乱序，以及升级前遗留的误关订单。
        List<PaymentOrder> recentlyClosedOrders = paymentOrderMapper.selectList(
                new LambdaQueryWrapper<PaymentOrder>()
                        .eq(PaymentOrder::getStatus, SystemConstants.ORDER_STATUS_CLOSE)
                        .gt(PaymentOrder::getUpdateTime, now.minusMinutes(10))
                        .lt(PaymentOrder::getUpdateTime, now.minusSeconds(30))
                        .orderByAsc(PaymentOrder::getUpdateTime)
                        .last("LIMIT 100")
        );
        recentlyClosedOrders.forEach(order -> reconcileOne(order, now));
    }

    private void reconcileOne(PaymentOrder order, LocalDateTime now) {
        try {
            PaymentOrderReconciliationService.Outcome outcome = reconciliationService.reconcile(order);
            if (outcome == PaymentOrderReconciliationService.Outcome.NOT_FOUND
                    && Objects.equals(order.getStatus(), SystemConstants.ORDER_STATUS_INITIATING)
                    && order.getExpireTime() != null
                    && order.getExpireTime().isAfter(now)
                    && order.getUpdateTime() != null
                    && order.getUpdateTime().isBefore(now.minusMinutes(1))) {
                // 微信预下单结果未知，但一分钟后渠道仍无此单，可以重新发起。
                stateService.resetInitiation(order.getId());
                return;
            }
            if (outcome == PaymentOrderReconciliationService.Outcome.UNPAID
                    || outcome == PaymentOrderReconciliationService.Outcome.NOT_FOUND
                    || outcome == PaymentOrderReconciliationService.Outcome.UNKNOWN) {
                stateService.touch(order.getId(), order.getStatus(), now);
            }
        } catch (RuntimeException e) {
            log.error("支付订单主动对账异常，orderNo={}", order.getOrderNo(), e);
        }
    }
}
