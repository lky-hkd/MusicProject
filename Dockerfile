# =====================================================================
# Music Platform 应用镜像
# 多阶段构建：先用 Maven 打包，再用精简 JRE 运行
# =====================================================================

# ---------- 构建阶段 ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# 先只拷贝 pom.xml，利�?Docker 缓存加速依赖下�?COPY pom.xml .
RUN mvn -B dependency:go-offline

# 再拷贝源码并打包（跳过测试，加快构建�?COPY src ./src
RUN mvn -B clean package -DskipTests

# ---------- 运行阶段 ----------
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# 时区设为上海
RUN apk add --no-cache tzdata \
    && cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone

# 创建�?root 用户运行，降低安全风�?RUN addgroup -S app && adduser -S app -G app

# 拷贝构建产物
COPY --from=build /app/target/*.jar app.jar

# 上传目录（由 compose 挂载持久卷）
RUN mkdir -p /data/uploads && chown -R app:app /data /app

USER app

# 生产 profile 启动

ENV SPRING_PROFILES_ACTIVE=prod \
    TZ=Asia/Shanghai

EXPOSE 8080

# JVM 参数可通过 JAVA_OPTS 环境变量覆盖
ENV JAVA_OPTS="-Xms256m -Xmx512m"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]