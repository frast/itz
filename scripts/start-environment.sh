#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

docker compose \
    -f compose.yaml \
    -f compose.dev.yaml \
    --profile observability \
    -p itz \
    up -d
