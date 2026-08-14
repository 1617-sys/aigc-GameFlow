# Docker 部署手册

## 启动

```powershell
Copy-Item .env.template .env
docker compose config --quiet
docker compose up -d --build
docker compose ps
```

Compose 会启动 `nginx`、`app-1`、`app-2`、`mysql`、`redis`、`rabbitmq` 和 `minio`。Nginx 镜像会先构建 Vue 前端，再托管静态文件，并把 `/user`、`/api` 请求负载均衡到两个后端实例。浏览器统一访问 `http://localhost:8080`。

为避免误用付费服务，Compose 会覆盖 `.env` 并强制使用 Mock Provider。要联调 ComfyUI 或万相，请在本地开发环境显式切换，不要用真实 Provider 做吞吐压测。

## 观察

```powershell
docker compose logs -f app-1 app-2 nginx
docker compose ps
```

- RabbitMQ：`http://localhost:15672`，guest / guest
- MinIO：`http://localhost:9001`，minioadmin / minioadmin

## 已有数据库升级

全新数据卷会自动执行 `src/main/resources/schema.sql`。旧数据库需要先备份，再执行：

```text
scripts/migrate_v2.sql
```

## 端口冲突

可在 `.env` 中覆盖：`MYSQL_EXPOSED_PORT`、`REDIS_EXPOSED_PORT`、`RABBITMQ_AMQP_PORT`、`RABBITMQ_MANAGEMENT_PORT`、`MINIO_API_PORT`、`MINIO_CONSOLE_PORT`。

## 停止

```powershell
docker compose down
```

不要随意执行 `docker compose down -v`，它会删除 MySQL 与 MinIO 数据卷。
