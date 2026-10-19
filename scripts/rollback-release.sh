#!/usr/bin/env bash
# 回滚到某次发布的「之前」状态。
#
# 用法：scripts/rollback-release.sh <release-id> [--db]
#   --db 同时恢复该次发布前的数据库备份（会覆盖当前数据库，需谨慎）
#
# 前提：发布目录里存在回滚三件套（旧镜像、旧 revision、环境变量快照）。
# 没有快照就不做「猜测式回滚」——宁可停下让人判断。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

RELEASE="${1:-}"
WITH_DB=0
[ "${2:-}" = '--db' ] && WITH_DB=1

[ -n "$RELEASE" ] || { echo "用法: $0 <release-id> [--db]" >&2; exit 1; }
RELDIR=".cache/deployments/$RELEASE"
[ -d "$RELDIR/private" ] || { echo "错误: 找不到发布目录 $RELDIR" >&2; exit 1; }

for f in old-frontend-image.txt old-backend-image.txt env-before.txt; do
  if [ ! -f "$RELDIR/private/$f" ]; then
    echo "错误: 缺少回滚依据 $RELDIR/private/$f" >&2
    exit 1
  fi
done

OLD_F="$(cat "$RELDIR/private/old-frontend-image.txt")"
OLD_B="$(cat "$RELDIR/private/old-backend-image.txt")"
echo "回滚目标:"
echo "  前端: $OLD_F"
echo "  后端: $OLD_B"
echo "  代码: $(cat "$RELDIR/private/old-revision.txt" 2>/dev/null || echo 未知)"

for img in "$OLD_F" "$OLD_B"; do
  docker image inspect "$img" >/dev/null 2>&1 || { echo "错误: 镜像已不存在 $img" >&2; exit 1; }
done

if [ "$WITH_DB" = 1 ]; then
  [ -f "$RELDIR/private/db-before.sql" ] || { echo "错误: 找不到 $RELDIR/private/db-before.sql" >&2; exit 1; }
  echo
  echo "准备恢复数据库到发布前状态（这会覆盖当前数据）"
  echo "  备份文件: $RELDIR/private/db-before.sql"
  read -r -p "  输入 RESTORE 确认: " ans
  [ "$ans" = 'RESTORE' ] || { echo "已取消"; exit 1; }
  docker exec -i course-mysql sh -c \
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot "$MYSQL_DATABASE"' \
    < "$RELDIR/private/db-before.sql"
  echo "  数据库已恢复"
fi

echo
echo "切换容器..."
python3 - "$RELDIR/private/compose.rollback.json" "$OLD_F" "$OLD_B" "$RELDIR/private/env-before.txt" <<'PY'
import json, sys
out, f_img, b_img, envfile = sys.argv[1:5]
env = {}
for line in open(envfile):
    line = line.rstrip('\n')
    if '=' in line:
        k, v = line.split('=', 1); env[k] = v
json.dump({"services": {
    "frontend": {"image": f_img, "environment": env},
    "backend":  {"image": b_img},
}}, open(out, 'w'), indent=2, ensure_ascii=False)
PY
chmod 0600 "$RELDIR/private/compose.rollback.json"

docker compose -p online-course-platform -f docker-compose.yml -f "$RELDIR/private/compose.rollback.json" \
  up -d --no-deps --no-build --force-recreate backend 2>&1 | tail -2
docker compose -p online-course-platform -f docker-compose.yml -f "$RELDIR/private/compose.rollback.json" \
  up -d --no-deps --no-build --force-recreate frontend 2>&1 | tail -2

for n in course-backend course-frontend; do
  for _ in $(seq 1 60); do
    st="$(docker inspect "$n" --format '{{.State.Health.Status}}' 2>/dev/null || echo unknown)"
    [ "$st" = 'healthy' ] && break
    sleep 3
  done
  echo "  $n: $(docker inspect "$n" --format '{{.State.Health.Status}}')"
done

if [ -f "$RELDIR/private/old-revision.txt" ]; then
  echo
  echo "代码回退（如需要，手动执行）:"
  echo "  git checkout $(cat "$RELDIR/private/old-revision.txt")"
fi
