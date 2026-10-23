#!/usr/bin/env bash
set -euo pipefail

repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
test_mode=unit
case "${1:-}" in
  '') ;;
  --verify) test_mode=integration ;;
  --help)
    echo '用法：bash scripts/package-release.sh [--verify]'
    echo '默认运行后端单元/架构测试；--verify 额外运行需要 Docker 的集成测试。'
    echo '在隔离临时目录构建，产物位于 dist/releases，不上传或部署。'
    exit 0 ;;
  *) echo '未知参数，请使用 --help。' >&2; exit 1 ;;
esac
for dependency in node npm java tar git; do
  command -v "$dependency" >/dev/null || { echo "缺少工具：$dependency" >&2; exit 1; }
done
java -version 2>&1 | grep -Eq 'version "21[."]' || { echo '需要 Java 21；请检查 JAVA_HOME 和 PATH。' >&2; exit 1; }
node -e 'const [a,b]=process.versions.node.split(".").map(Number);if(!((a===20&&b>=19)||(a===22&&b>=12)||a>22))process.exit(1)' || {
  echo 'Node.js 需满足当前 Vite 要求：20.19+ 或 22.12+（建议使用受维护的 LTS）。' >&2; exit 1;
}
if [[ "$test_mode" == integration ]]; then
  docker info >/dev/null 2>&1 || { echo '--verify 需要可用的 Docker，不能以跳过集成测试代替验证。' >&2; exit 1; }
fi

mkdir -p "$repo_dir/tmp"
workspace="$(mktemp -d "$repo_dir/tmp/ndi-package.XXXXXX")"
build_complete=false
touch "$workspace/.ndi-package-owned"
cleanup() {
  # 仅由最外层脚本在整个归档完成后清理，不让子 shell 提前回收测试类。
  [[ "${BASH_SUBSHELL:-0}" == 0 ]] || return 0
  if [[ "$build_complete" != true ]]; then
    echo "构建失败，未生成交付包；诊断目录已保留：$workspace" >&2
    return
  fi
  # 仅清理本次创建且带标记的临时目录，不触碰工作区与正在运行的 target。
  if [[ -f "$workspace/.ndi-package-owned" && "${workspace##*/}" == ndi-package.* ]]; then
    rm -rf -- "$workspace"
  fi
}
trap cleanup EXIT
mkdir -p "$workspace/source"
export COPYFILE_DISABLE=1
tar -C "$repo_dir" --exclude=node_modules --exclude=dist --exclude=target \
  --exclude='.env' --exclude='.env.*' --exclude='*.tsbuildinfo' --exclude='.DS_Store' \
  -cf - pom.xml mvnw .mvn src frontend scripts/restore-backup.sh | tar -C "$workspace/source" -xf -
echo '正在隔离目录构建前端和后端，不修改当前 IDEA 的 target。'
(cd "$workspace/source/frontend" && npm ci && npm run format:check && npm run build)
if [[ "$test_mode" == integration ]]; then
  (cd "$workspace/source" && bash mvnw -B verify)
else
  (cd "$workspace/source" && bash mvnw -B test)
fi
(cd "$workspace/source" && bash mvnw -B -Prelease -DskipTests package)

commit="$(git -C "$repo_dir" rev-parse --short=12 HEAD)"
release_id="$(date -u +%Y%m%dT%H%M%SZ)-$commit"
release_dir="$workspace/ndi-release-$release_id"
mkdir -p "$release_dir/backend" "$release_dir/frontend"
cp "$workspace/source/target/ndi-0.1.0-SNAPSHOT.jar" "$release_dir/backend/app.jar"
cp -R "$workspace/source/frontend/dist/." "$release_dir/frontend/"
cp "$repo_dir/deploy/deploy.sh" "$release_dir/deploy.sh"
cp "$repo_dir/scripts/check-server-env.sh" "$release_dir/check-env.sh"
cp -R "$repo_dir/deploy/templates" "$release_dir/templates"
cp "$repo_dir/deploy/deploy.conf.example" "$repo_dir/deploy/application.env.example" "$repo_dir/deploy/minio-cors.xml.example" "$release_dir/"
cp "$repo_dir/deploy/minio-app-policy.json.example" "$release_dir/"
java_home_bin="$(dirname "$(command -v java)")"
# jar 来自本地 JDK；服务器无需 JDK 构建工具或 npm。
jar_command="${JAVA_HOME:+$JAVA_HOME/bin/}jar"
command -v "$jar_command" >/dev/null || jar_command="$java_home_bin/jar"
"$jar_command" tf "$release_dir/backend/app.jar" > "$workspace/jar-entries"
grep -q '^BOOT-INF/classes/application-prod.yml$' "$workspace/jar-entries"
if grep -q '^BOOT-INF/classes/application-local.yml$' "$workspace/jar-entries"; then
  echo '发布 JAR 意外包含本地配置，拒绝交付。' >&2; exit 1
fi
test -s "$release_dir/frontend/index.html"
printf 'RELEASE_ID=%s\nCOMMIT=%s\nBACKEND_TEST_MODE=%s\nBROWSER_TESTS=not-run\n' \
  "$release_id" "$commit" "$test_mode" > "$release_dir/release.txt"
if [[ -n "$(git -C "$repo_dir" status --porcelain --untracked-files=no)" ]]; then
  printf 'TRACKED_WORKTREE=modified\n' >> "$release_dir/release.txt"
else
  printf 'TRACKED_WORKTREE=clean\n' >> "$release_dir/release.txt"
fi
node "$repo_dir/scripts/release-checksums.mjs" "$release_dir"
mkdir -p "$repo_dir/dist/releases"
archive="$repo_dir/dist/releases/ndi-release-$release_id.tar.gz"
[[ ! -e "$archive" ]] || { echo '同名交付包已存在，请稍后重新构建。' >&2; exit 1; }
tar -C "$workspace" -czf "$archive" "ndi-release-$release_id"
build_complete=true
echo "打包完成：$archive"
echo "后端测试模式：$test_mode；浏览器测试未在本脚本执行，见 release.txt。"
echo '上传后先执行 sudo bash deploy.sh --init-config，填写服务器配置，再执行 --check 与 --deploy。'
