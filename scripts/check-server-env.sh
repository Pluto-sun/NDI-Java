#!/usr/bin/env bash
# 只读检查，不安装软件、不创建数据库或 Bucket、不修改服务及安全策略。
set -uo pipefail
passed=0 failed=0 unknown=0 warnings=0
deploy_conf=/etc/ndi/deploy.conf
app_env=/etc/ndi/application.env
mysql_login_path=''
minio_alias=''
public_ip=''
report() {
  local level=$1; shift
  printf '[%s] %s\n' "$level" "$*"
  case "$level" in PASS) passed=$((passed+1));; FAIL) failed=$((failed+1));; UNKNOWN) unknown=$((unknown+1));; WARN) warnings=$((warnings+1));; esac
}
usage() {
  echo '用法：sudo bash check-server-env.sh [--public-ip IPv4] [--mysql-login-path 已有登录配置名] [--minio-alias 已有mc别名]'
  echo '凭据仅使用服务器已有配置；不会输出密码，也不会执行配置文件。退出码：0=检查通过，1=存在失败，2=仍有未验证项目。'
}
while (( $# )); do
  case "$1" in
    --help) usage; exit 0;;
    --public-ip|--mysql-login-path|--minio-alias)
      (( $# >= 2 )) || { usage; exit 1; }
      case "$1" in --public-ip) public_ip=$2;; --mysql-login-path) mysql_login_path=$2;; --minio-alias) minio_alias=$2;; esac
      shift 2;;
    *) usage; exit 1;;
  esac
done
[[ "$(uname -s)" == Linux ]] || { report FAIL '请在目标 Linux 服务器执行，而非本地 Mac。'; exit 1; }
echo 'NDI 服务器部署环境检测（只读；不代表业务验收）'
read_value() {
  [[ -r "$1" && -f "$1" ]] || return 0
  awk -v key="$2" 'index($0,key "=")==1 {v=substr($0,length(key)+2); if(v~/^".*"$/ || v~/^\047.*\047$/)v=substr(v,2,length(v)-2); print v;exit}' "$1"
}
[[ $EUID == 0 ]] || report WARN '非 root：部分配置、进程和 Nginx 检查可能无法读取，建议 sudo 重跑。'
if [[ -f /etc/centos-release ]] && grep -q 'release 7' /etc/centos-release; then
  report WARN 'CentOS 7 已结束维护；脚本兼容不等于操作系统安全支持，建议规划系统升级。'
fi
for dependency in systemctl systemd-analyze nginx curl ss flock sha256sum awk stat cmp diff find readlink install runuser timeout tar; do
  if command -v "$dependency" >/dev/null; then report PASS "工具：$dependency"; else report FAIL "缺少工具：$dependency"; fi
done
java_bin=$(read_value "$deploy_conf" JAVA_BIN)
java_bin=${java_bin:-$(command -v java || true)}
if [[ -n "$java_bin" && -x "$java_bin" ]] && "$java_bin" -version 2>&1 | grep -Eq 'version "21[."]'; then
  report PASS 'Java 21 可执行（仍需部署时确认 ndi 运行账号权限）。'
else report FAIL '未找到可执行的 Java 21；配置 JAVA_BIN。'; fi
if command -v systemctl >/dev/null && systemctl show -p Version >/dev/null 2>&1; then report PASS '可连接 systemd 管理器。'; else report FAIL '无法连接 systemd，不能管理应用服务。'; fi
for service in nginx minio; do
  if systemctl is-active --quiet "$service" 2>/dev/null; then report PASS "$service 服务运行中。"; else report WARN "$service 未运行或服务名不同。"; fi
done
if command -v nginx >/dev/null; then
  if nginx -t >/dev/null 2>&1; then report PASS '现有 Nginx 配置语法有效。'; else report FAIL 'Nginx -t 失败，请在服务器运行 sudo nginx -t 查看详情。'; fi
fi
if [[ -d /etc/nginx/conf.d && -f /etc/nginx/mime.types ]]; then report PASS 'Nginx 标准站点目录和 mime.types 存在。'; else report FAIL '需准备 /etc/nginx/conf.d 和 /etc/nginx/mime.types。'; fi
for file in "$deploy_conf" "$app_env"; do
  if [[ -r "$file" && -f "$file" && ! -L "$file" ]]; then
    if [[ "$(stat -c %u "$file")" == 0 && "$(stat -c %a "$file")" == 600 ]]; then report PASS "配置权限：$file"; else report FAIL "配置需 root 所有、0600：$file"; fi
  else report UNKNOWN "配置尚未初始化或不可读：$file"; fi
done
for key in NDI_DATASOURCE_URL NDI_DATASOURCE_USERNAME NDI_DATASOURCE_PASSWORD NDI_MINIO_ENDPOINT NDI_MINIO_PUBLIC_ENDPOINT NDI_MINIO_BUCKET NDI_MINIO_ACCESS_KEY NDI_MINIO_SECRET_KEY; do
  value=$(read_value "$app_env" "$key")
  if [[ -n "$value" && "$value" != *CHANGE_ME* ]]; then report PASS "已填写：$key（值不展示）"; else report UNKNOWN "待填写：$key"; fi
done
deploy_mode=$(read_value "$deploy_conf" DEPLOY_MODE)
if [[ "$deploy_mode" == http ]]; then
  report WARN 'HTTP 明文传输账号、会话及附件，仅应在受控网络使用。'
  if [[ "$(read_value "$app_env" NDI_SECURE_COOKIE)" == false ]]; then report PASS 'HTTP Cookie 配置匹配。'; else report FAIL 'HTTP 模式必须 NDI_SECURE_COOKIE=false，否则浏览器登录失败。'; fi
fi
public_ip=${public_ip:-$(read_value "$deploy_conf" PUBLIC_IP)}
[[ -n "$public_ip" && "$public_ip" != CHANGE_ME ]] && report PASS '已指定客户端访问 IP（不代表公网已连通）。' || report UNKNOWN '尚未指定 PUBLIC_IP。'
web_port=$(read_value "$deploy_conf" WEB_PORT); web_port=${web_port:-8088}
files_port=$(read_value "$deploy_conf" FILES_PORT); files_port=${files_port:-9002}
backend_port=$(read_value "$deploy_conf" APP_PORT); backend_port=${backend_port:-8080}
for port in "$web_port" "$files_port" "$backend_port"; do
  if [[ ! "$port" =~ ^[1-9][0-9]{0,4}$ ]] || (( port > 65535 )); then report FAIL '配置中存在无效端口。'; continue; fi
  if command -v ss >/dev/null; then
    listener=$(ss -lntp 2>/dev/null | awk -v p=":$port" 'index($4,p)==length($4)-length(p)+1 {print}')
    if [[ -z "$listener" ]]; then report PASS "TCP $port 未占用。"; else report WARN "TCP $port 已监听；部署 --check 将核对是否由 NDI/Nginx 托管。"; fi
  fi
done
endpoint=$(read_value "$app_env" NDI_MINIO_ENDPOINT); endpoint=${endpoint:-http://127.0.0.1:9000}
if [[ "$endpoint" =~ ^https?://[a-zA-Z0-9.:-]+$ ]] && curl --noproxy '*' -fsS --connect-timeout 3 --max-time 8 "$endpoint/minio/health/ready" >/dev/null 2>&1; then report PASS 'MinIO 就绪探针通过（不代表 Bucket/应用权限正确）。'; else report FAIL 'MinIO 就绪探针失败或内网地址无效。'; fi
db_url=$(read_value "$app_env" NDI_DATASOURCE_URL)
if [[ "$db_url" =~ ^jdbc:mysql://([a-zA-Z0-9.-]+):([0-9]+)/([a-zA-Z0-9_]+)(\?.*)?$ ]]; then
  db_host=${BASH_REMATCH[1]}; db_port=${BASH_REMATCH[2]}; db_name=${BASH_REMATCH[3]}
  if timeout 5 bash -c 'exec 3<>/dev/tcp/$1/$2' ndi-db-probe "$db_host" "$db_port" 2>/dev/null; then report PASS 'MySQL TCP 可连接（尚未证明身份与数据库权限）。'; else report FAIL '数据库 TCP 不通。'; fi
else db_name=''; report UNKNOWN '需填写标准 JDBC 地址，包含主机、端口和库名。'; fi
if [[ -n "$mysql_login_path" && -n "$db_name" ]] && command -v mysql >/dev/null; then
  if db_version=$(mysql --login-path="$mysql_login_path" --host="$db_host" --port="$db_port" --protocol=TCP --connect-timeout=5 --batch --skip-column-names --database="$db_name" -e 'SELECT VERSION();' 2>/dev/null); then
    if [[ "$db_version" == 8.* ]]; then report PASS '只读 SQL 验证：MySQL 8 可登录且目标库存在。'; else report FAIL '服务器实际版本并非 MySQL 8。'; fi
    report WARN 'SQL 使用指定 login-path；请确保与应用账号一致。Flyway 的 DDL 权限未进行写入验证。'
  else report FAIL '只读 SQL 登录或目标库访问失败。'; fi
else report UNKNOWN '未执行 SQL 登录检查；可提供已有 --mysql-login-path，检测脚本不索取或保存密码。'; fi
bucket=$(read_value "$app_env" NDI_MINIO_BUCKET)
if [[ -n "$minio_alias" && "$minio_alias" =~ ^[a-zA-Z0-9_-]+$ && "$bucket" =~ ^[a-z0-9][a-z0-9.-]+$ ]] && command -v mc >/dev/null; then
  if timeout 10 mc stat "$minio_alias/$bucket" >/dev/null 2>&1; then report PASS '指定 mc 别名可访问 Bucket（应使用应用凭据配置该别名）。'; else report FAIL 'Bucket 不存在或指定 mc 账号无权访问。'; fi
  if policy=$(timeout 10 mc anonymous get "$minio_alias/$bucket" 2>/dev/null) && [[ "$policy" =~ (^|[^[:alnum:]_])private[^[:alnum:]_]*$ ]]; then report PASS 'Bucket 匿名策略为私有。'; else report UNKNOWN '未确认 Bucket 匿名策略为私有，请人工核对。'; fi
  expected_origin="http://$public_ip:$web_port"
  if [[ "$deploy_mode" == https ]]; then expected_origin="https://$(read_value "$deploy_conf" APP_HOST)"; fi
  if cors=$(timeout 10 mc cors get "$minio_alias/$bucket" 2>/dev/null) && [[ "$cors" == *"<AllowedOrigin>$expected_origin</AllowedOrigin>"* ]]; then report PASS 'CORS 包含应用精确 Origin（完整规则及浏览器上传仍需验收）。'; else report UNKNOWN 'CORS 未确认包含应用精确 Origin，需人工核对。'; fi
else report UNKNOWN '未验证 Bucket、私有策略及 CORS；可提供已有 --minio-alias。'; fi
if command -v getenforce >/dev/null && [[ "$(getenforce)" == Enforcing ]]; then
  if getsebool httpd_can_network_connect 2>/dev/null | grep -q -- '--> on'; then report PASS 'SELinux 允许 Nginx 连接上游。'; else report FAIL 'SELinux 未授权 Nginx 上游连接。'; fi
  report UNKNOWN '需核对 Nginx 监听端口的 http_port_t 和 /opt/ndi 的 httpd_sys_content_t 持久标签。'
fi
for path in /opt /var/lib /etc/nginx/conf.d; do
  if [[ -d "$path" && -w "$path" ]]; then report PASS "当前账号可写部署父目录：$path"; else report UNKNOWN "目录不存在或不可写：$path；部署需 root。"; fi
done
df -Ph /opt /var/lib 2>/dev/null || true
report WARN '未自动修改或放行防火墙/安全组；需客户端验证应用端口和对象端口，勿对外开放 MySQL、Java、MinIO 管理端口。'
printf '\n结果：PASS=%s WARN=%s FAIL=%s UNKNOWN=%s\n' "$passed" "$warnings" "$failed" "$unknown"
if (( failed )); then echo '结论：存在阻断项，暂不可部署。'; exit 1; fi
if (( unknown )); then echo '结论：基础检查未发现阻断，但仍有待确认项，不能认定可完整部署。'; exit 2; fi
echo '结论：本次检查通过；仍须执行交付包 deploy.sh --check 及真实浏览器验收。'
