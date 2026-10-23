#!/usr/bin/env bash
# 使用真实 Nginx 验证静态资源、路由回退、HTTPS 和对象代理，不访问业务服务。
set -euo pipefail
[[ -f /.dockerenv && "${NDI_DEPLOY_TEST_CONTAINER:-}" == 1 ]] || { echo '只能在一次性 Docker 容器运行。' >&2; exit 1; }
mkdir -p /test-nginx /opt/ndi/frontend-current /opt/ndi/shared/assets /var/log/nginx
openssl req -x509 -newkey rsa:2048 -nodes -days 1 -keyout /test-nginx/key.pem -out /test-nginx/cert.pem \
  -subj /CN=app.example.test -addext 'subjectAltName=DNS:app.example.test,DNS:files.example.test' >/dev/null 2>&1
content="$(< /source/templates/ndi.nginx.conf.template)"
for key in APP_CERT APP_KEY FILES_CERT FILES_KEY; do
  if [[ "$key" == *_KEY ]]; then value=/test-nginx/key.pem; else value=/test-nginx/cert.pem; fi
  content=${content//@@$key@@/$value}
done
content=${content//@@APP_HOST@@/app.example.test}
content=${content//@@FILES_HOST@@/files.example.test}
content=${content//@@APP_PORT@@/8080}
content=${content//@@MINIO_UPSTREAM@@/127.0.0.1:9000}
printf '%s\n' "$content" > /test-nginx/ndi.conf
printf '<html><div id="root"></div></html>\n' > /opt/ndi/frontend-current/index.html
printf 'console.log("test");\n' > /opt/ndi/shared/assets/test-hash.js
# 用独立的 Nginx server 充当上游，验证 URI、Host 和查询参数是否保留。
printf '%s\n' \
  'server { listen 127.0.0.1:8080; location /api/v1/auth/mode { return 200 "SESSION:$request_uri:$http_host:$http_x_forwarded_proto"; } location / { return 401; } }' \
  'server { listen 127.0.0.1:9000; location / { return 200 "$request_uri:$http_host"; } }' > /test-nginx/upstream.conf
printf '%s\n' 'pid /test-nginx/nginx.pid;' 'error_log /test-nginx/error.log;' 'events {}' \
  'http { include /etc/nginx/mime.types; include /test-nginx/ndi.conf; include /test-nginx/upstream.conf; }' > /test-nginx/nginx.conf
nginx -t -c /test-nginx/nginx.conf
nginx -c /test-nginx/nginx.conf
trap 'nginx -s quit -c /test-nginx/nginx.conf' EXIT
app_curl=(--noproxy '*' --cacert /test-nginx/cert.pem --resolve app.example.test:443:127.0.0.1 -fsS)
curl "${app_curl[@]}" https://app.example.test/ | grep -q 'id="root"'
curl "${app_curl[@]}" https://app.example.test/projects/uuid | grep -q 'id="root"'
curl "${app_curl[@]}" https://app.example.test/api/v1/auth/mode | grep -q 'SESSION:/api/v1/auth/mode:app.example.test:https'
curl "${app_curl[@]}" https://app.example.test/assets/test-hash.js | grep -q console.log
[[ "$(curl --noproxy '*' --cacert /test-nginx/cert.pem --resolve app.example.test:443:127.0.0.1 -sS -o /dev/null -w '%{http_code}' https://app.example.test/assets/missing.js)" == 404 ]]
[[ "$(curl --noproxy '*' --cacert /test-nginx/cert.pem --resolve app.example.test:443:127.0.0.1 -sS -o /dev/null -w '%{http_code}' https://app.example.test/api/v1/projects)" == 401 ]]
[[ "$(curl --noproxy '*' --cacert /test-nginx/cert.pem --resolve app.example.test:443:127.0.0.1 -sS -o /dev/null -w '%{http_code}' https://app.example.test/actuator/health)" == 404 ]]
curl --noproxy '*' --cacert /test-nginx/cert.pem --resolve files.example.test:443:127.0.0.1 -fsS \
  'https://files.example.test/ndi-documents/staging/test?X-Amz-Signature=temporary-test-signature' | grep -q '/ndi-documents/staging/test?X-Amz-Signature=temporary-test-signature:files.example.test'
if grep -q temporary-test-signature /var/log/nginx/ndi-files-access.log; then echo '预签名查询参数泄漏到访问日志'; exit 1; fi
echo '通过：真实 Nginx HTTPS、首页、深链接、资源、缺失资源 404、API 前缀/认证、管理路径屏蔽、对象路径/签名/Host 保留及安全访问日志。'
