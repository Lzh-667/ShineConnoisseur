USE shineconnoisseur;

-- 已有环境执行一次：补齐订单权益快照，并约束同一渠道交易号只能入账一次。
ALTER TABLE payment_order
    ADD COLUMN duration_days INT NULL COMMENT '下单时的VIP套餐时长快照，单位：天' AFTER product_id;

UPDATE payment_order po
JOIN vip_product vp ON vp.id = po.product_id
SET po.duration_days = vp.duration_days
WHERE po.duration_days IS NULL;

ALTER TABLE payment_order
    MODIFY COLUMN duration_days INT NOT NULL COMMENT '下单时的VIP套餐时长快照，单位：天',
    ADD UNIQUE KEY uk_payment_transaction (payment_method, transaction_id);
