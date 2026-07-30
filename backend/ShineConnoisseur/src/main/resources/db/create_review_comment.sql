USE shineconnoisseur;

CREATE TABLE `review_comment` (
                                  `id` bigint NOT NULL AUTO_INCREMENT,
                                  `user_id` bigint NOT NULL COMMENT '用户ID',
                                  `review_id` bigint NOT NULL COMMENT '影评ID',
                                  `root_id` bigint DEFAULT '0' COMMENT '一级评论ID，0表示一级评论',
                                  `reply_user_id` bigint DEFAULT '0' COMMENT '被回复用户ID',
                                  `content` varchar(1000) NOT NULL COMMENT '评论内容',
                                  `like_count` int NOT NULL DEFAULT '0' COMMENT '点赞数',
                                  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0-用户删除，1-正常，2-管理员封禁',
                                  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                  PRIMARY KEY (`id`),
                                  KEY `idx_review_id` (`review_id`),
                                  KEY `idx_root_id` (`root_id`),
                                  KEY `idx_user_id` (`user_id`),
                                  KEY `idx_review_root` (`review_id`,`root_id`)
) ENGINE=InnoDB AUTO_INCREMENT=32 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='影评评论表'