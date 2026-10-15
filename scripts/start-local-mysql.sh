#!/bin/zsh

set -eu

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
COMPOSE_FILE="${PROJECT_DIR}/docker/compose.yml"
SERVICE_NAME="mysql"
HOST_PORT="3307"

if ! command -v docker >/dev/null 2>&1; then
  echo "未找到 Docker。请先安装并启动 Docker Desktop。"
  exit 1
fi

if ! docker info >/dev/null 2>&1; then
  echo "Docker 尚未启动。请先打开 Docker Desktop，等待其显示为 Running 后再运行此脚本。"
  exit 1
fi

compose=(docker compose --project-directory "${PROJECT_DIR}" -f "${COMPOSE_FILE}")

echo "正在启动 NDI 专用 MySQL（127.0.0.1:${HOST_PORT}）…"
"${compose[@]}" up -d "${SERVICE_NAME}"

container_id="$("${compose[@]}" ps -q "${SERVICE_NAME}")"
if [[ -z "${container_id}" ]]; then
  echo "未获取到 NDI MySQL 容器，请检查 Docker Compose 输出。"
  exit 1
fi

for attempt in {1..30}; do
  health_status="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "${container_id}")"
  if [[ "${health_status}" == "healthy" ]]; then
    echo "NDI MySQL 已就绪。连接地址：127.0.0.1:${HOST_PORT}；数据库：ndi；用户名：ndi"
    exit 0
  fi
  sleep 1
done

echo "NDI MySQL 在 30 秒内未通过健康检查。"
"${compose[@]}" logs --tail=100 "${SERVICE_NAME}"
exit 1
