USE game_flow;

-- 增加消费者租约字段，用于识别执行者和恢复超时任务。
ALTER TABLE gen_task
  ADD COLUMN worker_id varchar(64) DEFAULT NULL AFTER retry_count,
  ADD COLUMN lease_expire_time datetime DEFAULT NULL AFTER worker_id,
  ADD COLUMN last_heartbeat_time datetime DEFAULT NULL AFTER lease_expire_time,
  ADD KEY idx_status_lease_expire (status, lease_expire_time);

-- 增加事务 Outbox，解决任务落库与 MQ 发送无法使用同一事务的问题。
CREATE TABLE generation_outbox (
  id bigint NOT NULL AUTO_INCREMENT,
  event_id varchar(64) NOT NULL,
  task_uuid varchar(64) NOT NULL,
  trace_id varchar(64) DEFAULT NULL,
  event_type varchar(64) NOT NULL,
  payload text,
  status varchar(20) NOT NULL DEFAULT 'PENDING',
  retry_count int NOT NULL DEFAULT 0,
  next_attempt_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  locked_by varchar(64) DEFAULT NULL,
  locked_until datetime DEFAULT NULL,
  last_error varchar(500) DEFAULT NULL,
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  sent_time datetime DEFAULT NULL,
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_outbox_event_id (event_id),
  KEY idx_outbox_dispatch (status, next_attempt_time),
  KEY idx_outbox_task_uuid (task_uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
