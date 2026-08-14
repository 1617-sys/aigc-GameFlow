CREATE DATABASE IF NOT EXISTS game_flow DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE game_flow;

CREATE TABLE IF NOT EXISTS sys_user (
  id bigint NOT NULL AUTO_INCREMENT,
  username varchar(50) NOT NULL,
  password varchar(255) NOT NULL,
  balance int NOT NULL DEFAULT 10,
  role varchar(20) NOT NULL DEFAULT 'USER',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_deleted tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS gen_task (
  id bigint NOT NULL AUTO_INCREMENT,
  task_uuid varchar(64) NOT NULL,
  idempotency_key varchar(128) NOT NULL,
  request_hash varchar(64) NOT NULL,
  version int NOT NULL DEFAULT 0,
  retry_count int NOT NULL DEFAULT 0,
  worker_id varchar(64) DEFAULT NULL,
  lease_expire_time datetime DEFAULT NULL,
  last_heartbeat_time datetime DEFAULT NULL,
  prompt varchar(2000) NOT NULL,
  prompt_en varchar(2000) DEFAULT NULL,
  negative_prompt varchar(1000) DEFAULT NULL,
  status tinyint NOT NULL DEFAULT 0,
  provider varchar(32) DEFAULT NULL,
  model varchar(100) DEFAULT NULL,
  size varchar(32) DEFAULT NULL,
  quality varchar(32) DEFAULT NULL,
  provider_job_id varchar(128) DEFAULT NULL,
  image_url varchar(500) DEFAULT NULL,
  parameters text,
  error_msg varchar(500) DEFAULT NULL,
  user_id bigint NOT NULL,
  source_app varchar(100) DEFAULT NULL,
  external_run_id varchar(128) DEFAULT NULL,
  callback_url varchar(500) DEFAULT NULL,
  callback_status varchar(32) DEFAULT NULL,
  callback_error varchar(500) DEFAULT NULL,
  latency_ms bigint DEFAULT NULL,
  trace_id varchar(64) NOT NULL,
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_deleted tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_task_uuid (task_uuid),
  UNIQUE KEY uk_user_idempotency (user_id, idempotency_key),
  KEY idx_user_create_time (user_id, create_time DESC),
  KEY idx_status_update_time (status, update_time),
  KEY idx_status_lease_expire (status, lease_expire_time),
  KEY idx_trace_id (trace_id),
  KEY idx_external_run_id (external_run_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS generation_outbox (
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

CREATE TABLE IF NOT EXISTS generation_event (
  id bigint NOT NULL AUTO_INCREMENT,
  task_uuid varchar(64) NOT NULL,
  trace_id varchar(64) DEFAULT NULL,
  event_type varchar(64) NOT NULL,
  message varchar(500) DEFAULT NULL,
  payload text,
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_task_create_time (task_uuid, create_time),
  KEY idx_event_trace_id (trace_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
