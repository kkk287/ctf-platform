# ---------- 1. 前端构建：Vue3 三入口（player / admin / screen） ----------
# 产物直接输出到 /src/main/resources/static/vue（见 web/vite.config.js 的 outDir）
FROM node:22-alpine AS frontend
WORKDIR /web

# 先只复制依赖清单，利用层缓存预装依赖；npm 缓存挂载让重试可以复用已下载的包
COPY web/package.json web/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm \
    npm ci --no-audit --no-fund

COPY web/ ./
RUN npm run build

# ---------- 2. 后端打包：Maven（用上一阶段产物覆盖 static/vue） ----------
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build

# 先复制 pom，便于依赖层缓存失效控制；Maven 本地仓库用 cache mount 跨构建复用
COPY pom.xml ./

# 源码（.dockerignore 已排除预构建的 static/vue，避免把本地旧产物带进镜像）
COPY src ./src
COPY --from=frontend /src/main/resources/static/vue ./src/main/resources/static/vue
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -DskipTests package

# ---------- 3. 运行阶段 ----------
FROM eclipse-temurin:17-jre-alpine
LABEL org.opencontainers.image.title="ctf-platform" \
      org.opencontainers.image.description="CTF 平台：赛事/练习、动态积分、排行榜、理论赛、Writeup、动态靶机与 AWD"

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
