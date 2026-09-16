package com.lzh.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lzh.common.Result;
import com.lzh.config.WeChatPayConfig;
import com.lzh.mapper.PaymentOrderMapper;
import com.lzh.mapper.UserVipMapper;
import com.lzh.mapper.VipProductMapper;
import com.lzh.po.PaymentOrder;
import com.lzh.po.UserVip;
import com.lzh.po.VipProduct;
import com.lzh.service.WechatPayService;
import com.lzh.utils.SystemConstants;
import com.lzh.utils.UserHolder;
import com.wechat.pay.java.core.Config;
import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.notification.NotificationConfig;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.model.Transaction;
import com.wechat.pay.java.service.payments.nativepay.NativePayService;
import com.wechat.pay.java.service.payments.nativepay.model.Amount;
import com.wechat.pay.java.service.payments.nativepay.model.PrepayRequest;
import com.wechat.pay.java.service.payments.nativepay.model.PrepayResponse;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

@Slf4j
@Service
public class WechatPayServiceImpl implements WechatPayService {

    @Resource
    private PaymentOrderMapper paymentOrderMapper;
    @Resource
    private WeChatPayConfig weChatPayConfig;
    @Resource
    private VipProductMapper vipProductMapper;
    @Resource
    private UserVipMapper userVipMapper;
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
            request.setDescription("光影鉴赏家VIP会员");
            request.setNotifyUrl(weChatPayConfig.getNotifyUrl());
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
    @Transactional(rollbackFor = Exception.class)
    public String notify(String body,
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
                return "success";
            }
            //9.查询本地订单
            PaymentOrder paymentOrder = paymentOrderMapper.selectOne(
                    new LambdaQueryWrapper<PaymentOrder>()
                            .eq(PaymentOrder::getOrderNo, orderNo)
            );
            if (paymentOrder == null) {
                log.error("微信支付回调订单不存在：orderNo={}", orderNo);
                return "fail";
            }
            //10.幂等处理
            if (Objects.equals(
                    paymentOrder.getStatus(),
                    SystemConstants.ORDER_STATUS_SUCCESS)) {

                log.info("微信支付订单已经处理：orderNo={}", orderNo);
                return "success";
            }
            //11.校验订单状态
            if (!Objects.equals(
                    paymentOrder.getStatus(),
                    SystemConstants.ORDER_STATUS_PAYING)) {

                log.warn(
                        "微信支付订单状态异常：orderNo={}, status={}",
                        orderNo,
                        paymentOrder.getStatus()
                );
                return "fail";
            }
            //12.校验支付金额
            if (transaction.getAmount() == null
                    || transaction.getAmount().getTotal() == null) {

                log.error("微信支付回调金额为空：orderNo={}", orderNo);
                return "fail";
            }
            BigDecimal actualAmount = BigDecimal.valueOf(
                    transaction.getAmount().getTotal()
            ).divide(
                    BigDecimal.valueOf(100)
            );
            if (paymentOrder.getAmount().compareTo(actualAmount) != 0) {
                log.error(
                        "微信支付金额不一致：orderNo={}, expected={}, actual={}",
                        orderNo,
                        paymentOrder.getAmount(),
                        actualAmount
                );
                return "fail";
            }
            //13.更新订单状态
            paymentOrder.setStatus(
                    SystemConstants.ORDER_STATUS_SUCCESS
            );
            paymentOrder.setPayTime(LocalDateTime.now());
            paymentOrderMapper.updateById(paymentOrder);
            //14.开通 / 延长 VIP
            openVip(paymentOrder);
            //15.告诉微信：处理成功
            return "success";
        } catch (Exception e) {
            log.error("微信支付回调处理失败", e);
            throw new RuntimeException("微信支付回调处理失败", e);
        }
    }
    private void openVip(PaymentOrder paymentOrder) {
        //1.获取用户ID
        Long userId = paymentOrder.getUserId();
        //2.查询购买的VIP套餐
        VipProduct vipProduct = vipProductMapper.selectById(paymentOrder.getProductId());
        if (vipProduct == null) {
            throw new RuntimeException("VIP套餐不存在");
        }
        //3.获取会员时长
        Integer duration = vipProduct.getDurationDays();
        if (duration == null || duration <= 0) {
            throw new RuntimeException("VIP套餐时长异常");
        }
        //4.查询用户现有VIP记录
        UserVip userVip = userVipMapper.selectOne(
                new LambdaQueryWrapper<UserVip>()
                        .eq(UserVip::getUserId, userId)
        );
        LocalDateTime now = LocalDateTime.now();
        //5.用户还没有VIP记录
        if (userVip == null) {
            userVip = new UserVip();
            userVip.setUserId(userId);
            userVip.setStartTime(now);
            userVip.setExpireTime(now.plusDays(duration));
            userVipMapper.insert(userVip);
            log.info(
                    "用户开通VIP成功，userId={}, expireTime={}",
                    userId,
                    userVip.getExpireTime()
            );
            return;
        }
        //6.已经有VIP记录
        LocalDateTime expireTime = userVip.getExpireTime();
        //7.VIP已经过期，从当前时间重新开始计算
        if (expireTime == null || expireTime.isBefore(now)) {
            userVip.setStartTime(now);
            userVip.setExpireTime(
                    now.plusDays(duration)
            );
        } else {
            //8.VIP还没有过期，在原到期时间基础上续期
            userVip.setExpireTime(
                    expireTime.plusDays(duration)
            );
        }
        //9.更新VIP记录
        userVipMapper.updateById(userVip);
        log.info(
                "用户VIP续期成功，userId={}, expireTime={}",
                userId,
                userVip.getExpireTime()
        );
    }
}
