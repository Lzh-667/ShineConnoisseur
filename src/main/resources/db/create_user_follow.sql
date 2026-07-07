-- ============================================
-- 数据库：shineconnoisseur
-- 表：user_follow（用户关注关系表）
-- ============================================

USE shineconnoisseur;

DROP TABLE IF EXISTS `user_follow`;

CREATE TABLE `user_follow` (
                               `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                               `user_id`         BIGINT UNSIGNED NOT NULL COMMENT '关注者ID',
                               `follow_user_id`  BIGINT UNSIGNED NOT NULL COMMENT '被关注者ID',
                               `create_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '关注时间',

                               PRIMARY KEY (`id`),
                               UNIQUE KEY `uk_user_follow` (`user_id`, `follow_user_id`),
                               KEY `idx_follow_user_id` (`follow_user_id`)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户关注关系表';