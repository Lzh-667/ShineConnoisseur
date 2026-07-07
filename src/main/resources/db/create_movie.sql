-- ============================================
-- 数据库：shineconnoisseur
-- 表：movie（电影表）
-- ============================================

USE shineconnoisseur;

DROP TABLE IF EXISTS `movie`;

CREATE TABLE `movie` (
                         `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '电影ID',
                         `title`           VARCHAR(128)    NOT NULL COMMENT '电影名称',
                         `original_title`  VARCHAR(128)    DEFAULT NULL COMMENT '原名（外文）',
                         `cover`           VARCHAR(255)    DEFAULT NULL COMMENT '海报URL',
                         `director`        VARCHAR(200)    DEFAULT NULL COMMENT '导演，逗号分隔',
                         `actors`          VARCHAR(500)    DEFAULT NULL COMMENT '演员，逗号分隔',
                         `genre`           VARCHAR(100)    DEFAULT NULL COMMENT '类型，逗号分隔（剧情/喜剧/科幻）',
                         `region`          VARCHAR(50)     DEFAULT NULL COMMENT '地区',
                         `language`        VARCHAR(50)     DEFAULT NULL COMMENT '语言',
                         `release_date`    DATE            DEFAULT NULL COMMENT '上映日期',
                         `duration`        INT UNSIGNED    DEFAULT NULL COMMENT '片长（分钟）',
                         `summary`         TEXT            DEFAULT NULL COMMENT '剧情简介',
                         `rating`          DECIMAL(2,1)    DEFAULT NULL COMMENT '系统评分（1-10）',
                         `rating_count`    INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '评分人数',
                         `status`          TINYINT         NOT NULL DEFAULT 1 COMMENT '状态：0-下架，1-上架',
                         `create_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         `update_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

                         PRIMARY KEY (`id`),
                         KEY `idx_title` (`title`),
                         KEY `idx_genre` (`genre`(50)),
                         KEY `idx_status_rating` (`status`, `rating`),
                         KEY `idx_release_date` (`release_date`)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='电影表';