#!/usr/bin/env bash
# 本地安全门禁入口：缺少工具、扫描异常或发现漏洞都必须返回失败。
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

for scanner in gitleaks mvn npm trivy semgrep; do
  if ! command -v "$scanner" >/dev/null 2>&1; then
    echo "安全扫描未执行：缺少必要工具 $scanner" >&2
    exit 2
  fi
done

echo "==> [1/5] 扫描密钥"
gitleaks detect --source . --config .gitleaks.toml --no-git -v

echo "==> [2/5] 编译后端"
(cd backend && mvn -pl course-web -am -DskipTests compile)

echo "==> [3/5] 扫描前端生产依赖"
(cd frontend && npm audit --omit=dev)

echo "==> [4/5] 扫描文件系统漏洞"
trivy fs --exit-code 1 --severity HIGH,CRITICAL "$ROOT"

echo "==> [5/5] 扫描代码安全问题"
semgrep scan --config p/owasp-top-ten --config p/java --config p/security-audit --error --strict "$ROOT"

echo "本地安全扫描全部通过。"
