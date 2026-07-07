USE shineconnoisseur;

CREATE TABLE `user` (
                        `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用户ID',
                        `username`        VARCHAR(32)     NOT NULL COMMENT '用户名（登录用）',
                        `password`        VARCHAR(128)    NOT NULL COMMENT '加密密码',
                        `email`           VARCHAR(64)     DEFAULT NULL COMMENT '邮箱',
                        `phone`           VARCHAR(20)     DEFAULT NULL COMMENT '手机号',
                        `nickname`        VARCHAR(32)     DEFAULT NULL COMMENT '昵称',
                        `avatar`          VARCHAR(255)    DEFAULT NULL COMMENT '头像URL',
                        `gender`          TINYINT         DEFAULT 0 COMMENT '性别：0-保密，1-男，2-女',
                        `bio`             VARCHAR(200)    DEFAULT NULL COMMENT '个人简介',
                        `status`          TINYINT         NOT NULL DEFAULT 1 COMMENT '状态：0-禁用，1-正常',
                        `review_count`    INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '影评数',
                        `follower_count`  INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '粉丝数',
                        `following_count` INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '关注数',
                        `create_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
                        `update_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                        `last_login_time` DATETIME        DEFAULT NULL COMMENT '最后登录时间',

                        PRIMARY KEY (`id`),
                        UNIQUE KEY `uk_username` (`username`),
                        UNIQUE KEY `uk_email` (`email`),
                        UNIQUE KEY `uk_phone` (`phone`),
                        KEY `idx_status_create_time` (`status`, `create_time`)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';