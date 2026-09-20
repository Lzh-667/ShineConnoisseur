USE shineconnoisseur;
CREATE TABLE payment_order (
                               id BIGINT NOT NULL AUTO_INCREMENT COMMENT '订单ID',
                               order_no VARCHAR(32) NOT NULL COMMENT '业务订单号',
                               request_id VARCHAR(64) NOT NULL COMMENT '客户端创建订单幂等键',
                               user_id BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
                               product_id BIGINT NOT NULL COMMENT 'VIP套餐ID',
                               duration_days INT NOT NULL COMMENT '下单时的VIP套餐时长快照，单位：天',
                               amount DECIMAL(10,2) NOT NULL COMMENT '订单金额',
                               payment_method TINYINT NOT NULL COMMENT '支付方式：1支付宝，2微信',
                               status TINYINT NOT NULL DEFAULT 0 COMMENT '订单状态：0待发起，1支付成功，2已关闭，3已退款，4发起中，5待支付，6关单中，7退款中',
                               transaction_id VARCHAR(64) DEFAULT NULL COMMENT '第三方支付交易号',
                               payment_payload MEDIUMTEXT DEFAULT NULL COMMENT '支付页面HTML或二维码URL',
                               expire_time DATETIME NOT NULL COMMENT '订单支付截止时间',
                               pay_time DATETIME DEFAULT NULL COMMENT '支付成功时间',
                               create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                               update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

                               PRIMARY KEY (id),
                               UNIQUE KEY uk_order_no (order_no),
                               UNIQUE KEY uk_user_request (user_id, request_id),
                               UNIQUE KEY uk_payment_transaction (payment_method, transaction_id),
                               KEY idx_user_id (user_id),
                               KEY idx_product_id (product_id),
                               KEY idx_status_expire_time (status, expire_time),

                               CONSTRAINT fk_payment_order_user
                                   FOREIGN KEY (user_id) REFERENCES user(id),

                               CONSTRAINT fk_payment_order_product
                                   FOREIGN KEY (product_id) REFERENCES vip_product(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付订单表';
