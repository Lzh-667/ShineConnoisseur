package com.lzh.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lzh.common.Result;
import com.lzh.po.PaymentOrder;

public interface IPaymentOrderService extends IService<PaymentOrder> {
    Result createOrder(Long productId, Integer paymentMethod);
}
