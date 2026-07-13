-- ============================================
-- 数据库：shineconnoisseur
-- 表：review（影评表）
-- ============================================

USE shineconnoisseur;

CREATE TABLE `review` (
                          `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '影评ID',
                          `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
                          `movie_id` bigint unsigned NOT NULL COMMENT '电影ID',
                          `rating` tinyint NOT NULL COMMENT '评分 1-10',
                          `title` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '影评标题',
                          `content` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '影评内容',
                          `spoiler` tinyint NOT NULL DEFAULT '0' COMMENT '是否剧透：0-否，1-是',
                          `like_count` int unsigned NOT NULL DEFAULT '0' COMMENT '点赞数',
                          `comment_count` int unsigned NOT NULL DEFAULT '0' COMMENT '评论数',
                          `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0-删除，1-正常，2-审核中',
                          `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                          PRIMARY KEY (`id`),
                          UNIQUE KEY `uk_user_movie` (`user_id`,`movie_id`),
                          KEY `idx_movie_id` (`movie_id`),
                          KEY `idx_user_id` (`user_id`),
                          KEY `idx_status_create_time` (`status`,`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=23 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='影评表'