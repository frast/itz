#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."
for tool in docker jq timeout; do
    command -v "$tool" >/dev/null || { echo "Required command missing: $tool" >&2; exit 1; }
done
compose=(docker compose --profile observability)
"${compose[@]}" config --quiet
container=$(timeout 15s "${compose[@]}" ps -aq oracle)
diagnostics_volume=$("${compose[@]}" config --format json | jq -r '.volumes["oracle-diagnostics"].name')

# Provision permissions with the same image/user database as Oracle itself.
"${compose[@]}" run --rm --no-deps oracle-diagnostics-init
if [[ -n "$container" ]]; then
    mounted_volume=$(timeout 15s docker inspect "$container" |
        jq -r '.[0].Mounts[] | select(.Destination == "/opt/oracle/diag") | .Name // .Source')
    if [[ -n "$mounted_volume" && "$mounted_volume" != "$diagnostics_volume" ]]; then
        echo "FAIL: Oracle already uses a different diagnostics mount; migrate it explicitly before proceeding." >&2
        exit 1
    fi
    if [[ -z "$mounted_volume" ]]; then
        # Keep the original stopped container until its entire ADR tree is copied.
        # On copy failure it remains available and can be restarted without changes.
        echo "Stopping Oracle cleanly to preserve existing diagnostic files (up to 120s)..."
        docker stop --time 120 "$container" >/dev/null
        exit_code=$(timeout 15s docker inspect --format '{{.State.ExitCode}}' "$container")
        if [[ "$exit_code" == 137 ]]; then
            echo "FAIL: Oracle required a forced stop. Original container retained; investigate before replacing it." >&2
            exit 1
        fi
        echo "Copying existing diagnostics to the new volume; database files are not copied or changed..."
        if ! docker cp "$container:/opt/oracle/diag/." - |
            "${compose[@]}" run --rm -T --no-deps --entrypoint /bin/sh oracle-diagnostics-init \
                -c 'tar -xf - -C /diagnostics && chown oracle:oinstall /diagnostics'; then
            echo "FAIL: Copy failed. Original Oracle container is retained and stopped; do not recreate it." >&2
            echo "Fix the copy error and rerun this script, or start the existing container with docker compose start oracle." >&2
            exit 1
        fi
    fi
fi

echo "Starting Oracle with its existing database volume and the shared diagnostics volume..."
"${compose[@]}" up -d --no-deps oracle
"${compose[@]}" up -d --no-deps loki alloy grafana
"${compose[@]}" restart alloy
echo "Diagnostics enabled. Wait for Oracle to become healthy, then run bash scripts/test-oracle-logging.sh."
