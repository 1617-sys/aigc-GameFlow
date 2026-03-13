-- 创建数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS game_flow DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE game_flow;

-- 创建用户表
CREATE TABLE IF NOT EXISTS `sys_user` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '用户 ID',
  `username` varchar(50) NOT NULL COMMENT '用户名',
  `password` varchar(255) NOT NULL COMMENT '密码 (加密)',
  `balance` int(11) DEFAULT '10' COMMENT '余额/积分',
  `role` varchar(20) DEFAULT 'USER' COMMENT '角色：USER, ADMIN',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除：0-未删除，1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

-- 创建生成任务表
CREATE TABLE IF NOT EXISTS `gen_task` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '任务 ID',
  `task_uuid` varchar(64) NOT NULL COMMENT '任务 UUID',
  `prompt` varchar(1000) DEFAULT NULL COMMENT '中文提示词',
  `prompt_en` varchar(2000) DEFAULT NULL COMMENT '英文提示词',
  `status` tinyint(4) DEFAULT '0' COMMENT '状态：0-排队，1-生成中，2-成功，3-失败',
  `image_url` varchar(500) DEFAULT NULL COMMENT '图片 URL',
  `parameters` text COMMENT 'JSON 参数',
  `error_msg` varchar(500) DEFAULT NULL COMMENT '错误消息',
  `user_id` bigint(20) DEFAULT NULL COMMENT '用户 ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除：0-未删除，1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_uuid` (`task_uuid`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='生成任务表';

-- 插入测试用户（密码是 123456，使用 BCrypt 加密）
-- 注意：BCrypt 每次加密结果不同，这里是示例
INSERT INTO `sys_user` (`username`, `password`, `balance`, `role`) 
VALUES ('test', '$2a$10$N.zmdr9k7uOQoYvOz5.F4OKJqRJOm0m4hL0.vGj5xJxWlZzJ5Z5Z5', 100, 'USER')
ON DUPLICATE KEY UPDATE username=username;

-- 验证数据
SELECT * FROM sys_user;
SELECT * FROM gen_task;
