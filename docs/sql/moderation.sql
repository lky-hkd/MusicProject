-- 内容审核审计表
CREATE TABLE IF NOT EXISTS `moderation_audit` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `content_type` VARCHAR(16) NOT NULL COMMENT 'POST/COMMENT/REVIEW/NICKNAME/SIGNATURE/TEXT',
  `content_id` BIGINT NULL COMMENT '内容主键，创建前可为空',
  `user_id` BIGINT NULL,
  `content_hash` CHAR(64) NOT NULL COMMENT '内容SHA-256',
  `snippet` VARCHAR(200) NULL COMMENT '内容摘要',
  `stage` VARCHAR(16) NOT NULL COMMENT 'RULE/MODEL/FALLBACK',
  `action` VARCHAR(16) NOT NULL COMMENT 'ALLOW/REVIEW/BLOCK',
  `hits` VARCHAR(500) NULL COMMENT '命中的敏感词',
  `model_decision` VARCHAR(16) NULL,
  `confidence` DECIMAL(4,3) NULL,
  `reason` VARCHAR(500) NULL,
  `latency_ms` INT NULL,
  `sampled` TINYINT NOT NULL DEFAULT 0 COMMENT '是否异步抽检',
  `handled` TINYINT NOT NULL DEFAULT 0 COMMENT '异步命中后是否已下架',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_action` (`action`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_content` (`content_type`, `content_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='内容审核审计';

-- 待审内容表：昵称、个性签名这类没有独立业务表的内容，可疑时先存这里，管理员通过后写回 user 表
CREATE TABLE IF NOT EXISTS `moderation_pending` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `content_type` VARCHAR(16) NOT NULL COMMENT 'NICKNAME/SIGNATURE',
  `user_id` BIGINT NOT NULL,
  `content` VARCHAR(500) NOT NULL,
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0待审 1通过 2驳回',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NULL,
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`),
  KEY `idx_user` (`user_id`, `content_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='待审内容';

-- 私聊撤回：0 正常 / 1 违规撤回（每个库只需执行一次）
ALTER TABLE `chat_message` ADD COLUMN `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0正常 1违规撤回';

-- 违规阶梯封禁：多次违规后写入截止时间（每个库只需执行一次）
ALTER TABLE `user` ADD COLUMN `banned_until` DATETIME NULL COMMENT '封禁截止时间';

-- 曲库上下架：0 上架 / 1 已下架（每个库只需执行一次）
ALTER TABLE `song` ADD COLUMN `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0上架 1已下架';
