package com.lzh.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lzh.common.Result;
import com.lzh.po.PaymentOrder;

public interface IPaymentOrderService extends IService<PaymentOrder> {
    Result createOrder(Long productId, Integer paymentMethod, String requestId);

    Result showOrder(String orderNo);

    Result deleteOrder(String orderNo);
}
