#!/usr/bin/env bash
# 仅在一次性 Linux 容器内运行；外部服务用替身，绝不操作宿主机服务。
set -euo pipefail
[[ -f /.dockerenv && "${NDI_DEPLOY_TEST_CONTAINER:-}" == 1 ]] || { echo '只能在显式指定的一次性 Docker 容器运行。' >&2; exit 1; }
mkdir -p /test-bin /test-control /bundle /certs /etc/nginx/conf.d /etc/systemd/system /var/log/nginx
trap 'result=$?; if (( result != 0 )); then echo "部署测试失败：$result"; cat /test-control/output 2>/dev/null || true; fi' EXIT
printf 'test certificate\n' > /certs/app.pem
cp /certs/app.pem /certs/key.pem
cp /certs/app.pem /etc/nginx/mime.types
export PATH="/test-bin:$PATH"
export NDI_DEPLOY_TEST_CONTAINER

make_mock() { printf '%s\n' '#!/bin/bash' 'set -eu' "$2" > "/test-bin/$1"; chmod 0755 "/test-bin/$1"; }
make_mock java 'echo '\''openjdk version "21.0.1"'\'' >&2'
make_mock sleep 'exit 0'
make_mock nginx '
if [[ "${1:-}" == -T ]]; then echo "# configuration file /etc/nginx/conf.d/ndi.conf:"; exit 0; fi
[[ ! -f /test-control/fail-nginx ]]
'
make_mock systemd-analyze 'exit 0'
make_mock ss '
echo "State Recv-Q Send-Q LocalAddress:Port PeerAddress:Port Process"
echo '\''LISTEN 0 128 *:80 *:* users:(("nginx",pid=99,fd=3))'\''
if [[ -f /test-control/foreign-port ]]; then
 echo '\''LISTEN 0 128 127.0.0.1:8080 0.0.0.0:* users:(("other",pid=66,fd=3))'\''
elif [[ -f /test-control/running ]]; then
 echo '\''LISTEN 0 128 127.0.0.1:8080 0.0.0.0:* users:(("java",pid=77,fd=3))'\''
fi
'
make_mock systemctl '
echo "$*" >> /test-control/systemctl.log
case "$1" in
 show) if [[ -f /test-control/running ]]; then echo MainPID=77; else echo MainPID=0; fi ;;
 stop) rm -f /test-control/running ;;
 start) [[ "$2" != ndi.service ]] || touch /test-control/running ;;
 is-active) [[ "${*: -1}" == nginx || -f /test-control/running ]] ;;
 *) exit 0 ;;
esac
'
make_mock curl '
url="${*: -1}"
case "$url" in
 */actuator/health) [[ ! -f /test-control/fail-health ]] || exit 22; echo '\''{"status":"UP"}'\'' ;;
 */minio/health/ready) [[ ! -f /test-control/fail-minio ]] ;;
 */api/v1/auth/mode) echo '\''{"data":{"mode":"SESSION"}}'\'' ;;
 */api/v1/projects) printf 401 ;;
 *) echo '\''<html><div id="root"></div></html>'\'' ;;
esac
'

revision=0
manifest() { (cd /bundle && find . -type f ! -name SHA256SUMS -exec sha256sum {} + > SHA256SUMS); }
new_bundle() {
  revision=$((revision+1))
  cp /source/deploy.sh /bundle/deploy.sh
  cp -R /source/templates /bundle/
  cp /source/*.example /bundle/
  mkdir -p /bundle/backend /bundle/frontend/assets
  printf 'fake JAR %s\n' "$revision" > /bundle/backend/app.jar
  printf '<div id="root"></div>\n' > /bundle/frontend/index.html
  printf 'fake JS\n' > /bundle/frontend/assets/index-hash.js
  release_id="$(printf '20261023T1500%02dZ-abcdef012345' "$revision")"
  printf 'RELEASE_ID=%s\n' "$release_id" > /bundle/release.txt
  manifest
}
run_deploy() { bash /bundle/deploy.sh "$@" > /test-control/output 2>&1; }
expect_failure() {
  if run_deploy "$1"; then echo "应失败但成功：$2"; exit 1; fi
  grep -q "$2" /test-control/output || { cat /test-control/output; exit 1; }
}
stop_count() { grep -c '^stop ndi.service$' /test-control/systemctl.log 2>/dev/null || true; }
assert_no_new_stop() { [[ "$(stop_count)" == "$before" ]] || { echo '预检错误停止了服务'; exit 1; }; }

new_bundle
run_deploy --init-config
[[ "$(stat -c %a /etc/ndi/application.env)" == 600 ]]
printf '# keep marker\n' >> /etc/ndi/deploy.conf
run_deploy --init-config
grep -q 'keep marker' /etc/ndi/deploy.conf
expect_failure --check 'IPv4'
echo '通过：配置初始化幂等、占位配置拒绝。'

printf '%s\n' 'DEPLOY_MODE=https' 'APP_HOST=app.example.test' 'FILES_HOST=files.example.test' \
  'APP_CERT=/certs/app.pem' 'APP_KEY=/certs/key.pem' 'FILES_CERT=/certs/app.pem' 'FILES_KEY=/certs/key.pem' \
  'JAVA_BIN=/test-bin/java' 'APP_PORT=8080' 'MINIO_UPSTREAM=127.0.0.1:9000' 'START_TIMEOUT=30' > /etc/ndi/deploy.conf
printf 'DATABASE_BACKUP_CONFIRMED=yes\n' >> /etc/ndi/deploy.conf
printf '%s\n' 'SPRING_PROFILES_ACTIVE=prod' 'NDI_AUTH_MODE=SESSION' 'NDI_SECURE_COOKIE=true' \
  'NDI_DATASOURCE_URL="jdbc:mysql://127.0.0.1:3306/ndi?serverTimezone=UTC"' \
  'NDI_DATASOURCE_USERNAME=ndi' 'NDI_DATASOURCE_PASSWORD=private_test_password' \
  'NDI_MINIO_ENDPOINT=http://127.0.0.1:9000' 'NDI_MINIO_PUBLIC_ENDPOINT=https://files.example.test' \
  'NDI_MINIO_ACCESS_KEY=ndi_test_key' 'NDI_MINIO_SECRET_KEY=private_test_secret' \
  'NDI_MINIO_BUCKET=ndi-documents' > /etc/ndi/application.env
run_deploy --check
[[ ! -e /opt/ndi/backend-current && ! -e /etc/nginx/conf.d/ndi.conf ]]
before="$(stop_count)"
flock /run/ndi-deploy.lock /bin/bash -c 'touch /test-control/lock-held; /bin/sleep 2' &
lock_pid=$!
while [[ ! -f /test-control/lock-held ]]; do /bin/sleep 0.1; done
expect_failure --deploy '已有 NDI 部署'
wait "$lock_pid"
assert_no_new_stop
echo altered >> /bundle/backend/app.jar
expect_failure --deploy '校验失败'
assert_no_new_stop
manifest
touch /test-control/foreign-port
expect_failure --deploy '未托管进程'
assert_no_new_stop
rm /test-control/foreign-port
printf 'unmanaged\n' > /etc/systemd/system/ndi.service
expect_failure --deploy '非本脚本管理'
assert_no_new_stop
rm /etc/systemd/system/ndi.service
touch /test-control/fail-nginx
expect_failure --deploy '现有 Nginx'
assert_no_new_stop
rm /test-control/fail-nginx
printf 'extra\n' > /bundle/extra.txt
expect_failure --deploy '未校验文件'
assert_no_new_stop
rm /bundle/extra.txt
touch /test-control/fail-minio
expect_failure --deploy 'MinIO 未就绪'
assert_no_new_stop
rm /test-control/fail-minio
echo '通过：只读预检、校验损坏、外部端口、未托管服务、无效 Nginx、额外文件和 MinIO 未就绪均不停止服务。'

run_deploy --deploy
[[ "$(readlink /opt/ndi/backend-current)" == "/opt/ndi/releases/$release_id/backend" ]]
[[ "$(readlink /opt/ndi/frontend-current)" == "/opt/ndi/releases/$release_id/frontend" ]]
[[ -f /test-control/running && -f /opt/ndi/shared/assets/index-hash.js ]]
grep -q 'proxy_pass http://127.0.0.1:9000;' /etc/nginx/conf.d/ndi.conf
before="$(stop_count)"
original_hash="$(sha256sum /opt/ndi/backend-current/app.jar)"
run_deploy --deploy
[[ "$(stop_count)" == $((before+1)) ]]
[[ "$(sha256sum /opt/ndi/backend-current/app.jar)" == "$original_hash" ]]
echo '通过：首次部署、模板渲染、共享资源保留、同版本再次部署不覆盖运行文件。'

sed -i 's/DEPLOY_MODE=https/DEPLOY_MODE=http/' /etc/ndi/deploy.conf
printf '%s\n' 'PUBLIC_IP=192.0.2.10' 'WEB_PORT=8088' 'FILES_PORT=9002' >> /etc/ndi/deploy.conf
sed -i 's/NDI_SECURE_COOKIE=true/NDI_SECURE_COOKIE=false/;s@https://files.example.test@http://192.0.2.10:9002@' /etc/ndi/application.env
run_deploy --check
run_deploy --deploy
grep -q 'listen 8088;' /etc/nginx/conf.d/ndi.conf
grep -q 'listen 9002;' /etc/nginx/conf.d/ndi.conf
! grep -q ssl_certificate /etc/nginx/conf.d/ndi.conf
sed -i 's/NDI_SECURE_COOKIE=false/NDI_SECURE_COOKIE=true/' /etc/ndi/application.env
expect_failure --check 'COOKIE'
sed -i 's/NDI_SECURE_COOKIE=true/NDI_SECURE_COOKIE=false/' /etc/ndi/application.env
echo '通过：HTTP 双端口无证书部署，以及 Cookie 协议不匹配拦截。'

if [[ -f /check-env.sh ]]; then
  make_mock timeout 'shift; if [[ "$1" == bash ]]; then exit 0; fi; exec "$@"'
  make_mock mysql 'echo 8.4.0'
  make_mock mc 'if [[ "$1" == cors ]]; then echo "<AllowedOrigin>http://192.0.2.10:8088</AllowedOrigin>"; else echo '\''Access permission is `private`'\''; fi'
  printf '%s\n' 'UNUSED=$(touch /test-control/pwned)' >> /etc/ndi/application.env
  before="$(stop_count)"
  env_hash="$(sha256sum /etc/ndi/application.env)"
  bash /check-env.sh --mysql-login-path ndi --minio-alias ndi-app > /test-control/checker-output
  grep -q 'FAIL=0 UNKNOWN=0' /test-control/checker-output
  [[ ! -e /test-control/pwned && "$(sha256sum /etc/ndi/application.env)" == "$env_hash" ]]
  ! grep -Eq 'private_test_password|private_test_secret' /test-control/checker-output
  assert_no_new_stop
  touch /test-control/fail-minio
  checker_status=0
  bash /check-env.sh > /test-control/checker-output || checker_status=$?
  [[ "$checker_status" == 1 ]]
  grep -q 'MinIO 就绪探针失败' /test-control/checker-output
  rm /test-control/fail-minio
  checker_status=0
  bash /check-env.sh > /test-control/checker-output || checker_status=$?
  [[ "$checker_status" == 2 ]]
  grep -q '未执行 SQL 登录检查' /test-control/checker-output
  echo '通过：环境检测退出码 0/1/2、配置不执行、不修改文件/服务及不输出密码。'
fi

new_bundle
before="$(stop_count)"
run_deploy --deploy
[[ "$(stop_count)" == $((before+1)) ]]
old_frontend="$(readlink /opt/ndi/frontend-current)"
[[ "$old_frontend" == "/opt/ndi/releases/$release_id/frontend" ]]
new_bundle
touch /test-control/fail-health
expect_failure --deploy '健康检查超时'
[[ "$(readlink /opt/ndi/frontend-current)" == "$old_frontend" ]]
[[ "$(readlink /opt/ndi/backend-current)" == "/opt/ndi/releases/$release_id/backend" ]]
[[ -f /opt/ndi/releases/$release_id/previous.txt ]]
! grep -Eq 'private_test_password|private_test_secret' /test-control/output
echo '通过：运行中更新、后端失败时前端不切换、不自动回滚、敏感值不打印。'
