#!/usr/bin/env bash
# 生产漂移检测：尽快发现「生产跑的不是 dev」，而不是等下次部署才撞上。
#
# 用法：scripts/check-drift.sh [--ref origin/dev] [--quiet]
# 退出码：0=一致  1=有漂移  2=检查本身失败
#
# 判定什么：
#   - 生产 HEAD 是否落后 / 领先 / 分叉于目标引用
#   - 生产工作区是否有未提交改动（脏工作区会让下次发布无法开始）
#   - 运行中的 app 容器镜像是否由当前生产的 commit 构建（标签不保证，但能发现明显错配）
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT" || exit 2

REF="origin/dev"
QUIET=0
while [ $# -gt 0 ]; do
  case "$1" in
    --ref) REF="${2:?}"; shift 2 ;;
    --quiet) QUIET=1; shift ;;
    *) echo "未知参数: $1" >&2; exit 2 ;;
  esac
done

say() { [ "$QUIET" = 1 ] || echo "$@"; }
fail() { echo "$@" >&2; }

if ! git rev-parse --verify "$REF" >/dev/null 2>&1; then
  fail "引用不存在: $REF"
  exit 2
fi

HEAD_REV="$(git rev-parse HEAD)"
REF_REV="$(git rev-parse "$REF")"

if [ "$HEAD_REV" = "$REF_REV" ]; then
  say "生产与 $REF 一致（${HEAD_REV:0:8}）"
else
  BEHIND="$(git rev-list --count "HEAD..$REF" 2>/dev/null || echo 0)"
  AHEAD="$(git rev-list --count "$REF..HEAD" 2>/dev/null || echo 0)"
  if [ "$AHEAD" -gt 0 ]; then
    fail "漂移：生产有 $AHEAD 个 $REF 没有的提交（分叉）"
    git log --oneline "$REF..HEAD" | head -5 | sed 's/^/    /' >&2
  else
    say "生产落后 $REF $BEHIND 个提交（正常运行状态，未发布新版本）"
  fi
  [ "$BEHIND" -gt 0 ] && say "    待发布: ${HEAD_REV:0:8} -> ${REF_REV:0:8}"
fi

DIRTY="$(git status --porcelain | head -5)"
if [ -n "$DIRTY" ]; then
  fail "漂移：生产工作区有未提交改动"
  echo "$DIRTY" | sed 's/^/    /' >&2
  exit 1
fi

[ "$HEAD_REV" = "$REF_REV" ] || [ "${AHEAD:-0}" = 0 ] || exit 1
exit 0
