USE shineconnoisseur;
CREATE TABLE like_record (
                             id BIGINT PRIMARY KEY AUTO_INCREMENT,

                             user_id BIGINT NOT NULL COMMENT '用户ID',

                             target_id BIGINT NOT NULL COMMENT '目标ID（评论ID或影评ID）',

                             target_type TINYINT NOT NULL COMMENT '类型：1评论 2影评',

                             create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

                             update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    -- 防止重复点赞（非常重要）
                             UNIQUE KEY uk_user_target (user_id, target_id, target_type),

                             INDEX idx_target (target_id, target_type),
                             INDEX idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='点赞记录表（评论/影评通用）';