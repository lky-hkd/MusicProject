-- VIP 功能数据库变更
-- 1. 歌曲表加 VIP 标识
ALTER TABLE song ADD COLUMN is_vip TINYINT NOT NULL DEFAULT 0 COMMENT '0=免费 1=VIP专属';

-- 2. VIP 商品种子数据(平台自营,merchant_id 为 NULL;virtual_value = 天数)
INSERT INTO product (name, subtitle, product_type, virtual_type, virtual_value, price, original_price, stock, sales, status, create_time, update_time)
VALUES
 ('VIP月卡', '30天VIP畅听', 'virtual', 'vip_month',   30,   15.00, 30.00, 999999, 0, 1, NOW(), NOW()),
 ('VIP季卡', '90天VIP畅听', 'virtual', 'vip_quarter', 90,   40.00, 90.00, 999999, 0, 1, NOW(), NOW()),
 ('VIP年卡', '365天VIP畅听','virtual', 'vip_year',    365,  138.00, 365.00, 999999, 0, 1, NOW(), NOW());

-- 3. 标记示例:把某首歌设为 VIP(按需执行)
-- UPDATE song SET is_vip = 1 WHERE id = ?;
