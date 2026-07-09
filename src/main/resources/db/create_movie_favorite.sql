use shineconnoisseur;
CREATE TABLE movie_favorite (
                                     id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                                     user_id BIGINT NOT NULL COMMENT '用户ID',
                                     movie_id BIGINT NOT NULL COMMENT '电影ID',
                                     create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
                                     UNIQUE KEY uk_user_movie(user_id,movie_id),

                                     KEY idx_user_id (user_id),
                                     KEY idx_movie_id (movie_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户电影收藏表';