-- 动态（微博式讨论发言）一期
-- 执行方式：mysql -uroot -p music_db < post.sql

CREATE TABLE IF NOT EXISTS `post` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`       BIGINT       NOT NULL COMMENT '作者',
  `content`       VARCHAR(1000) NOT NULL COMMENT '正文',
  `images`        JSON         DEFAULT NULL COMMENT '图片URL数组，最多9张',
  `like_count`    INT UNSIGNED NOT NULL DEFAULT 0,
  `comment_count` INT UNSIGNED NOT NULL DEFAULT 0,
  `status`        TINYINT      NOT NULL DEFAULT 0 COMMENT '0正常 1已删除 2审核隐藏',
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_status_id` (`status`, `id`),
  KEY `idx_user_id` (`user_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户动态';

CREATE TABLE IF NOT EXISTS `post_like` (
  `id`          BIGINT   NOT NULL AUTO_INCREMENT,
  `post_id`     BIGINT   NOT NULL,
  `user_id`     BIGINT   NOT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_post_user` (`post_id`, `user_id`),
  KEY `idx_user` (`user_id`, `post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='动态点赞';

CREATE TABLE IF NOT EXISTS `post_comment` (
  `id`               BIGINT       NOT NULL AUTO_INCREMENT,
  `post_id`          BIGINT       NOT NULL,
  `user_id`          BIGINT       NOT NULL COMMENT '评论人',
  `content`          VARCHAR(500) NOT NULL,
  `reply_to_user_id` BIGINT       DEFAULT NULL COMMENT '回复的目标用户',
  `status`           TINYINT      NOT NULL DEFAULT 0 COMMENT '0正常 1已删除 2审核隐藏',
  `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_post_id` (`post_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='动态评论';
