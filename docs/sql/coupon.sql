-- ============================================================
-- 优惠券 / 活动中心 建表与字段变更脚本（MySQL 8）
-- 按从上到下顺序执行。
-- 说明：
--   1) CREATE TABLE IF NOT EXISTS 在表已存在时会自动跳过，不会丢数据；
--   2) 末尾的 ALTER 若提示 1060 Duplicate column，说明字段已存在，忽略即可。
-- ============================================================

-- 1. 优惠券主表（表已存在则跳过）
CREATE TABLE IF NOT EXISTS `voucher` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `shop_id`      BIGINT       NULL COMMENT '店铺id',
  `title`        VARCHAR(255) NOT NULL COMMENT '券标题',
  `sub_title`    VARCHAR(255) NULL COMMENT '副标题',
  `rules`        VARCHAR(255) NULL COMMENT '使用规则',
  `pay_value`    BIGINT       NOT NULL DEFAULT 0 COMMENT '支付金额（分）',
  `actual_value` BIGINT       NOT NULL DEFAULT 0 COMMENT '抵扣金额（分）',
  `threshold`    BIGINT       NOT NULL DEFAULT 0 COMMENT '使用门槛（分），0 无门槛',
  `valid_days`   INT          NOT NULL DEFAULT 0 COMMENT '领取后有效天数，0 不限',
  `type`         TINYINT      NOT NULL DEFAULT 0 COMMENT '0 普通券 / 1 秒杀券',
  `status`       TINYINT      NOT NULL DEFAULT 1 COMMENT '1 上架 / 2 下架 / 3 过期',
  `create_time`  DATETIME     NULL,
  `update_time`  DATETIME     NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='优惠券';

-- 2. 秒杀券库存表（表已存在则跳过）
CREATE TABLE IF NOT EXISTS `seckill_voucher` (
  `voucher_id`  BIGINT   NOT NULL COMMENT '优惠券id',
  `stock`       INT      NOT NULL DEFAULT 0 COMMENT '库存',
  `begin_time`  DATETIME NULL COMMENT '秒杀开始时间',
  `end_time`    DATETIME NULL COMMENT '秒杀结束时间',
  `create_time` DATETIME NULL,
  `update_time` DATETIME NULL,
  PRIMARY KEY (`voucher_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀券';

-- 3. 用户优惠券（领取记录 / 核销状态）
CREATE TABLE IF NOT EXISTS `user_coupon` (
  `id`              BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`         BIGINT      NOT NULL COMMENT '用户id',
  `voucher_id`      BIGINT      NOT NULL COMMENT '优惠券id',
  `status`          TINYINT     NOT NULL DEFAULT 0 COMMENT '0 未使用 / 1 已使用 / 2 已过期',
  `receive_time`    DATETIME    NOT NULL COMMENT '领取时间',
  `expire_time`     DATETIME    NULL COMMENT '过期时间，NULL 不限',
  `use_time`        DATETIME    NULL COMMENT '使用时间',
  `pay_no`          VARCHAR(64) NULL COMMENT '核销时所在支付批次号',
  `discount_amount` BIGINT      NULL COMMENT '本次实际抵扣金额（分）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_voucher` (`user_id`, `voucher_id`),
  KEY `idx_user_status` (`user_id`, `status`),
  KEY `idx_pay_no` (`pay_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户优惠券';

-- 4. 已有表的字段扩展（字段已存在会报 1060，忽略即可）
ALTER TABLE `voucher`
  ADD COLUMN `threshold` BIGINT NOT NULL DEFAULT 0 COMMENT '使用门槛（分），0 无门槛';

ALTER TABLE `voucher`
  ADD COLUMN `valid_days` INT NOT NULL DEFAULT 0 COMMENT '领取后有效天数，0 不限';

ALTER TABLE `order`
  ADD COLUMN `discount_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '优惠券抵扣金额（元）';

-- ============================================================
-- 样例数据（可选，便于在活动中心快速看到效果）
-- ============================================================

-- 普通券：满 100 减 10，领取后 30 天有效
INSERT INTO `voucher`
  (`shop_id`, `title`, `sub_title`, `rules`, `pay_value`, `actual_value`, `threshold`, `valid_days`, `type`, `status`, `create_time`, `update_time`)
VALUES
  (NULL, '满100减10', '全场通用', '订单满 100 元可用，领取后 30 天内有效', 0, 1000, 10000, 30, 0, 1, NOW(), NOW());

-- 秒杀券：满 200 减 50，限量 20 张，7 天内有效
INSERT INTO `voucher`
  (`shop_id`, `title`, `sub_title`, `rules`, `pay_value`, `actual_value`, `threshold`, `valid_days`, `type`, `status`, `create_time`, `update_time`)
VALUES
  (NULL, '秒杀满200减50', '限时抢购，先到先得', '订单满 200 元可用，限量 20 张', 0, 5000, 20000, 7, 1, 1, NOW(), NOW());

INSERT INTO `seckill_voucher`
  (`voucher_id`, `stock`, `begin_time`, `end_time`, `create_time`, `update_time`)
VALUES
  (LAST_INSERT_ID(), 20, NOW(), DATE_ADD(NOW(), INTERVAL 7 DAY), NOW(), NOW());
