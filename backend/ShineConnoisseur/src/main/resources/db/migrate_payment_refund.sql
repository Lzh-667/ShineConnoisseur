-- 用户24小时全额退款；先执行本迁移，再部署服务。保留已有表和数据。
CREATE TABLE IF NOT EXISTS payment_refund (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    order_no VARCHAR(32) NOT NULL,
    refund_no VARCHAR(64) NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    duration_days INT NOT NULL,
    status VARCHAR(16) NOT NULL,
    entitlement_expire_before DATETIME NOT NULL,
    entitlement_expire_after DATETIME NOT NULL,
    requested_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) DEFAULT NULL,
    rights_revoked_at DATETIME(6) DEFAULT NULL,
    last_error VARCHAR(255) DEFAULT NULL,
    next_retry_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_refund_order (order_id),
    UNIQUE KEY uk_refund_no (refund_no),
    KEY idx_refund_due (status, next_retry_at),
    KEY idx_refund_user_status (user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户VIP整单退款与权益扣回记录';

-- 提高同一用户连续支付的排序精度；历史同秒订单以ID倒序作为稳定次序。
ALTER TABLE payment_order MODIFY COLUMN pay_time DATETIME(6) DEFAULT NULL COMMENT '支付成功时间';
