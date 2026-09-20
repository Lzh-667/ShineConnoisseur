package com.lzh.service.impl;

import com.lzh.po.PaymentOrder;
import com.lzh.service.AlipayService;
import com.lzh.service.PaymentCloseResult;
import com.lzh.service.WechatPayService;
import com.lzh.utils.SystemConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentOrderCloseServiceTest {

    @Mock
    private PaymentOrderStateService stateService;
    @Mock
    private AlipayService alipayService;
    @Mock
    private WechatPayService wechatPayService;
    @Mock
    private PaymentOrderReconciliationService reconciliationService;
    @InjectMocks
    private PaymentOrderCloseService closeService;

    private PaymentOrder order;

    @BeforeEach
    void setUp() {
        order = new PaymentOrder();
        order.setId(1L);
        order.setOrderNo("order-1");
        order.setPaymentMethod(SystemConstants.VIP_PAY_METHOD_WECHAT);
        order.setExpireTime(LocalDateTime.now().plusMinutes(10));
    }

    @Test
    void uninitiatedOrder_closesLocallyWithoutCallingChannel() {
        order.setStatus(SystemConstants.ORDER_STATUS_PAYING);
        when(stateService.transition(
                1L,
                SystemConstants.ORDER_STATUS_PAYING,
                SystemConstants.ORDER_STATUS_CLOSE
        )).thenReturn(true);

        assertEquals(
                PaymentOrderCloseService.CloseOutcome.CLOSED,
                closeService.requestClose(order, false)
        );
        verify(wechatPayService, never()).closeOrder("order-1");
    }

    @Test
    void waitPayOrder_claimsClosingBeforeCallingChannel() {
        order.setStatus(SystemConstants.ORDER_STATUS_WAIT_PAY);
        when(reconciliationService.reconcile(order))
                .thenReturn(PaymentOrderReconciliationService.Outcome.UNPAID);
        when(stateService.transition(
                1L,
                SystemConstants.ORDER_STATUS_WAIT_PAY,
                SystemConstants.ORDER_STATUS_CLOSING
        )).thenReturn(true);
        when(wechatPayService.closeOrder("order-1")).thenReturn(PaymentCloseResult.CLOSED);
        when(stateService.transition(
                1L,
                SystemConstants.ORDER_STATUS_CLOSING,
                SystemConstants.ORDER_STATUS_CLOSE
        )).thenReturn(true);

        assertEquals(
                PaymentOrderCloseService.CloseOutcome.CLOSED,
                closeService.requestClose(order, false)
        );
    }

    @Test
    void repeatedUserCancel_doesNotCallChannelAgain() {
        order.setStatus(SystemConstants.ORDER_STATUS_CLOSING);
        when(reconciliationService.reconcile(order))
                .thenReturn(PaymentOrderReconciliationService.Outcome.UNPAID);

        assertEquals(
                PaymentOrderCloseService.CloseOutcome.IN_PROGRESS,
                closeService.requestClose(order, false)
        );
        verify(wechatPayService, never()).closeOrder("order-1");
    }

    @Test
    void notFoundBeforeExpiration_remainsClosing() {
        order.setStatus(SystemConstants.ORDER_STATUS_WAIT_PAY);
        when(reconciliationService.reconcile(order))
                .thenReturn(PaymentOrderReconciliationService.Outcome.NOT_FOUND);
        when(stateService.transition(
                1L,
                SystemConstants.ORDER_STATUS_WAIT_PAY,
                SystemConstants.ORDER_STATUS_CLOSING
        )).thenReturn(true);
        when(wechatPayService.closeOrder("order-1")).thenReturn(PaymentCloseResult.NOT_FOUND);

        assertEquals(
                PaymentOrderCloseService.CloseOutcome.IN_PROGRESS,
                closeService.requestClose(order, false)
        );
        verify(stateService, never()).transition(
                1L,
                SystemConstants.ORDER_STATUS_CLOSING,
                SystemConstants.ORDER_STATUS_CLOSE
        );
    }

    @Test
    void notFoundAfterSafetyWindow_closesLocally() {
        order.setStatus(SystemConstants.ORDER_STATUS_CLOSING);
        order.setExpireTime(LocalDateTime.now().minusMinutes(3));
        when(reconciliationService.reconcile(order))
                .thenReturn(PaymentOrderReconciliationService.Outcome.NOT_FOUND);
        when(wechatPayService.closeOrder("order-1")).thenReturn(PaymentCloseResult.NOT_FOUND);
        when(stateService.transition(
                1L,
                SystemConstants.ORDER_STATUS_CLOSING,
                SystemConstants.ORDER_STATUS_CLOSE
        )).thenReturn(true);

        assertEquals(
                PaymentOrderCloseService.CloseOutcome.CLOSED,
                closeService.requestClose(order, true)
        );
    }

    @Test
    void paidDuringClosing_isFulfilledInsteadOfClosed() {
        order.setStatus(SystemConstants.ORDER_STATUS_CLOSING);
        when(reconciliationService.reconcile(order))
                .thenReturn(PaymentOrderReconciliationService.Outcome.FULFILLED);

        assertEquals(
                PaymentOrderCloseService.CloseOutcome.STATE_CHANGED,
                closeService.requestClose(order, true)
        );
        verify(wechatPayService, never()).closeOrder("order-1");
    }
}
