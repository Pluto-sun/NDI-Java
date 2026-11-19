#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [[ "${1:-}" == '--help' ]]; then
  echo '用法：scripts/start-backend.sh [Maven 参数]'
  echo '使用 Java 21、Maven Wrapper 和 local 配置前台启动，默认端口 8080。'
  echo '自定义端口：NDI_BACKEND_PORT=8081 scripts/start-backend.sh'
  exit 0
fi

port="${NDI_BACKEND_PORT:-8080}"
if [[ ! "${port}" =~ ^[0-9]{1,5}$ ]] || (( 10#${port} < 1 || 10#${port} > 65535 )); then
  echo 'NDI_BACKEND_PORT 必须为 1 至 65535 的整数。' >&2
  exit 1
fi

# 未显式指定 JDK 时，优先选择 macOS 已安装的 Java 21。
if [[ -z "${JAVA_HOME:-}" && -x /usr/libexec/java_home ]]; then
  JAVA_HOME="$(/usr/libexec/java_home -v 21 2>/dev/null)" || {
    echo '未找到 Java 21，请安装 JDK 21 或设置 JAVA_HOME。' >&2
    exit 1
  }
  export JAVA_HOME
fi
java_command="${JAVA_HOME:+${JAVA_HOME}/bin/}java"
if ! java_version="$("${java_command}" -version 2>&1)" ||
  [[ ! "${java_version}" =~ version\ \"21[.\"] ]]; then
  echo '后端需要 Java 21，请检查 JAVA_HOME 与 java -version。' >&2
  exit 1
fi
if command -v lsof >/dev/null 2>&1 && lsof -nP -iTCP:"${port}" -sTCP:LISTEN >/dev/null 2>&1; then
  echo "端口 ${port} 已占用，请停止原服务或设置 NDI_BACKEND_PORT。" >&2
  exit 1
fi

cd "${PROJECT_DIR}"
echo "正在启动后端：http://127.0.0.1:${port}（local 配置）"
echo '请确保 MySQL 和 MinIO 已启动；按 Ctrl+C 停止后端。'
# 明确使用开发配置；数据库、对象存储等仍从现有 NDI_* 环境变量读取。
export SERVER_ADDRESS=127.0.0.1
export SERVER_PORT="${port}"
exec ./mvnw spring-boot:run -Dspring-boot.run.profiles=local "$@"
