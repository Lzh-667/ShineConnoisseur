package com.lzh.service.impl;

import com.lzh.po.PaymentOrder;
import com.lzh.service.AlipayService;
import com.lzh.service.PaymentCloseResult;
import com.lzh.service.WechatPayService;
import com.lzh.utils.SystemConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Objects;

/** 串行化本地状态迁移与支付渠道关单。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentOrderCloseService {

    public enum CloseOutcome {
        CLOSED,
        IN_PROGRESS,
        STATE_CHANGED
    }

    private final PaymentOrderStateService stateService;
    private final PaymentOrderReconciliationService reconciliationService;
    private final AlipayService alipayService;
    private final WechatPayService wechatPayService;

    public CloseOutcome requestClose(PaymentOrder order, boolean expired) {
        Integer status = order.getStatus();
        boolean claimedClose = false;
        if (Objects.equals(status, SystemConstants.ORDER_STATUS_CLOSE)) {
            return CloseOutcome.CLOSED;
        }
        if (Objects.equals(status, SystemConstants.ORDER_STATUS_SUCCESS)
                || Objects.equals(status, SystemConstants.ORDER_STATUS_REFUND)
                || Objects.equals(status, SystemConstants.ORDER_STATUS_REFUNDING)) {
            return CloseOutcome.STATE_CHANGED;
        }

        // 尚未发起渠道请求的订单可以直接关闭，不存在“先关单后创建渠道订单”的窗口。
        if (Objects.equals(status, SystemConstants.ORDER_STATUS_PAYING)) {
            return stateService.transition(
                    order.getId(),
                    SystemConstants.ORDER_STATUS_PAYING,
                    SystemConstants.ORDER_STATUS_CLOSE
            ) ? CloseOutcome.CLOSED : CloseOutcome.STATE_CHANGED;
        }

        PaymentOrderReconciliationService.Outcome reconciliation =
                reconciliationService.reconcile(order);
        if (reconciliation == PaymentOrderReconciliationService.Outcome.FULFILLED
                || reconciliation == PaymentOrderReconciliationService.Outcome.REFUNDED) {
            return CloseOutcome.STATE_CHANGED;
        }
        if (reconciliation == PaymentOrderReconciliationService.Outcome.REMOTE_CLOSED) {
            return CloseOutcome.CLOSED;
        }
        if (reconciliation == PaymentOrderReconciliationService.Outcome.UNKNOWN) {
            return CloseOutcome.IN_PROGRESS;
        }

        // 用户取消不能打断正在执行的渠道下单；过期任务可以接管长期未完成的发起状态。
        if (Objects.equals(status, SystemConstants.ORDER_STATUS_INITIATING)) {
            if (!expired) {
                return CloseOutcome.IN_PROGRESS;
            }
            if (!stateService.transition(
                    order.getId(),
                    SystemConstants.ORDER_STATUS_INITIATING,
                    SystemConstants.ORDER_STATUS_CLOSING
            )) {
                return CloseOutcome.STATE_CHANGED;
            }
            status = SystemConstants.ORDER_STATUS_CLOSING;
            claimedClose = true;
        }

        if (Objects.equals(status, SystemConstants.ORDER_STATUS_WAIT_PAY)) {
            if (!stateService.transition(
                    order.getId(),
                    SystemConstants.ORDER_STATUS_WAIT_PAY,
                    SystemConstants.ORDER_STATUS_CLOSING
            )) {
                return CloseOutcome.STATE_CHANGED;
            }
            status = SystemConstants.ORDER_STATUS_CLOSING;
            claimedClose = true;
        }

        if (!Objects.equals(status, SystemConstants.ORDER_STATUS_CLOSING)) {
            return CloseOutcome.STATE_CHANGED;
        }
        if (!claimedClose && !expired) {
            return CloseOutcome.IN_PROGRESS;
        }

        PaymentCloseResult channelResult = closeRemoteOrder(order);
        if (channelResult == PaymentCloseResult.CLOSED
                || (channelResult == PaymentCloseResult.NOT_FOUND && safeToFinalizeNotFound(order, expired))) {
            return stateService.transition(
                    order.getId(),
                    SystemConstants.ORDER_STATUS_CLOSING,
                    SystemConstants.ORDER_STATUS_CLOSE
            ) ? CloseOutcome.CLOSED : CloseOutcome.STATE_CHANGED;
        }

        if (channelResult == PaymentCloseResult.FAILED) {
            PaymentOrder latest = new PaymentOrder();
            latest.setId(order.getId());
            latest.setOrderNo(order.getOrderNo());
            latest.setPaymentMethod(order.getPaymentMethod());
            latest.setAmount(order.getAmount());
            latest.setStatus(SystemConstants.ORDER_STATUS_CLOSING);
            PaymentOrderReconciliationService.Outcome afterClose = reconciliationService.reconcile(latest);
            if (afterClose == PaymentOrderReconciliationService.Outcome.FULFILLED
                    || afterClose == PaymentOrderReconciliationService.Outcome.REFUNDED) {
                return CloseOutcome.STATE_CHANGED;
            }
            if (afterClose == PaymentOrderReconciliationService.Outcome.REMOTE_CLOSED) {
                return CloseOutcome.CLOSED;
            }
        }

        // 未过期且渠道侧尚无订单时，可能仍有已签发的支付宝页面，保持关单中直至过期。
        return CloseOutcome.IN_PROGRESS;
    }

    private boolean safeToFinalizeNotFound(PaymentOrder order, boolean expired) {
        if (!expired || order.getExpireTime() == null) {
            return false;
        }
        // 支付宝页面可能已签发但尚未在渠道落单；绝对失效时间后再留两分钟处理时钟偏差和在途请求。
        return LocalDateTime.now().isAfter(order.getExpireTime().plusMinutes(2));
    }

    private PaymentCloseResult closeRemoteOrder(PaymentOrder order) {
        if (Objects.equals(order.getPaymentMethod(), SystemConstants.VIP_PAY_METHOD_ALIBABA)) {
            return alipayService.closeOrder(order.getOrderNo());
        }
        if (Objects.equals(order.getPaymentMethod(), SystemConstants.VIP_PAY_METHOD_WECHAT)) {
            return wechatPayService.closeOrder(order.getOrderNo());
        }
        log.error(
                "订单支付方式异常，无法关单，orderNo={}, paymentMethod={}",
                order.getOrderNo(),
                order.getPaymentMethod()
        );
        return PaymentCloseResult.FAILED;
    }
}
