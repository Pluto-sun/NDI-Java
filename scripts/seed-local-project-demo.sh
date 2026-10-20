#!/bin/zsh

set -eu

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
COMPOSE_FILE="${PROJECT_DIR}/docker/compose.yml"
SEED_FILE="${PROJECT_DIR}/scripts/seed-local-project-demo.sql"

if ! docker compose --project-directory "${PROJECT_DIR}" -f "${COMPOSE_FILE}" ps --status running mysql | grep -q mysql; then
  echo "NDI 本地 MySQL 未运行，请先执行 scripts/start-local-mysql.sh。"
  exit 1
fi

docker compose --project-directory "${PROJECT_DIR}" -f "${COMPOSE_FILE}" exec -T mysql \
  mysql --default-character-set=utf8mb4 -undi -pndi_local_password ndi < "${SEED_FILE}"
echo "本地演示组织与账号已就绪。"
