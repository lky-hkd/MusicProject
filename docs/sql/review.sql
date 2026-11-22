-- 商品评价 + 商家回复
-- 执行方式：mysql -uroot -p music_db < review.sql

CREATE TABLE IF NOT EXISTS `product_review` (
  `id`                  BIGINT       NOT NULL AUTO_INCREMENT,
  `product_id`          BIGINT       NOT NULL,
  `order_id`            BIGINT       NOT NULL,
  `order_item_id`       BIGINT       NOT NULL,
  `user_id`             BIGINT       NOT NULL,
  `merchant_id`         BIGINT       DEFAULT NULL,
  `rating`              TINYINT      NOT NULL COMMENT '1-5 星',
  `content`             VARCHAR(500) NOT NULL,
  `images`              JSON         DEFAULT NULL COMMENT '最多3张',
  `anonymous`           TINYINT      NOT NULL DEFAULT 0,
  `status`              TINYINT      NOT NULL DEFAULT 0 COMMENT '0正常 1已隐藏',
  `merchant_reply`      VARCHAR(500) DEFAULT NULL,
  `merchant_reply_time` DATETIME     DEFAULT NULL,
  `create_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_item` (`order_item_id`),
  KEY `idx_product` (`product_id`, `id`),
  KEY `idx_user` (`user_id`, `id`),
  KEY `idx_merchant` (`merchant_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品评价';

-- 商品评分统计字段（存在则跳过）
SET @sql = (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product' AND COLUMN_NAME = 'review_count') = 0,
  'ALTER TABLE `product` ADD COLUMN `review_count` INT NOT NULL DEFAULT 0',
  'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product' AND COLUMN_NAME = 'rating_avg') = 0,
  'ALTER TABLE `product` ADD COLUMN `rating_avg` DECIMAL(3,2) NOT NULL DEFAULT 5.00',
  'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
