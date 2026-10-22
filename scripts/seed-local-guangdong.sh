#!/bin/zsh
set -eu
TASK_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# 本脚本只允许对本地开发身份模式及项目自带容器执行，禁止用于生产初始化。
AUTH_MODE="$(curl --fail --silent http://127.0.0.1:8080/api/v1/auth/mode)"
if [[ "${AUTH_MODE}" != *'"LOCAL_ACTOR"'* ]]; then
  echo '仅允许在 8080 服务为 LOCAL_ACTOR 开发模式时初始化。' >&2
  exit 1
fi
docker compose -f "${TASK_ROOT}/docker/compose.yml" exec -T mysql \
  sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql --default-character-set=utf8mb4 -u"$MYSQL_USER" "$MYSQL_DATABASE"' \
  < "${TASK_ROOT}/scripts/seed-local-guangdong.sql"
echo '广东标准地市与全角色本地账号已就绪；未设置登录密码，请刷新开发身份列表。'
