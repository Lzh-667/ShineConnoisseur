package com.lzh.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
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
        String orderNo = System.currentTimeMillis()+ RandomUtil.randomNumbers(6);
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
}
