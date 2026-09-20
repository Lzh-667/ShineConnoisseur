package com.lzh.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lzh.mapper.PaymentOrderMapper;
import com.lzh.po.PaymentOrder;
import com.lzh.service.impl.PaymentOrderCloseService;
import com.lzh.utils.SystemConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/** 后台关闭过期订单，避免由查询接口触发有副作用的关单。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentOrderExpirationTask {

    private final PaymentOrderMapper paymentOrderMapper;
    private final PaymentOrderCloseService closeService;

    @Scheduled(
            initialDelayString = "${payment.expiration-initial-delay-ms:30000}",
            fixedDelayString = "${payment.expiration-scan-delay-ms:60000}"
    )
    public void closeExpiredOrders() {
        List<PaymentOrder> orders = paymentOrderMapper.selectList(
                new LambdaQueryWrapper<PaymentOrder>()
                        .lt(PaymentOrder::getExpireTime, LocalDateTime.now())
                        .in(PaymentOrder::getStatus,
                                SystemConstants.ORDER_STATUS_PAYING,
                                SystemConstants.ORDER_STATUS_INITIATING,
                                SystemConstants.ORDER_STATUS_WAIT_PAY,
                                SystemConstants.ORDER_STATUS_CLOSING)
                        // 对账任务会刷新无进展订单的 update_time，避免前100个异常订单饿死后续订单。
                        .orderByAsc(PaymentOrder::getUpdateTime)
                        .last("LIMIT 100")
        );
        for (PaymentOrder order : orders) {
            try {
                closeService.requestClose(order, true);
            } catch (RuntimeException e) {
                log.error("过期支付订单关单异常，orderNo={}", order.getOrderNo(), e);
            }
        }
    }
}
