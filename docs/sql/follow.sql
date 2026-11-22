-- 用户关注
-- 执行方式：mysql -uroot -p music_db < follow.sql

CREATE TABLE IF NOT EXISTS `user_follow` (
  `id`          BIGINT   NOT NULL AUTO_INCREMENT,
  `follower_id` BIGINT   NOT NULL COMMENT '发起关注的人',
  `followee_id` BIGINT   NOT NULL COMMENT '被关注的人',
  `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_follower_followee` (`follower_id`, `followee_id`),
  KEY `idx_followee_created` (`followee_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户关注关系';

-- 新库如果 user 表还没有计数列，取消下面注释执行一次：
-- ALTER TABLE `user`
--   ADD COLUMN `following_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '关注数',
--   ADD COLUMN `follower_count`  INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '粉丝数';
