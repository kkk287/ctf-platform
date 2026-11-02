# ---------- 构建阶段 ----------
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build

# 先只复制 pom，利用 Docker 层缓存预下载依赖
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

# 再复制源码并打包（测试已在 CI 中执行，这里跳过以加快镜像构建）
COPY src ./src
RUN mvn -B -q -DskipTests package

# ---------- 运行阶段 ----------
FROM eclipse-temurin:17-jre-alpine
LABEL org.opencontainers.image.title="ctf-platform" \
      org.opencontainers.image.description="CTF 平台：赛事/练习、动态积分、个人与战队排行榜"

RUN addgroup -S app && adduser -S -G app app \
    && mkdir -p /app/data/uploads && chown -R app:app /app

WORKDIR /app
COPY --from=builder /build/target/ctf-platform-1.0.0.jar /app/app.jar

USER app
ENV SERVER_PORT=8080 \
    SPRING_PROFILES_ACTIVE=h2 \
    UPLOAD_DIR=/app/data/uploads \
    TZ=Asia/Shanghai

EXPOSE 8080
VOLUME ["/app/data"]

# 使用 busybox wget 检查 Actuator 健康端点，避免额外安装 curl
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
    CMD wget -qO- "http://127.0.0.1:${SERVER_PORT}/actuator/health" | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
