USE shineconnoisseur;
CREATE TABLE vip_product (
                             id BIGINT NOT NULL AUTO_INCREMENT COMMENT '套餐ID',
                             name VARCHAR(50) NOT NULL COMMENT '套餐名称',
                             duration_days INT NOT NULL COMMENT '会员时长，单位：天',
                             price DECIMAL(10,2) NOT NULL COMMENT '套餐价格',
                             status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0下架，1上架',
                             create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                             update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

                             PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='VIP套餐表';