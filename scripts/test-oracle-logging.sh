#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."
for tool in docker curl jq timeout; do
    command -v "$tool" >/dev/null || { echo "Required command missing: $tool" >&2; exit 1; }
done
compose=(timeout 15s docker compose --profile observability)
echo "Checking Oracle diagnostics mount and health..."
project=$("${compose[@]}" config --format json | jq -r '.name')
container=$("${compose[@]}" ps -q oracle)
[[ -n "$container" ]] || { echo "FAIL: Oracle is not running." >&2; exit 1; }
timeout 15s docker inspect "$container" | jq -e '
    .[0] | .State.Health.Status == "healthy" and
    any(.Mounts[]; .Destination == "/opt/oracle/diag" and .Type == "volume")' >/dev/null || {
    echo "FAIL: Oracle must be healthy and use the diagnostics volume. Run enable-oracle-diagnostics.sh first." >&2
    exit 1
}

for source in oracle_alert oracle_listener; do
    echo "Checking $source: non-empty Oracle file and matching filename in Grafana..."
    if [[ "$source" == oracle_alert ]]; then
        pattern='/opt/oracle/diag/rdbms/*/*/trace/alert*.log'
    else
        pattern='/opt/oracle/diag/tnslsnr/*/*/trace/listener.log'
    fi
    # Only filenames leave the container; no real diagnostic payload is printed.
    paths=$(timeout 15s docker exec "$container" find /opt/oracle/diag -type f \
        -path "$pattern" -size +0c -print | jq -Rsc '
            split("\n") | map(select(length > 0) | sub("^/opt/oracle/diag"; "/var/log/oracle"))')
    jq -e 'length > 0' <<< "$paths" >/dev/null || {
        echo "FAIL: No non-empty file found for $source in the Oracle ADR directory." >&2
        exit 1
    }
    query=$(jq -rn --arg project "$project" --arg source "$source" '
        "{project=" + ($project | tojson) + ",service_name=\"oracle\",log_source=" + ($source | tojson) + "}"')
    deadline=$((SECONDS + 60))
    verified=false
    while ((SECONDS < deadline)); do
        if response=$(curl -sS --max-time 5 --get \
            http://127.0.0.1:3000/api/datasources/proxy/uid/loki/loki/api/v1/query_range \
            --data-urlencode "query=$query" --data-urlencode 'since=1h' \
            --data-urlencode 'limit=1000' --write-out '\n%{http_code}' 2>/dev/null); then
            status=${response##*$'\n'}
            body=${response%$'\n'*}
            if [[ "$status" != 200 ]]; then
                echo "FAIL: Grafana/Loki returned HTTP $status for $source." >&2
                exit 1
            fi
            if jq -e --argjson paths "$paths" '.status == "success" and
                any(.data.result[]; .stream.filename as $file |
                    ($paths | index($file)) != null and (.values | length > 0))' <<< "$body" >/dev/null; then
                echo "PASS: Real $source file is available in Grafana."
                verified=true
                break
            fi
        fi
        echo "WAIT: No matching $source file entries in Grafana within the last hour yet."
        sleep 5
    done
    [[ "$verified" == true ]] || { echo "FAIL: $source ingestion timed out; check Alloy and its diagnostics mount." >&2; exit 1; }
done
