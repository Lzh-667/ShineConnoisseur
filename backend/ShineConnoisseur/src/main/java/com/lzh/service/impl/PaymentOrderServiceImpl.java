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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

@Slf4j
@Service
public class PaymentOrderServiceImpl extends ServiceImpl<PaymentOrderMapper, PaymentOrder> implements IPaymentOrderService {

    @Resource
    private VipProductMapper vipProductMapper;
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
        //4.创建order
        PaymentOrder paymentOrder = new PaymentOrder();
        //5.生成orderNo
        String orderNo = IdUtil.getSnowflakeNextIdStr();
        paymentOrder.setOrderNo(orderNo);
        //6.设置金额，支付方式，过期时间
        paymentOrder.setUserId(userId);
        paymentOrder.setProductId(productId);
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
            paymentOrder.setStatus(SystemConstants.ORDER_STATUS_CLOSE);
            updateById(paymentOrder);
        }
        //3.包装为VO返回
        PaymentOrderVO vo = BeanUtil.copyProperties(paymentOrder,PaymentOrderVO.class);
        return Result.ok(vo);
    }
    @Override
    @Transactional(rollbackFor = Exception.class)
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
        //6.删除订单
        paymentOrder.setStatus(SystemConstants.ORDER_STATUS_CLOSE);
        updateById(paymentOrder);
        log.info("用户取消支付订单，orderNo={}, userId={}", orderNo, userId);

        return Result.ok("取消订单成功");
    }
}
