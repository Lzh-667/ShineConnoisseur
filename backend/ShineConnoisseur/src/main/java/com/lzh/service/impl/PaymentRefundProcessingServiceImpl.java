package com.lzh.service.impl;

import com.lzh.enums.PaymentRefundResult;
import com.lzh.mapper.PaymentOrderMapper;
import com.lzh.mapper.PaymentRefundMapper;
import com.lzh.po.PaymentOrder;
import com.lzh.po.PaymentRefund;
import com.lzh.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.Objects;
import static com.lzh.utils.SystemConstants.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentRefundProcessingServiceImpl implements PaymentRefundProcessingService {
    private final PaymentRefundMapper refundMapper;
    private final PaymentOrderMapper orderMapper;
    private final PaymentRefundService refundService;
    private final AlipayService alipayService;
    private final WechatPayService wechatPayService;

    @Override
    public void process(Long refundId) {
        PaymentRefund refund = refundMapper.selectById(refundId);
        if (refund == null || !"PROCESSING".equals(refund.getStatus())) return;
        try {
            PaymentOrder order = orderMapper.selectById(refund.getOrderId());
            if (order == null || !Objects.equals(order.getStatus(), ORDER_STATUS_REFUNDING))
                throw new IllegalStateException("退款订单状态异常");
            PaymentRefundResult result;
            if (Objects.equals(order.getPaymentMethod(), VIP_PAY_METHOD_ALIBABA)) {
                result = alipayService.queryRefund(order.getOrderNo(), refund.getRefundNo());
                if (result == PaymentRefundResult.FAILED)
                    result = alipayService.refundOrder(order.getOrderNo(), refund.getAmount(), refund.getRefundNo());
            } else if (Objects.equals(order.getPaymentMethod(), VIP_PAY_METHOD_WECHAT)) {
                result = wechatPayService.queryRefund(refund.getRefundNo());
                if (result == PaymentRefundResult.FAILED)
                    result = wechatPayService.refundOrder(order.getOrderNo(), refund.getAmount(), refund.getRefundNo());
            } else {
                throw new IllegalStateException("退款渠道异常");
            }
            if (result == PaymentRefundResult.SUCCESS) {
                refundService.complete(refundId);
            } else {
                refundMapper.recordRetry(refundId, result == PaymentRefundResult.PROCESSING
                        ? "渠道处理中或结果待确认" : "渠道未确认退款成功，将使用原退款号重试",
                        LocalDateTime.now().plusMinutes(1));
            }
        } catch (RuntimeException e) {
            log.error("退款处理待重试，refundNo={}", refund.getRefundNo(), e);
            refundMapper.recordRetry(refundId, "退款处理异常，请关注服务端日志；系统将继续核对",
                    LocalDateTime.now().plusMinutes(1));
        }
    }
}
