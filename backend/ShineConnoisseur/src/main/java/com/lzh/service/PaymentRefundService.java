package com.lzh.service;

import com.lzh.po.PaymentRefund;
import com.lzh.vo.PaymentOrderVO;
import com.lzh.vo.RefundQuotaVO;
import java.util.List;

public interface PaymentRefundService {
    RefundQuotaVO quota(Long userId);
    PaymentRefund request(Long userId, String orderNo);
    void complete(Long refundId);
    List<PaymentOrderVO> recentOrders(Long userId);
}
