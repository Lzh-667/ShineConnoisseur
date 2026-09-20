package com.lzh.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lzh.common.BusinessException;
import com.lzh.mapper.PaymentOrderMapper;
import com.lzh.mapper.UserVipMapper;
import com.lzh.po.PaymentOrder;
import com.lzh.utils.SystemConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 支付成功后的统一履约入口。渠道回调只负责验签和解析，本类负责原子入账与发放权益。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentFulfillmentService {

    private final PaymentOrderMapper paymentOrderMapper;
    private final UserVipMapper userVipMapper;

    @Transactional(rollbackFor = Exception.class)
    public void fulfill(String orderNo,
                        Integer expectedPaymentMethod,
                        String transactionId,
                        BigDecimal paidAmount) {
        if (StrUtil.isBlank(orderNo) || StrUtil.isBlank(transactionId) || paidAmount == null) {
            throw new BusinessException("支付回调关键参数缺失");
        }

        PaymentOrder order = paymentOrderMapper.selectOne(
                new LambdaQueryWrapper<PaymentOrder>()
                        .eq(PaymentOrder::getOrderNo, orderNo)
                        .last("FOR UPDATE")
        );
        if (order == null) {
            throw new BusinessException("支付订单不存在");
        }
        if (!Objects.equals(order.getPaymentMethod(), expectedPaymentMethod)) {
            throw new BusinessException("支付回调渠道与订单不一致");
        }
        if (order.getAmount() == null || order.getAmount().compareTo(paidAmount) != 0) {
            throw new BusinessException("支付回调金额与订单不一致");
        }

        if (Objects.equals(order.getStatus(), SystemConstants.ORDER_STATUS_SUCCESS)) {
            bindOrValidateTransactionId(order, transactionId);
            log.info("支付订单已履约，忽略重复通知，orderNo={}, transactionId={}", orderNo, transactionId);
            return;
        }
        if (!List.of(
                SystemConstants.ORDER_STATUS_PAYING,
                SystemConstants.ORDER_STATUS_INITIATING,
                SystemConstants.ORDER_STATUS_WAIT_PAY,
                SystemConstants.ORDER_STATUS_CLOSING,
                SystemConstants.ORDER_STATUS_CLOSE
        ).contains(order.getStatus())) {
            throw new BusinessException("支付订单状态不允许履约");
        }

        Integer durationDays = order.getDurationDays();
        if (durationDays == null || durationDays <= 0) {
            throw new BusinessException("订单中的VIP时长快照异常");
        }

        LocalDateTime now = LocalDateTime.now();
        int changed = paymentOrderMapper.update(
                null,
                new LambdaUpdateWrapper<PaymentOrder>()
                        .eq(PaymentOrder::getId, order.getId())
                        .in(PaymentOrder::getStatus,
                                SystemConstants.ORDER_STATUS_PAYING,
                                SystemConstants.ORDER_STATUS_INITIATING,
                                SystemConstants.ORDER_STATUS_WAIT_PAY,
                                SystemConstants.ORDER_STATUS_CLOSING,
                                SystemConstants.ORDER_STATUS_CLOSE)
                        .set(PaymentOrder::getStatus, SystemConstants.ORDER_STATUS_SUCCESS)
                        .set(PaymentOrder::getTransactionId, transactionId)
                        .set(PaymentOrder::getPayTime, now)
        );
        if (changed != 1) {
            throw new BusinessException("支付订单状态已变化");
        }

        int granted = userVipMapper.grantOrExtend(order.getUserId(), durationDays, now);
        if (granted <= 0) {
            throw new BusinessException("VIP权益发放失败");
        }
        log.info(
                "支付订单履约成功，orderNo={}, transactionId={}, userId={}, durationDays={}",
                orderNo,
                transactionId,
                order.getUserId(),
                durationDays
        );
    }

    private void bindOrValidateTransactionId(PaymentOrder order, String transactionId) {
        if (StrUtil.isNotBlank(order.getTransactionId())) {
            if (!Objects.equals(order.getTransactionId(), transactionId)) {
                throw new BusinessException("同一订单对应了不同的第三方交易号");
            }
            return;
        }

        int changed = paymentOrderMapper.update(
                null,
                new LambdaUpdateWrapper<PaymentOrder>()
                        .eq(PaymentOrder::getId, order.getId())
                        .isNull(PaymentOrder::getTransactionId)
                        .set(PaymentOrder::getTransactionId, transactionId)
        );
        if (changed != 1) {
            throw new BusinessException("第三方交易号绑定失败");
        }
    }
}
