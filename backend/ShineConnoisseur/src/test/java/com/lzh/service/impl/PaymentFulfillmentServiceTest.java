package com.lzh.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.lzh.common.BusinessException;
import com.lzh.mapper.PaymentOrderMapper;
import com.lzh.mapper.UserVipMapper;
import com.lzh.po.PaymentOrder;
import com.lzh.utils.SystemConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentFulfillmentServiceTest {

    @Mock
    private PaymentOrderMapper paymentOrderMapper;
    @Mock
    private UserVipMapper userVipMapper;
    @InjectMocks
    private PaymentFulfillmentService paymentFulfillmentService;

    private PaymentOrder order;

    @BeforeAll
    static void initializeMybatisPlusMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "payment-fulfillment-test"),
                PaymentOrder.class
        );
    }

    @BeforeEach
    void setUp() {
        order = new PaymentOrder();
        order.setId(1L);
        order.setOrderNo("order-1");
        order.setUserId(10L);
        order.setPaymentMethod(SystemConstants.VIP_PAY_METHOD_WECHAT);
        order.setStatus(SystemConstants.ORDER_STATUS_PAYING);
        order.setAmount(new BigDecimal("19.90"));
        order.setDurationDays(30);
    }

    @Test
    void fulfill_firstSuccess_updatesOrderAndGrantsVip() {
        when(paymentOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(paymentOrderMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(userVipMapper.grantOrExtend(any(), any(), any())).thenReturn(1);

        paymentFulfillmentService.fulfill(
                "order-1",
                SystemConstants.VIP_PAY_METHOD_WECHAT,
                "wx-transaction-1",
                new BigDecimal("19.90")
        );

        verify(paymentOrderMapper).update(isNull(), any(LambdaUpdateWrapper.class));
        verify(userVipMapper).grantOrExtend(any(), any(), any());
    }

    @Test
    void fulfill_duplicateNotification_doesNotGrantVipAgain() {
        order.setStatus(SystemConstants.ORDER_STATUS_SUCCESS);
        order.setTransactionId("wx-transaction-1");
        when(paymentOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);

        paymentFulfillmentService.fulfill(
                "order-1",
                SystemConstants.VIP_PAY_METHOD_WECHAT,
                "wx-transaction-1",
                new BigDecimal("19.90")
        );

        verify(paymentOrderMapper, never()).update(isNull(), any(LambdaUpdateWrapper.class));
        verify(userVipMapper, never()).grantOrExtend(any(), any(), any());
    }

    @Test
    void fulfill_concurrentCallback_loserDoesNotGrantVip() {
        when(paymentOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(paymentOrderMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(0);

        assertThrows(
                BusinessException.class,
                () -> paymentFulfillmentService.fulfill(
                        "order-1",
                        SystemConstants.VIP_PAY_METHOD_WECHAT,
                        "wx-transaction-1",
                        new BigDecimal("19.90")
                )
        );

        verify(userVipMapper, never()).grantOrExtend(any(), any(), any());
    }

    @Test
    void fulfill_wrongChannel_rejectedBeforeUpdate() {
        when(paymentOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);

        assertThrows(
                BusinessException.class,
                () -> paymentFulfillmentService.fulfill(
                        "order-1",
                        SystemConstants.VIP_PAY_METHOD_ALIBABA,
                        "ali-transaction-1",
                        new BigDecimal("19.90")
                )
        );

        verify(paymentOrderMapper, never()).update(isNull(), any(LambdaUpdateWrapper.class));
        verify(userVipMapper, never()).grantOrExtend(any(), any(), any());
    }

    @Test
    void fulfill_verifiedLatePayment_reopensClosedOrderAndGrantsVip() {
        order.setStatus(SystemConstants.ORDER_STATUS_CLOSE);
        when(paymentOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(paymentOrderMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(userVipMapper.grantOrExtend(any(), any(), any())).thenReturn(1);

        paymentFulfillmentService.fulfill(
                "order-1",
                SystemConstants.VIP_PAY_METHOD_WECHAT,
                "wx-transaction-1",
                new BigDecimal("19.90")
        );

        verify(paymentOrderMapper).update(isNull(), any(LambdaUpdateWrapper.class));
        verify(userVipMapper).grantOrExtend(any(), any(), any());
    }
}
