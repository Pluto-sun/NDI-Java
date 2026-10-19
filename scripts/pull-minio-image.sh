#!/usr/bin/env bash

set -euo pipefail

MINIO_IMAGE="netiedge/minio:RELEASE.2025-01-20T14-49-07Z@sha256:e6c282005f6c8496256f8fd07e9a8b82073344c217ea87436d9af888db67eb0e"

if ! command -v docker >/dev/null 2>&1; then
  echo "未找到 Docker。请先安装 Docker Engine 或 Docker Desktop。"
  exit 1
fi

if ! docker info >/dev/null 2>&1; then
  echo "Docker 尚未启动，或当前账号没有访问 Docker 的权限。"
  exit 1
fi

echo "正在拉取固定版本的 MinIO 镜像：${MINIO_IMAGE}"
docker pull "${MINIO_IMAGE}"
echo "MinIO 镜像已就绪：${MINIO_IMAGE}"
