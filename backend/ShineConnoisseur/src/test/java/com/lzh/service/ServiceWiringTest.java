package com.lzh.service;

import com.lzh.common.BusinessException;
import com.lzh.mapper.*;
import com.lzh.service.impl.*;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.SimpleTransactionStatus;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ServiceWiringTest {
    @Configuration
    @EnableTransactionManagement
    static class TransactionConfig {}

    @Test
    void interfacesResolveAndFulfillmentStillRunsThroughTransactionProxy() {
        var transactionManager = mock(PlatformTransactionManager.class);
        var transaction = new SimpleTransactionStatus();
        when(transactionManager.getTransaction(any())).thenReturn(transaction);
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(TransactionConfig.class, VipIdentityServiceImpl.class,
                    VipRecommendationServiceImpl.class, PaymentFulfillmentServiceImpl.class,
                    PaymentOrderStateServiceImpl.class, PaymentOrderCloseServiceImpl.class,
                    PaymentOrderReconciliationServiceImpl.class);
            context.registerBean(PlatformTransactionManager.class, () -> transactionManager);
            context.registerBean(UserVipMapper.class, () -> mock(UserVipMapper.class));
            context.registerBean(MovieMapper.class, () -> mock(MovieMapper.class));
            context.registerBean(MovieFavoriteMapper.class, () -> mock(MovieFavoriteMapper.class));
            context.registerBean(ReviewMapper.class, () -> mock(ReviewMapper.class));
            context.registerBean(PaymentOrderMapper.class, () -> mock(PaymentOrderMapper.class));
            context.registerBean(AlipayService.class, () -> mock(AlipayService.class));
            context.registerBean(WechatPayService.class, () -> mock(WechatPayService.class));
            context.refresh();

            for (Class<?> contract : new Class<?>[]{VipIdentityService.class,
                    VipRecommendationService.class, PaymentFulfillmentService.class,
                    PaymentOrderStateService.class, PaymentOrderCloseService.class,
                    PaymentOrderReconciliationService.class}) {
                assertEquals(1, context.getBeansOfType(contract).size());
            }
            var fulfillment = context.getBean(PaymentFulfillmentService.class);
            assertTrue(AopUtils.isAopProxy(fulfillment));
            assertThrows(BusinessException.class, () -> fulfillment.fulfill(null, 1, null, null));
            verify(transactionManager).rollback(transaction);
            verify(transactionManager, never()).commit(any());
        }
    }
}
