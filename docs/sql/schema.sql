
/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
DROP TABLE IF EXISTS `address`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `address` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `receiver` varchar(50) NOT NULL COMMENT '收件人',
  `phone` varchar(20) NOT NULL COMMENT '手机号',
  `province` varchar(50) DEFAULT NULL COMMENT '省',
  `city` varchar(50) DEFAULT NULL COMMENT '市',
  `district` varchar(50) DEFAULT NULL COMMENT '区',
  `detail` varchar(200) NOT NULL COMMENT '详细地址',
  `is_default` tinyint DEFAULT '0' COMMENT '1默认 0非默认',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='收货地址表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `admin`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `admin` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(50) NOT NULL,
  `password` varchar(100) NOT NULL COMMENT 'MD5或BCrypt加密',
  `nickname` varchar(50) DEFAULT NULL,
  `role` varchar(30) DEFAULT 'super' COMMENT 'super / normal',
  `status` tinyint DEFAULT '1',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `username` (`username`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='管理员表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `cart`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cart` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `product_id` bigint NOT NULL,
  `quantity` int NOT NULL DEFAULT '1',
  `selected` tinyint DEFAULT '1' COMMENT '1选中 0未选',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_product` (`user_id`,`product_id`)
) ENGINE=InnoDB AUTO_INCREMENT=26 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='购物车表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `chat_conversation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `chat_conversation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_a_id` bigint NOT NULL COMMENT '会话双方中较小的用户ID',
  `user_b_id` bigint NOT NULL COMMENT '会话双方中较大的用户ID',
  `type` tinyint NOT NULL DEFAULT '0' COMMENT '0私聊 1平台客服',
  `last_msg` varchar(500) DEFAULT NULL COMMENT '最后一条消息摘要',
  `last_msg_time` datetime DEFAULT NULL COMMENT '最后消息时间，用于排序',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pair` (`user_a_id`,`user_b_id`),
  KEY `idx_b` (`user_b_id`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='聊天会话';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `chat_message`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `chat_message` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `conversation_id` bigint NOT NULL,
  `sender_id` bigint NOT NULL,
  `receiver_id` bigint NOT NULL,
  `client_msg_id` varchar(64) DEFAULT NULL COMMENT '客户端消息ID，用于发送重试幂等',
  `content` text NOT NULL COMMENT '文本内容或图片URL',
  `type` tinyint NOT NULL DEFAULT '0' COMMENT '0文本 1图片',
  `is_read` tinyint NOT NULL DEFAULT '0' COMMENT '0未读 1已读',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sender_client` (`sender_id`,`client_msg_id`),
  KEY `idx_conv` (`conversation_id`,`id`),
  KEY `idx_receiver_read` (`receiver_id`,`is_read`)
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='聊天消息';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `merchant`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `merchant` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '店铺id',
  `user_id` bigint NOT NULL COMMENT '关联 user.id（该用户 role 必须=1 商家）',
  `shop_name` varchar(100) NOT NULL COMMENT '店铺名称',
  `logo` varchar(255) DEFAULT NULL COMMENT '店铺logo',
  `description` varchar(500) DEFAULT NULL COMMENT '店铺简介',
  `contact_phone` varchar(20) DEFAULT NULL COMMENT '联系电话',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0禁用 1正常',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`) COMMENT '一个用户只能开一家店'
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商家店铺';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `order`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `order` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) NOT NULL COMMENT '订单号（时间戳+随机）',
  `user_id` bigint NOT NULL,
  `total_amount` decimal(10,2) NOT NULL COMMENT '订单总额',
  `pay_amount` decimal(10,2) NOT NULL COMMENT '实付金额',
  `address_id` bigint DEFAULT NULL,
  `receiver` varchar(50) DEFAULT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `address_detail` varchar(300) DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '0待付款 1已付款 2已发货 3已完成 4已取消 5已退款',
  `pay_type` varchar(20) DEFAULT NULL COMMENT 'alipay / wechat / mock',
  `pay_time` datetime DEFAULT NULL,
  `ship_time` datetime DEFAULT NULL,
  `finish_time` datetime DEFAULT NULL,
  `express_no` varchar(100) DEFAULT NULL COMMENT '快递单号',
  `remark` varchar(500) DEFAULT NULL COMMENT '用户备注',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `trade_no` varchar(64) DEFAULT NULL COMMENT '支付宝交易号',
  `refund_status` tinyint NOT NULL DEFAULT '0' COMMENT '0无退款 1退款申请中 2退款中 3退款成功 4退款失败 5已拒绝',
  `refund_amount` decimal(10,2) DEFAULT NULL COMMENT '退款金额',
  `refund_time` datetime DEFAULT NULL COMMENT '退款完成时间',
  `merchant_id` bigint DEFAULT NULL COMMENT '店铺id，拆单后每单只属于一个店铺',
  `pay_no` varchar(64) DEFAULT NULL COMMENT '支付批次号，同一次结算的多个订单共享',
  `discount_amount` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '优惠券抵扣金额（元）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `order_no` (`order_no`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_user` (`user_id`),
  KEY `idx_status` (`status`),
  KEY `idx_order_no` (`order_no`),
  KEY `idx_refund_status` (`refund_status`),
  KEY `idx_order_merchant` (`merchant_id`),
  KEY `idx_order_pay_no` (`pay_no`)
) ENGINE=InnoDB AUTO_INCREMENT=63 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `order_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `order_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` bigint NOT NULL,
  `merchant_id` bigint DEFAULT NULL COMMENT '下单时商品归属商家快照',
  `product_id` bigint NOT NULL,
  `product_name` varchar(200) NOT NULL COMMENT '商品名快照',
  `product_cover` varchar(500) DEFAULT NULL COMMENT '封面快照',
  `product_type` varchar(20) NOT NULL COMMENT '实体/虚拟快照',
  `virtual_type` varchar(30) DEFAULT NULL COMMENT '虚拟类型快照',
  `virtual_value` int DEFAULT NULL,
  `price` decimal(10,2) NOT NULL COMMENT '单价快照',
  `quantity` int NOT NULL,
  `subtotal` decimal(10,2) NOT NULL COMMENT '小计',
  PRIMARY KEY (`id`),
  KEY `idx_order` (`order_id`),
  KEY `idx_merchant_id` (`merchant_id`)
) ENGINE=InnoDB AUTO_INCREMENT=64 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单明细表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `payment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `payment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) NOT NULL,
  `trade_no` varchar(128) DEFAULT NULL COMMENT '第三方支付流水号',
  `amount` decimal(10,2) NOT NULL,
  `pay_type` varchar(20) NOT NULL,
  `status` tinyint DEFAULT '0' COMMENT '0待支付 1成功 2失败',
  `raw_response` text COMMENT '第三方返回原始数据',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_order_no` (`order_no`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='支付流水表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `post`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `post` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '作者',
  `content` varchar(1000) NOT NULL COMMENT '正文',
  `images` json DEFAULT NULL COMMENT '图片URL数组，最多9张',
  `like_count` int unsigned NOT NULL DEFAULT '0',
  `comment_count` int unsigned NOT NULL DEFAULT '0',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '0正常 1已删除 2审核隐藏',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_status_id` (`status`,`id`),
  KEY `idx_user_id` (`user_id`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户动态';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `post_comment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `post_comment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `post_id` bigint NOT NULL,
  `user_id` bigint NOT NULL COMMENT '评论人',
  `content` varchar(500) NOT NULL,
  `reply_to_user_id` bigint DEFAULT NULL COMMENT '回复的目标用户',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '0正常 1已删除 2审核隐藏',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_post_id` (`post_id`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='动态评论';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `post_like`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `post_like` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `post_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_post_user` (`post_id`,`user_id`),
  KEY `idx_user` (`user_id`,`post_id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='动态点赞';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `product`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `product` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '商品id',
  `merchant_id` bigint DEFAULT NULL COMMENT '归属商家id（merchant.id），NULL=平台自营',
  `name` varchar(200) NOT NULL COMMENT '商品名称',
  `subtitle` varchar(255) DEFAULT NULL COMMENT '副标题/卖点',
  `description` text COMMENT '商品详情（富文本）',
  `product_type` varchar(20) NOT NULL DEFAULT 'physical' COMMENT '类型：physical=实体, virtual=虚拟',
  `category_id` bigint DEFAULT NULL COMMENT '分类id',
  `virtual_type` varchar(30) DEFAULT NULL COMMENT '虚拟类型：vip_month / vip_quarter / vip_year / vip_forever / cover / audio',
  `virtual_value` int DEFAULT NULL COMMENT '虚拟值：如VIP天数',
  `price` decimal(10,2) NOT NULL COMMENT '售价',
  `original_price` decimal(10,2) DEFAULT NULL COMMENT '原价（划线价）',
  `cover_url` varchar(500) DEFAULT NULL COMMENT '封面图',
  `images` text COMMENT '多图（JSON数组）',
  `stock` int NOT NULL DEFAULT '0' COMMENT '库存（虚拟商品可设999999）',
  `sales` int NOT NULL DEFAULT '0' COMMENT '销量',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0=下架, 1=上架',
  `related_song_id` bigint DEFAULT NULL COMMENT '关联歌曲id（如专辑CD）',
  `related_singer` varchar(100) DEFAULT NULL COMMENT '关联歌手',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `review_count` int NOT NULL DEFAULT '0',
  `rating_avg` decimal(3,2) NOT NULL DEFAULT '5.00',
  PRIMARY KEY (`id`),
  KEY `idx_category` (`category_id`),
  KEY `idx_type` (`product_type`),
  KEY `idx_status` (`status`),
  KEY `idx_merchant_id` (`merchant_id`)
) ENGINE=InnoDB AUTO_INCREMENT=22 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `product_category`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `product_category` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(50) NOT NULL COMMENT '分类名：专辑/明星周边/二次元周边/虚拟商品',
  `icon` varchar(500) DEFAULT NULL COMMENT '图标',
  `sort` int DEFAULT '0' COMMENT '排序',
  `status` tinyint DEFAULT '1' COMMENT '1启用 0禁用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品分类表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `product_review`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `product_review` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `product_id` bigint NOT NULL,
  `order_id` bigint NOT NULL,
  `order_item_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `merchant_id` bigint DEFAULT NULL,
  `rating` tinyint NOT NULL COMMENT '1-5 星',
  `content` varchar(500) NOT NULL,
  `images` json DEFAULT NULL COMMENT '最多3张',
  `anonymous` tinyint NOT NULL DEFAULT '0',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '0正常 1已隐藏',
  `merchant_reply` varchar(500) DEFAULT NULL,
  `merchant_reply_time` datetime DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_item` (`order_item_id`),
  KEY `idx_product` (`product_id`,`id`),
  KEY `idx_user` (`user_id`,`id`),
  KEY `idx_merchant` (`merchant_id`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品评价';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `refund_order`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `refund_order` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `order_no` varchar(64) NOT NULL COMMENT '订单号',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `refund_no` varchar(64) NOT NULL COMMENT '退款单号（商户生成，调支付宝用）',
  `refund_amount` decimal(10,2) NOT NULL,
  `reason` varchar(255) DEFAULT NULL COMMENT '用户退款原因',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '1申请中 2退款中 3成功 4失败 5拒绝',
  `reject_reason` varchar(255) DEFAULT NULL,
  `alipay_trade_no` varchar(64) DEFAULT NULL COMMENT '支付宝流水号',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_refund_no` (`refund_no`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='退款单';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `seckill_order_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `seckill_order_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_no` varchar(64) NOT NULL COMMENT '预分配订单号(业务幂等键)',
  `biz_type` tinyint NOT NULL COMMENT '1=商品订单 2=优惠券订单',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `payload` json NOT NULL COMMENT '落库所需完整快照(商品项/券ID等)',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '0创建中 1成功 2失败(待人工)',
  `retry_count` int NOT NULL DEFAULT '0' COMMENT '已重试次数',
  `last_error` varchar(500) DEFAULT NULL COMMENT '最近一次失败原因',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_status_time` (`status`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='秒杀异步落库任务表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `seckill_voucher`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `seckill_voucher` (
  `voucher_id` bigint unsigned NOT NULL COMMENT '关联的优惠券的id',
  `stock` int unsigned NOT NULL COMMENT '库存',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `begin_time` timestamp NOT NULL COMMENT '生效时间',
  `end_time` timestamp NOT NULL COMMENT '失效时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`voucher_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='秒杀优惠券表，与优惠券是一对一关系';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `sign_vip_redeem`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sign_vip_redeem` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `milestone` int NOT NULL COMMENT '兑换时达到的累计签到天数（30/60/90...）',
  `vip_days` int NOT NULL COMMENT '本次奖励的VIP天数',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_milestone` (`user_id`,`milestone`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='签到兑换VIP记录';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `song`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `song` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `song_name` varchar(100) NOT NULL COMMENT '歌曲名',
  `singer` varchar(100) NOT NULL COMMENT '歌手',
  `lyric` text COMMENT '歌词',
  `audio_url` varchar(255) NOT NULL COMMENT '音频地址',
  `cover_url` varchar(255) DEFAULT NULL COMMENT '封面图',
  `duration` int DEFAULT NULL COMMENT '时长秒数',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `play_count` int DEFAULT '0' COMMENT '播放量',
  `type` varchar(32) DEFAULT NULL COMMENT '歌曲风格分类',
  `is_vip` tinyint NOT NULL DEFAULT '0' COMMENT '0免费 1VIP专享',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=14 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `song_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `song_history` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户id',
  `song_id` bigint NOT NULL COMMENT '歌曲id',
  `play_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '播放时间',
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=19 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `username` varchar(50) NOT NULL COMMENT '账号',
  `password` varchar(100) NOT NULL COMMENT '加密密码',
  `nickname` varchar(50) DEFAULT NULL COMMENT '昵称',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `role` tinyint NOT NULL DEFAULT '0' COMMENT '0普通用户 1商家 2管理员',
  `avatar` varchar(255) DEFAULT NULL COMMENT '头像地址',
  `phone` varchar(30) DEFAULT NULL,
  `following_count` int unsigned NOT NULL DEFAULT '0' COMMENT '关注数：我关注了多少人',
  `follower_count` int unsigned NOT NULL DEFAULT '0' COMMENT '粉丝数：多少人关注了我',
  `gender` tinyint NOT NULL DEFAULT '0' COMMENT '0保密 1男 2女',
  `birthday` date DEFAULT NULL,
  `signature` varchar(100) DEFAULT NULL,
  `password_set` tinyint NOT NULL DEFAULT '1',
  PRIMARY KEY (`id`),
  UNIQUE KEY `username` (`username`)
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `user_collect`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_collect` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户id',
  `song_id` bigint NOT NULL COMMENT '歌曲id',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_song` (`user_id`,`song_id`)
) ENGINE=InnoDB AUTO_INCREMENT=30 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `user_coupon`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_coupon` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户id',
  `voucher_id` bigint NOT NULL COMMENT '优惠券id',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '0 未使用 / 1 已使用 / 2 已过期',
  `receive_time` datetime NOT NULL COMMENT '领取时间',
  `expire_time` datetime DEFAULT NULL COMMENT '过期时间，NULL 不限',
  `use_time` datetime DEFAULT NULL COMMENT '使用时间',
  `pay_no` varchar(64) DEFAULT NULL COMMENT '核销时所在支付批次号',
  `discount_amount` bigint DEFAULT NULL COMMENT '本次实际抵扣金额（分）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_voucher` (`user_id`,`voucher_id`),
  KEY `idx_user_status` (`user_id`,`status`),
  KEY `idx_pay_no` (`pay_no`)
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户优惠券';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `user_follow`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_follow` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `follower_id` bigint NOT NULL COMMENT '发起关注的人（粉丝）',
  `followee_id` bigint NOT NULL COMMENT '被关注的人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '关注时间，用于列表排序',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_follower_followee` (`follower_id`,`followee_id`),
  KEY `idx_followee_created` (`followee_id`,`created_at`),
  CONSTRAINT `fk_follow_followee` FOREIGN KEY (`followee_id`) REFERENCES `user` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_follow_follower` FOREIGN KEY (`follower_id`) REFERENCES `user` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户关注关系表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `user_vip`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_vip` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `vip_level` tinyint DEFAULT '1' COMMENT '会员等级（预留）',
  `expire_time` datetime DEFAULT NULL COMMENT '到期时间，NULL=永久',
  `is_forever` tinyint DEFAULT '0' COMMENT '1永久 0有期限',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `user_id` (`user_id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户会员表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `voucher`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `voucher` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `shop_id` bigint unsigned DEFAULT NULL COMMENT '商铺id',
  `title` varchar(255) NOT NULL COMMENT '代金券标题',
  `sub_title` varchar(255) DEFAULT NULL COMMENT '副标题',
  `rules` varchar(1024) DEFAULT NULL COMMENT '使用规则',
  `pay_value` bigint unsigned NOT NULL COMMENT '支付金额，单位是分。例如200代表2元',
  `actual_value` bigint NOT NULL COMMENT '抵扣金额，单位是分。例如200代表2元',
  `type` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '0,普通券；1,秒杀券',
  `status` tinyint unsigned NOT NULL DEFAULT '1' COMMENT '1,上架；2,下架；3,过期',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `threshold` bigint NOT NULL DEFAULT '0' COMMENT '使用门槛（分），0 无门槛',
  `valid_days` int NOT NULL DEFAULT '0' COMMENT '领取后有效天数，0 不限',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `voucher_order`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `voucher_order` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint unsigned NOT NULL COMMENT '下单的用户id',
  `voucher_id` bigint unsigned NOT NULL COMMENT '购买的代金券id',
  `pay_type` varchar(10) NOT NULL COMMENT '支付方式',
  `status` tinyint unsigned NOT NULL COMMENT '1待支付 2已支付 3已核销 4已取消',
  `create_time` timestamp NULL DEFAULT NULL COMMENT '下单时间',
  `pay_time` timestamp NULL DEFAULT NULL COMMENT '支付时间',
  `use_time` timestamp NULL DEFAULT NULL COMMENT '核销时间',
  `refund_time` timestamp NULL DEFAULT NULL COMMENT '退款时间',
  `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_voucher_id` (`voucher_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='代金券订单表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

