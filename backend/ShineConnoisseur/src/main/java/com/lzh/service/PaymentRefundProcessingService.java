package com.lzh.service;

public interface PaymentRefundProcessingService {
    /** 在本地受理事务提交后执行；网络请求不持有数据库事务和行锁。 */
    void process(Long refundId);
}
