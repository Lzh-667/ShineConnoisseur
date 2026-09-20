package com.lzh.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lzh.common.Result;
import com.lzh.config.WeChatPayConfig;
import com.lzh.mapper.PaymentOrderMapper;
import com.lzh.po.PaymentOrder;
import com.lzh.service.PaymentCloseResult;
import com.lzh.service.PaymentOrderQueryResult;
import com.lzh.service.PaymentRefundResult;
import com.lzh.service.WechatPayService;
import com.lzh.utils.SystemConstants;
import com.lzh.utils.UserHolder;
import com.wechat.pay.java.core.Config;
import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.exception.ServiceException;
import com.wechat.pay.java.core.notification.NotificationConfig;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.model.Transaction;
import com.wechat.pay.java.service.payments.nativepay.NativePayService;
import com.wechat.pay.java.service.payments.nativepay.model.Amount;
import com.wechat.pay.java.service.payments.nativepay.model.CloseOrderRequest;
import com.wechat.pay.java.service.payments.nativepay.model.PrepayRequest;
import com.wechat.pay.java.service.payments.nativepay.model.PrepayResponse;
import com.wechat.pay.java.service.payments.nativepay.model.QueryOrderByOutTradeNoRequest;
import com.wechat.pay.java.service.refund.RefundService;
import com.wechat.pay.java.service.refund.model.AmountReq;
import com.wechat.pay.java.service.refund.model.CreateRequest;
import com.wechat.pay.java.service.refund.model.Refund;
import com.wechat.pay.java.service.refund.model.QueryByOutRefundNoRequest;
import com.wechat.pay.java.service.refund.model.Status;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

@Slf4j
@Service
public class WechatPayServiceImpl implements WechatPayService {

    @Resource
    private PaymentOrderMapper paymentOrderMapper;
    @Resource
    private WeChatPayConfig weChatPayConfig;
    @Resource
    private PaymentFulfillmentService paymentFulfillmentService;
    @Resource
    private PaymentOrderStateService paymentOrderStateService;
    @Override
    public Result wechat(String orderNo) {
        //1.查询订单
        PaymentOrder paymentOrder = paymentOrderMapper.selectOne(
                new LambdaQueryWrapper<PaymentOrder>()
                        .eq(PaymentOrder::getOrderNo, orderNo)
        );
        if (paymentOrder == null) {
            return Result.fail("订单不存在");
        }
        //2.校验当前用户
        Long userId = UserHolder.getUser().getId();
        if (!Objects.equals(paymentOrder.getUserId(), userId)) {
            return Result.fail("无权操作该订单");
        }
        //3.校验支付方式
        if (!Objects.equals(
                paymentOrder.getPaymentMethod(),
                SystemConstants.VIP_PAY_METHOD_WECHAT)) {
            return Result.fail("该订单不是微信支付订单");
        }
        //4.校验订单是否过期
        if (paymentOrder.getExpireTime().isBefore(LocalDateTime.now())) {
            return Result.fail("订单已过期");
        }

        //5.复用已生成的二维码；只有一个请求能抢占到发起中状态
        Result existingResult = existingPaymentResult(paymentOrder);
        if (existingResult != null) {
            return existingResult;
        }
        if (!paymentOrderStateService.transition(
                paymentOrder.getId(),
                SystemConstants.ORDER_STATUS_PAYING,
                SystemConstants.ORDER_STATUS_INITIATING
        )) {
            PaymentOrder latest = paymentOrderMapper.selectById(paymentOrder.getId());
            Result latestResult = existingPaymentResult(latest);
            return latestResult != null ? latestResult : Result.fail("订单状态已变化，请刷新后重试");
        }

        try {
            // 6. 创建微信支付配置
            Config config = new RSAAutoCertificateConfig.Builder()
                    .merchantId(weChatPayConfig.getMerchantId())
                    .privateKeyFromPath(
                            weChatPayConfig.getPrivateKeyPath())
                    .merchantSerialNumber(
                            weChatPayConfig.getMerchantSerialNumber())
                    .apiV3Key(
                            weChatPayConfig.getApiV3Key())
                    .build();
            //7.创建 Native 支付服务
            NativePayService service = new NativePayService.Builder()
                    .config(config)
                    .build();
            //8.创建预支付订单
            PrepayRequest request = new PrepayRequest();
            request.setOutTradeNo(paymentOrder.getOrderNo());
            request.setAppid(weChatPayConfig.getAppId());
            request.setMchid(weChatPayConfig.getMerchantId());
            request.setDescription("光影鉴赏家VIP会员");
            request.setNotifyUrl(weChatPayConfig.getNotifyUrl());
            request.setTimeExpire(
                    paymentOrder.getExpireTime()
                            .atZone(ZoneId.systemDefault())
                            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            );
            //9.设置金额
            Amount amount = new Amount();
            int total = paymentOrder.getAmount()
                    .multiply(new BigDecimal("100"))
                    .intValueExact();
            amount.setTotal(total);
            request.setAmount(amount);
            //10.调用微信
            PrepayResponse response = service.prepay(request);
            //11.缓存二维码并迁移到待支付状态
            if (!paymentOrderStateService.completeInitiation(
                    paymentOrder.getId(),
                    response.getCodeUrl()
            )) {
                PaymentOrder latest = paymentOrderMapper.selectById(paymentOrder.getId());
                if (latest != null
                        && Objects.equals(latest.getStatus(), SystemConstants.ORDER_STATUS_SUCCESS)) {
                    return Result.ok(response.getCodeUrl());
                }
                return Result.fail("订单正在关闭，请勿继续支付");
            }
            return Result.ok(response.getCodeUrl());
        } catch (Exception e) {
            log.error("微信支付下单失败，orderNo={}", orderNo, e);
            // 网络异常时渠道侧结果未知，保留发起中状态，避免取消线程先关闭本地再被迟到请求创建渠道订单。
            return Result.fail("微信支付下单结果待确认，请稍后查询订单");
        }
    }

    @Override
    public boolean notify(String body,
                          String signature,
                          String timestamp,
                          String nonce,
                          String serialNumber) {
        try {
            //1.构造微信支付回调请求参数
            RequestParam requestParam = new RequestParam.Builder()
                    .serialNumber(serialNumber)
                    .nonce(nonce)
                    .signature(signature)
                    .timestamp(timestamp)
                    .body(body)
                    .build();
            //2.创建回调通知配置
            NotificationConfig config =
                    new RSAAutoCertificateConfig.Builder()
                            .merchantId(weChatPayConfig.getMerchantId())
                            .privateKeyFromPath(
                                    weChatPayConfig.getPrivateKeyPath())
                            .merchantSerialNumber(
                                    weChatPayConfig.getMerchantSerialNumber())
                            .apiV3Key(
                                    weChatPayConfig.getApiV3Key())
                            .build();
            //3.创建通知解析器
            NotificationParser parser = new NotificationParser(config);
            //4.验签 + 解密
            Transaction transaction = parser.parse(requestParam, Transaction.class);
            //5.获取微信支付订单号
            String transactionId = transaction.getTransactionId();
            //6.获取商户订单号
            String orderNo = transaction.getOutTradeNo();
            //7.获取支付状态
            Transaction.TradeStateEnum tradeState =
                    transaction.getTradeState();
            log.info(
                    "微信支付回调：transactionId={}, orderNo={}, tradeState={}",
                    transactionId,
                    orderNo,
                    tradeState
            );
            //8.只处理支付成功
            if (!Transaction.TradeStateEnum.SUCCESS.equals(tradeState)) {
                log.warn(
                        "微信支付未成功：orderNo={}, tradeState={}",
                        orderNo,
                        tradeState
                );
                return true;
            }

            //9.校验应用和收款商户
            if (!Objects.equals(transaction.getAppid(), weChatPayConfig.getAppId())
                    || !Objects.equals(transaction.getMchid(), weChatPayConfig.getMerchantId())) {
                log.error(
                        "微信支付回调商户身份不匹配：orderNo={}, appid={}, mchid={}",
                        orderNo,
                        transaction.getAppid(),
                        transaction.getMchid()
                );
                return false;
            }

            //10.校验并转换支付金额
            if (transaction.getAmount() == null
                    || transaction.getAmount().getTotal() == null) {
                log.error("微信支付回调金额为空：orderNo={}", orderNo);
                return false;
            }
            BigDecimal actualAmount = BigDecimal.valueOf(
                    transaction.getAmount().getTotal()
            ).divide(
                    BigDecimal.valueOf(100)
            );
            //11.统一服务通过条件更新完成幂等入账和权益发放
            paymentFulfillmentService.fulfill(
                    orderNo,
                    SystemConstants.VIP_PAY_METHOD_WECHAT,
                    transactionId,
                    actualAmount
            );
            return true;
        } catch (Exception e) {
            log.error("微信支付回调处理失败", e);
            throw new RuntimeException("微信支付回调处理失败", e);
        }
    }

    @Override
    public PaymentCloseResult closeOrder(String orderNo) {
        CloseOrderRequest request = new CloseOrderRequest();
        request.setMchid(weChatPayConfig.getMerchantId());
        request.setOutTradeNo(orderNo);
        try {
            NativePayService service = new NativePayService.Builder()
                    .config(createConfig())
                    .build();
            service.closeOrder(request);
            return PaymentCloseResult.CLOSED;
        } catch (ServiceException e) {
            // 未调用过预下单时，微信侧没有订单，本地可以安全关闭。
            if ("ORDER_NOT_EXISTS".equals(e.getErrorCode())
                    || "ORDER_NOT_EXIST".equals(e.getErrorCode())) {
                return PaymentCloseResult.NOT_FOUND;
            }
            log.warn(
                    "微信支付关单失败，orderNo={}, code={}, message={}",
                    orderNo,
                    e.getErrorCode(),
                    e.getErrorMessage()
            );
            return PaymentCloseResult.FAILED;
        } catch (Exception e) {
            log.error("微信支付关单异常，orderNo={}", orderNo, e);
            return PaymentCloseResult.FAILED;
        }
    }

    @Override
    public PaymentOrderQueryResult queryOrder(String orderNo) {
        QueryOrderByOutTradeNoRequest request = new QueryOrderByOutTradeNoRequest();
        request.setMchid(weChatPayConfig.getMerchantId());
        request.setOutTradeNo(orderNo);
        try {
            Transaction transaction = new NativePayService.Builder()
                    .config(createConfig())
                    .build()
                    .queryOrderByOutTradeNo(request);
            if (!Objects.equals(transaction.getAppid(), weChatPayConfig.getAppId())
                    || !Objects.equals(transaction.getMchid(), weChatPayConfig.getMerchantId())
                    || !Objects.equals(transaction.getOutTradeNo(), orderNo)) {
                log.error("微信查单返回的商户信息不匹配，orderNo={}", orderNo);
                return PaymentOrderQueryResult.of(PaymentOrderQueryResult.State.UNKNOWN);
            }
            return switch (transaction.getTradeState()) {
                case SUCCESS -> {
                    if (transaction.getAmount() == null
                            || transaction.getAmount().getTotal() == null
                            || !"CNY".equals(transaction.getAmount().getCurrency())) {
                        log.error("微信查单金额或币种异常，orderNo={}", orderNo);
                        yield PaymentOrderQueryResult.of(PaymentOrderQueryResult.State.UNKNOWN);
                    }
                    yield PaymentOrderQueryResult.paid(
                            transaction.getTransactionId(),
                            BigDecimal.valueOf(transaction.getAmount().getTotal(), 2));
                }
                case NOTPAY, USERPAYING, ACCEPT ->
                        PaymentOrderQueryResult.of(PaymentOrderQueryResult.State.UNPAID);
                case REFUND -> PaymentOrderQueryResult.of(PaymentOrderQueryResult.State.REFUNDED);
                case CLOSED, REVOKED, PAYERROR ->
                        PaymentOrderQueryResult.of(PaymentOrderQueryResult.State.CLOSED);
            };
        } catch (ServiceException e) {
            if ("ORDER_NOT_EXISTS".equals(e.getErrorCode())
                    || "ORDER_NOT_EXIST".equals(e.getErrorCode())) {
                return PaymentOrderQueryResult.of(PaymentOrderQueryResult.State.NOT_FOUND);
            }
            log.warn("微信查单失败，orderNo={}, code={}, message={}",
                    orderNo, e.getErrorCode(), e.getErrorMessage());
            return PaymentOrderQueryResult.of(PaymentOrderQueryResult.State.UNKNOWN);
        } catch (Exception e) {
            log.error("微信查单异常，orderNo={}", orderNo, e);
            return PaymentOrderQueryResult.of(PaymentOrderQueryResult.State.UNKNOWN);
        }
    }

    @Override
    public PaymentRefundResult refundOrder(String orderNo, BigDecimal amount, String refundRequestNo) {
        CreateRequest request = new CreateRequest();
        request.setOutTradeNo(orderNo);
        request.setOutRefundNo(refundRequestNo);
        request.setReason("支付成功但权益发放失败，自动退款");
        long cents = amount.movePointRight(2).longValueExact();
        AmountReq refundAmount = new AmountReq();
        refundAmount.setRefund(cents);
        refundAmount.setTotal(cents);
        refundAmount.setCurrency("CNY");
        request.setAmount(refundAmount);
        try {
            Refund refund = new RefundService.Builder()
                    .config(createConfig())
                    .build()
                    .create(request);
            if (refund.getStatus() == Status.SUCCESS) {
                return PaymentRefundResult.SUCCESS;
            }
            if (refund.getStatus() == Status.PROCESSING) {
                return PaymentRefundResult.PROCESSING;
            }
            log.warn("微信退款未成功，orderNo={}, refundStatus={}", orderNo, refund.getStatus());
            return PaymentRefundResult.FAILED;
        } catch (Exception e) {
            log.error("微信退款异常，orderNo={}", orderNo, e);
            return PaymentRefundResult.FAILED;
        }
    }

    @Override
    public PaymentRefundResult queryRefund(String refundRequestNo) {
        QueryByOutRefundNoRequest request = new QueryByOutRefundNoRequest();
        request.setOutRefundNo(refundRequestNo);
        try {
            Refund refund = new RefundService.Builder()
                    .config(createConfig())
                    .build()
                    .queryByOutRefundNo(request);
            if (refund.getStatus() == Status.SUCCESS) {
                return PaymentRefundResult.SUCCESS;
            }
            if (refund.getStatus() == Status.PROCESSING) {
                return PaymentRefundResult.PROCESSING;
            }
            return PaymentRefundResult.FAILED;
        } catch (ServiceException e) {
            if ("RESOURCE_NOT_EXISTS".equals(e.getErrorCode())) {
                return PaymentRefundResult.FAILED;
            }
            log.warn("微信退款查询失败，refundRequestNo={}, code={}, message={}",
                    refundRequestNo, e.getErrorCode(), e.getErrorMessage());
            return PaymentRefundResult.PROCESSING;
        } catch (Exception e) {
            log.error("微信退款查询异常，refundRequestNo={}", refundRequestNo, e);
            return PaymentRefundResult.PROCESSING;
        }
    }

    private RSAAutoCertificateConfig createConfig() {
        return new RSAAutoCertificateConfig.Builder()
                .merchantId(weChatPayConfig.getMerchantId())
                .privateKeyFromPath(weChatPayConfig.getPrivateKeyPath())
                .merchantSerialNumber(weChatPayConfig.getMerchantSerialNumber())
                .apiV3Key(weChatPayConfig.getApiV3Key())
                .build();
    }

    private Result existingPaymentResult(PaymentOrder order) {
        if (order == null) {
            return Result.fail("订单不存在");
        }
        if (Objects.equals(order.getStatus(), SystemConstants.ORDER_STATUS_WAIT_PAY)) {
            return order.getPaymentPayload() != null && !order.getPaymentPayload().isBlank()
                    ? Result.ok(order.getPaymentPayload())
                    : Result.fail("历史订单缺少支付信息，请取消后重新下单");
        }
        if (Objects.equals(order.getStatus(), SystemConstants.ORDER_STATUS_INITIATING)) {
            return Result.fail("支付信息正在生成，请稍后重试");
        }
        if (Objects.equals(order.getStatus(), SystemConstants.ORDER_STATUS_CLOSING)) {
            return Result.fail("订单正在取消，请勿继续支付");
        }
        if (Objects.equals(order.getStatus(), SystemConstants.ORDER_STATUS_SUCCESS)) {
            return Result.fail("订单已支付");
        }
        if (Objects.equals(order.getStatus(), SystemConstants.ORDER_STATUS_CLOSE)
                || Objects.equals(order.getStatus(), SystemConstants.ORDER_STATUS_REFUND)) {
            return Result.fail("订单已关闭");
        }
        return null;
    }
}
