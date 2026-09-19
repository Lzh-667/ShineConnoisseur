package com.lzh.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lzh.common.Result;
import com.lzh.mapper.PaymentOrderMapper;
import com.lzh.mapper.VipProductMapper;
import com.lzh.po.PaymentOrder;
import com.lzh.po.VipProduct;
import com.lzh.service.IPaymentOrderService;
import com.lzh.service.AlipayService;
import com.lzh.service.WechatPayService;
import com.lzh.utils.SystemConstants;
import com.lzh.utils.UserHolder;
import com.lzh.vo.PaymentOrderVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Objects;

@Slf4j
@Service
public class PaymentOrderServiceImpl extends ServiceImpl<PaymentOrderMapper, PaymentOrder> implements IPaymentOrderService {

    @Resource
    private VipProductMapper vipProductMapper;
    @Resource
    private AlipayService alipayService;
    @Resource
    private WechatPayService wechatPayService;
    @Override
    public Result createOrder(Long productId, Integer paymentMethod) {
        if (!Objects.equals(paymentMethod, SystemConstants.VIP_PAY_METHOD_ALIBABA) && !Objects.equals(paymentMethod, SystemConstants.VIP_PAY_METHOD_WECHAT)) {
            return Result.fail("不支持的支付方式");
        }
        //1.获取当前用户
        Long userId = UserHolder.getUser().getId();
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
        //6.设置金额，支付方式，过期时间
        paymentOrder.setUserId(userId);
        paymentOrder.setProductId(productId);
        paymentOrder.setDurationDays(vipProduct.getDurationDays());
        paymentOrder.setAmount(vipProduct.getPrice());
        paymentOrder.setPaymentMethod(paymentMethod);
        paymentOrder.setStatus(SystemConstants.ORDER_STATUS_PAYING);
        paymentOrder.setExpireTime(LocalDateTime.now().plusMinutes(SystemConstants.ORDER_EXPIRE_TIME));
        //7.保存订单
        save(paymentOrder);
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
        //5.校验订单是否过期
        if(Objects.equals(paymentOrder.getStatus(), SystemConstants.ORDER_STATUS_PAYING) &&paymentOrder.getExpireTime()!=null&&paymentOrder.getExpireTime().isBefore(LocalDateTime.now())){
            if (closeRemoteOrder(paymentOrder) && closeLocalOrder(paymentOrder)) {
                paymentOrder.setStatus(SystemConstants.ORDER_STATUS_CLOSE);
            } else {
                log.warn("过期订单关单失败，等待后续重试，orderNo={}", orderNo);
            }
        }
        //3.包装为VO返回
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
        //6.先关闭支付渠道订单，再以条件更新关闭本地订单
        if (!closeRemoteOrder(paymentOrder)) {
            return Result.fail("支付渠道关单失败，请刷新订单状态后重试");
        }
        if (!closeLocalOrder(paymentOrder)) {
            PaymentOrder latest = getById(paymentOrder.getId());
            if (latest != null && Objects.equals(latest.getStatus(), SystemConstants.ORDER_STATUS_SUCCESS)) {
                return Result.fail("订单已支付，无法取消");
            }
            if (latest != null && Objects.equals(latest.getStatus(), SystemConstants.ORDER_STATUS_CLOSE)) {
                return Result.ok("订单已取消");
            }
            return Result.fail("订单状态已变化，请刷新后重试");
        }
        log.info("用户取消支付订单，orderNo={}, userId={}", orderNo, userId);

        return Result.ok("取消订单成功");
    }

    private boolean closeRemoteOrder(PaymentOrder paymentOrder) {
        if (Objects.equals(
                paymentOrder.getPaymentMethod(),
                SystemConstants.VIP_PAY_METHOD_ALIBABA
        )) {
            return alipayService.closeOrder(paymentOrder.getOrderNo());
        }
        if (Objects.equals(
                paymentOrder.getPaymentMethod(),
                SystemConstants.VIP_PAY_METHOD_WECHAT
        )) {
            return wechatPayService.closeOrder(paymentOrder.getOrderNo());
        }
        log.error(
                "订单支付方式异常，无法关单，orderNo={}, paymentMethod={}",
                paymentOrder.getOrderNo(),
                paymentOrder.getPaymentMethod()
        );
        return false;
    }

    private boolean closeLocalOrder(PaymentOrder paymentOrder) {
        return update(
                new LambdaUpdateWrapper<PaymentOrder>()
                        .eq(PaymentOrder::getId, paymentOrder.getId())
                        .eq(PaymentOrder::getStatus, SystemConstants.ORDER_STATUS_PAYING)
                        .set(PaymentOrder::getStatus, SystemConstants.ORDER_STATUS_CLOSE)
        );
    }
}
