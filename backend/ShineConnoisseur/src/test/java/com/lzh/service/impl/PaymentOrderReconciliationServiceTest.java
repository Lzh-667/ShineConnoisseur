package com.lzh.service.impl;

import com.lzh.common.BusinessException;
import com.lzh.mapper.PaymentOrderMapper;
import com.lzh.po.PaymentOrder;
import com.lzh.service.AlipayService;
import com.lzh.service.PaymentOrderQueryResult;
import com.lzh.service.PaymentRefundResult;
import com.lzh.service.WechatPayService;
import com.lzh.utils.SystemConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentOrderReconciliationServiceTest {

    @Mock
    private PaymentOrderMapper paymentOrderMapper;
    @Mock
    private PaymentFulfillmentService fulfillmentService;
    @Mock
    private PaymentOrderStateService stateService;
    @Mock
    private AlipayService alipayService;
    @Mock
    private WechatPayService wechatPayService;
    @InjectMocks
    private PaymentOrderReconciliationService reconciliationService;

    private PaymentOrder order;

    @BeforeEach
    void setUp() {
        order = new PaymentOrder();
        order.setId(1L);
        order.setOrderNo("order-1");
        order.setPaymentMethod(SystemConstants.VIP_PAY_METHOD_WECHAT);
        order.setStatus(SystemConstants.ORDER_STATUS_WAIT_PAY);
        order.setAmount(new BigDecimal("19.90"));
    }

    @Test
    void paidOrder_isFulfilledWhenCallbackWasLost() {
        when(wechatPayService.queryOrder("order-1"))
                .thenReturn(PaymentOrderQueryResult.paid(
                        "wx-transaction-1", new BigDecimal("19.90")));

        assertEquals(
                PaymentOrderReconciliationService.Outcome.FULFILLED,
                reconciliationService.reconcile(order)
        );
        verify(fulfillmentService).fulfill(
                "order-1",
                SystemConstants.VIP_PAY_METHOD_WECHAT,
                "wx-transaction-1",
                new BigDecimal("19.90")
        );
    }

    @Test
    void irrecoverableFulfillmentFailure_isRefundedIdempotently() {
        when(wechatPayService.queryOrder("order-1"))
                .thenReturn(PaymentOrderQueryResult.paid(
                        "wx-transaction-1", new BigDecimal("19.90")));
        doThrow(new BusinessException("订单快照异常"))
                .when(fulfillmentService)
                .fulfill("order-1", SystemConstants.VIP_PAY_METHOD_WECHAT,
                        "wx-transaction-1", new BigDecimal("19.90"));
        when(stateService.markRefunding(1L, "wx-transaction-1")).thenReturn(true);
        when(wechatPayService.refundOrder(
                "order-1", new BigDecimal("19.90"), "RForder-1"))
                .thenReturn(PaymentRefundResult.SUCCESS);

        assertEquals(
                PaymentOrderReconciliationService.Outcome.REFUNDED,
                reconciliationService.reconcile(order)
        );
        verify(stateService).transition(
                1L,
                SystemConstants.ORDER_STATUS_REFUNDING,
                SystemConstants.ORDER_STATUS_REFUND
        );
    }

    @Test
    void refundingOrder_retriesSameRefundRequest() {
        order.setStatus(SystemConstants.ORDER_STATUS_REFUNDING);
        when(wechatPayService.queryRefund("RForder-1"))
                .thenReturn(PaymentRefundResult.FAILED);
        when(wechatPayService.queryOrder("order-1"))
                .thenReturn(PaymentOrderQueryResult.paid(
                        "wx-transaction-1", new BigDecimal("19.90")));
        when(wechatPayService.refundOrder(
                "order-1", new BigDecimal("19.90"), "RForder-1"))
                .thenReturn(PaymentRefundResult.PROCESSING);

        assertEquals(
                PaymentOrderReconciliationService.Outcome.UNKNOWN,
                reconciliationService.reconcile(order)
        );
    }

    @Test
    void refundIsFinalOnlyAfterRefundOrderReportsSuccess() {
        order.setStatus(SystemConstants.ORDER_STATUS_REFUNDING);
        when(wechatPayService.queryRefund("RForder-1"))
                .thenReturn(PaymentRefundResult.SUCCESS);

        assertEquals(
                PaymentOrderReconciliationService.Outcome.REFUNDED,
                reconciliationService.reconcile(order)
        );
        verify(stateService).transition(
                1L,
                SystemConstants.ORDER_STATUS_REFUNDING,
                SystemConstants.ORDER_STATUS_REFUND
        );
    }
}
