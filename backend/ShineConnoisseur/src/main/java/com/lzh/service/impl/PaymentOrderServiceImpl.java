package com.lzh.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lzh.common.Result;
import com.lzh.mapper.PaymentOrderMapper;
import com.lzh.mapper.VipProductMapper;
import com.lzh.po.PaymentOrder;
import com.lzh.po.VipProduct;
import com.lzh.service.IPaymentOrderService;
import com.lzh.utils.SystemConstants;
import com.lzh.utils.UserHolder;
import com.lzh.vo.PaymentOrderVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Objects;

@Slf4j
@Service
public class PaymentOrderServiceImpl extends ServiceImpl<PaymentOrderMapper, PaymentOrder> implements IPaymentOrderService {

    @Resource
    private VipProductMapper vipProductMapper;
    @Resource
    private PaymentOrderCloseService paymentOrderCloseService;
    @Override
    public Result createOrder(Long productId, Integer paymentMethod, String requestId) {
        if (!Objects.equals(paymentMethod, SystemConstants.VIP_PAY_METHOD_ALIBABA) && !Objects.equals(paymentMethod, SystemConstants.VIP_PAY_METHOD_WECHAT)) {
            return Result.fail("不支持的支付方式");
        }
        if (StrUtil.isBlank(requestId) || requestId.length() > 64) {
            return Result.fail("Idempotency-Key不能为空且长度不能超过64字符");
        }
        //1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        PaymentOrder existingOrder = getOne(
                new LambdaQueryWrapper<PaymentOrder>()
                        .eq(PaymentOrder::getUserId, userId)
                        .eq(PaymentOrder::getRequestId, requestId)
        );
        if (existingOrder != null) {
            return sameOrderRequest(existingOrder, productId, paymentMethod)
                    ? Result.ok(BeanUtil.copyProperties(existingOrder, PaymentOrderVO.class))
                    : Result.fail("Idempotency-Key已用于其他订单请求");
        }
        //2.查询VIP套餐
        //3.校验套餐是否上架
        VipProduct vipProduct = vipProductMapper.selectOne(
                new LambdaQueryWrapper<VipProduct>()
                        .eq(VipProduct::getId,productId)
                        .eq(VipProduct::getStatus, SystemConstants.VIPPRODUCT_STATUS_NORMAL)
        );
        if(vipProduct==null){
            return Result.fail("VIP套餐不存在或已下架");
        }
        if (vipProduct.getDurationDays() == null
                || vipProduct.getDurationDays() <= 0
                || vipProduct.getPrice() == null
                || vipProduct.getPrice().signum() <= 0) {
            log.error("VIP套餐配置异常，productId={}", productId);
            return Result.fail("VIP套餐配置异常");
        }
        //4.创建order
        PaymentOrder paymentOrder = new PaymentOrder();
        //5.生成orderNo
        String orderNo = IdUtil.getSnowflakeNextIdStr();
        paymentOrder.setOrderNo(orderNo);
        paymentOrder.setRequestId(requestId);
        //6.设置金额，支付方式，过期时间
        paymentOrder.setUserId(userId);
        paymentOrder.setProductId(productId);
        paymentOrder.setDurationDays(vipProduct.getDurationDays());
        paymentOrder.setAmount(vipProduct.getPrice());
        paymentOrder.setPaymentMethod(paymentMethod);
        paymentOrder.setStatus(SystemConstants.ORDER_STATUS_PAYING);
        paymentOrder.setExpireTime(LocalDateTime.now().plusMinutes(SystemConstants.ORDER_EXPIRE_TIME));
        //7.保存订单
        try {
            save(paymentOrder);
        } catch (DuplicateKeyException e) {
            // 两个相同请求同时插入时，唯一键只允许一个成功，另一方返回已创建订单。
            PaymentOrder concurrentOrder = getOne(
                    new LambdaQueryWrapper<PaymentOrder>()
                            .eq(PaymentOrder::getUserId, userId)
                            .eq(PaymentOrder::getRequestId, requestId)
            );
            if (concurrentOrder == null) {
                throw e;
            }
            return sameOrderRequest(concurrentOrder, productId, paymentMethod)
                    ? Result.ok(BeanUtil.copyProperties(concurrentOrder, PaymentOrderVO.class))
                    : Result.fail("Idempotency-Key已用于其他订单请求");
        }
        //8.返回订单信息
        PaymentOrderVO vo = BeanUtil.copyProperties(paymentOrder,PaymentOrderVO.class);
        return Result.ok(vo);
    }
    @Override
    public Result showOrder(String orderNo) {
        //1.参数校验
        if(StrUtil.isBlank(orderNo)){
            return Result.fail("订单号不能为空");
        }
        //2.获取当前用户
        Long userId = UserHolder.getUser().getId();
        //3.根据orderNo查询订单
        PaymentOrder paymentOrder = getOne(
                new LambdaQueryWrapper<PaymentOrder>()
                        .eq(PaymentOrder::getOrderNo,orderNo)
                        .eq(PaymentOrder::getUserId,userId)
        );
        //4.判断是否为空
        if(paymentOrder==null){
            return Result.fail("订单不存在");
        }
        //5.查询接口不执行关单副作用，过期订单由后台任务处理
        PaymentOrderVO vo = BeanUtil.copyProperties(paymentOrder,PaymentOrderVO.class);
        return Result.ok(vo);
    }
    @Override
    public Result deleteOrder(String orderNo) {
        //1.参数校验
        if(StrUtil.isBlank(orderNo)){
            return Result.fail("订单号不能为空");
        }
        //2.获取当前用户
        Long userId = UserHolder.getUser().getId();
        //3.根据orderNo获取订单
        PaymentOrder paymentOrder = getOne(
                new LambdaQueryWrapper<PaymentOrder>()
                        .eq(PaymentOrder::getOrderNo,orderNo)
                        .eq(PaymentOrder::getUserId,userId)
        );
        //4.判断是否为空
        if(paymentOrder==null){
            return Result.fail("订单不存在");
        }
        //5.判断状态
        if(Objects.equals(paymentOrder.getStatus(), SystemConstants.ORDER_STATUS_SUCCESS)){
            return Result.fail("订单已支付，无法取消");
        }
        if(Objects.equals(paymentOrder.getStatus(), SystemConstants.ORDER_STATUS_CLOSE)){
            return Result.fail("订单已关闭");
        }
        if(Objects.equals(paymentOrder.getStatus(), SystemConstants.ORDER_STATUS_REFUND)){
            return Result.fail("订单已退款，无法取消");
        }
        PaymentOrderCloseService.CloseOutcome outcome =
                paymentOrderCloseService.requestClose(paymentOrder, false);
        if (outcome == PaymentOrderCloseService.CloseOutcome.CLOSED) {
            log.info("用户取消支付订单，orderNo={}, userId={}", orderNo, userId);
            return Result.ok("取消订单成功");
        }
        if (outcome == PaymentOrderCloseService.CloseOutcome.IN_PROGRESS) {
            return Result.ok("取消请求已受理，正在确认关单");
        }

        PaymentOrder latest = getById(paymentOrder.getId());
        if (latest != null && Objects.equals(latest.getStatus(), SystemConstants.ORDER_STATUS_SUCCESS)) {
            return Result.fail("订单已支付，无法取消");
        }
        if (latest != null && Objects.equals(latest.getStatus(), SystemConstants.ORDER_STATUS_CLOSE)) {
            return Result.ok("订单已取消");
        }
        return Result.fail("订单状态已变化，请刷新后重试");
    }

    private boolean sameOrderRequest(PaymentOrder order, Long productId, Integer paymentMethod) {
        return Objects.equals(order.getProductId(), productId)
                && Objects.equals(order.getPaymentMethod(), paymentMethod);
    }
}
