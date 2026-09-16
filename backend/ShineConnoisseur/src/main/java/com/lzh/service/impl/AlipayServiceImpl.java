package com.lzh.service.impl;

import cn.hutool.core.util.StrUtil;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.domain.AlipayTradePagePayModel;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.response.AlipayTradePagePayResponse;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lzh.common.Result;
import com.lzh.config.AlipayConfig;
import com.lzh.mapper.PaymentOrderMapper;
import com.lzh.mapper.UserVipMapper;
import com.lzh.mapper.VipProductMapper;
import com.lzh.po.PaymentOrder;
import com.lzh.po.UserVip;
import com.lzh.po.VipProduct;
import com.lzh.service.AlipayService;
import com.lzh.utils.SystemConstants;
import com.lzh.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlipayServiceImpl implements AlipayService {

    private final AlipayConfig alipayConfig;

    private final PaymentOrderMapper paymentOrderMapper;

    private final UserVipMapper userVipMapper;

    private final VipProductMapper vipProductMapper;
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
        AlipayClient alipayClient =
                new DefaultAlipayClient(
                        alipayConfig.getGatewayUrl(),
                        alipayConfig.getAppId(),
                        alipayConfig.getAppPrivateKey(),
                        alipayConfig.getFormat(),
                        alipayConfig.getCharset(),
                        alipayConfig.getAlipayPublicKey(),
                        alipayConfig.getSignType()
                );
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
    @Transactional(rollbackFor = Exception.class)
    public String notify(Map<String, String> params) {
        try {
            //1.验证支付宝签名
            boolean signVerified = AlipaySignature.rsaCheckV1(
                    params,
                    alipayConfig.getAlipayPublicKey(),
                    alipayConfig.getCharset(),
                    alipayConfig.getSignType()
            );
            if (!signVerified) {
                log.error("支付宝异步通知验签失败，params={}", params);
                return "failure";
            }
            //2.获取通知参数
            String orderNo = params.get("out_trade_no");
            String totalAmount = params.get("total_amount");
            String tradeStatus = params.get("trade_status");
            String appId = params.get("app_id");
            log.info(
                    "收到支付宝异步通知，orderNo={}, totalAmount={}, tradeStatus={}",
                    orderNo,
                    totalAmount,
                    tradeStatus
            );
            //3.校验 app_id
            if (!Objects.equals(appId, alipayConfig.getAppId())) {
                log.error(
                        "支付宝异步通知 app_id 校验失败，appId={}",
                        appId
                );
                return "failure";
            }
            //4.查询订单
            PaymentOrder paymentOrder =
                    paymentOrderMapper.selectOne(
                            new LambdaQueryWrapper<PaymentOrder>()
                                    .eq(
                                            PaymentOrder::getOrderNo,
                                            orderNo
                                    )
                    );
            if (paymentOrder == null) {
                log.error(
                        "支付宝异步通知订单不存在，orderNo={}",
                        orderNo
                );
                return "failure";
            }
            //5.校验订单金额
            BigDecimal notifyAmount =
                    new BigDecimal(totalAmount);

            if (paymentOrder.getAmount()
                    .compareTo(notifyAmount) != 0) {
                log.error(
                        "支付宝异步通知金额异常，orderNo={}, orderAmount={}, notifyAmount={}",
                        orderNo,
                        paymentOrder.getAmount(),
                        notifyAmount
                );
                return "failure";
            }
            //6.判断交易状态
            if (!"TRADE_SUCCESS".equals(tradeStatus)
                    && !"TRADE_FINISHED".equals(tradeStatus)) {
                log.info(
                        "支付宝交易未成功，orderNo={}, tradeStatus={}",
                        orderNo,
                        tradeStatus
                );
                return "success";
            }
            //7.幂等处理
            if (Objects.equals(
                    paymentOrder.getStatus(),
                    SystemConstants.ORDER_STATUS_SUCCESS
            )) {
                log.info(
                        "订单已经支付成功，重复通知，orderNo={}",
                        orderNo
                );
                return "success";
            }
            //8.校验订单是否处于待支付状态
            if (!Objects.equals(
                    paymentOrder.getStatus(),
                    SystemConstants.ORDER_STATUS_PAYING
            )) {
                log.warn(
                        "订单状态异常，orderNo={}, status={}",
                        orderNo,
                        paymentOrder.getStatus()
                );
                return "failure";
            }
            //9.更新订单状态
            paymentOrder.setStatus(SystemConstants.ORDER_STATUS_SUCCESS);
            paymentOrder.setPayTime(LocalDateTime.now());
            paymentOrderMapper.updateById(paymentOrder);
            //10.开通 VIP
            openVip(paymentOrder);
            log.info(
                    "支付宝支付成功，订单处理完成，orderNo={}",
                    orderNo
            );
            //11.告诉支付宝已经成功接收通知
            return "success";
        } catch (Exception e) {
            log.error(
                    "支付宝异步通知处理异常",
                    e
            );
            return "failure";
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
