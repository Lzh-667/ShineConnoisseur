USE shineconnoisseur;

DROP TABLE IF EXISTS `user_movie_relation`;

CREATE TABLE `user_movie_relation` (
                                       `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                                       `user_id`      BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
                                       `movie_id`     BIGINT UNSIGNED NOT NULL COMMENT '电影ID',
                                       `is_favorite`  TINYINT         NOT NULL DEFAULT 0 COMMENT '0-未收藏，1-已收藏',
                                       `tags`         VARCHAR(200)    DEFAULT NULL COMMENT '个人标签',
                                       `create_time`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                       `update_time`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

                                       PRIMARY KEY (`id`),
                                       UNIQUE KEY `uk_user_movie` (`user_id`, `movie_id`),
                                       KEY `idx_user_favorite` (`user_id`, `is_favorite`)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户与电影的关系';