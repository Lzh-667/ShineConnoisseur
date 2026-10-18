-- Run against the configured application database before deploying the new backend.
CREATE TABLE IF NOT EXISTS message_outbox (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    payload JSON NOT NULL,
    dedup_key CHAR(64) DEFAULT NULL,
    routing_key VARCHAR(100) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    lease_token VARCHAR(36) DEFAULT NULL,
    last_error VARCHAR(1000) DEFAULT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    sent_at DATETIME(6) DEFAULT NULL,
    KEY idx_outbox_due (status, next_attempt_at, id),
    UNIQUE KEY uk_outbox_dedup (dedup_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Upgrade installations that already ran the first Outbox migration.
SET @outbox_has_dedup = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'message_outbox' AND column_name = 'dedup_key');
SET @outbox_upgrade_sql = IF(@outbox_has_dedup > 0, 'SELECT 1',
    'ALTER TABLE message_outbox ADD COLUMN dedup_key CHAR(64) DEFAULT NULL');
PREPARE outbox_upgrade_stmt FROM @outbox_upgrade_sql;
EXECUTE outbox_upgrade_stmt;
DEALLOCATE PREPARE outbox_upgrade_stmt;
SET @outbox_has_index = (SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'message_outbox' AND index_name = 'uk_outbox_dedup');
SET @outbox_upgrade_sql = IF(@outbox_has_index > 0, 'SELECT 1',
    'ALTER TABLE message_outbox ADD UNIQUE KEY uk_outbox_dedup (dedup_key)');
PREPARE outbox_upgrade_stmt FROM @outbox_upgrade_sql;
EXECUTE outbox_upgrade_stmt;
DEALLOCATE PREPARE outbox_upgrade_stmt;

CREATE TABLE IF NOT EXISTS message_failure (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    source_key CHAR(64) NOT NULL,
    source_queue VARCHAR(255) NOT NULL,
    message_id VARCHAR(255) DEFAULT NULL,
    body LONGBLOB NOT NULL,
    status VARCHAR(16) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    replayed_at DATETIME(6) DEFAULT NULL,
    UNIQUE KEY uk_failure_source (source_key),
    KEY idx_failure_status (status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Existing installations may predate business-key deduplication.
-- If historical duplicates exist, ADD UNIQUE fails; review those rows before migration.
-- Never silently delete historical notifications in a migration.
SET @message_unique_exists = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'message'
      AND index_name = 'uk_biz_key' AND non_unique = 0
);
SET @message_unique_sql = IF(@message_unique_exists > 0, 'SELECT 1',
    'ALTER TABLE message ADD UNIQUE KEY uk_biz_key (user_id, from_user_id, type, target_type, target_id)');
PREPARE message_unique_stmt FROM @message_unique_sql;
EXECUTE message_unique_stmt;
DEALLOCATE PREPARE message_unique_stmt;
