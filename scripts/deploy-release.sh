#!/usr/bin/env bash
# 生产发布：把「手工 SSH 敲命令」固化成可复现、可回滚的一步。
#
# 用法（在生产机仓库根目录执行）：
#   scripts/deploy-release.sh --release <名称> [--ref origin/dev] [--dry-run] [--skip-migration-check]
#
# 它做什么（顺序即安全边界）：
#   1. 前置检查：工作区干净、revision 存在、迁移账本无未应用项
#   2. 记录回滚三件套：旧 revision / 旧镜像 / 旧环境变量
#   3. 从干净 worktree 快照构建（绝不拿生产工作区当构建上下文）
#   4. 切换前反向对照：证明新镜像确实含本次改动
#   5. 只切换 app 容器，数据服务与卷完全不动
#   6. 回填运行中容器的环境变量并 pin 另一侧镜像
#   7. 健康检查 + 线上产物验证
#
# 不做什么：
#   - 不清理 docker builder 缓存（清了会让后端重建从 60 秒退化到 5-8 分钟）
#   - 不执行数据库迁移（迁移需人工确认，见 scripts/migrate.sh）
#   - 不动 mysql/redis/elasticsearch（数据服务重启风险与收益不成比例）
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

RELEASE=""
REF="origin/dev"
DRY_RUN=0
SKIP_MIGRATION_CHECK=0

die()  { echo "错误: $*" >&2; exit 1; }
step() { echo; echo "=== $* ==="; }

while [ $# -gt 0 ]; do
  case "$1" in
    --release)              RELEASE="${2:?--release 需要名称}"; shift 2 ;;
    --ref)                  REF="${2:?--ref 需要引用}"; shift 2 ;;
    --dry-run)              DRY_RUN=1; shift ;;
    --skip-migration-check) SKIP_MIGRATION_CHECK=1; shift ;;
    -h|--help)              sed -n '2,20p' "$0"; exit 0 ;;
    *)                      die "未知参数: $1" ;;
  esac
done

[ -n "$RELEASE" ] || die "必须指定 --release <名称>"
case "$RELEASE" in
  *[!A-Za-z0-9._-]*) die "release 名只能包含字母数字和 . _ -" ;;
esac

RELDIR=".cache/deployments/$RELEASE"
BACKEND_IMAGE="course-platform-backend:$RELEASE"
FRONTEND_IMAGE="course-platform-frontend:$RELEASE"

step "[1/8] 前置检查"
git rev-parse --verify "$REF" >/dev/null 2>&1 || die "引用不存在: $REF"
if [ -n "$(git status --porcelain)" ]; then
  die "工作区不干净，发布必须从干净状态开始
$(git status --short | head)"
fi
git fetch -q origin 2>/dev/null || echo "  警告: fetch 失败，使用本地已有的 $REF"
TARGET_REV="$(git rev-parse "$REF")"
echo "  目标: $REF -> $TARGET_REV"
echo "  当前: $(git rev-parse HEAD)"

if [ "$SKIP_MIGRATION_CHECK" = 0 ]; then
  if [ -x scripts/migrate.sh ]; then
    if ! OUT="$(scripts/migrate.sh status 2>&1)"; then
      echo "$OUT" >&2
      die "迁移状态检查失败；确认后可用 --skip-migration-check 跳过"
    fi
    # 取「合计」行里的两个计数，避免依赖中文分隔符匹配
    SUMMARY="$(printf '%s\n' "$OUT" | grep -E '合计' | tail -1)"
    PENDING="$(printf '%s' "$SUMMARY" | grep -oE '[0-9]+' | sed -n '2p')"
    DRIFT="$(printf '%s' "$SUMMARY" | grep -oE '[0-9]+' | sed -n '3p')"
    echo "  迁移: 待应用 ${PENDING:-?}，校验漂移 ${DRIFT:-?}"
    if [ "${PENDING:-0}" != "0" ]; then
      echo "$OUT" | grep -E 'PENDING|合计' || true
      die "存在未应用的迁移。先执行 scripts/migrate.sh apply，或人工确认后用 --skip-migration-check"
    fi
    if [ "${DRIFT:-0}" != "0" ]; then
      die "迁移文件校验漂移，账本与文件不一致，需人工确认"
    fi
  else
    echo "  警告: 未找到 scripts/migrate.sh，跳过迁移检查"
  fi
fi

DB_BACKUP=1
step "[2/8] 记录回滚三件套 + 数据库备份"
if [ "$DRY_RUN" = 1 ]; then
  echo "  (dry-run) 将写入 $RELDIR/private/"
else
  mkdir -p "$RELDIR/private"
  git rev-parse HEAD > "$RELDIR/private/old-revision.txt"
  docker inspect course-frontend --format '{{.Config.Image}}' > "$RELDIR/private/old-frontend-image.txt"
  docker inspect course-backend  --format '{{.Config.Image}}' > "$RELDIR/private/old-backend-image.txt"
  docker inspect course-frontend --format '{{range .Config.Env}}{{println .}}{{end}}' | sort > "$RELDIR/private/env-before.txt"
  docker inspect course-backend  --format '{{range .Config.Env}}{{println .}}{{end}}' | sort > "$RELDIR/private/env-before-backend.txt"
  cp docker-compose.yml "$RELDIR/private/docker-compose.yml.bak"
  echo "  旧后端镜像: $(cat "$RELDIR/private/old-backend-image.txt")"
  echo "  旧前端镜像: $(cat "$RELDIR/private/old-frontend-image.txt")"
  echo "  旧 revision: $(cat "$RELDIR/private/old-revision.txt")"

  if [ "$DB_BACKUP" = 1 ]; then
    echo "  备份数据库..."
    docker exec course-mysql sh -c \
      'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot \
       --single-transaction --routines --triggers --events --set-gtid-purged=OFF "$MYSQL_DATABASE"' \
      > "$RELDIR/private/db-before.sql" 2>/dev/null || die "数据库备份失败"
    TABLES="$(grep -c '^CREATE TABLE' "$RELDIR/private/db-before.sql" || true)"
    grep -q 'Dump completed' "$RELDIR/private/db-before.sql" || die "数据库备份不完整"
    echo "  备份完成: $TABLES 张表"
  fi
  echo "$RELEASE" > "$RELDIR/release-id.txt"
fi

step "[3/8] 干净快照（构建上下文绝不用生产工作区）"
SNAPSHOT="$RELDIR/source"
if [ "$DRY_RUN" = 1 ]; then
  echo "  (dry-run) 将创建 worktree $SNAPSHOT @ $TARGET_REV"
else
  git worktree remove --force "$SNAPSHOT" 2>/dev/null || true
  git worktree add --detach "$SNAPSHOT" "$TARGET_REV" >/dev/null
  echo "  快照 HEAD: $(git -C "$SNAPSHOT" rev-parse --short HEAD)"
  [ -z "$(git -C "$SNAPSHOT" status --porcelain)" ] || die "快照不干净"
fi

step "[4/8] 构建镜像"
if [ "$DRY_RUN" = 1 ]; then
  echo "  (dry-run) 将构建 $BACKEND_IMAGE 与 $FRONTEND_IMAGE"
else
  # 后端
  echo "  构建后端..."
  START="$(date +%s)"
  if ! docker build -f "$SNAPSHOT/backend/Dockerfile" -t "$BACKEND_IMAGE" \
       "$SNAPSHOT/backend" > "$RELDIR/private/backend-build.log" 2>&1; then
    tail -30 "$RELDIR/private/backend-build.log" >&2
    die "后端构建失败"
  fi
  echo "    完成，用时 $(( $(date +%s) - START )) 秒"

  # 前端：TURNSTILE_SITE_KEY 是构建期注入的公开值，必须在场
  SITE_KEY="$(grep -E '^TURNSTILE_SITE_KEY=' .env 2>/dev/null | cut -d= -f2- || true)"
  [ -n "$SITE_KEY" ] || die "TURNSTILE_SITE_KEY 为空，前端镜像会回退到测试 Key"
  echo "  构建前端..."
  START="$(date +%s)"
  if ! docker build -f "$SNAPSHOT/frontend/Dockerfile" \
       --build-arg VITE_TURNSTILE_SITE_KEY="$SITE_KEY" \
       -t "$FRONTEND_IMAGE" "$SNAPSHOT/frontend" \
       > "$RELDIR/private/frontend-build.log" 2>&1; then
    tail -30 "$RELDIR/private/frontend-build.log" >&2
    die "前端构建失败"
  fi
  echo "    完成，用时 $(( $(date +%s) - START )) 秒"
fi

step "[5/8] 切换前验证：新镜像含本次改动"
if [ "$DRY_RUN" = 1 ]; then
  echo "  (dry-run) 跳过"
else
  check_image() {
    local img="$1" label="$2"
    local cid; cid="$(docker create "$img" 2>/dev/null)"
    docker cp "$cid:/usr/share/nginx/html/index.html" "$RELDIR/private/$label-index.html" >/dev/null 2>&1 || true
    docker rm -f "$cid" >/dev/null 2>&1
    if [ -f "$RELDIR/private/$label-index.html" ]; then
      echo "  $label: index.html 可提取 ($(wc -c < "$RELDIR/private/$label-index.html") 字节)"
    else
      echo "  $label: 警告，无法从镜像提取 index.html"
    fi
  }
  check_image "$FRONTEND_IMAGE" frontend
  docker run --rm --entrypoint sh "$BACKEND_IMAGE" -c 'test -f /app/app.jar && echo "  backend: app.jar 存在"' || echo "  backend: 警告，未找到 app.jar"
fi

step "[6/8] 生成 compose override（回填环境变量 + pin 另一侧镜像）"
OVERRIDE="$RELDIR/private/compose.release.json"
if [ "$DRY_RUN" = 1 ]; then
  echo "  (dry-run) 将生成 $OVERRIDE"
else
  python3 - "$OVERRIDE" "$FRONTEND_IMAGE" "$BACKEND_IMAGE" <<'PY'
import json, subprocess, sys
out, f_img, b_img = sys.argv[1], sys.argv[2], sys.argv[3]

def env_of(name):
    insp = json.loads(subprocess.check_output(['docker', 'inspect', name]))[0]
    return {k: v for k, v in (kv.split('=', 1) for kv in insp['Config']['Env'] if '=' in kv)}

# 两个服务都 pin 具体 tag：compose 里是 :latest，up -d 会把没 pin 的一侧切回旧镜像
override = {"services": {
    "frontend": {"image": f_img, "environment": env_of('course-frontend')},
    "backend":  {"image": b_img, "environment": env_of('course-backend')},
}}
with open(out, 'w') as fh:
    json.dump(override, fh, indent=2, ensure_ascii=False)
PY
  chmod 0600 "$OVERRIDE"   # 含密钥，仅属主可读
  echo "  override 已生成（0600）"
  if ! docker compose -p online-course-platform -f docker-compose.yml -f "$OVERRIDE" config >/dev/null 2>"$RELDIR/private/compose-config.err"; then
    cat "$RELDIR/private/compose-config.err" >&2
    die "compose 配置校验失败"
  fi
  echo "  compose 配置校验通过"
fi

step "[7/8] 只切换 app 容器"
if [ "$DRY_RUN" = 1 ]; then
  echo "  (dry-run) 将 recreate backend 与 frontend（--no-deps，不碰数据服务）"
else
  # 先切后端：前端在等它，反序会让用户看到 502
  docker compose -p online-course-platform -f docker-compose.yml -f "$OVERRIDE" \
    up -d --no-deps --no-build --force-recreate backend 2>&1 | tail -2
  docker compose -p online-course-platform -f docker-compose.yml -f "$OVERRIDE" \
    up -d --no-deps --no-build --force-recreate frontend 2>&1 | tail -2
fi

step "[8/8] 健康检查与线上验证"
if [ "$DRY_RUN" = 1 ]; then
  echo "  (dry-run) 跳过"
  echo
  echo "dry-run 完成，未做任何修改。"
  exit 0
fi
wait_healthy() {
  local name="$1" want="${2:-healthy}"
  for _ in $(seq 1 60); do
    local st; st="$(docker inspect "$name" --format '{{.State.Health.Status}}' 2>/dev/null || echo unknown)"
    [ "$st" = "$want" ] && { echo "  $name: $st"; return 0; }
    sleep 3
  done
  echo "  $name: 超时未达 $want（当前 $(docker inspect "$name" --format '{{.State.Health.Status}}' 2>/dev/null)）" >&2
  return 1
}
FAIL=0
wait_healthy course-backend  || FAIL=1
wait_healthy course-frontend || FAIL=1

echo "  线上产物验证:"
docker exec course-frontend sh -c '
  code=$(curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1/)
  echo "    index.html: HTTP $code"
  css=$(curl -s http://127.0.0.1/ | grep -oE "/assets/[a-zA-Z0-9_-]+\.css" | head -1)
  if [ -n "$css" ]; then
    ccode=$(curl -s -o /dev/null -w "%{http_code}" "http://127.0.0.1$css")
    echo "    $css: HTTP $ccode"
  fi
' || FAIL=1

echo "  环境变量对照:"
docker inspect course-frontend --format '{{range .Config.Env}}{{println .}}{{end}}' | sort > "$RELDIR/private/env-after.txt"
if diff -q "$RELDIR/private/env-before.txt" "$RELDIR/private/env-after.txt" >/dev/null; then
  echo "    前端: 与切换前一致"
else
  echo "    前端: 有差异" >&2; diff "$RELDIR/private/env-before.txt" "$RELDIR/private/env-after.txt" >&2 || true; FAIL=1
fi
echo "    后端镜像: $(docker inspect course-backend --format '{{.Config.Image}}')"
echo "    前端镜像: $(docker inspect course-frontend --format '{{.Config.Image}}')"

echo
if [ "$FAIL" = 0 ]; then
  echo "发布成功: $RELEASE"
  echo "  发布目录: $RELDIR"
  echo "  回滚: scripts/rollback-release.sh $RELEASE"
else
  echo "发布过程中有检查未通过，见上方输出。" >&2
  echo "回滚三件套在 $RELDIR/private/" >&2
  exit 1
fi
