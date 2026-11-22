# 🎵 Music Platform 音乐平台

一个基于 **Spring Boot 3 + MyBatis-Plus** 的前后端一体音乐 / 电商 / 社交综合性平台。
集成了在线音乐播放、商品秒杀、社交动态、即时聊天、AI 搜索、会员体系与支付宝支付等模块。

---

## ✨ 功能特性

### 🎧 音乐
- 在线音乐播放（支持多首曲目、音频可视化水面效果）
- 歌曲搜索、播放历史、收藏
- AI 智能搜索（基于智谱 GLM 大模型）

### 🛒 电商
- 商品浏览、购物车、下单、订单管理
- **秒杀系统**：Redis Lua 预扣库存 + RabbitMQ 异步下单 + 库存回滚补偿
- 优惠券、代金券秒杀领取
- 收货地址管理
- 支付宝沙箱支付（异步回调）

### 👥 社交
- 发布动态（图文）、评论、点赞、关注
- 实时私信（WebSocket + Redis Pub/Sub 多实例广播）
- 用户主页、消息中心

### 🏪 商家 & 后台
- 商家入驻、商家中心、商品管理
- 订单支付、退款申请与审核
- 管理后台：用户管理、店铺管理、优惠券管理、退款审核

### 💎 会员体系
- VIP 会员、签到、代金券兑换

---

## 🛠 技术栈

| 类别 | 技术 |
|---|---|
| 语言 / 版本 | Java 17 |
| 框架 | Spring Boot 3.2.5 |
| ORM | MyBatis-Plus 3.5.5 |
| 数据库 | MySQL 8 |
| 缓存 / 分布式 | Redis + Redisson 3.13.6 |
| 消息队列 | RabbitMQ |
| 实时通信 | Spring WebSocket |
| 安全 | Spring Security Crypto (BCrypt) |
| 支付 | 支付宝 SDK 4.40 |
| AI | 智谱 GLM-4 |
| 前端 | 原生 HTML / CSS / JS + Three.js + ECharts |
| 构建 | Maven |

---

## 📁 项目结构

```
MusicPlatform
├── docs/
│   ├── env.example          # 生产环境变量模板
│   └── sql/                 # 数据库建表脚本
├── src/main/
│   ├── java/com/music/
│   │   ├── config/          # 配置类（Redis、WebMvc、WebSocket、拦截器等）
│   │   ├── controller/      # 接口层
│   │   ├── service/         # 业务层
│   │   ├── mapper/          # 数据访问层
│   │   ├── entity/          # 实体类
│   │   ├── dto/             # 数据传输对象
│   │   ├── common/          # 通用常量 / 返回结构
│   │   ├── task/            # 定时任务（库存对账等）
│   │   └── util/            # 工具类
│   └── resources/
│       ├── application.yml               # 公共配置
│       ├── application-dev.example.yml   # 开发配置模板（复制为 application-dev.yml）
│       ├── application-prod.yml          # 生产配置（密钥走环境变量）
│       ├── lua/                          # 秒杀 / 限流 / 锁 脚本
│       ├── mybatis/                      # Mapper XML
│       └── static/                       # 前端页面与静态资源
└── pom.xml
```

---

## 🚀 快速开始

### 环境要求

- JDK 17+
- Maven 3.8+
- MySQL 8.x
- Redis 6.x+
- RabbitMQ 3.x+

### 1. 初始化数据库

创建数据库并导入表结构：

```sql
CREATE DATABASE music_db DEFAULT CHARACTER SET utf8mb4;
```

然后依次执行 `docs/sql/` 目录下的脚本（按业务模块）。

### 2. 配置本地环境

复制开发配置模板，并填入你自己的连接信息与密钥：

```bash
cp src/main/resources/application-dev.example.yml src/main/resources/application-dev.yml
```

编辑 `application-dev.yml`，至少修改：
- `spring.datasource` —— MySQL 账号密码
- `spring.data.redis` —— Redis 地址与密码
- `spring.rabbitmq` —— RabbitMQ 账号密码
- `llm.api-key` —— 智谱 GLM API Key
- `alipay.*` —— 支付宝沙箱密钥（如需测试支付）

> ⚠️ 真实的 `application-dev.yml` 已被 `.gitignore` 忽略，**不会**被提交到 Git。

### 3. 启动

```bash
mvn spring-boot:run
```

默认访问地址：<http://localhost:8080>

> 启动时默认激活 `dev` 环境。可通过 `--spring.profiles.active=dev` 显式指定。

---

## ⚙️ 配置说明

项目采用 **多环境配置**，敏感信息统一通过 **环境变量** 注入：

| 文件 | 用途 | 是否提交 |
|---|---|---|
| `application.yml` | 公共配置 + 激活 profile | ✅ |
| `application-dev.example.yml` | 开发配置模板（占位符） | ✅ |
| `application-dev.yml` | 本地真实配置 | ❌ 已忽略 |
| `application-prod.yml` | 生产配置（纯环境变量） | ✅ |

生产环境的完整变量清单见 `docs/env.example`。

---

## 🔐 生产部署要点

1. **密钥外置**：生产所有敏感信息（数据库、Redis、支付宝、LLM Key）通过环境变量注入，`application-prod.yml` 中无默认值，缺失即启动失败。
2. **HTTPS**：生产务必配置 HTTPS，`app.cookie.secure=true`。
3. **CORS**：生产设置 `CORS_ALLOWED_ORIGINS` 为真实域名。
4. **SQL 日志**：生产已关闭 MyBatis SQL 打印。
5. **文件上传**：多实例部署时需将 `uploads/` 挂载到持久卷或改用对象存储（OSS / MinIO）。
6. **Redis 高可用**：建议主从 + Sentinel 哨兵，业务量增长后再评估分片集群。

---

## 📄 许可证

本项目目前未声明开源协议。
