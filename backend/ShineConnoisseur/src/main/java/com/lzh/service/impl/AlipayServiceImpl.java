package com.lzh.service.impl;

import cn.hutool.core.util.StrUtil;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.domain.AlipayTradePagePayModel;
import com.alipay.api.domain.AlipayTradeCloseModel;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradeCloseRequest;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.response.AlipayTradeCloseResponse;
import com.alipay.api.response.AlipayTradePagePayResponse;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lzh.common.Result;
import com.lzh.config.AlipayConfig;
import com.lzh.mapper.PaymentOrderMapper;
import com.lzh.po.PaymentOrder;
import com.lzh.service.AlipayService;
import com.lzh.utils.SystemConstants;
import com.lzh.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlipayServiceImpl implements AlipayService {

    private final AlipayConfig alipayConfig;

    private final PaymentOrderMapper paymentOrderMapper;

    private final PaymentFulfillmentService paymentFulfillmentService;
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
        // 4. 校验订单状态
        if (!paymentOrder.getStatus()
                .equals(SystemConstants.ORDER_STATUS_PAYING)) {
            return Result.fail("订单状态异常");
        }
        // 5. 校验订单是否过期
        if (paymentOrder.getExpireTime() != null
                && paymentOrder.getExpireTime().isBefore(LocalDateTime.now())) {
            return Result.fail("订单已过期");
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
        long remainingMinutes = Math.max(
                1,
                Duration.between(LocalDateTime.now(), paymentOrder.getExpireTime()).toMinutes()
        );
        model.setTimeoutExpress(remainingMinutes + "m");
        // 产品码
        model.setProductCode("FAST_INSTANT_TRADE_PAY");
        request.setBizModel(model);
        // 11. 调用支付宝
        try {
            AlipayTradePagePayResponse response =
                    alipayClient.pageExecute(request);
            if (response.isSuccess()) {
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
            return Result.fail("支付宝支付页面生成失败");
        } catch (AlipayApiException e) {
            log.error(
                    "调用支付宝支付接口异常，orderNo={}",
                    orderNo,
                    e
            );
            return Result.fail("调用支付宝支付接口失败");
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
    public boolean closeOrder(String orderNo) {
        AlipayTradeCloseRequest request = new AlipayTradeCloseRequest();
        AlipayTradeCloseModel model = new AlipayTradeCloseModel();
        model.setOutTradeNo(orderNo);
        request.setBizModel(model);
        try {
            AlipayTradeCloseResponse response = createClient().execute(request);
            if (response.isSuccess()) {
                return true;
            }
            // 尚未在支付宝侧形成交易时，本地可直接关闭。
            if ("ACQ.TRADE_NOT_EXIST".equals(response.getSubCode())) {
                return true;
            }
            log.warn(
                    "支付宝关单失败，orderNo={}, code={}, subCode={}, msg={}",
                    orderNo,
                    response.getCode(),
                    response.getSubCode(),
                    response.getSubMsg()
            );
            return false;
        } catch (AlipayApiException e) {
            log.error("支付宝关单异常，orderNo={}", orderNo, e);
            return false;
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
}
