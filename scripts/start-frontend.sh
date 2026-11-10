#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [[ "${1:-}" == '--help' ]]; then
  echo '用法：scripts/start-frontend.sh'
  echo '前台启动 Vite，默认监听 0.0.0.0:5173，代理后端至 127.0.0.1:8080。'
  echo '仅本机访问：NDI_FRONTEND_HOST=127.0.0.1 scripts/start-frontend.sh'
  echo '自定义端口：NDI_FRONTEND_PORT=5174 scripts/start-frontend.sh'
  echo '自定义后端：NDI_DEV_API_TARGET=http://127.0.0.1:8081 scripts/start-frontend.sh'
  exit 0
fi

port="${NDI_FRONTEND_PORT:-5173}"
bind_host="${NDI_FRONTEND_HOST:-0.0.0.0}"
if [[ ! "${port}" =~ ^[0-9]{1,5}$ ]] || (( 10#${port} < 1 || 10#${port} > 65535 )); then
  echo 'NDI_FRONTEND_PORT 必须为 1 至 65535 的整数。' >&2
  exit 1
fi
if ! command -v node >/dev/null 2>&1 || ! command -v npm >/dev/null 2>&1; then
  echo '请先安装 Node.js 和 npm，并确保当前终端能找到它们。' >&2
  exit 1
fi
if [[ ! -x "${PROJECT_DIR}/frontend/node_modules/.bin/vite" ]]; then
  echo "前端依赖未就绪，请先执行：cd \"${PROJECT_DIR}/frontend\" && npm ci" >&2
  exit 1
fi

cd "${PROJECT_DIR}/frontend"
export NDI_DEV_API_TARGET="${NDI_DEV_API_TARGET:-http://127.0.0.1:8080}"
echo "前端监听：${bind_host}:${port}"
echo "本机访问：http://localhost:${port}"
if [[ "${bind_host}" == '0.0.0.0' ]]; then
  lan_ip=""
  if command -v ipconfig >/dev/null 2>&1; then
    lan_ip="$(ipconfig getifaddr en0 2>/dev/null || ipconfig getifaddr en1 2>/dev/null || true)"
  fi
  if [[ -n "${lan_ip}" ]]; then
    echo "局域网访问：http://${lan_ip}:${port}"
  else
    echo "局域网访问：http://本机局域网IP:${port}"
  fi
fi
echo "后端代理：${NDI_DEV_API_TARGET}；按 Ctrl+C 停止前端。"
# 端口被占用时直接退出，避免 Vite 自动换端口导致访问地址不一致。
exec npm run dev -- --host "${bind_host}" --port "${port}" --strictPort
