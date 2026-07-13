use shineconnoisseur;
CREATE TABLE `message` (
                           `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '消息id',

                           `user_id` BIGINT NOT NULL COMMENT '接收消息的用户id',

                           `from_user_id` BIGINT NOT NULL COMMENT '触发消息的用户id',

                           `type` INT NOT NULL COMMENT '消息类型 0-关注 1-点赞影评 2-评论影评 3-点赞评论 4-回复评论',

                           `target_type` INT NOT NULL COMMENT '目标类型 0-用户 1-影评 2-评论',

                           `target_id` BIGINT NOT NULL COMMENT '关联业务id',

                           `content` VARCHAR(255) DEFAULT NULL COMMENT '消息内容',

                           `status` INT NOT NULL DEFAULT 0 COMMENT '阅读状态 0-未读 1-已读',

                           `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

                           `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

                           PRIMARY KEY (`id`),

                           KEY `idx_user_id` (`user_id`),

                           KEY `idx_from_user_id` (`from_user_id`),

                           KEY `idx_create_time` (`create_time`),

                           KEY `idx_user_status` (`user_id`, `status`)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息通知表';