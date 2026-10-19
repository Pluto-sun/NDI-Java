#!/usr/bin/env bash

set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
COMPOSE_FILE="${PROJECT_DIR}/docker/compose.yml"
SERVICE_NAME="minio"
API_PORT="9000"
CONSOLE_PORT="9001"

if ! command -v docker >/dev/null 2>&1; then
  echo "未找到 Docker。请先安装 Docker Engine 或 Docker Desktop。"
  exit 1
fi

if ! docker info >/dev/null 2>&1; then
  echo "Docker 尚未启动，或当前账号没有访问 Docker 的权限。"
  exit 1
fi

if ! docker compose version >/dev/null 2>&1; then
  echo "未找到 Docker Compose v2。请安装 docker compose 插件后重试。"
  exit 1
fi

compose=(docker compose --project-directory "${PROJECT_DIR}" -f "${COMPOSE_FILE}")

echo "正在启动 NDI 专用 MinIO（对象存储：127.0.0.1:${API_PORT}；控制台：127.0.0.1:${CONSOLE_PORT}）…"
"${compose[@]}" up -d "${SERVICE_NAME}"

container_id="$("${compose[@]}" ps -q "${SERVICE_NAME}")"
if [[ -z "${container_id}" ]]; then
  echo "未获取到 NDI MinIO 容器，请检查 Docker Compose 输出。"
  exit 1
fi

for attempt in {1..30}; do
  health_status="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "${container_id}")"
  if [[ "${health_status}" == "healthy" ]]; then
    echo "NDI MinIO 已就绪。S3 地址：http://127.0.0.1:${API_PORT}；控制台：http://127.0.0.1:${CONSOLE_PORT}"
    exit 0
  fi
  sleep 1
done

echo "NDI MinIO 在 30 秒内未通过健康检查。"
"${compose[@]}" logs --tail=100 "${SERVICE_NAME}"
exit 1
