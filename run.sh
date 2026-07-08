#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

if [[ ! -f .env ]]; then
  echo "[run.sh] .env not found"
  exit 1
fi

set -a
# shellcheck disable=SC1091
source .env
set +a

exec ./gradlew bootRun "$@"
