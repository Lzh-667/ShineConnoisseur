-- ============================================
-- 数据库：shineconnoisseur
-- 表：movie（电影表）
-- ============================================

USE shineconnoisseur;

CREATE TABLE `movie` (
                         `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '电影ID',
                         `title` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '电影名称',
                         `original_title` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '原名（外文）',
                         `cover` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '海报URL',
                         `director` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '导演，逗号分隔',
                         `actors` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '演员，逗号分隔',
                         `genre` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '类型，逗号分隔（剧情/喜剧/科幻）',
                         `region` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '地区',
                         `language` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '语言',
                         `release_date` date DEFAULT NULL COMMENT '上映日期',
                         `duration` int unsigned DEFAULT NULL COMMENT '片长（分钟）',
                         `summary` text COLLATE utf8mb4_unicode_ci COMMENT '剧情简介',
                         `rating_sum` decimal(10,1) NOT NULL DEFAULT '0.0' COMMENT '评分总分',
                         `rating_count` int unsigned NOT NULL DEFAULT '0' COMMENT '评分人数',
                         `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0-下架，1-上架',
                         `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                         PRIMARY KEY (`id`),
                         KEY `idx_title` (`title`),
                         KEY `idx_genre` (`genre`(50)),
                         KEY `idx_status_rating` (`status`,`rating_sum`),
                         KEY `idx_release_date` (`release_date`)
) ENGINE=InnoDB AUTO_INCREMENT=21 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='电影表'