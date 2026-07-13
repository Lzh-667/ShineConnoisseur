USE shineconnoisseur;

CREATE TABLE `user` (
                        `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '用户ID',
                        `username` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户名（登录用）',
                        `password` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '加密密码',
                        `email` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '邮箱',
                        `phone` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '手机号',
                        `nickname` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '昵称',
                        `avatar` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '头像URL',
                        `gender` tinyint DEFAULT '0' COMMENT '性别：0-保密，1-男，2-女',
                        `bio` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '个人简介',
                        `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0-禁用，1-正常',
                        `review_count` int unsigned NOT NULL DEFAULT '0' COMMENT '影评数',
                        `follower_count` int unsigned NOT NULL DEFAULT '0' COMMENT '粉丝数',
                        `following_count` int unsigned NOT NULL DEFAULT '0' COMMENT '关注数',
                        `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
                        `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                        `last_login_time` datetime DEFAULT NULL COMMENT '最后登录时间',
                        PRIMARY KEY (`id`),
                        UNIQUE KEY `uk_username` (`username`),
                        UNIQUE KEY `uk_phone` (`phone`),
                        UNIQUE KEY `uk_email` (`email`),
                        KEY `idx_status_create_time` (`status`,`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=32 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表'