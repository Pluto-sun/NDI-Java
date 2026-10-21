#!/bin/sh
set -eu
PROJECT_DIR="$(CDPATH= cd "$(dirname "$0")/.." && pwd)"
ARCHIVE_JAR="${PROJECT_DIR}/target/ndi-0.1.0-SNAPSHOT.jar"
if [ ! -f "${ARCHIVE_JAR}" ]; then
  echo "未找到可执行工程包，请先运行 ./mvnw -DskipTests package。" >&2
  exit 2
fi
# 避免宿主机通用 Boot Loader 环境变量把离线命令切回 Web 应用入口。
unset LOADER_ARGS LOADER_PATH LOADER_HOME LOADER_CONFIG_LOCATION LOADER_SYSTEM
LOADER_MAIN=com.pluto.ndi.backup.util.BackupRestoreCli
export LOADER_MAIN
exec java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -Dloader.main=com.pluto.ndi.backup.util.BackupRestoreCli -cp "${ARCHIVE_JAR}" org.springframework.boot.loader.launch.PropertiesLauncher "$@"
