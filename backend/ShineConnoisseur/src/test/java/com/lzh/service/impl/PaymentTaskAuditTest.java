package com.lzh.service.impl;

import com.lzh.mapper.PaymentOrderMapper;
import com.lzh.po.PaymentOrder;
import com.lzh.service.PaymentOrderReconciliationService;
import com.lzh.service.PaymentOrderStateService;
import com.lzh.task.PaymentOrderReconciliationTask;
import com.lzh.utils.SystemConstants;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PaymentTaskAuditTest {
    /** 已关闭订单的查单结果不能延长固定复查窗口。 */
    @Test void closedNotFoundOrderKeepsItsReconciliationWindow() {
        var mapper = mock(PaymentOrderMapper.class);
        var reconciliation = mock(PaymentOrderReconciliationService.class);
        var states = mock(PaymentOrderStateService.class);
        var order = new PaymentOrder();
        order.setId(1L); order.setOrderNo("never-submitted");
        order.setStatus(SystemConstants.ORDER_STATUS_CLOSE);
        order.setUpdateTime(LocalDateTime.now().minusMinutes(9));
        when(mapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(List.of(), List.of(order));
        when(reconciliation.reconcile(order)).thenReturn(PaymentOrderReconciliationService.Outcome.NOT_FOUND);
        new PaymentOrderReconciliationTask(mapper, reconciliation, states).reconcileOrders();
        verify(states, never()).touch(any(), any(), any());
    }

    @Test void closedUnknownAndUnpaidResultsDoNotExtendWindow() {
        for (var outcome : List.of(PaymentOrderReconciliationService.Outcome.UNKNOWN,
                PaymentOrderReconciliationService.Outcome.UNPAID)) {
            var mapper = mock(PaymentOrderMapper.class);
            var reconciliation = mock(PaymentOrderReconciliationService.class);
            var states = mock(PaymentOrderStateService.class);
            var order = new PaymentOrder();
            order.setId(1L); order.setStatus(SystemConstants.ORDER_STATUS_CLOSE);
            when(mapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                    .thenReturn(List.of(), List.of(order));
            when(reconciliation.reconcile(order)).thenReturn(outcome);
            new PaymentOrderReconciliationTask(mapper, reconciliation, states).reconcileOrders();
            verifyNoInteractions(states);
        }
    }

    @Test void activeUnknownOrderStillMovesToBackOfQueue() {
        var mapper = mock(PaymentOrderMapper.class);
        var reconciliation = mock(PaymentOrderReconciliationService.class);
        var states = mock(PaymentOrderStateService.class);
        var order = new PaymentOrder();
        order.setId(1L); order.setStatus(SystemConstants.ORDER_STATUS_WAIT_PAY);
        when(mapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(List.of(order), List.of());
        when(reconciliation.reconcile(order)).thenReturn(PaymentOrderReconciliationService.Outcome.UNKNOWN);
        new PaymentOrderReconciliationTask(mapper, reconciliation, states).reconcileOrders();
        verify(states).touch(eq(1L), eq(SystemConstants.ORDER_STATUS_WAIT_PAY), any());
    }

    @Test void closedOrderScanContinuesBeyondOneHundredRows() {
        var mapper = mock(PaymentOrderMapper.class);
        var reconciliation = mock(PaymentOrderReconciliationService.class);
        var states = mock(PaymentOrderStateService.class);
        var firstBatch = java.util.stream.LongStream.rangeClosed(1, 100).mapToObj(id -> {
            var order = new PaymentOrder(); order.setId(id); order.setStatus(SystemConstants.ORDER_STATUS_CLOSE); return order;
        }).toList();
        var last = new PaymentOrder(); last.setId(101L); last.setStatus(SystemConstants.ORDER_STATUS_CLOSE);
        when(mapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(List.of(), firstBatch, List.of(last));
        when(reconciliation.reconcile(any())).thenReturn(PaymentOrderReconciliationService.Outcome.NOT_FOUND);
        new PaymentOrderReconciliationTask(mapper, reconciliation, states).reconcileOrders();
        verify(reconciliation, times(101)).reconcile(any());
        verify(reconciliation).reconcile(last);
        verify(mapper, times(3)).selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
        verifyNoInteractions(states);
    }
}
