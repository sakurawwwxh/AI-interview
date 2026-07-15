# 运行与运维说明

## 本地开发

默认不启用 `production` Profile，继续使用本机的 `application.yml` 与现有启动方式。

## 生产启动

生产环境必须设置 `SPRING_PROFILES_ACTIVE=production`。该 Profile 不保存任何密钥，并要求由部署平台注入以下变量：

- `POSTGRES_URL`、`POSTGRES_USER`、`POSTGRES_PASSWORD`
- `REDIS_ADDRESS`（例如 `redis://redis.example.internal:6379`）
- `DASHSCOPE_API_KEY`
- `APP_JWT_SECRET`
- `APP_STORAGE_ENDPOINT`、`APP_STORAGE_ACCESS_KEY`、`APP_STORAGE_SECRET_KEY`、`APP_STORAGE_BUCKET`
- `CORS_ALLOWED_ORIGINS`

生产 Profile 使用 `spring.jpa.hibernate.ddl-auto=validate`：应用只校验数据库结构，不会修改结构。任何表结构调整应先经过受控的数据库迁移流程，再部署应用。

## 健康检查与指标

- `GET /actuator/health/liveness`：存活探针。
- `GET /actuator/health/readiness`：就绪探针，包含数据库和 Redis 可用性。
- `GET /actuator/info`：最小化应用信息。
- `GET /actuator/prometheus`：Prometheus 指标；需要 `ADMIN` JWT。

除健康检查和 info 外，所有 Actuator 端点都要求 `ADMIN` 角色。不要将管理端口或指标端点直接暴露到公网。

## 请求追踪

每个响应都会返回 `X-Request-Id`，同一值会写入后端日志。上游网关可传入符合 `[A-Za-z0-9._-]{8,128}` 的 `X-Request-Id`；其他值会被替换为服务端生成的 UUID。

## 质量门禁

GitHub Actions 工作流会在 push 和 pull request 时执行：

- `mvn -B -ntp -pl app test`
- `pnpm install --frozen-lockfile && pnpm run build`
