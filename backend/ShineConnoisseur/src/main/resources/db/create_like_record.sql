USE shineconnoisseur;
CREATE TABLE `like_record` (
                               `id` bigint NOT NULL AUTO_INCREMENT,
                               `user_id` bigint NOT NULL COMMENT '用户ID',
                               `target_id` bigint NOT NULL COMMENT '目标ID（评论ID或影评ID）',
                               `target_type` tinyint NOT NULL COMMENT '类型：1评论 2影评',
                               `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                               `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                               PRIMARY KEY (`id`),
                               UNIQUE KEY `uk_user_target` (`user_id`,`target_id`,`target_type`),
                               KEY `idx_target` (`target_id`,`target_type`),
                               KEY `idx_user` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=14 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='点赞记录表（评论/影评通用）'