USE shineconnoisseur;

-- 已有环境执行一次：增加创建幂等键、支付发起结果缓存和并发状态机。
ALTER TABLE payment_order
    ADD COLUMN request_id VARCHAR(64) NULL COMMENT '客户端创建订单幂等键' AFTER order_no,
    ADD COLUMN payment_payload MEDIUMTEXT NULL COMMENT '支付页面HTML或二维码URL' AFTER transaction_id,
    MODIFY COLUMN status TINYINT NOT NULL DEFAULT 0
        COMMENT '订单状态：0待发起，1支付成功，2已关闭，3已退款，4发起中，5待支付，6关单中，7退款中';

UPDATE payment_order
SET request_id = order_no
WHERE request_id IS NULL;

-- 旧版本的状态0可能已经在渠道侧创建支付单，保守迁移为待支付状态。
UPDATE payment_order
SET status = 5
WHERE status = 0;

ALTER TABLE payment_order
    MODIFY COLUMN request_id VARCHAR(64) NOT NULL COMMENT '客户端创建订单幂等键',
    ADD UNIQUE KEY uk_user_request (user_id, request_id);
