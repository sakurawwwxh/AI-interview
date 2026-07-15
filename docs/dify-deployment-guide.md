# Dify 部署指南

**目标服务器**: 阿里云 8.163.23.204  
**部署方式**: Docker Compose  
**日期**: 2026-07-11

---

## 1. 前置要求

### 1.1 服务器配置

- **操作系统**: Linux (推荐 Ubuntu 20.04+ / CentOS 7+)
- **内存**: >= 8GB
- **磁盘**: >= 50GB
- **CPU**: >= 4核

### 1.2 软件要求

- Docker >= 20.10
- Docker Compose >= 2.0
- Git

### 1.3 网络要求

- 开放端口: 80 (HTTP)
- 阿里云安全组配置允许 80 端口访问

---

## 2. 安装 Docker

```bash
# Ubuntu/Debian
curl -fsSL https://get.docker.com | sh
sudo systemctl start docker
sudo systemctl enable docker

# 验证安装
docker --version
docker compose version
```

---

## 3. 部署步骤

### 3.1 创建部署目录

```bash
mkdir -p /opt/dify
cd /opt/dify
```

### 3.2 下载 Dify 代码

```bash
git clone https://github.com/langgenius/dify.git .
```

### 3.3 进入 Docker 目录

```bash
cd docker
```

### 3.4 复制环境变量文件

```bash
cp .env.example .env
```

### 3.5 修改环境变量

```bash
vi .env
```

修改以下关键配置：

```bash
# 密钥（必须修改为随机字符串）
SECRET_KEY=your-random-secret-key-here

# 数据库密码
DB_USERNAME=postgres
DB_PASSWORD=your-strong-password
POSTGRES_PASSWORD=your-strong-password

# Redis 密码
REDIS_PASSWORD=your-redis-password

# 应用访问地址
CONSOLE_WEB_URL=http://8.163.23.204
SERVICE_API_URL=http://8.163.23.204
APP_WEB_URL=http://8.163.23.204
```

生成随机密钥：

```bash
openssl rand -hex 32
```

### 3.6 启动服务

```bash
docker compose up -d
```

### 3.7 查看服务状态

```bash
docker compose ps
```

预期输出：

```
NAME                  IMAGE                             STATUS
docker-api-1          langgenius/dify-api:latest        Up
docker-web-1          langgenius/dify-web:latest        Up
docker-worker-1       langgenius/dify-api:latest        Up
docker-db-1           postgres:15-alpine                Up (healthy)
docker-redis-1        redis:7-alpine                    Up (healthy)
docker-nginx-1        nginx:latest                      Up
docker-sandbox-1      langgenius/dify-sandbox:latest    Up
docker-weaviate-1     semitechnologies/weaviate:latest  Up
```

### 3.8 查看日志

```bash
# 查看所有服务日志
docker compose logs -f

# 查看特定服务日志
docker compose logs -f api
docker compose logs -f web
```

---

## 4. 初始化配置

### 4.1 访问 Dify 控制台

浏览器打开：`http://8.163.23.204`

### 4.2 创建管理员账户

首次访问会进入初始化页面，创建管理员账户：

- **邮箱**: admin@example.com
- **密码**: 设置一个强密码
- **用户名**: admin

### 4.3 配置模型供应商

1. 进入 **设置** → **模型供应商**
2. 添加模型供应商（如 OpenAI、阿里云等）

**阿里云 DashScope 配置**：

- 供应商类型: OpenAI API Compatible
- API Base URL: `https://dashscope.aliyuncs.com/compatible-mode`
- API Key: 你的 DashScope API Key
- 模型名称: qwen-plus

### 4.4 创建知识库

1. 进入 **知识库**
2. 点击 **创建知识库**
3. 输入知识库名称（如 "interview-guide"）
4. 上传文档或手动添加内容

### 4.5 获取 API Key

1. 进入 **设置** → **API 密钥**
2. 点击 **创建密钥**
3. 复制保存 API Key（格式：`app-xxxxxxxxxx`）

### 4.6 获取知识库 ID

1. 进入 **知识库**
2. 点击目标知识库
3. 从 URL 中获取 ID（格式：`xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx`）

---

## 5. 防火墙配置

### 5.1 阿里云安全组

1. 登录阿里云控制台
2. 进入 ECS 实例 → 安全组
3. 添加入站规则：
   - 协议: TCP
   - 端口: 80
   - 授权对象: 0.0.0.0/0

### 5.2 服务器防火墙

```bash
# firewalld
firewall-cmd --permanent --add-port=80/tcp
firewall-cmd --reload

# iptables
iptables -A INPUT -p tcp --dport 80 -j ACCEPT
```

---

## 6. 配置 Spring Boot 应用

### 6.1 设置环境变量

```bash
# Linux/Mac
export DIFY_API_KEY=app-xxxxxxxxxx
export DIFY_DATASET_ID=xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx

# Windows PowerShell
$env:DIFY_API_KEY="app-xxxxxxxxxx"
$env:DIFY_DATASET_ID="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
```

### 6.2 application.yml 配置

```yaml
dify:
  api-url: http://8.163.23.204/v1
  api-key: ${DIFY_API_KEY:}
  dataset-id: ${DIFY_DATASET_ID:}
  sync:
    enabled: true
    interval: 600000  # 10分钟（毫秒）
    retry-count: 3
    retry-delay: 5000
```

---

## 7. 常用运维命令

### 7.1 服务管理

```bash
# 启动服务
docker compose up -d

# 停止服务
docker compose down

# 重启服务
docker compose restart

# 重启特定服务
docker compose restart api
docker compose restart web
```

### 7.2 日志查看

```bash
# 查看所有日志
docker compose logs -f

# 查看 API 日志
docker compose logs -f api

# 查看最近100行日志
docker compose logs --tail 100 api
```

### 7.3 更新版本

```bash
cd /opt/dify
git pull origin main
cd docker
docker compose down
docker compose pull
docker compose up -d
```

### 7.4 数据库备份

```bash
# 备份 PostgreSQL
docker compose exec db pg_dump -U postgres dify > backup_$(date +%Y%m%d).sql

# 恢复 PostgreSQL
cat backup_20260711.sql | docker compose exec -T db psql -U postgres -d dify
```

### 7.5 清理数据

```bash
# 停止并删除所有数据（慎用！）
docker compose down -v

# 清理未使用的镜像
docker image prune -a
```

---

## 8. 故障排查

### 8.1 服务无法启动

```bash
# 查看详细日志
docker compose logs api

# 检查磁盘空间
df -h

# 检查内存
free -h
```

### 8.2 数据库连接失败

```bash
# 检查数据库状态
docker compose ps db

# 测试数据库连接
docker compose exec db psql -U postgres -d dify -c "SELECT 1;"
```

### 8.3 Redis 连接失败

```bash
# 检查 Redis 状态
docker compose ps redis

# 测试 Redis 连接
docker compose exec redis redis-cli -a your-redis-password ping
```

### 8.4 API 调用失败

```bash
# 测试 API 健康检查
curl http://localhost/health

# 检查 API 日志
docker compose logs api | tail -50
```

---

## 9. 安全建议

1. **修改默认密码**: 立即修改所有默认密码
2. **使用 HTTPS**: 生产环境配置 SSL 证书
3. **限制访问**: 使用防火墙限制访问来源
4. **定期备份**: 配置自动化备份策略
5. **更新版本**: 定期更新到最新稳定版

---

## 10. 参考链接

- [Dify 官方文档](https://docs.dify.ai/)
- [Dify GitHub](https://github.com/langgenius/dify)
- [Docker Compose 文档](https://docs.docker.com/compose/)

---

## 附录: 快速部署脚本

```bash
#!/bin/bash

# 快速部署 Dify 脚本
# 使用方法: chmod +x deploy-dify.sh && ./deploy-dify.sh

set -e

echo "=== Dify 快速部署脚本 ==="

# 1. 检查 Docker
if ! command -v docker &> /dev/null; then
    echo "安装 Docker..."
    curl -fsSL https://get.docker.com | sh
    systemctl start docker
    systemctl enable docker
fi

# 2. 创建目录
mkdir -p /opt/dify
cd /opt/dify

# 3. 克隆代码
if [ ! -d ".git" ]; then
    echo "克隆 Dify 代码..."
    git clone https://github.com/langgenius/dify.git .
fi

# 4. 配置环境变量
cd docker
if [ ! -f ".env" ]; then
    cp .env.example .env
    SECRET_KEY=$(openssl rand -hex 32)
    sed -i "s/SECRET_KEY=.*/SECRET_KEY=$SECRET_KEY/" .env
    echo "已生成 SECRET_KEY: $SECRET_KEY"
fi

# 5. 启动服务
echo "启动 Dify 服务..."
docker compose up -d

# 6. 等待服务启动
echo "等待服务启动..."
sleep 30

# 7. 验证服务
echo "验证服务状态..."
docker compose ps

echo ""
echo "=== 部署完成 ==="
echo "访问地址: http://$(curl -s ifconfig.me)"
echo "请完成初始配置："
echo "1. 访问 Web 界面创建管理员账户"
echo "2. 配置模型供应商"
echo "3. 创建知识库"
echo "4. 获取 API Key"
```
