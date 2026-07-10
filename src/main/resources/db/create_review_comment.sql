USE shineconnoisseur;
CREATE TABLE review_comment (
                                id BIGINT PRIMARY KEY AUTO_INCREMENT,

                                user_id BIGINT NOT NULL COMMENT '用户ID',
                                review_id BIGINT NOT NULL COMMENT '影评ID',

                                root_id BIGINT DEFAULT 0 COMMENT '一级评论ID，0表示一级评论',

                                reply_user_id BIGINT DEFAULT NULL COMMENT '被回复用户ID',

                                content VARCHAR(1000) NOT NULL COMMENT '评论内容',

                                like_count INT NOT NULL DEFAULT 0 COMMENT '点赞数',

                                status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1正常 0删除',

                                create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

                                update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

                                INDEX idx_review_id (review_id),
                                INDEX idx_parent_id (root_id),
                                INDEX idx_user_id (user_id),

                                INDEX idx_review_parent (review_id, root_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='影评评论表';