-- 站内消息（顾客↔顾客 / 顾客↔商家 / 平台客服↔顾客）
-- 执行方式：mysql -uroot -p music_db < chat.sql

CREATE TABLE IF NOT EXISTS `chat_conversation` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `user_a_id`     BIGINT       NOT NULL COMMENT '会话双方中较小的用户ID',
  `user_b_id`     BIGINT       NOT NULL COMMENT '会话双方中较大的用户ID',
  `type`          TINYINT      NOT NULL DEFAULT 0 COMMENT '0私聊 1平台客服',
  `last_msg`      VARCHAR(500) DEFAULT NULL COMMENT '最后一条消息摘要',
  `last_msg_time` DATETIME     DEFAULT NULL COMMENT '最后消息时间，用于排序',
  `create_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pair` (`user_a_id`, `user_b_id`),
  KEY `idx_b` (`user_b_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天会话';

CREATE TABLE IF NOT EXISTS `chat_message` (
  `id`              BIGINT   NOT NULL AUTO_INCREMENT,
  `conversation_id` BIGINT   NOT NULL,
  `sender_id`       BIGINT   NOT NULL,
  `receiver_id`     BIGINT   NOT NULL,
  `client_msg_id`   VARCHAR(64) DEFAULT NULL COMMENT '客户端消息ID，用于发送重试幂等',
  `content`         TEXT     NOT NULL COMMENT '文本内容或图片URL',
  `type`            TINYINT  NOT NULL DEFAULT 0 COMMENT '0文本 1图片',
  `is_read`         TINYINT  NOT NULL DEFAULT 0 COMMENT '0未读 1已读',
  `create_time`     DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sender_client` (`sender_id`, `client_msg_id`),
  KEY `idx_conv` (`conversation_id`, `id`),
  KEY `idx_receiver_read` (`receiver_id`, `is_read`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天消息';

-- 已经建过 chat_message 的库，执行下面这条补字段（新库不用执行）：
-- ALTER TABLE `chat_message`
--   ADD COLUMN `client_msg_id` VARCHAR(64) DEFAULT NULL COMMENT '客户端消息ID，用于发送重试幂等' AFTER `receiver_id`,
--   ADD UNIQUE KEY `uk_sender_client` (`sender_id`, `client_msg_id`);
