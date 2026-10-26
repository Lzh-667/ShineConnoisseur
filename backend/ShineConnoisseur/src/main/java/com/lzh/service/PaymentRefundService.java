package com.lzh.service;

import com.lzh.po.PaymentRefund;
import com.lzh.vo.PaymentOrderVO;
import java.util.List;

public interface PaymentRefundService {
    PaymentRefund request(Long userId, String orderNo);
    void complete(Long refundId);
    List<PaymentOrderVO> recentOrders(Long userId);
}
