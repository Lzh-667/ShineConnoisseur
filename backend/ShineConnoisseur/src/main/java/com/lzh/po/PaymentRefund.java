package com.lzh.po;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 用户整单退款记录；受理后金额、权益快照和渠道幂等号不再变化。 */
@Data
@TableName("payment_refund")
public class PaymentRefund {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long userId;
    private String orderNo;
    private String refundNo;
    private BigDecimal amount;
    private Integer durationDays;
    private String status;
    private LocalDateTime entitlementExpireBefore;
    private LocalDateTime entitlementExpireAfter;
    private LocalDateTime requestedAt;
    private LocalDateTime completedAt;
    private LocalDateTime rightsRevokedAt;
    private String lastError;
    private LocalDateTime nextRetryAt;
}
