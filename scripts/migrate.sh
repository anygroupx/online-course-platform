#!/usr/bin/env bash
# 迁移账本管理：让「哪个迁移跑过」有据可查，而不是靠记忆。
#
# 用法：
#   scripts/migrate.sh status           # 列出已应用/待应用（只读）
#   scripts/migrate.sh adopt            # 把存量迁移登记为已应用（不回放 SQL）
#   scripts/migrate.sh apply            # 按序执行待应用迁移并登记
#   scripts/migrate.sh apply --dry-run  # 只打印将要执行什么
#
# 设计约束：
#   - 只通过 docker exec 连容器内的 MySQL，不碰宿主机网络，不需要客户端凭据落盘。
#   - checksum 记录文件内容的 SHA256：文件被改写会被发现，而不是静默重跑。
#   - adopt 只登记不执行。存量环境的 001-039 已经跑过（没有账本），
#     唯一诚实的做法是登记事实，而不是猜它们「应该」是什么状态。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MIG_DIR="$ROOT/database/migrations"
CONTAINER="${MYSQL_CONTAINER:-course-mysql}"
DB="${MYSQL_DATABASE:-online_course}"
LEDGER_DDL="$MIG_DIR/040_schema_migration.sql"

die() { echo "错误: $*" >&2; exit 1; }

mysql_exec() {
  docker exec -i "$CONTAINER" sh -c \
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --batch --skip-column-names --default-character-set=utf8mb4 "$@"' \
    sh "$@" 2>&1
}

query() { printf '%s\n' "$1" | mysql_exec -uroot "$DB"; }

sha256_file() {
  if command -v sha256sum >/dev/null 2>&1; then sha256sum "$1" | awk '{print $1}'
  else shasum -a 256 "$1" | awk '{print $1}'; fi
}

ledger_exists() {
  query "SELECT COUNT(*) FROM information_schema.tables
         WHERE table_schema=DATABASE() AND table_name='schema_migration';" | grep -qx '1'
}

bootstrap_ledger() {
  if ledger_exists; then return 0; fi
  echo "==> 迁移账本不存在，先用 040 建表"
  printf '%s\n' "$(cat "$LEDGER_DDL")" | mysql_exec -uroot "$DB" || die "建账本失败"
  query "INSERT IGNORE INTO schema_migration(version,source,checksum,applied_by)
         VALUES('040_schema_migration.sql','baseline','$(sha256_file "$LEDGER_DDL")','migrate.sh');" >/dev/null
  ledger_exists || die "建账本后仍检测不到 schema_migration"
}

migration_files() {
  find "$MIG_DIR" -maxdepth 1 -name '[0-9][0-9][0-9]_*.sql' -type f | sort
}

cmd_status() {
  bootstrap_ledger
  local applied; applied="$(query "SELECT version FROM schema_migration ORDER BY version;")"
  echo "数据库: $DB   容器: $CONTAINER"
  echo
  printf '%-52s %-10s %s\n' '版本' '状态' '校验'
  local pending=0 drift=0 total=0
  while IFS= read -r f; do
    local version; version="$(basename "$f")"
    local sum; sum="$(sha256_file "$f")"
    total=$((total+1))
    if grep -qxF "$version" <<<"$applied"; then
      local recorded; recorded="$(query "SELECT checksum FROM schema_migration WHERE version='$version';")"
      if [ "$recorded" = "$sum" ]; then
        printf '%-52s %-10s %s\n' "$version" 'applied' 'ok'
      else
        printf '%-52s %-10s %s\n' "$version" 'applied' "DRIFT(账本=${recorded:0:12} 实际=${sum:0:12})"
        drift=$((drift+1))
      fi
    else
      printf '%-52s %-10s %s\n' "$version" 'PENDING' '-'
      pending=$((pending+1))
    fi
  done < <(migration_files)
  echo
  echo "合计 ${total} 个文件：待应用 ${pending}，校验漂移 ${drift}"
  if [ "$drift" -gt 0 ]; then
    echo "警告: 校验漂移说明文件在登记后被改动过，需人工确认后再继续" >&2
  fi
  return 0
}

cmd_adopt() {
  bootstrap_ledger
  local applied; applied="$(query "SELECT version FROM schema_migration;")"
  local n=0
  while IFS= read -r f; do
    local version; version="$(basename "$f")"
    [ "$version" = '040_schema_migration.sql' ] && continue
    grep -qxF "$version" <<<"$applied" && continue
    query "INSERT IGNORE INTO schema_migration(version,source,checksum,applied_by)
           VALUES('$version','adopt','$(sha256_file "$f")','migrate.sh');" >/dev/null
    echo "  adopt $version"
    n=$((n+1))
  done < <(migration_files)
  echo "已登记 $n 个存量迁移（未执行任何 SQL）"
}

cmd_apply() {
  local dry=0
  [ "${1:-}" = '--dry-run' ] && dry=1
  bootstrap_ledger
  local applied; applied="$(query "SELECT version FROM schema_migration;")"
  local n=0
  while IFS= read -r f; do
    local version; version="$(basename "$f")"
    grep -qxF "$version" <<<"$applied" && continue
    n=$((n+1))
    if [ "$dry" = 1 ]; then
      echo "  将执行 $version"
      continue
    fi
    echo "==> 执行 $version"
    local out
    if ! out="$(printf '%s\n' "$(cat "$f")" | mysql_exec -uroot "$DB" 2>&1)"; then
      die "迁移失败 $version
$out"
    fi
    query "INSERT IGNORE INTO schema_migration(version,source,checksum,applied_by)
           VALUES('$version','apply','$(sha256_file "$f")','migrate.sh');" >/dev/null
    echo "    已登记"
  done < <(migration_files)
  [ "$n" -eq 0 ] && echo "没有待应用的迁移"
  return 0
}

case "${1:-status}" in
  status) cmd_status ;;
  adopt)  cmd_adopt ;;
  apply)  cmd_apply "${2:-}" ;;
  *)      die "用法: $0 {status|adopt|apply [--dry-run]}" ;;
esac
