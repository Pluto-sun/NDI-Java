#!/usr/bin/env bash
# 对真实发布 JAR 做 prod 空库启动、首次管理员和会话认证验收，仅使用隔离依赖。
set -euo pipefail
umask 077
repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
archive=${1:?请指定本地打包生成的 tar.gz}
[[ -f "$archive" ]] || { echo '交付包不存在。' >&2; exit 1; }
compose_file="$repo_dir/docker/compose.e2e.yml"
[[ -z "$(docker compose -f "$compose_file" ps -a -q)" ]] || { echo '已有隔离验收容器，请先结束其任务。' >&2; exit 1; }
for port in 13317 19000 18082; do
  if lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; then echo "隔离验收端口 $port 被占用。" >&2; exit 1; fi
done
work="$(mktemp -d "$repo_dir/tmp/ndi-package.smoke.XXXXXX")"
backend_pid=''
compose_started=false
cleanup() {
  if [[ -n "$backend_pid" ]] && kill -0 "$backend_pid" 2>/dev/null; then
    kill "$backend_pid"
    wait "$backend_pid" 2>/dev/null || true
  fi
  if [[ "$compose_started" == true ]]; then docker compose -f "$compose_file" down --remove-orphans; fi
  echo "发布验收日志保留在：$work"
}
trap cleanup EXIT
tar -xzf "$archive" -C "$work"
release_dirs=("$work"/ndi-release-*)
[[ ${#release_dirs[@]} -eq 1 && -d "${release_dirs[0]}" ]] || { echo '交付目录无效。'; exit 1; }
release_dir=${release_dirs[0]}
# 只读取现有清单做校验，不重新生成，以免掩盖文件损坏。
node --input-type=module - "$release_dir" <<'JS'
import fs from 'node:fs';
import crypto from 'node:crypto';
import path from 'node:path';
const root = process.argv[2];
for (const line of fs.readFileSync(path.join(root, 'SHA256SUMS'), 'utf8').trim().split('\n')) {
  const [hash, file] = line.split('  ');
  if (!file?.startsWith('./') || file.includes('..') || crypto.createHash('sha256').update(fs.readFileSync(path.join(root, file))).digest('hex') !== hash)
    throw new Error('交付包校验失败');
}
JS
compose_started=true
docker compose -f "$compose_file" up -d --wait --wait-timeout 90
NDI_DATASOURCE_URL='jdbc:mysql://127.0.0.1:13317/ndi?serverTimezone=UTC' \
NDI_DATASOURCE_USERNAME=ndi NDI_DATASOURCE_PASSWORD=ndi_e2e_password \
NDI_MINIO_ENDPOINT=http://127.0.0.1:19000 NDI_MINIO_PUBLIC_ENDPOINT=http://127.0.0.1:19000 \
NDI_MINIO_ACCESS_KEY=ndi_e2e_admin NDI_MINIO_SECRET_KEY=ndi_e2e_minio_password NDI_MINIO_BUCKET=ndi-documents \
NDI_BOOTSTRAP_ADMIN_LOGIN=release-smoke-admin NDI_BOOTSTRAP_ADMIN_PASSWORD=release-smoke-test-password \
NDI_AUTH_MODE=SESSION NDI_SECURE_COOKIE=false NDI_APPROVAL_REMINDERS_ENABLED=false NDI_PROJECT_INACTIVITY_ENABLED=false \
java -jar "$release_dir/backend/app.jar" --spring.profiles.active=prod --server.address=127.0.0.1 --server.port=18082 \
  > "$work/backend.log" 2>&1 &
backend_pid=$!
ready=false
for attempt in {1..90}; do
  kill -0 "$backend_pid" 2>/dev/null || { echo '发布 JAR 启动失败，请查看验收日志。'; exit 1; }
  if curl --noproxy '*' -fsS http://127.0.0.1:18082/actuator/health >/dev/null 2>&1; then ready=true; break; fi
  sleep 1
done
[[ "$ready" == true ]] || { echo '发布 JAR 启动超时。'; exit 1; }
curl --noproxy '*' -fsS http://127.0.0.1:18082/api/v1/auth/mode | grep -q SESSION
curl --noproxy '*' -fsS -c "$work/cookies" http://127.0.0.1:18082/api/v1/auth/csrf > "$work/csrf.json"
token="$(node -p 'JSON.parse(require("fs").readFileSync(process.argv[1],"utf8")).data.token' "$work/csrf.json")"
printf '%s\n' '{"loginName":"release-smoke-admin","password":"release-smoke-test-password"}' > "$work/login.json"
logged_in=false
for attempt in {1..3}; do
  # 根健康端点可能先于 ApplicationReadyEvent 管理员初始化完成，允许短暂等待。
  status="$(curl --noproxy '*' -sS -b "$work/cookies" -c "$work/cookies" -H "X-XSRF-TOKEN: $token" \
    -H 'Content-Type: application/json' --data-binary "@$work/login.json" -o "$work/login-result.json" -w '%{http_code}' http://127.0.0.1:18082/api/v1/auth/sessions)"
  if [[ "$status" == 201 ]]; then logged_in=true; break; fi
  sleep 1
done
[[ "$logged_in" == true ]] || { echo '首次管理员登录验收失败。'; exit 1; }
curl --noproxy '*' -fsS -b "$work/cookies" http://127.0.0.1:18082/api/v1/auth/me | grep -q release-smoke-admin
status="$(curl --noproxy '*' -sS -o /dev/null -w '%{http_code}' http://127.0.0.1:18082/api/v1/projects)"
[[ "$status" == 401 ]]
status="$(curl --noproxy '*' -sS -b "$work/cookies" -o /dev/null -w '%{http_code}' http://127.0.0.1:18082/api/v1/projects/dev/actors)"
[[ "$status" == 403 ]]
echo '通过：真实发布 JAR 的 prod 空库迁移、管理员初始化、CSRF、登录会话、未登录保护与开发身份禁用。'
echo '此验收的 HTTP/Cookie 例外仅作用于隔离进程，部署模板仍要求 HTTPS 和安全 Cookie。'
