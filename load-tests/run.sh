#!/usr/bin/env bash
# Runs a k6 scenario inside the Compose network against the containerized backend.
#   ./load-tests/run.sh <spike|naive> <shoppers> <stock>
set -euo pipefail
scenario="${1:?scenario: spike or naive}"
shoppers="${2:-2000}"
stock="${3:-100}"
root="$(cd "$(dirname "$0")/.." && pwd)"
mkdir -p "$root/load-tests/results"

MSYS_NO_PATHCONV=1 docker run --rm --network flash-sale_default \
  -v "$root/load-tests:/scripts" -v "$root/load-tests/results:/results" \
  -e BASE_URL=http://backend:8080 -e SHOPPERS="$shoppers" -e STOCK="$stock" \
  grafana/k6:1.8.2 run --quiet "/scripts/${scenario}.js"
