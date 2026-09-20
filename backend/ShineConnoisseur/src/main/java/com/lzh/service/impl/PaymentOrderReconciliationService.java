package com.lzh.service.impl;

import com.lzh.common.BusinessException;
import com.lzh.mapper.PaymentOrderMapper;
import com.lzh.po.PaymentOrder;
import com.lzh.service.AlipayService;
import com.lzh.service.PaymentOrderQueryResult;
import com.lzh.service.PaymentRefundResult;
import com.lzh.service.WechatPayService;
import com.lzh.utils.SystemConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * 以渠道查单结果修复本地状态。支付成功优先补发权益；确定无法履约时进入幂等退款。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentOrderReconciliationService {

    public enum Outcome {
        FULFILLED,
        REFUNDED,
        REMOTE_CLOSED,
        UNPAID,
        NOT_FOUND,
        UNKNOWN
    }

    private static final List<Integer> PENDING_STATUSES = List.of(
            SystemConstants.ORDER_STATUS_PAYING,
            SystemConstants.ORDER_STATUS_INITIATING,
            SystemConstants.ORDER_STATUS_WAIT_PAY,
            SystemConstants.ORDER_STATUS_CLOSING,
            SystemConstants.ORDER_STATUS_CLOSE
    );

    private final PaymentOrderMapper paymentOrderMapper;
    private final PaymentFulfillmentService fulfillmentService;
    private final PaymentOrderStateService stateService;
    private final AlipayService alipayService;
    private final WechatPayService wechatPayService;

    public Outcome reconcile(PaymentOrder order) {
        if (Objects.equals(order.getStatus(), SystemConstants.ORDER_STATUS_REFUNDING)) {
            String refundRequestNo = refundRequestNo(order);
            PaymentRefundResult refundStatus = queryRemoteRefund(order, refundRequestNo);
            if (refundStatus == PaymentRefundResult.SUCCESS) {
                stateService.transition(order.getId(), SystemConstants.ORDER_STATUS_REFUNDING,
                        SystemConstants.ORDER_STATUS_REFUND);
                return Outcome.REFUNDED;
            }
            if (refundStatus == PaymentRefundResult.PROCESSING) {
                return Outcome.UNKNOWN;
            }
            PaymentOrderQueryResult refundQuery = queryRemote(order);
            // 始终使用渠道实收金额退款，避免本地金额异常时形成部分退款。
            if (refundQuery.state() == PaymentOrderQueryResult.State.PAID) {
                return retryRefund(order, refundQuery.paidAmount());
            }
            return Outcome.UNKNOWN;
        }
        PaymentOrderQueryResult queryResult = queryRemote(order);
        return switch (queryResult.state()) {
            case PAID -> handlePaid(order, queryResult);
            case REFUNDED -> {
                // 主订单“转入退款”不代表退款资金已到账，只有退款单查询成功才能落最终状态。
                yield Outcome.UNKNOWN;
            }
            case CLOSED -> {
                stateService.transitionFromAny(order.getId(), PENDING_STATUSES,
                        SystemConstants.ORDER_STATUS_CLOSE);
                yield Outcome.REMOTE_CLOSED;
            }
            case UNPAID -> Outcome.UNPAID;
            case NOT_FOUND -> Outcome.NOT_FOUND;
            case UNKNOWN -> Outcome.UNKNOWN;
        };
    }

    private Outcome handlePaid(PaymentOrder order, PaymentOrderQueryResult queryResult) {
        try {
            fulfillmentService.fulfill(
                    order.getOrderNo(),
                    order.getPaymentMethod(),
                    queryResult.transactionId(),
                    queryResult.paidAmount()
            );
            return Outcome.FULFILLED;
        } catch (BusinessException e) {
            log.error("支付已成功但订单无法履约，转入退款补偿，orderNo={}, reason={}",
                    order.getOrderNo(), e.getMessage());
            if (!Objects.equals(order.getStatus(), SystemConstants.ORDER_STATUS_REFUNDING)
                    && !stateService.markRefunding(order.getId(), queryResult.transactionId())) {
                PaymentOrder latest = paymentOrderMapper.selectById(order.getId());
                if (latest == null
                        || !Objects.equals(latest.getStatus(), SystemConstants.ORDER_STATUS_REFUNDING)) {
                    return Outcome.UNKNOWN;
                }
                order = latest;
            } else {
                order.setStatus(SystemConstants.ORDER_STATUS_REFUNDING);
                order.setTransactionId(queryResult.transactionId());
            }
            return retryRefund(order, queryResult.paidAmount());
        }
    }

    private Outcome retryRefund(PaymentOrder order, java.math.BigDecimal refundAmount) {
        PaymentRefundResult result = refundRemote(order, refundAmount);
        if (result == PaymentRefundResult.SUCCESS) {
            stateService.transition(order.getId(), SystemConstants.ORDER_STATUS_REFUNDING,
                    SystemConstants.ORDER_STATUS_REFUND);
            log.warn("异常支付已自动退款，orderNo={}", order.getOrderNo());
            return Outcome.REFUNDED;
        }
        return Outcome.UNKNOWN;
    }

    private PaymentOrderQueryResult queryRemote(PaymentOrder order) {
        if (Objects.equals(order.getPaymentMethod(), SystemConstants.VIP_PAY_METHOD_ALIBABA)) {
            return alipayService.queryOrder(order.getOrderNo());
        }
        if (Objects.equals(order.getPaymentMethod(), SystemConstants.VIP_PAY_METHOD_WECHAT)) {
            return wechatPayService.queryOrder(order.getOrderNo());
        }
        log.error("订单支付方式异常，无法查单，orderNo={}, paymentMethod={}",
                order.getOrderNo(), order.getPaymentMethod());
        return PaymentOrderQueryResult.of(PaymentOrderQueryResult.State.UNKNOWN);
    }

    private PaymentRefundResult refundRemote(PaymentOrder order, java.math.BigDecimal refundAmount) {
        String refundRequestNo = refundRequestNo(order);
        if (Objects.equals(order.getPaymentMethod(), SystemConstants.VIP_PAY_METHOD_ALIBABA)) {
            return alipayService.refundOrder(order.getOrderNo(), refundAmount, refundRequestNo);
        }
        if (Objects.equals(order.getPaymentMethod(), SystemConstants.VIP_PAY_METHOD_WECHAT)) {
            return wechatPayService.refundOrder(order.getOrderNo(), refundAmount, refundRequestNo);
        }
        return PaymentRefundResult.FAILED;
    }

    private PaymentRefundResult queryRemoteRefund(PaymentOrder order, String refundRequestNo) {
        if (Objects.equals(order.getPaymentMethod(), SystemConstants.VIP_PAY_METHOD_ALIBABA)) {
            return alipayService.queryRefund(order.getOrderNo(), refundRequestNo);
        }
        if (Objects.equals(order.getPaymentMethod(), SystemConstants.VIP_PAY_METHOD_WECHAT)) {
            return wechatPayService.queryRefund(refundRequestNo);
        }
        return PaymentRefundResult.FAILED;
    }

    private String refundRequestNo(PaymentOrder order) {
        return "RF" + order.getOrderNo();
    }
}
