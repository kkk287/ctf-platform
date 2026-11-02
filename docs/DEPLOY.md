# 部署指南（DEPLOY）

本文覆盖三条路径：本地运行、本机 Docker、GitHub Actions + 服务器部署。最后给出 Secrets 清单与常见故障排查。

## 0. 前置条件

| 场景 | 需要 |
|---|---|
| 本地运行 | JDK 17（`java -version` 显示 17.x）；Maven 由 Wrapper 自带，无需安装 |
| 本机 Docker | Docker Engine 20+、Docker Compose v2（`docker compose version`） |
| 服务器部署 | Linux 服务器、Docker + Compose v2、开放业务端口（默认 8080）、可 SSH 登录 |
| GitHub 自动部署 | 仓库 Secrets / Variables 配置权限；服务器可访问 `ghcr.io` |

## 1. 本地运行

```bash
# Windows: mvnw.cmd，Linux/macOS: ./mvnw
mvnw.cmd -B clean test          # 运行全部测试
mvnw.cmd spring-boot:run        # 启动，默认 8080
```

或：

```bash
mvnw.cmd -B clean package -DskipTests
java -jar target/ctf-platform-1.0.0.jar
```

启动后：

- 页面：<http://localhost:8080/>
- 健康检查：<http://localhost:8080/actuator/health>
- Swagger：<http://localhost:8080/swagger-ui.html>
- 数据文件：`./data/ctf.mv.db`、附件 `./data/uploads`
- 想重置演示数据：停止应用，删除 `data/` 目录后重启（Flyway 会重新建表与种子）

环境变量可写入 `.env`（Docker 自动读取），或本地运行时直接设置，如：

```bash
# PowerShell
$env:JWT_SECRET="请替换为至少32字节随机串"; mvnw.cmd spring-boot:run
# bash
JWT_SECRET=... ./mvnw spring-boot:run
```

## 2. 本机 Docker

```bash
cp .env.example .env       # 按需修改 APP_PORT / JWT_SECRET
docker compose up -d --build
docker compose ps
curl http://localhost:8080/actuator/health
```

- 镜像名：`ctf-platform:local`（或 `.env` 中 `APP_IMAGE`）
- 数据卷：`ctf-platform-data`（H2 库 + 附件）
- 重启策略：`unless-stopped`
- 健康检查：容器内 `wget /actuator/health`，`start_period=45s`
- 停止：`docker compose down`；连同数据清空：`docker compose down -v`

## 3. GitHub 部署（三步走）

以下命令均使用真实仓库地址 `kkk287/ctf-platform`；若你 fork 到其他账号，替换为对应地址即可。

### 第 1 步：推送仓库

```bash
cd ctf-platform
git init
git add .
git commit -m "feat: CTF platform initial commit"
git branch -M main
git remote add origin https://github.com/kkk287/ctf-platform.git
git push -u origin main
```

推送后自动触发 `.github/workflows/ci.yml`：

1. `setup-java`（Temurin 17）+ Maven 缓存
2. `./mvnw -B clean test`
3. Docker 镜像构建（不推送）
4. 上传 surefire 报告为构建产物

在仓库 **Actions** 页应看到绿色对勾。若测试失败，先本地执行同样的 `mvnw test` 排查。

### 第 2 步：配置 Secrets / Variables

路径：仓库 **Settings → Secrets and variables → Actions**。

**Secrets（加密，不能回显）**

| 名称 | 必填 | 内容 |
|---|---|---|
| `SERVER_HOST` | SSH 部署必填 | 服务器公网 IP 或域名 |
| `SERVER_USER` | SSH 部署必填 | 登录用户，如 `deploy`（需属于 docker 组） |
| `SERVER_SSH_KEY` | SSH 部署必填 | **私钥全文**（含 `BEGIN OPENSSH PRIVATE KEY` 行）；对应公钥写入服务器 `~/.ssh/authorized_keys` |
| `SERVER_PORT` | 否 | SSH 端口，默认 22 |
| `GHCR_TOKEN` | 私有仓库/私有包必填 | GitHub PAT（classic 或 fine-grained），至少勾选 `read:packages`；公开包可留空 |

**Variables（明文）**

| 名称 | 必填 | 内容 |
|---|---|---|
| `DEPLOY_ENABLED` | 自动部署必填 | 设为 `true`；不设置则 Deploy 工作流只推送镜像、跳过 SSH |
| `APP_PORT` | 否 | 服务器映射端口，默认 `8080` |

生成 SSH 密钥示例（本地执行，把公钥上传服务器）：

```bash
ssh-keygen -t ed25519 -C "github-actions-ctf" -f ./ctf_deploy_key -N ""
ssh-copy-id -i ./ctf_deploy_key.pub <user>@<server>
# 把 ctf_deploy_key（私钥）全文填入 SERVER_SSH_KEY
```

> 服务器用户需能执行 docker：`sudo usermod -aG docker <user>`，重新登录生效。

### 第 3 步：触发部署

**A. 打 tag 自动部署（推荐）**

```bash
git tag v1.0.0
git push origin v1.0.0
```

`deploy.yml` 将：

1. 构建镜像并推送 `ghcr.io/kkk287/ctf-platform:v1.0.0`（同时推送 `latest`、`sha-<短哈希>`）
2. 通过 scp 把 `deploy/docker-compose.server.yml` 复制到服务器 `/opt/ctf/docker-compose.yml`
3. SSH 执行 `docker compose pull` + `up -d`（重启容器），保留服务器 `.env` 中已有的 `JWT_SECRET`
4. 输出 `docker compose ps`

**B. 手动触发**：Actions → **Deploy** → *Run workflow*（选择分支）。镜像 tag 为 `sha-<短哈希>`；`latest` 不会更新。

**C. 服务器手动一条命令（不走 Actions）**

前提：GHCR 上已有镜像（可先在本地构建推送，或先跑一次 Actions）。

```bash
sudo mkdir -p /opt/ctf && cd /opt/ctf \
&& curl -fsSL https://raw.githubusercontent.com/kkk287/ctf-platform/main/deploy/docker-compose.server.yml -o docker-compose.yml \
&& echo "APP_IMAGE=ghcr.io/kkk287/ctf-platform:latest" > .env \
&& echo "JWT_SECRET=$(openssl rand -hex 32)" >> .env \
&& docker compose pull && docker compose up -d
```

私有包需先登录：

```bash
echo "<GHCR_TOKEN>" | docker login ghcr.io -u <github用户名> --password-stdin
```

**D. 直接在服务器 clone 源码构建（不依赖 GHCR）**

仓库自成闭环，服务器只需 Docker：

```bash
git clone https://github.com/kkk287/ctf-platform.git /opt/ctf-src
cd /opt/ctf-src
cp .env.example .env    # 修改 JWT_SECRET / APP_PORT
docker compose up -d --build
```

## 4. 上线后核验清单

```bash
# 1) 健康检查
curl -fsS http://<server>:8080/actuator/health     # 期望 {"status":"UP"}

# 2) 页面可访问
curl -I http://<server>:8080/                      # 200

# 3) 容器状态与重启策略
docker compose -f /opt/ctf/docker-compose.yml ps   # STATUS 应为 healthy / running
docker inspect -f '{{.HostConfig.RestartPolicy.Name}}' ctf-platform   # unless-stopped
```

浏览器验证：打开 `http://<server>:8080/` → `admin / Admin@123` 登录 → 进入管理后台 → 用 `alice / Alice@123` 解题 → 排行榜出现名次。

服务器重启自恢复测试：

```bash
sudo reboot
# 重启后
docker compose -f /opt/ctf/docker-compose.yml ps
curl -fsS http://<server>:8080/actuator/health
```

数据卷保证数据库与附件不丢失。

## 5. 常见坑与排查

### 5.1 Actions 没有触发 / 部署步骤被跳过

- `deploy.yml` 只在 **push tag `v*`** 或手动触发时运行；普通 push 不会部署。
- `DEPLOY_ENABLED` 未设为 `true`（Variables 不是 Secrets）时，SSH 部署 job 会被跳过，属正常行为。
- Fork 仓库默认不执行上游 Actions；在自己的 fork 中推送即可。

### 5.2 GHCR 拉取失败（denied / unauthorized）

- **包可见性**：仓库 → **Packages** → 找到 `ctf-platform` → *Package settings* → 将可见性设为 **Public**（否则服务器必须登录）。
- 私有仓库/私有包：必须配置 `GHCR_TOKEN`（`read:packages`），服务器执行 `docker login ghcr.io`。
- 组织仓库下包可能继承仓库权限，确认执行部署的账号有读包权限。
- 首次推送后包才会出现；先跑一次 Deploy 或 CI 镜像构建。

### 5.3 端口 / 防火墙

- 云服务器安全组需放行 `APP_PORT`（默认 8080）；本机防火墙（ufw/firewalld）同样需要放行。
- 80/443 被占用时用 `APP_PORT=8080` 或前置 Nginx 反向代理。
- 检查监听：`ss -lntp | grep 8080`；容器端口映射：`docker compose ps`。
- 若前面有 Nginx，确保传递 `X-Forwarded-For`，应用已启用 `forward-headers-strategy: framework`，提交审计可拿到真实 IP。

### 5.4 容器 unhealthy

```bash
docker compose logs -f ctf
docker inspect --format '{{json .State.Health}}' ctf-platform
```

常见原因：

| 现象 | 原因 / 处理 |
|---|---|
| 启动即退出，日志有 Flyway 错误 | 数据卷内 H2 文件与迁移不匹配；备份后删除卷重建（演示环境可 `down -v`） |
| `Port is already allocated` | 宿主机端口被占用，改 `APP_PORT` 或停止占用进程 |
| 健康检查一直 starting | 首次启动较慢（BCrypt/种子数据），等待 60s；持续失败看日志 |
| `Permission denied` 写 `/app/data` | 数据卷属主异常；`docker compose down` 后重建卷 |
| 登录后重启即 401 | `JWT_SECRET` 为空导致临时密钥变化；在 `.env` 固定 `JWT_SECRET` |
| 页面能开但接口 401 | token 过期或密钥变更，重新登录 |

### 5.5 演示数据没有出现

- 仅当数据库为空且 `DEMO_SEED_ENABLED=true`（默认）时写入。
- 已有数据卷时不会重复写入。重置：`docker compose down -v` 后重新 `up -d`。

### 5.6 SSH 部署失败

- `SERVER_SSH_KEY` 必须是私钥全文，包含首尾行，且无多余空格/换行错位。
- 服务器 `~/.ssh/authorized_keys` 权限应为 600，`.ssh` 为 700。
- 用户不在 docker 组时会提示 permission denied 连接 docker socket。
- 内网服务器需 GitHub 能访问到（公网 IP 或反向隧道）。

### 5.7 可选数据库（MySQL / PostgreSQL）

默认路径永远是 H2，外部数据库仅作为可选：

```bash
# .env
SPRING_PROFILES_ACTIVE=mysql
DB_URL=jdbc:mysql://127.0.0.1:3306/ctf?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false
DB_USERNAME=ctf
DB_PASSWORD=******
```

- 数据库需预先创建，Flyway 会自动建表。
- MySQL 8 / PostgreSQL 14+ 已验证 SQL 兼容性设计（UUID 存 VARCHAR(36)、无数据库特有语法）。
- 容器访问宿主机数据库时，`127.0.0.1` 需改为 `host.docker.internal` 或宿主机内网 IP。

## 6. 备份、升级与回滚

### 备份数据卷

```bash
docker run --rm -v ctf-platform-data:/data -v $(pwd):/backup alpine \
  tar czf /backup/ctf-data-$(date +%Y%m%d).tar.gz -C /data .
```

恢复：

```bash
docker compose down
docker run --rm -v ctf-platform-data:/data -v $(pwd):/backup alpine \
  sh -c "cd /data && tar xzf /backup/ctf-data-YYYYMMDD.tar.gz"
docker compose up -d
```

### 升级

推新 tag（如 `v1.0.1`）→ Deploy 自动拉取新镜像并重启。数据卷保留。

### 回滚

```bash
cd /opt/ctf
sed -i 's|^APP_IMAGE=.*|APP_IMAGE=ghcr.io/kkk287/ctf-platform:v1.0.0|' .env
docker compose -f docker-compose.server.yml up -d
```

> 注意：若新版本包含破坏性 Flyway 迁移，回滚前需恢复对应数据库备份。
