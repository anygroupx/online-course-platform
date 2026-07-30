#!/usr/bin/env bash
# 本地可跑的安全扫描入口（不依赖 CI secrets）
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

echo "==> [1/5] Gitleaks (if installed)"
if command -v gitleaks >/dev/null 2>&1; then
  gitleaks detect --source . --config .gitleaks.toml --no-git -v || true
else
  echo "skip: gitleaks not installed (https://github.com/gitleaks/gitleaks)"
fi

echo "==> [2/5] Maven compile"
if [ -d backend ]; then
  export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}"
  (cd backend && mvn -pl course-web -am -DskipTests compile)
fi

echo "==> [3/5] npm audit"
if [ -f frontend/package-lock.json ]; then
  (cd frontend && npm audit --omit=dev || true)
else
  echo "skip: no package-lock.json"
fi

echo "==> [4/5] Trivy fs (if installed)"
if command -v trivy >/dev/null 2>&1; then
  trivy fs --severity HIGH,CRITICAL "$ROOT" || true
else
  echo "skip: trivy not installed"
fi

echo "==> [5/5] Semgrep (if installed)"
if command -v semgrep >/dev/null 2>&1; then
  semgrep --config p/owasp-top-ten --config p/java --error=false "$ROOT/backend" || true
else
  echo "skip: semgrep not installed"
fi

echo "Local security scans finished."
