package com.lzh.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lzh.po.PaymentRefund;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

public interface PaymentRefundMapper extends BaseMapper<PaymentRefund> {
    // 所有用户退款、支付履约先锁用户，再锁订单/权益；禁止反向加锁。
    @Select("SELECT id FROM user WHERE id = #{userId} FOR UPDATE")
    Long lockUser(Long userId);

    @Select("SELECT * FROM payment_refund WHERE order_id = #{orderId}")
    PaymentRefund findByOrder(Long orderId);

    @Select("SELECT id FROM payment_refund WHERE user_id = #{userId} AND status = 'PROCESSING' LIMIT 1 FOR UPDATE")
    Long pendingId(Long userId);

    default int pendingCount(Long userId) { return pendingId(userId) == null ? 0 : 1; }

    @Select("SELECT * FROM payment_refund WHERE status = 'PROCESSING' AND next_retry_at <= #{now} ORDER BY next_retry_at, id LIMIT 100")
    List<PaymentRefund> due(LocalDateTime now);

    @Update("UPDATE payment_refund SET last_error = #{message}, next_retry_at = #{next} WHERE id = #{id} AND status = 'PROCESSING'")
    int recordRetry(@Param("id") Long id, @Param("message") String message, @Param("next") LocalDateTime next);
}
