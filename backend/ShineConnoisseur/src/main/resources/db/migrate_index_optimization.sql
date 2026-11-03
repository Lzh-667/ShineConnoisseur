-- 索引优化迁移，适用于 MySQL 8.0；可重复执行，无需先运行分页索引迁移。
-- 执行前备份并暂停业务写入；客户端不得使用 --force，遇错应停止。
-- 重复影评会中止迁移，不自动删除或合并用户数据。DDL 会隐式提交，失败后修复原因再重跑。
USE shineconnoisseur;

DELIMITER $$
DROP PROCEDURE IF EXISTS sc_index_ensure_v4$$
CREATE PROCEDURE sc_index_ensure_v4(IN p_table VARCHAR(64), IN p_name VARCHAR(64),
    IN p_columns VARCHAR(512), IN p_signature VARCHAR(512), IN p_unique BOOLEAN)
BEGIN
    DECLARE actual_signature TEXT;
    DECLARE actual_non_unique INT;
    DECLARE error_text VARCHAR(128);
    SELECT GROUP_CONCAT(CONCAT(column_name, ':', collation,
               IF(sub_part IS NULL, '', CONCAT(':', sub_part))) ORDER BY seq_in_index SEPARATOR ','),
           MIN(non_unique)
      INTO actual_signature, actual_non_unique
      FROM information_schema.statistics
     WHERE table_schema = DATABASE() AND table_name = p_table AND index_name = p_name;
    IF actual_signature IS NULL THEN
        SET @sc_index_ddl = CONCAT('ALTER TABLE `', p_table, '` ADD ',
            IF(p_unique, 'UNIQUE ', ''), 'INDEX `', p_name, '` (', p_columns, ')');
        PREPARE sc_index_statement FROM @sc_index_ddl;
        EXECUTE sc_index_statement;
        DEALLOCATE PREPARE sc_index_statement;
    ELSEIF actual_signature <> p_signature OR actual_non_unique <> IF(p_unique, 0, 1) THEN
        SET error_text = CONCAT('索引定义不一致，请人工核对：', p_table, '.', p_name);
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = error_text;
    END IF;
END$$

DROP PROCEDURE IF EXISTS sc_index_drop_v4$$
CREATE PROCEDURE sc_index_drop_v4(IN p_table VARCHAR(64), IN p_name VARCHAR(64),
    IN p_signature VARCHAR(512), IN p_unique BOOLEAN)
BEGIN
    DECLARE actual_signature TEXT;
    DECLARE actual_non_unique INT;
    DECLARE error_text VARCHAR(128);
    SELECT GROUP_CONCAT(CONCAT(column_name, ':', collation,
               IF(sub_part IS NULL, '', CONCAT(':', sub_part))) ORDER BY seq_in_index SEPARATOR ','),
           MIN(non_unique)
      INTO actual_signature, actual_non_unique
      FROM information_schema.statistics
     WHERE table_schema = DATABASE() AND table_name = p_table AND index_name = p_name;
    IF actual_signature IS NOT NULL THEN
        IF actual_signature <> p_signature OR actual_non_unique <> IF(p_unique, 0, 1) THEN
            SET error_text = CONCAT('拒绝删除定义不一致的索引：', p_table, '.', p_name);
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = error_text;
        END IF;
        SET @sc_index_ddl = CONCAT('ALTER TABLE `', p_table, '` DROP INDEX `', p_name, '`');
        PREPARE sc_index_statement FROM @sc_index_ddl;
        EXECUTE sc_index_statement;
        DEALLOCATE PREPARE sc_index_statement;
    END IF;
END$$

DROP PROCEDURE IF EXISTS sc_index_optimize_v4$$
CREATE PROCEDURE sc_index_optimize_v4()
BEGIN
    DECLARE review_signature TEXT;
    DECLARE review_non_unique INT;
    -- 必须先检查跨状态重复记录，避免在检查失败前修改任何业务表索引。
    IF EXISTS (SELECT 1 FROM review GROUP BY user_id, movie_id HAVING COUNT(*) > 1) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '存在同一用户、电影的多条影评，请人工处理后重试索引迁移';
    END IF;

    SELECT GROUP_CONCAT(CONCAT(column_name, ':', collation,
               IF(sub_part IS NULL, '', CONCAT(':', sub_part))) ORDER BY seq_in_index SEPARATOR ','),
           MIN(non_unique)
      INTO review_signature, review_non_unique
      FROM information_schema.statistics
     WHERE table_schema = DATABASE() AND table_name = 'review' AND index_name = 'uk_user_movie';
    IF review_signature = 'user_id:A,movie_id:A,status:A' AND review_non_unique = 0 THEN
        -- 同一条 ALTER 原子替换唯一约束，避免出现没有唯一保护的窗口。
        ALTER TABLE review DROP INDEX uk_user_movie, ADD UNIQUE INDEX uk_user_movie (user_id, movie_id);
    ELSE
        CALL sc_index_ensure_v4('review', 'uk_user_movie', 'user_id, movie_id', 'user_id:A,movie_id:A', TRUE);
    END IF;

    -- 先建立全部替代索引，再删除旧索引；同时校验同名索引的列顺序和方向。
    CALL sc_index_ensure_v4('review_comment', 'idx_comment_root_seek', 'review_id, status, reply_user_id, like_count DESC, create_time DESC, id DESC', 'review_id:A,status:A,reply_user_id:A,like_count:D,create_time:D,id:D', FALSE);
    CALL sc_index_ensure_v4('review_comment', 'idx_comment_child_seek', 'root_id, status, create_time DESC, id DESC', 'root_id:A,status:A,create_time:D,id:D', FALSE);
    CALL sc_index_ensure_v4('review_comment', 'idx_comment_user_seek', 'user_id, status, create_time DESC, id DESC', 'user_id:A,status:A,create_time:D,id:D', FALSE);
    CALL sc_index_ensure_v4('review', 'idx_review_user_seek', 'user_id, status, like_count DESC, id DESC', 'user_id:A,status:A,like_count:D,id:D', FALSE);
    CALL sc_index_ensure_v4('review', 'idx_review_movie_page', 'movie_id, status, like_count DESC, create_time DESC, id DESC', 'movie_id:A,status:A,like_count:D,create_time:D,id:D', FALSE);
    CALL sc_index_ensure_v4('review', 'idx_review_sync_seek', 'update_time, id', 'update_time:A,id:A', FALSE);
    CALL sc_index_ensure_v4('message', 'idx_message_user_page', 'user_id, create_time DESC, id DESC', 'user_id:A,create_time:D,id:D', FALSE);
    CALL sc_index_ensure_v4('message', 'idx_message_type_page', 'user_id, type, create_time DESC, id DESC', 'user_id:A,type:A,create_time:D,id:D', FALSE);
    CALL sc_index_ensure_v4('movie_favorite', 'idx_favorite_user_page', 'user_id, create_time DESC, id DESC', 'user_id:A,create_time:D,id:D', FALSE);
    CALL sc_index_ensure_v4('user_follow', 'idx_following_page', 'user_id, create_time DESC, id DESC', 'user_id:A,create_time:D,id:D', FALSE);
    CALL sc_index_ensure_v4('user_follow', 'idx_followers_page', 'follow_user_id, create_time DESC, id DESC', 'follow_user_id:A,create_time:D,id:D', FALSE);
    CALL sc_index_ensure_v4('payment_order', 'idx_order_status_update', 'status, update_time, id', 'status:A,update_time:A,id:A', FALSE);
    CALL sc_index_ensure_v4('payment_order', 'idx_order_user_paid', 'user_id, pay_time DESC, id DESC', 'user_id:A,pay_time:D,id:D', FALSE);
    CALL sc_index_ensure_v4('movie', 'idx_movie_hot', 'status, rating_count DESC, rating_sum DESC, release_date DESC', 'status:A,rating_count:D,rating_sum:D,release_date:D', FALSE);

    CALL sc_index_drop_v4('like_record', 'idx_user', 'user_id:A', FALSE);
    CALL sc_index_drop_v4('movie_favorite', 'idx_user_id', 'user_id:A', FALSE);
    CALL sc_index_drop_v4('message', 'idx_user_id', 'user_id:A', FALSE);
    CALL sc_index_drop_v4('review', 'idx_user_id', 'user_id:A', FALSE);
    CALL sc_index_drop_v4('review', 'idx_movie_id', 'movie_id:A', FALSE);
    CALL sc_index_drop_v4('review_comment', 'idx_review_id', 'review_id:A', FALSE);
    CALL sc_index_drop_v4('review_comment', 'idx_root_id', 'root_id:A', FALSE);
    CALL sc_index_drop_v4('review_comment', 'idx_user_id', 'user_id:A', FALSE);
    CALL sc_index_drop_v4('user_follow', 'idx_follow_user_id', 'follow_user_id:A', FALSE);
    CALL sc_index_drop_v4('movie', 'idx_status_rating', 'status:A,rating_sum:A', FALSE);
    -- 兼容旧导出文件中使用的唯一键名称。
    CALL sc_index_drop_v4('review', 'idx_user_movie', 'user_id:A,movie_id:A', TRUE);
END$$
DELIMITER ;

CALL sc_index_optimize_v4();
DROP PROCEDURE sc_index_optimize_v4;
DROP PROCEDURE sc_index_drop_v4;
DROP PROCEDURE sc_index_ensure_v4;
