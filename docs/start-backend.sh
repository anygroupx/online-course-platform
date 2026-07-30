#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../backend"
echo "Starting course-web (dev profile, port 8080)..."
mvn -pl course-web -am spring-boot:run
