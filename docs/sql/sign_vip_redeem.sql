-- 签到兑换 VIP 记录表
CREATE TABLE IF NOT EXISTS `sign_vip_redeem` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `milestone` INT NOT NULL COMMENT '兑换时达到的累计签到天数（30/60/90...）',
  `vip_days` INT NOT NULL COMMENT '本次奖励的VIP天数',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_milestone` (`user_id`, `milestone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='签到兑换VIP记录';
