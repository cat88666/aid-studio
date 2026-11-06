#!/usr/bin/env bash
# 在 Mac 构建当前 Git 提交，再发布到已经初始化的 ai-hk。
set -euo pipefail
case "${1:-}" in
  '') mode=deploy ;;
  --check) mode=check ;;
  *) echo "用法: $0 [--check]" >&2; exit 2 ;;
esac
[[ $# -le 1 ]] || exit 2
cd "$(dirname "$0")/.."
if [[ "$mode" == deploy && -n "$(git status --porcelain)" ]]; then
  echo "存在未提交改动。请先提交要发布的版本，或在干净的工作树中运行。" >&2
  exit 1
fi
commit=$(git rev-parse HEAD)
sql_tree=$(git rev-parse "${commit}:sql")
host=ai-hk
remote=(ssh -o BatchMode=yes -o ConnectTimeout=12 "$host")
work=$(mktemp -d)
stage=''
cleanup() {
  if [[ "$stage" == /tmp/aid-publish.* ]]; then
    "${remote[@]}" "rm -rf -- '$stage'" || true
  fi
  rm -rf -- "$work"
}
trap cleanup EXIT
preflight() {
  "${remote[@]}" sudo -n bash -s -- "$sql_tree" <<'REMOTE'
set -euo pipefail
cd /opt/aid-studio/current
test -s aid-admin.jar && test -s admin/index.html && test -s web-prefix/index.html || exit 1
systemctl is-active --quiet aid-studio
systemctl is-active --quiet aid-studio-mysql
systemctl is-active --quiet aid-studio-redis
nginx -t
# 首次接管的手工部署使用 9f7a46a 的 SQL。
installed_sql=1f358c52483ce5db7d6d1150e720eb9997cbe160
if [[ -f .sql-tree ]]; then installed_sql=$(cat .sql-tree); fi
[[ "$installed_sql" == "$1" ]] || { echo "SQL 已变化，请先单独备份、迁移并确认线上 schema 后更新 .sql-tree。" >&2; exit 1; }
stat -Lc '%d:%i' .
REMOTE
}
readiness() {
  local base=https://43.154.247.11/aid-studio
  curl -fsS --max-time 15 "$base/" | grep '/aid-studio/_next/' >/dev/null &&
  curl -fsS --max-time 15 -X POST "$base/aid/auth/public-config" | python3 -c 'import json,sys; assert json.load(sys.stdin)["code"] == 200' &&
  curl -fsS --max-time 15 --range 0-15 "$base/profile/aid/style/sample/1-linghu_01.jpg" | python3 -c 'import sys; assert sys.stdin.buffer.read().startswith(bytes.fromhex("ffd8ff"))'
}
baseline=$(preflight)
readiness
echo "构建 Git 提交 ${commit}（只包含已提交文件）"
mkdir "$work/src" "$work/package"
git archive "$commit" | tar -xf - -C "$work/src"
if [[ $(uname -s) == Darwin ]]; then
  JAVA_HOME=$(/usr/libexec/java_home -v 17)
  export JAVA_HOME PATH="$JAVA_HOME/bin:$PATH"
fi
cat > "$work/settings.xml" <<'XML'
<settings><mirrors><mirror><id>aid-central</id><mirrorOf>*</mirrorOf><url>https://repo.maven.apache.org/maven2</url></mirror></mirrors></settings>
XML
(cd "$work/src" && mvn -B -ntp -s "$work/settings.xml" clean package -DskipTests)
(cd "$work/src/frontend/admin" && npm ci --prefer-offline --no-audit --no-fund && npm run build)
(cd "$work/src/frontend/web" && npm ci --prefer-offline --no-audit --no-fund && NEXT_PUBLIC_BASE_PATH=/aid-studio npm run generate)
mv "$work/src/aid-admin/target/aid-admin.jar" "$work/package/"
mv "$work/src/frontend/admin/dist" "$work/package/admin"
mv "$work/src/frontend/web/dist/public" "$work/package/web-prefix"
unzip -tq "$work/package/aid-admin.jar" >/dev/null
test -s "$work/package/admin/index.html"
grep -F '/aid-studio/_next/' "$work/package/web-prefix/works/index.html" >/dev/null
printf '%s\n' "$commit" > "$work/package/.commit"
printf '%s\n' "$sql_tree" > "$work/package/.sql-tree"
if [[ "$mode" == check ]]; then echo "构建、服务器预检和现役公网入口验证通过。"; exit 0; fi
COPYFILE_DISABLE=1 tar -czf "$work/bundle.tgz" -C "$work/package" .
digest=$(shasum -a 256 "$work/bundle.tgz" | awk '{print $1}')
stage=$("${remote[@]}" 'mktemp -d /tmp/aid-publish.XXXXXXXX')
[[ "$stage" =~ ^/tmp/aid-publish\.[[:alnum:]]+$ ]] || exit 1
scp -o BatchMode=yes "$work/bundle.tgz" "$host:$stage/"
"${remote[@]}" sudo -n bash -s -- "$stage" "$digest" "$baseline" <<'REMOTE'
set -euo pipefail
cd /opt/aid-studio
exec 9>.publish.lock
flock -n 9 || { echo "已有发布正在执行"; exit 1; }
[[ "$(stat -Lc '%d:%i' current)" == "$3" ]] || { echo "构建期间线上版本已变动，请重新发布"; exit 1; }
printf '%s  %s\n' "$2" "$1/bundle.tgz" | sha256sum -c -
mkdir -p releases
slot=$(mktemp -d "$PWD/releases/release-XXXXXXXX")
chmod 755 "$slot"
tar --no-same-owner -xzf "$1/bundle.tgz" -C "$slot"
# Linux 原子交换两个目录，首次发布也兼容原有的实体 current 目录。
swap() {
  python3 - "$slot" <<'PY'
import ctypes, os, sys
libc = ctypes.CDLL(None, use_errno=True)
if libc.renameat2(-100, b'current', -100, os.fsencode(sys.argv[1]), 2):
    raise OSError(ctypes.get_errno(), os.strerror(ctypes.get_errno()))
PY
}
base='https://43.154.247.11/aid-studio'
probe=(curl --noproxy '*' --resolve 43.154.247.11:443:127.0.0.1 -fsS --max-time 6)
wait_api() {
  local n
  for ((n=0; n<60; n++)); do
    if "${probe[@]}" -X POST "$base/aid/auth/public-config" 2>/dev/null |
      python3 -c 'import json,sys; assert json.load(sys.stdin)["code"] == 200' 2>/dev/null; then return 0; fi
    if systemctl is-failed --quiet aid-studio; then return 1; fi
    sleep 2
  done
  return 1
}
rollback() {
  trap - EXIT HUP INT TERM
  echo "发布未完成，恢复原版本" >&2
  swap && systemctl reset-failed aid-studio && systemctl restart aid-studio && wait_api || { echo "恢复失败，请检查 $slot 与 current" >&2; exit 1; }
  exit 1
}
swap
trap rollback EXIT
trap 'exit 1' HUP INT TERM
systemctl restart aid-studio
wait_api
"${probe[@]}" "$base/" | grep -F '/aid-studio/_next/' >/dev/null
curl -fsS --max-time 8 http://127.0.0.1:18489/ | grep -Fi '<html' >/dev/null
"${probe[@]}" --range 0-15 "$base/profile/aid/style/sample/1-linghu_01.jpg" | python3 -c 'import sys; assert sys.stdin.buffer.read().startswith(bytes.fromhex("ffd8ff"))'
trap - EXIT HUP INT TERM
echo "已切换 AID；旧程序保存在 $slot"
REMOTE
readiness || { echo "服务端验证已通过，但本机公网复验失败，请检查公网入口。" >&2; exit 1; }
echo "发布成功 https://43.154.247.11/aid-studio/"
