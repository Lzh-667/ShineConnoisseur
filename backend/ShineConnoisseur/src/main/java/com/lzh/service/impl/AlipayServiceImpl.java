package com.lzh.service.impl;

import cn.hutool.core.util.StrUtil;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.domain.AlipayTradePagePayModel;
import com.alipay.api.domain.AlipayTradeCloseModel;
import com.alipay.api.domain.AlipayTradeQueryModel;
import com.alipay.api.domain.AlipayTradeRefundModel;
import com.alipay.api.domain.AlipayTradeFastpayRefundQueryModel;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradeCloseRequest;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.request.AlipayTradeFastpayRefundQueryRequest;
import com.alipay.api.response.AlipayTradeCloseResponse;
import com.alipay.api.response.AlipayTradePagePayResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.alipay.api.response.AlipayTradeRefundResponse;
import com.alipay.api.response.AlipayTradeFastpayRefundQueryResponse;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lzh.common.Result;
import com.lzh.config.AlipayConfig;
import com.lzh.mapper.PaymentOrderMapper;
import com.lzh.po.PaymentOrder;
import com.lzh.service.AlipayService;
import com.lzh.service.PaymentCloseResult;
import com.lzh.service.PaymentOrderQueryResult;
import com.lzh.service.PaymentRefundResult;
import com.lzh.utils.SystemConstants;
import com.lzh.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlipayServiceImpl implements AlipayService {

    private static final DateTimeFormatter ALIPAY_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AlipayConfig alipayConfig;

    private final PaymentOrderMapper paymentOrderMapper;

    private final PaymentFulfillmentService paymentFulfillmentService;

    private final PaymentOrderStateService paymentOrderStateService;
    @Override
    public Result alipay(String orderNo) {
        // 1. 查询订单
        PaymentOrder paymentOrder =
                paymentOrderMapper.selectOne(
                        new LambdaQueryWrapper<PaymentOrder>()
                                .eq(PaymentOrder::getOrderNo, orderNo)
                );

        if (paymentOrder == null) {
            return Result.fail("订单不存在");
        }
        // 2. 校验订单是否属于当前用户
        Long userId = UserHolder.getUser().getId();

        if (!paymentOrder.getUserId().equals(userId)) {
            return Result.fail("无权操作该订单");
        }
        // 3. 校验支付方式
        if (!paymentOrder.getPaymentMethod()
                .equals(SystemConstants.VIP_PAY_METHOD_ALIBABA)) {
            return Result.fail("该订单不是支付宝订单");
        }
        // 4. 校验订单是否过期
        if (paymentOrder.getExpireTime() != null
                && paymentOrder.getExpireTime().isBefore(LocalDateTime.now())) {
            return Result.fail("订单已过期");
        }

        // 5. 已生成的支付页面直接复用，保证接口幂等
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

        // 6. 创建支付宝客户端
        AlipayClient alipayClient = createClient();
        // 7. 创建支付宝电脑网站支付请求
        AlipayTradePagePayRequest request =
                new AlipayTradePagePayRequest();
        // 8. 设置异步通知地址
        if (StrUtil.isNotBlank(alipayConfig.getNotifyUrl())) {
            request.setNotifyUrl(alipayConfig.getNotifyUrl());
        }
        // 9. 设置同步跳转地址
        if (StrUtil.isNotBlank(alipayConfig.getReturnUrl())) {
            request.setReturnUrl(alipayConfig.getReturnUrl());
        }
        // 10. 设置业务参数
        AlipayTradePagePayModel model =
                new AlipayTradePagePayModel();
        // 商户订单号
        model.setOutTradeNo(paymentOrder.getOrderNo());
        // 订单标题
        model.setSubject("光影鉴赏家VIP会员");
        // 支付金额
        model.setTotalAmount(
                paymentOrder.getAmount().toString()
        );
        // 使用绝对截止时间，避免向下取整或最小一分钟造成渠道有效期晚于本地订单。
        model.setTimeExpire(paymentOrder.getExpireTime().format(ALIPAY_TIME_FORMATTER));
        // 产品码
        model.setProductCode("FAST_INSTANT_TRADE_PAY");
        request.setBizModel(model);
        // 11. 调用支付宝
        try {
            AlipayTradePagePayResponse response =
                    alipayClient.pageExecute(request);
            if (response.isSuccess()) {
                if (!paymentOrderStateService.completeInitiation(
                        paymentOrder.getId(),
                        response.getBody()
                )) {
                    PaymentOrder latest = paymentOrderMapper.selectById(paymentOrder.getId());
                    if (latest != null
                            && Objects.equals(latest.getStatus(), SystemConstants.ORDER_STATUS_SUCCESS)) {
                        return Result.ok(response.getBody());
                    }
                    return Result.fail("订单正在关闭，请勿继续支付");
                }
                log.info(
                        "支付宝支付页面生成成功，orderNo={}",
                        orderNo
                );
                // 返回支付宝支付页面 HTML
                return Result.ok(response.getBody());
            }
            log.error(
                    "支付宝支付页面生成失败，orderNo={}, code={}, msg={}",
                    orderNo,
                    response.getCode(),
                    response.getMsg()
            );
            paymentOrderStateService.resetInitiation(paymentOrder.getId());
            return Result.fail("支付宝支付页面生成失败");
        } catch (AlipayApiException e) {
            log.error(
                    "调用支付宝支付接口异常，orderNo={}",
                    orderNo,
                    e
            );
            // pageExecute 失败时客户端没有拿到可提交的表单，允许安全重试。
            paymentOrderStateService.resetInitiation(paymentOrder.getId());
            return Result.fail("支付宝支付页面生成失败，请重试");
        }
    }
    @Override
    public boolean notify(Map<String, String> params) {
        try {
            //1.验证支付宝签名
            boolean signVerified = AlipaySignature.rsaCheckV1(
                    params,
                    alipayConfig.getAlipayPublicKey(),
                    alipayConfig.getCharset(),
                    alipayConfig.getSignType()
            );
            if (!signVerified) {
                log.error("支付宝异步通知验签失败");
                return false;
            }
            //2.获取通知参数
            String orderNo = params.get("out_trade_no");
            String totalAmount = params.get("total_amount");
            String tradeStatus = params.get("trade_status");
            String appId = params.get("app_id");
            String sellerId = params.get("seller_id");
            String transactionId = params.get("trade_no");
            log.info(
                    "收到支付宝异步通知，orderNo={}, totalAmount={}, tradeStatus={}",
                    orderNo,
                    totalAmount,
                    tradeStatus
            );
            //3.校验应用和收款商户
            if (!Objects.equals(appId, alipayConfig.getAppId())
                    || !Objects.equals(sellerId, alipayConfig.getSellerId())) {
                log.error(
                        "支付宝异步通知商户身份校验失败，appId={}, sellerId={}",
                        appId,
                        sellerId
                );
                return false;
            }
            //4.非成功状态无需履约，但通知已被正确接收
            if (!"TRADE_SUCCESS".equals(tradeStatus)
                    && !"TRADE_FINISHED".equals(tradeStatus)) {
                log.info(
                        "支付宝交易未成功，orderNo={}, tradeStatus={}",
                        orderNo,
                        tradeStatus
                );
                return true;
            }

            //5.统一服务通过条件更新完成幂等入账和权益发放
            paymentFulfillmentService.fulfill(
                    orderNo,
                    SystemConstants.VIP_PAY_METHOD_ALIBABA,
                    transactionId,
                    new BigDecimal(totalAmount)
            );
            log.info(
                    "支付宝支付成功，订单处理完成，orderNo={}",
                    orderNo
            );
            return true;
        } catch (Exception e) {
            log.error(
                    "支付宝异步通知处理异常",
                    e
            );
            throw new RuntimeException("支付宝异步通知处理异常", e);
        }
    }

    @Override
    public PaymentCloseResult closeOrder(String orderNo) {
        AlipayTradeCloseRequest request = new AlipayTradeCloseRequest();
        AlipayTradeCloseModel model = new AlipayTradeCloseModel();
        model.setOutTradeNo(orderNo);
        request.setBizModel(model);
        try {
            AlipayTradeCloseResponse response = createClient().execute(request);
            if (response.isSuccess()) {
                return PaymentCloseResult.CLOSED;
            }
            if ("ACQ.TRADE_NOT_EXIST".equals(response.getSubCode())) {
                return PaymentCloseResult.NOT_FOUND;
            }
            log.warn(
                    "支付宝关单失败，orderNo={}, code={}, subCode={}, msg={}",
                    orderNo,
                    response.getCode(),
                    response.getSubCode(),
                    response.getSubMsg()
            );
            return PaymentCloseResult.FAILED;
        } catch (AlipayApiException e) {
            log.error("支付宝关单异常，orderNo={}", orderNo, e);
            return PaymentCloseResult.FAILED;
        }
    }

    @Override
    public PaymentOrderQueryResult queryOrder(String orderNo) {
        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
        AlipayTradeQueryModel model = new AlipayTradeQueryModel();
        model.setOutTradeNo(orderNo);
        request.setBizModel(model);
        try {
            AlipayTradeQueryResponse response = createClient().execute(request);
            if (!response.isSuccess()) {
                if ("ACQ.TRADE_NOT_EXIST".equals(response.getSubCode())) {
                    return PaymentOrderQueryResult.of(PaymentOrderQueryResult.State.NOT_FOUND);
                }
                log.warn("支付宝查单失败，orderNo={}, code={}, subCode={}, msg={}",
                        orderNo, response.getCode(), response.getSubCode(), response.getSubMsg());
                return PaymentOrderQueryResult.of(PaymentOrderQueryResult.State.UNKNOWN);
            }
            return switch (response.getTradeStatus()) {
                case "TRADE_SUCCESS", "TRADE_FINISHED" -> PaymentOrderQueryResult.paid(
                        response.getTradeNo(), new BigDecimal(response.getTotalAmount()));
                case "WAIT_BUYER_PAY" -> PaymentOrderQueryResult.of(PaymentOrderQueryResult.State.UNPAID);
                case "TRADE_CLOSED" -> PaymentOrderQueryResult.of(PaymentOrderQueryResult.State.CLOSED);
                default -> PaymentOrderQueryResult.of(PaymentOrderQueryResult.State.UNKNOWN);
            };
        } catch (Exception e) {
            log.error("支付宝查单异常，orderNo={}", orderNo, e);
            return PaymentOrderQueryResult.of(PaymentOrderQueryResult.State.UNKNOWN);
        }
    }

    @Override
    public PaymentRefundResult refundOrder(String orderNo, BigDecimal amount, String refundRequestNo) {
        AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();
        AlipayTradeRefundModel model = new AlipayTradeRefundModel();
        model.setOutTradeNo(orderNo);
        model.setRefundAmount(amount.toPlainString());
        model.setOutRequestNo(refundRequestNo);
        model.setRefundReason("支付成功但权益发放失败，自动退款");
        request.setBizModel(model);
        try {
            AlipayTradeRefundResponse response = createClient().execute(request);
            if (response.isSuccess()) {
                return PaymentRefundResult.SUCCESS;
            }
            log.warn("支付宝退款失败，orderNo={}, code={}, subCode={}, msg={}",
                    orderNo, response.getCode(), response.getSubCode(), response.getSubMsg());
            return PaymentRefundResult.FAILED;
        } catch (Exception e) {
            log.error("支付宝退款异常，orderNo={}", orderNo, e);
            return PaymentRefundResult.FAILED;
        }
    }

    @Override
    public PaymentRefundResult queryRefund(String orderNo, String refundRequestNo) {
        AlipayTradeFastpayRefundQueryRequest request = new AlipayTradeFastpayRefundQueryRequest();
        AlipayTradeFastpayRefundQueryModel model = new AlipayTradeFastpayRefundQueryModel();
        model.setOutTradeNo(orderNo);
        model.setOutRequestNo(refundRequestNo);
        request.setBizModel(model);
        try {
            AlipayTradeFastpayRefundQueryResponse response = createClient().execute(request);
            if (!response.isSuccess()) {
                return PaymentRefundResult.FAILED;
            }
            if ("REFUND_SUCCESS".equals(response.getRefundStatus())
                    || response.getGmtRefundPay() != null) {
                return PaymentRefundResult.SUCCESS;
            }
            return PaymentRefundResult.PROCESSING;
        } catch (Exception e) {
            log.error("支付宝退款查询异常，orderNo={}, refundRequestNo={}",
                    orderNo, refundRequestNo, e);
            return PaymentRefundResult.PROCESSING;
        }
    }

    private AlipayClient createClient() {
        return new DefaultAlipayClient(
                alipayConfig.getGatewayUrl(),
                alipayConfig.getAppId(),
                alipayConfig.getAppPrivateKey(),
                alipayConfig.getFormat(),
                alipayConfig.getCharset(),
                alipayConfig.getAlipayPublicKey(),
                alipayConfig.getSignType()
        );
    }

    private Result existingPaymentResult(PaymentOrder order) {
        if (order == null) {
            return Result.fail("订单不存在");
        }
        if (Objects.equals(order.getStatus(), SystemConstants.ORDER_STATUS_WAIT_PAY)) {
            return StrUtil.isNotBlank(order.getPaymentPayload())
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
