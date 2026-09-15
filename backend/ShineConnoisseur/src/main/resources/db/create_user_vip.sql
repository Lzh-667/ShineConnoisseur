USE shineconnoisseur;
CREATE TABLE user_vip (
                          id BIGINT NOT NULL AUTO_INCREMENT COMMENT '会员记录ID',
                          user_id BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
                          start_time DATETIME NOT NULL COMMENT '会员开始时间',
                          expire_time DATETIME NOT NULL COMMENT '会员到期时间',
                          create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                          update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

                          PRIMARY KEY (id),
                          UNIQUE KEY uk_user_id (user_id),
                          KEY idx_expire_time (expire_time),

                          CONSTRAINT fk_user_vip_user
                              FOREIGN KEY (user_id) REFERENCES user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户会员表';