#!/bin/zsh
set -eu

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
COMPOSE_FILE="${PROJECT_DIR}/docker/compose.e2e.yml"
backend_pid=""
compose_started=false
backup_directory=""

cleanup() {
  if [[ -n "${backend_pid}" ]] && kill -0 "${backend_pid}" 2>/dev/null; then
    kill "${backend_pid}"
    wait "${backend_pid}" 2>/dev/null || true
  fi
  if [[ "${compose_started}" == true ]]; then
    docker compose -f "${COMPOSE_FILE}" down --remove-orphans
  fi
  if [[ -n "${backup_directory}" && "${backup_directory}" == /tmp/ndi-java-e2e-backups.* && -d "${backup_directory}" ]]; then
    find "${backup_directory}" -maxdepth 1 -type f \( -name '*.zip' -o -name '*.partial' \) -delete
    rmdir "${backup_directory}" || echo "隔离备份目录存在其他文件，已保留：${backup_directory}"
  fi
}
trap cleanup EXIT INT TERM

if [[ -n "$(docker compose -f "${COMPOSE_FILE}" ps -a -q)" ]]; then
  echo "已有 ndi-java-e2e 验收容器，请先结束原验收进程再运行。"
  exit 1
fi
if lsof -nP -iTCP:18080 -sTCP:LISTEN >/dev/null 2>&1; then
  echo "18080 端口已被占用，未启动验收服务。"
  exit 1
fi

cd "${PROJECT_DIR}"
if [[ "${NDI_E2E_SKIP_BUILD:-0}" != 1 ]]; then
  ./mvnw -q -DskipTests package
fi
mkdir -p "${PROJECT_DIR}/tmp/e2e-logs"
compose_started=true
backup_directory="$(mktemp -d /tmp/ndi-java-e2e-backups.XXXXXXXX)"
docker compose -f "${COMPOSE_FILE}" up -d --wait --wait-timeout 90

NDI_DATASOURCE_URL='jdbc:mysql://127.0.0.1:13317/ndi?serverTimezone=UTC' \
NDI_DATASOURCE_USERNAME=ndi NDI_DATASOURCE_PASSWORD=ndi_e2e_password \
NDI_MINIO_ENDPOINT=http://127.0.0.1:19000 NDI_MINIO_PUBLIC_ENDPOINT=http://127.0.0.1:19000 \
NDI_MINIO_ACCESS_KEY=ndi_e2e_admin NDI_MINIO_SECRET_KEY=ndi_e2e_minio_password \
NDI_APPROVAL_REMINDERS_ENABLED=false NDI_PROJECT_INACTIVITY_ENABLED=false \
NDI_BACKUP_ENABLED=true NDI_BACKUP_DIRECTORY="${backup_directory}" \
NDI_AUTH_MODE=${NDI_E2E_AUTH_MODE:-LOCAL_ACTOR} \
java -jar target/ndi-0.1.0-SNAPSHOT.jar --spring.profiles.active=local --server.port=18080 \
  > "${PROJECT_DIR}/tmp/e2e-logs/backend.log" 2>&1 &
backend_pid=$!

ready=false
for attempt in {1..60}; do
  if ! kill -0 "${backend_pid}" 2>/dev/null; then
    echo "验收服务启动失败，请查看 tmp/e2e-logs/backend.log。"
    exit 1
  fi
  if curl -fsS http://127.0.0.1:18080/actuator/health >/dev/null 2>&1; then
    ready=true
    break
  fi
  sleep 1
done
if [[ "${ready}" != true ]]; then
  echo "验收服务未就绪，请查看 tmp/e2e-logs/backend.log。"
  exit 1
fi

docker compose -f "${COMPOSE_FILE}" exec -T -e MYSQL_PWD=ndi_e2e_password mysql \
  mysql --default-character-set=utf8mb4 -undi ndi < scripts/seed-local-project-demo.sql
docker compose -f "${COMPOSE_FILE}" exec -T -e MYSQL_PWD=ndi_e2e_password mysql \
  mysql --default-character-set=utf8mb4 -undi ndi < scripts/seed-project-e2e.sql
if [[ "${NDI_E2E_AUTH_MODE:-LOCAL_ACTOR}" == SESSION ]]; then
  docker compose -f "${COMPOSE_FILE}" exec -T -e MYSQL_PWD=ndi_e2e_password mysql \
    mysql --default-character-set=utf8mb4 -undi ndi < scripts/seed-session-e2e.sql
fi

cd frontend
NDI_E2E_ENABLED=1 npm run test:e2e -- "$@"
