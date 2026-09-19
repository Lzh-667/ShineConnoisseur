package com.lzh.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lzh.common.Result;
import com.lzh.config.WeChatPayConfig;
import com.lzh.mapper.PaymentOrderMapper;
import com.lzh.po.PaymentOrder;
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
        //4.校验订单状态
        if (!Objects.equals(
                paymentOrder.getStatus(),
                SystemConstants.ORDER_STATUS_PAYING)) {
            return Result.fail("订单状态异常");
        }
        //5.校验订单是否过期
        if (paymentOrder.getExpireTime().isBefore(LocalDateTime.now())) {
            return Result.fail("订单已过期");
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
            //11.返回二维码链接
            return Result.ok(response.getCodeUrl());
        } catch (Exception e) {
            log.error("微信支付下单失败，orderNo={}", orderNo, e);
            return Result.fail("微信支付下单失败");
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
    public boolean closeOrder(String orderNo) {
        CloseOrderRequest request = new CloseOrderRequest();
        request.setMchid(weChatPayConfig.getMerchantId());
        request.setOutTradeNo(orderNo);
        try {
            NativePayService service = new NativePayService.Builder()
                    .config(createConfig())
                    .build();
            service.closeOrder(request);
            return true;
        } catch (ServiceException e) {
            // 未调用过预下单时，微信侧没有订单，本地可以安全关闭。
            if ("ORDER_NOT_EXISTS".equals(e.getErrorCode())
                    || "ORDER_NOT_EXIST".equals(e.getErrorCode())) {
                return true;
            }
            log.warn(
                    "微信支付关单失败，orderNo={}, code={}, message={}",
                    orderNo,
                    e.getErrorCode(),
                    e.getErrorMessage()
            );
            return false;
        } catch (Exception e) {
            log.error("微信支付关单异常，orderNo={}", orderNo, e);
            return false;
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
}
