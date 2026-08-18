USE game_flow;

-- 为已有任务表补充幂等、版本和重试字段。
ALTER TABLE gen_task
  ADD COLUMN idempotency_key varchar(128) DEFAULT NULL AFTER task_uuid,
  ADD COLUMN request_hash varchar(64) DEFAULT NULL AFTER idempotency_key,
  ADD COLUMN version int NOT NULL DEFAULT 0 AFTER request_hash,
  ADD COLUMN retry_count int NOT NULL DEFAULT 0 AFTER version;

-- 先回填旧数据，之后才能把新字段改为非空并建立唯一索引。
UPDATE gen_task
SET idempotency_key = CONCAT('legacy-', task_uuid),
    request_hash = SHA2(CONCAT('legacy-', task_uuid), 256)
WHERE idempotency_key IS NULL OR request_hash IS NULL;

ALTER TABLE gen_task
  MODIFY idempotency_key varchar(128) NOT NULL,
  MODIFY request_hash varchar(64) NOT NULL,
  ADD UNIQUE KEY uk_user_idempotency (user_id, idempotency_key),
  ADD KEY idx_user_create_time (user_id, create_time DESC),
  ADD KEY idx_status_update_time (status, update_time);
