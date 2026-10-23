#!/usr/bin/env bash
# 仅管理 NDI 的目录、站点和服务；支持 CentOS 7 的 Bash 4 与 systemd 219。
set -euo pipefail
umask 077
bundle_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
config_file=/etc/ndi/deploy.conf
app_env=/etc/ndi/application.env
install_dir=/opt/ndi
unit_file=/etc/systemd/system/ndi.service
site_file=/etc/nginx/conf.d/ndi.conf
mode="${1:---help}"
stage_dir=''
service_stopped=false
deployment_started=false
old_backend=''
old_frontend=''
reuse_release=false

fail() { echo "错误：$*" >&2; exit 1; }
usage() {
  echo '用法：sudo bash deploy.sh --init-config | --check | --deploy'
  echo '--init-config  只创建缺失的配置模板，不覆盖已有配置、不启动服务。'
  echo '--check        检查交付文件、配置、端口及 Nginx 候选配置，不切换服务。'
  echo '--deploy       检查通过后部署。已有托管服务先优雅停止；否则直接启动。'
}
case "$mode" in
  --help) usage; exit 0 ;;
  --init-config|--check|--deploy) ;;
  *) usage; exit 1 ;;
esac
[[ $# -eq 1 ]] || fail '不接受额外参数。'
[[ $EUID -eq 0 ]] || fail '请使用 sudo 执行服务器部署入口。'
[[ "$(uname -s)" == Linux ]] || fail '部署脚本只在 Linux 服务器运行。'

cleanup() {
  local result=$?
  if (( result != 0 )) && [[ "$deployment_started" == true ]]; then
    echo '部署未完成。保留版本和日志，请查看 journalctl -u ndi。' >&2
    echo "原后端：${old_backend:-无}；原前端：${old_frontend:-无}" >&2
    if [[ "$service_stopped" == true ]]; then
      echo '旧服务已停止；数据库迁移可能已执行，不自动回退 JAR 或重启旧版本。' >&2
    fi
  fi
  if [[ -n "$stage_dir" && "$stage_dir" == /var/tmp/ndi-deploy.* && -f "$stage_dir/.ndi-owned" ]]; then
    rm -rf -- "$stage_dir"
  fi
}
trap cleanup EXIT

check_owned_config() {
  local path=$1 permission
  [[ -f "$path" && ! -L "$path" ]] || fail "缺少普通配置文件：$path"
  [[ "$(stat -c %u "$path")" == 0 ]] || fail "配置必须由 root 所有：$path"
  permission="$(stat -c %a "$path")"
  (( (8#$permission & 0022) == 0 )) || fail "配置不能由组或其他用户写入：$path"
}

if [[ "$mode" == --init-config ]]; then
  [[ ! -L /etc/ndi ]] || fail '/etc/ndi 不能是符号链接。'
  install -d -m 0700 /etc/ndi
  for pair in 'deploy.conf.example:deploy.conf' 'application.env.example:application.env'; do
    source_name=${pair%%:*}
    target_name=${pair#*:}
    if [[ -e "/etc/ndi/$target_name" || -L "/etc/ndi/$target_name" ]]; then
      echo "保留已有配置：/etc/ndi/$target_name"
    else
      install -o root -g root -m 0600 "$bundle_dir/$source_name" "/etc/ndi/$target_name"
      echo "已生成：/etc/ndi/$target_name，请在服务器本地编辑。"
    fi
  done
  exit 0
fi

for dependency in systemctl systemd-analyze nginx curl ss flock sha256sum awk stat cmp diff find readlink install runuser; do
  command -v "$dependency" >/dev/null || fail "缺少服务器工具：$dependency"
done
[[ ! -L /run/ndi-deploy.lock ]] || fail '部署锁不能为符号链接。'
exec 9>/run/ndi-deploy.lock
flock -n 9 || fail '已有 NDI 部署或检查正在运行。'
check_owned_config "$config_file"
check_owned_config "$app_env"
[[ -d /etc/ndi && ! -L /etc/ndi && "$(stat -c %u /etc/ndi)" == 0 && "$(stat -c %a /etc/ndi)" == 700 ]] || fail '/etc/ndi 必须为 root 所有、权限 0700 的真实目录。'
[[ "$(stat -c %a "$app_env")" == 600 ]] || fail 'application.env 权限必须为 0600。'
# 部署配置是管理员维护的 root 所有文件；凭据文件绝不通过 source 执行。
APP_HOST='' FILES_HOST='' APP_CERT='' APP_KEY='' FILES_CERT='' FILES_KEY=''
JAVA_BIN=/usr/bin/java APP_PORT=8080 MINIO_UPSTREAM=127.0.0.1:9000 START_TIMEOUT=180
DATABASE_BACKUP_CONFIRMED=no
DEPLOY_MODE=https PUBLIC_IP='' WEB_PORT=8088 FILES_PORT=9002
source "$config_file"
if [[ "$mode" == --deploy && "$DATABASE_BACKUP_CONFIRMED" != yes ]]; then
  fail '请先备份数据库或确认目标为空库，再设置 DATABASE_BACKUP_CONFIRMED=yes。'
fi

[[ "$DEPLOY_MODE" == http || "$DEPLOY_MODE" == https ]] || fail 'DEPLOY_MODE 仅支持 http 或 https。'
if [[ "$DEPLOY_MODE" == http ]]; then
  [[ "$PUBLIC_IP" =~ ^[0-9]{1,3}(\.[0-9]{1,3}){3}$ ]] || fail 'PUBLIC_IP 必须填写实际 IPv4 地址。'
  IFS=. read -r -a octets <<< "$PUBLIC_IP"
  for octet in "${octets[@]}"; do (( 10#$octet <= 255 )) || fail 'IPv4 地址无效。'; done
  for key in WEB_PORT FILES_PORT; do
    value=${!key}
    [[ "$value" =~ ^[1-9][0-9]{3,4}$ ]] && (( value >= 1024 && value <= 65535 )) || fail "$key 必须为 1024 至 65535。"
  done
  [[ "$WEB_PORT" != "$FILES_PORT" && "$WEB_PORT" != "$APP_PORT" && "$FILES_PORT" != "$APP_PORT" && "$WEB_PORT" != "${MINIO_UPSTREAM##*:}" && "$FILES_PORT" != "${MINIO_UPSTREAM##*:}" ]] || fail '应用、对象代理、Java 和 MinIO 端口不能冲突。'
  app_origin="http://$PUBLIC_IP:$WEB_PORT"
  files_origin="http://$PUBLIC_IP:$FILES_PORT"
  secure_cookie=false
else
for key in APP_HOST FILES_HOST; do
  value=${!key}
  [[ "$value" =~ ^[a-zA-Z0-9]([a-zA-Z0-9.-]*[a-zA-Z0-9])?$ && "$value" != *CHANGE_ME* && "$value" == *.* ]] || fail "请配置合法域名：$key"
done
[[ "$APP_HOST" != "$FILES_HOST" ]] || fail '应用与对象存储必须使用不同域名。'
  app_origin="https://$APP_HOST"
  files_origin="https://$FILES_HOST"
  secure_cookie=true
fi
[[ "$APP_PORT" =~ ^[1-9][0-9]{3,4}$ ]] && (( APP_PORT <= 65535 )) || fail 'APP_PORT 必须为 1024 至 65535。'
(( APP_PORT >= 1024 )) || fail 'APP_PORT 不能使用特权端口。'
[[ "$START_TIMEOUT" =~ ^[1-9][0-9]{0,2}$ ]] && (( START_TIMEOUT >= 30 && START_TIMEOUT <= 600 )) || fail '启动等待应为 30 至 600 秒。'
[[ "$MINIO_UPSTREAM" =~ ^[a-zA-Z0-9.-]+:[1-9][0-9]{0,4}$ ]] || fail 'MINIO_UPSTREAM 格式应为内网主机:端口，不包含协议或路径。'
(( ${MINIO_UPSTREAM##*:} <= 65535 )) || fail 'MinIO 端口无效。'
path_keys=(JAVA_BIN)
if [[ "$DEPLOY_MODE" == https ]]; then path_keys+=(APP_CERT APP_KEY FILES_CERT FILES_KEY); fi
for key in "${path_keys[@]}"; do
  value=${!key}
  [[ "$value" =~ ^/[a-zA-Z0-9._/-]+$ && "$value" != *CHANGE_ME* && "$value" != *..* ]] || fail "需要不含空格的真实绝对路径：$key"
  [[ -f "$value" ]] || fail "文件不存在：$key"
done
[[ -x "$JAVA_BIN" ]] || fail 'JAVA_BIN 不可执行。'
"$JAVA_BIN" -version 2>&1 | grep -Eq 'version "21[."]' || fail '服务器必须使用 Java 21。'

env_value() {
  # 只读取指定键，不执行环境文件，不向终端输出密码。
  awk -v key="$1" '
    index($0,key "=")==1 {
      value=substr($0,length(key)+2);
      if (value ~ /^".*"$/ || value ~ /^\047.*\047$/) value=substr(value,2,length(value)-2);
      print value; exit
    }' "$app_env"
}
if LC_ALL=C grep -q $'\r' "$app_env"; then fail 'application.env 请使用 Linux 换行。'; fi
awk '
  /^[[:space:]]*($|#)/ {next}
  !/^[A-Z][A-Z0-9_]*=/ {exit 1}
  {key=$0; sub(/=.*/,"",key); if(seen[key]++)exit 1}
' "$app_env" || fail 'application.env 存在重复键、export、BOM 或非 KEY=value 行。'
for key in NDI_DATASOURCE_URL NDI_DATASOURCE_USERNAME NDI_DATASOURCE_PASSWORD NDI_MINIO_ENDPOINT NDI_MINIO_PUBLIC_ENDPOINT NDI_MINIO_ACCESS_KEY NDI_MINIO_SECRET_KEY NDI_MINIO_BUCKET; do
  value="$(env_value "$key")"
  [[ -n "$value" && "$value" != *CHANGE_ME* && "$value" != *'${'* ]] || fail "缺少真实配置：$key（不输出值）"
done
[[ "$(env_value SPRING_PROFILES_ACTIVE)" == prod && "$(env_value NDI_AUTH_MODE)" == SESSION && "$(env_value NDI_SECURE_COOKIE)" == "$secure_cookie" ]] || fail '必须使用 prod、SESSION；HTTP 的 NDI_SECURE_COOKIE=false，HTTPS 则为 true。'
[[ "$(env_value NDI_MINIO_ENDPOINT)" == "http://$MINIO_UPSTREAM" ]] || fail '应用内网 MinIO 地址需与代理上游一致。'
[[ "$(env_value NDI_MINIO_PUBLIC_ENDPOINT)" == "$files_origin" ]] || fail 'MinIO 公共地址必须匹配对象代理入口，且不附加路径。'
[[ "$(env_value NDI_DATASOURCE_URL)" == jdbc:mysql://* ]] || fail '数据库 URL 必须使用 jdbc:mysql://。'
if [[ -n "$(env_value NDI_BOOTSTRAP_ADMIN_LOGIN)" || -n "$(env_value NDI_BOOTSTRAP_ADMIN_PASSWORD)" ]]; then
  [[ -n "$(env_value NDI_BOOTSTRAP_ADMIN_LOGIN)" && -n "$(env_value NDI_BOOTSTRAP_ADMIN_PASSWORD)" ]] || fail '首次管理员账号与密码必须同时填写。'
fi
for file in "$unit_file" "$site_file"; do
  if [[ -e "$file" || -L "$file" ]]; then
    [[ -f "$file" && ! -L "$file" ]] && grep -q '^# Managed by NDI deployment$' "$file" || fail "拒绝覆盖非本脚本管理的文件：$file"
  fi
done
[[ ! -d /etc/systemd/system/ndi.service.d ]] || fail '发现额外的 ndi.service drop-in，请管理员先核对并移出，避免启动参数被覆盖。'
[[ -f /etc/nginx/mime.types ]] || fail '缺少 /etc/nginx/mime.types。'
[[ -d /etc/nginx/conf.d && ! -L /etc/nginx/conf.d ]] || fail '需将 Nginx 站点目录配置为 /etc/nginx/conf.d，并在 http 内 include。'
selinux_enforcing=false
if command -v getenforce >/dev/null && [[ "$(getenforce)" == Enforcing ]]; then
  selinux_enforcing=true
  for dependency in getsebool matchpathcon restorecon; do
    command -v "$dependency" >/dev/null || fail "SELinux 检查缺少工具：$dependency"
  done
  getsebool httpd_can_network_connect | grep -q -- '--> on' || fail 'SELinux 未允许 Nginx 代理连接，请管理员配置 httpd_can_network_connect。'
  if [[ "$DEPLOY_MODE" == http ]]; then
    command -v semanage >/dev/null || fail 'SELinux 端口检查缺少 semanage。'
    for port in "$WEB_PORT" "$FILES_PORT"; do
      semanage port -l | awk -v p="$port" '$1=="http_port_t" && $2=="tcp" {for(i=3;i<=NF;i++){gsub(/,/,"",$i);n=split($i,r,"-");if((n==1 && p==r[1]) || (n==2 && p>=r[1] && p<=r[2]))ok=1}} END{exit !ok}' || fail 'SELinux 未将对外端口标记为 http_port_t；请管理员核对端口策略。'
    done
  fi
  for path in /opt/ndi/shared/assets/check.js /opt/ndi/releases/check/frontend/index.html; do
    matchpathcon -n "$path" | grep -q httpd_sys_content_t || fail '请先为 /opt/ndi 配置 httpd_sys_content_t 持久标签。'
  done
fi

[[ -f "$bundle_dir/SHA256SUMS" && -f "$bundle_dir/release.txt" ]] || fail '当前目录不是完整交付包。'
[[ -z "$(find "$bundle_dir" -type l -print -quit)" ]] || fail '交付包不允许包含符号链接。'
awk 'NF!=2 || length($1)!=64 || $1 !~ /^[a-f0-9]+$/ || $2 !~ /^\.\/[A-Za-z0-9._/-]+$/ || $2 ~ /(^|\/)\.\.(\/|$)/ {exit 1} {if(seen[$2]++)exit 1}' "$bundle_dir/SHA256SUMS" || fail '校验清单格式无效。'
(cd "$bundle_dir" && sha256sum --check --status SHA256SUMS) || fail '交付文件校验失败。'
while IFS= read -r file; do
  relative="./${file#"$bundle_dir/"}"
  [[ "$relative" == ./SHA256SUMS ]] && continue
  awk -v expected="$relative" '$2==expected {found=1} END {exit !found}' "$bundle_dir/SHA256SUMS" || fail '交付包存在未校验文件。'
done < <(find "$bundle_dir" -type f)
release_id="$(awk -F= '$1=="RELEASE_ID" {print $2}' "$bundle_dir/release.txt")"
[[ "$release_id" =~ ^[0-9]{8}T[0-9]{6}Z-[a-f0-9]{7,40}$ ]] || fail '发布版本号无效。'
for file in backend/app.jar frontend/index.html templates/ndi.service.template templates/ndi.nginx.conf.template deploy.sh release.txt; do
  [[ -s "$bundle_dir/$file" ]] || fail "交付包缺少：$file"
  awk -v expected="./$file" '$2==expected {found=1} END {exit !found}' "$bundle_dir/SHA256SUMS" || fail "关键文件未列入校验：$file"
done
if [[ -e "$install_dir/releases/$release_id" || -L "$install_dir/releases/$release_id" ]]; then
  existing_release="$install_dir/releases/$release_id"
  [[ -d "$existing_release" && ! -L "$existing_release" && -f "$existing_release/deployed.txt" ]] || fail '该版本存在未完成的部署，请检查失败记录并生成新版本包。'
  cmp -s "$bundle_dir/backend/app.jar" "$existing_release/backend/app.jar" &&
    diff -qr "$bundle_dir/frontend" "$existing_release/frontend" >/dev/null || fail '已有版本内容不一致，拒绝覆盖运行文件。'
  reuse_release=true
fi
for path in "$install_dir" "$install_dir/releases" "$install_dir/shared" "$install_dir/shared/assets" /var/lib/ndi; do
  [[ ! -L "$path" ]] || fail "目录不能为符号链接：$path"
done
for path in "$install_dir/backend-current" "$install_dir/frontend-current"; do
  [[ ! -e "$path" || -L "$path" ]] || fail "版本指针不能是普通文件或目录：$path"
done

main_pid="$(systemctl show ndi.service -p MainPID 2>/dev/null || true)"
main_pid=${main_pid#MainPID=}
[[ "$main_pid" =~ ^[0-9]+$ ]] || main_pid=0
listeners="$(ss -lntp | awk -v suffix=":$APP_PORT" 'index($4,suffix)==length($4)-length(suffix)+1 {print}')"
if [[ -n "$listeners" ]]; then
  [[ "$main_pid" != 0 ]] || fail '应用端口被未托管进程占用，拒绝强杀或接管。'
  while IFS= read -r line; do
    [[ "$line" == *"pid=$main_pid,"* ]] || fail '应用端口存在其他进程，拒绝停止。'
  done <<< "$listeners"
fi
if [[ "$DEPLOY_MODE" == http ]]; then
  for port in "$WEB_PORT" "$FILES_PORT"; do
    external_listeners="$(ss -lntp | awk -v suffix=":$port" 'index($4,suffix)==length($4)-length(suffix)+1 {print}')"
    while IFS= read -r line; do
      [[ -z "$line" || "$line" == *'("nginx",pid='* ]] || fail '对外端口被非 Nginx 进程占用。'
    done <<< "$external_listeners"
  done
fi
curl --noproxy '*' -fsS --connect-timeout 3 --max-time 10 "http://$MINIO_UPSTREAM/minio/health/ready" >/dev/null || fail '内网 MinIO 未就绪。'
nginx -t || fail '现有 Nginx 配置无效，不能部署。'

stage_dir="$(mktemp -d /var/tmp/ndi-deploy.XXXXXX)"
touch "$stage_dir/.ndi-owned"
render() {
  local content key
  content="$(< "$1")"
  for key in APP_HOST FILES_HOST APP_CERT APP_KEY FILES_CERT FILES_KEY JAVA_BIN APP_PORT MINIO_UPSTREAM PUBLIC_IP WEB_PORT FILES_PORT; do
    content=${content//@@$key@@/${!key}}
  done
  [[ "$content" != *'@@'* ]] || fail '存在未替换的模板变量。'
  printf '%s\n' "$content" > "$2"
}
render "$bundle_dir/templates/ndi.service.template" "$stage_dir/ndi.service"
nginx_template=ndi.nginx.conf.template
if [[ "$DEPLOY_MODE" == http ]]; then nginx_template=ndi.nginx.http.conf.template; fi
render "$bundle_dir/templates/$nginx_template" "$stage_dir/ndi.conf"
printf 'events {}\nhttp { include /etc/nginx/mime.types; include %s; }\n' "$stage_dir/ndi.conf" > "$stage_dir/preflight.conf"
nginx -t -c "$stage_dir/preflight.conf" || fail '候选站点配置或证书无效。'
systemd-analyze verify "$stage_dir/ndi.service" || fail '候选 systemd 配置无效。'
if [[ "$mode" == --check ]]; then
  echo "检查通过：$release_id。未停止服务、未修改站点或版本指针。"
  echo '仍须确认浏览器到入口的连通性、数据库备份、私有 Bucket、应用凭据权限及 MinIO CORS。'
  exit 0
fi

# 从此处才开始写入发布目录；所有配置预检均已通过。
deployment_started=true
install -d -m 0755 "$install_dir" "$install_dir/releases" "$install_dir/shared" "$install_dir/shared/assets"
if ! id ndi >/dev/null 2>&1; then
  useradd --system --user-group --no-create-home --home-dir /var/lib/ndi --shell /sbin/nologin ndi
fi
[[ "$(id -u ndi)" != 0 ]] || fail 'NDI 运行账号不得为 root。'
getent group ndi >/dev/null || fail '缺少 ndi 运行组。'
runuser -u ndi -- "$JAVA_BIN" -version >/dev/null 2>&1 || fail 'ndi 运行账号不能执行所配置的 Java，请将 JDK 放在服务账号可访问的位置。'
install -d -o ndi -g ndi -m 0700 /var/lib/ndi /var/lib/ndi/backups
release_dir="$install_dir/releases/$release_id"
if [[ "$reuse_release" == false ]]; then
  install -d -m 0755 "$release_dir" "$release_dir/backend" "$release_dir/frontend"
  install -m 0644 "$bundle_dir/backend/app.jar" "$release_dir/backend/app.jar"
  cp -R "$bundle_dir/frontend/." "$release_dir/frontend/"
  find "$release_dir/frontend" -type d -exec chmod 0755 {} +
  find "$release_dir/frontend" -type f -exec chmod 0644 {} +
  install -m 0644 "$bundle_dir/release.txt" "$release_dir/release.txt"
fi
old_backend="$(readlink "$install_dir/backend-current" || true)"
old_frontend="$(readlink "$install_dir/frontend-current" || true)"
if [[ "$reuse_release" == false ]]; then
  printf 'PREVIOUS_BACKEND=%s\nPREVIOUS_FRONTEND=%s\n' "$old_backend" "$old_frontend" > "$release_dir/previous.txt"
fi

# 保留旧哈希资源在原 URL 下可读，避免已打开页面懒加载时出现 404。
while IFS= read -r asset; do
  relative=${asset#"$release_dir/frontend/assets/"}
  target="$install_dir/shared/assets/$relative"
  install -d -m 0755 "$(dirname "$target")"
  if [[ -e "$target" ]]; then
    cmp -s "$asset" "$target" || fail '相同资源文件名内容不同，拒绝覆盖共享资源。'
  else
    install -m 0644 "$asset" "$target"
  fi
done < <(find "$release_dir/frontend/assets" -type f)
if [[ "$selinux_enforcing" == true ]]; then restorecon -R "$install_dir"; fi

site_existed=false
if [[ -f "$site_file" ]]; then
  cp -p "$site_file" "$stage_dir/previous-site.conf"
  site_existed=true
fi
install -m 0644 "$stage_dir/ndi.conf" "$site_file"
restore_site() {
  if [[ "$site_existed" == true ]]; then
    install -m 0644 "$stage_dir/previous-site.conf" "$site_file"
  else
    rm -- "$site_file"
  fi
}
if ! nginx -t > "$stage_dir/merged-check" 2>&1 || grep -qi 'conflicting server name' "$stage_dir/merged-check"; then
  cat "$stage_dir/merged-check" >&2
  restore_site
  fail '合并现有站点后 Nginx 检查失败，已恢复站点文件，未停止 Java。'
fi
if ! nginx -T > "$stage_dir/nginx-expanded" 2>&1; then
  restore_site
  fail '无法检查实际 Nginx 配置，已恢复站点文件。'
fi
if ! grep -Fq "# configuration file $site_file:" "$stage_dir/nginx-expanded"; then
  restore_site
  fail 'Nginx 未实际 include NDI 站点，已恢复站点文件，未停止 Java。'
fi
if [[ -f "$unit_file" ]]; then cp -p "$unit_file" "$release_dir/previous.service"; fi
install -m 0644 "$stage_dir/ndi.service" "$unit_file"
[[ ! -L /var/log/nginx ]] || fail 'Nginx 日志目录不能为符号链接。'
if [[ ! -d /var/log/nginx ]]; then install -d -m 0755 /var/log/nginx; fi
for name in ndi-access ndi-error ndi-files-access ndi-files-error; do
  log_path="/var/log/nginx/$name.log"
  [[ ! -L "$log_path" && ( ! -e "$log_path" || -f "$log_path" ) ]] || fail 'NDI 日志目标必须为普通文件。'
  touch "$log_path"
  chown root:root "$log_path"
  chmod 0640 "$log_path"
done
systemctl daemon-reload
systemctl stop ndi.service
service_stopped=true
[[ "$(systemctl show ndi.service -p MainPID)" == MainPID=0 ]] || fail '旧 Java 未完全停止，未切换版本。'
ln -s "$release_dir/backend" "$install_dir/.backend-next"
mv -Tf "$install_dir/.backend-next" "$install_dir/backend-current"
systemctl enable ndi.service
systemctl reset-failed ndi.service
systemctl start ndi.service
ready=false
for ((elapsed=0; elapsed<START_TIMEOUT; elapsed+=3)); do
  if curl --noproxy '*' -fsS --connect-timeout 2 --max-time 3 "http://127.0.0.1:$APP_PORT/actuator/health" | grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"'; then
    ready=true; break
  fi
  sleep 3
done
[[ "$ready" == true ]] || fail '新 Java 健康检查超时，未发布新前端。请检查数据库迁移和日志。'
systemctl is-active --quiet ndi.service || fail '健康检查后 Java 已退出。'
ln -s "$release_dir/frontend" "$install_dir/.frontend-next"
mv -Tf "$install_dir/.frontend-next" "$install_dir/frontend-current"
if systemctl is-active --quiet nginx; then systemctl reload nginx; else systemctl start nginx; fi

# 使用真实证书验证，--resolve 只将本次请求送往本机，不篡改 DNS 或跳过 TLS 校验。
curl_base=(--noproxy '*' --fail --silent --show-error --connect-timeout 3 --max-time 15 --resolve "$APP_HOST:443:127.0.0.1")
files_curl=(--noproxy '*' -fsS --connect-timeout 3 --max-time 10 --resolve "$FILES_HOST:443:127.0.0.1")
probe_origin=$app_origin
probe_files_origin=$files_origin
if [[ "$DEPLOY_MODE" == http ]]; then
  curl_base=(--noproxy '*' --fail --silent --show-error --connect-timeout 3 --max-time 15 -H "Host: $PUBLIC_IP:$WEB_PORT")
  files_curl=(--noproxy '*' -fsS --connect-timeout 3 --max-time 10 -H "Host: $PUBLIC_IP:$FILES_PORT")
  probe_origin="http://127.0.0.1:$WEB_PORT"
  probe_files_origin="http://127.0.0.1:$FILES_PORT"
fi
curl "${curl_base[@]}" "$probe_origin/index.html" | grep -q 'id="root"' || fail 'Nginx 首页验收失败。'
curl "${curl_base[@]}" "$probe_origin/projects/deployment-check" | grep -q 'id="root"' || fail '前端深链接验收失败。'
while IFS= read -r asset_uri; do
  curl "${curl_base[@]}" "$probe_origin$asset_uri" -o /dev/null || fail '入口静态资源验收失败。'
done < <(grep -oE '/assets/[A-Za-z0-9._/-]+\.(js|css)' "$release_dir/frontend/index.html" | sort -u)
curl "${curl_base[@]}" "$probe_origin/api/v1/auth/mode" | grep -q 'SESSION' || fail '代理未连接到 SESSION 后端。'
status="$(curl "${curl_base[@]}" --no-fail -o /dev/null -w '%{http_code}' "$probe_origin/api/v1/projects")"
[[ "$status" == 401 ]] || fail '未登录业务接口未返回 401，请检查代理和认证。'
curl "${files_curl[@]}" "$probe_files_origin/minio/health/ready" >/dev/null || fail 'MinIO 对外入口验收失败。'
printf 'DEPLOYED_AT=%s\n' "$(date -u +%FT%TZ)" > "$release_dir/deployed.txt"
echo "部署完成：$release_id；应用入口：$app_origin"
echo '需继续在真实浏览器验证登录、Excel 导入、附件上传下载和地图。'
echo '未自动修改 MySQL、MinIO、CORS、防火墙、SELinux 或其他 Nginx 站点。'
