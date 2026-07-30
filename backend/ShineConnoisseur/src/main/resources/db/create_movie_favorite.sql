use shineconnoisseur;
CREATE TABLE `movie_favorite` (
                                  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                  `user_id` bigint NOT NULL COMMENT '用户ID',
                                  `movie_id` bigint NOT NULL COMMENT '电影ID',
                                  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
                                  PRIMARY KEY (`id`),
                                  UNIQUE KEY `uk_user_movie` (`user_id`,`movie_id`),
                                  KEY `idx_user_id` (`user_id`),
                                  KEY `idx_movie_id` (`movie_id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户电影收藏表'