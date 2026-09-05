#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."
for tool in docker curl jq timeout; do
    command -v "$tool" >/dev/null || { echo "Required command missing: $tool" >&2; exit 1; }
done

# Read-only check of the real services after rebuilding EAP and recreating Keycloak.
# Inspect only this container's lifetime, so old logs cannot make the check pass.
compose=(timeout 15s docker compose --profile observability)
echo "Checking Docker and Compose (15s timeout per Docker command)..."
project=$("${compose[@]}" config --format json | jq -r '.name')
for service in eap keycloak; do
    container=$("${compose[@]}" ps -q "$service")
    [[ -n "$container" ]] || { echo "FAIL: $service is not running" >&2; exit 1; }
    started=$(timeout 15s docker inspect --format '{{.State.StartedAt}}' "$container")
    verified=false
    deadline=$((SECONDS + 60))
    echo "Checking $service: JSON startup message in container logs, then Grafana (about 60s maximum)..."
    while ((SECONDS < deadline)); do
        # Emit only the count, never real log payloads or credentials.
        if ! count=$(timeout 15s docker logs --since "$started" --tail 5000 "$container" 2>&1 |
            jq -Rsc --arg service "$service" '[split("\n")[] | fromjson? |
                select(type == "object") |
                select((.timestamp | type) == "string" and (.message | type) == "string" and
                    (.loggerName | type) == "string" and (.level | type) == "string") |
                select(if $service == "eap" then .message | contains("WFLYSRV0025")
                    else .loggerName == "io.quarkus" and (.message | contains("started in")) end)] | length'); then
            echo "FAIL: Cannot read $service container logs (Docker error or 15s timeout)." >&2
            exit 1
        fi
        query=$(jq -rn --arg project "$project" --arg service "$service" '
            "{project=" + ($project | tojson) + ",service_name=" + ($service | tojson) +
            ",level=\"INFO\"} | json | __error__=\"\" | " +
            (if $service == "eap" then "message=~\".*WFLYSRV0025.*\""
                else "loggerName=\"io.quarkus\" | message=~\".*started in.*\"" end)')
        if [[ "$count" -eq 0 ]]; then
            echo "WAIT: $service has no matching JSON startup message in its last 5000 log lines."
            sleep 5
            continue
        fi
        if ! response=$(curl -sS --max-time 5 --get \
            http://127.0.0.1:3000/api/datasources/proxy/uid/loki/loki/api/v1/query_range \
            --data-urlencode "query=$query" --data-urlencode "start=$started" \
            --data-urlencode 'limit=10' --write-out '\n%{http_code}' 2>/dev/null); then
            echo "WAIT: $service JSON startup message found; Grafana is unreachable or timed out."
            sleep 5
            continue
        fi
        status=${response##*$'\n'}
        body=${response%$'\n'*}
        if [[ "$status" != 200 ]]; then
            echo "FAIL: $service JSON startup message found, but Grafana/Loki returned HTTP $status." >&2
            echo "Check the Loki datasource and query in Grafana Explore; no response payload is printed." >&2
            exit 1
        fi
        if jq -e '.status == "success" and (.data.result | length > 0)' <<< "$body" >/dev/null; then
            verified=true
            echo "PASS: Real $service startup log is JSON and queryable by level in Grafana."
            break
        fi
        # Missing labels match the empty string. Filter before JSON parsing adds
        # the payload's level field, so this detects pre-upgrade log streams.
        legacy_query=${query/',level="INFO"}'/',level=""}'}
        if legacy_response=$(curl -fsS --max-time 5 --get \
            http://127.0.0.1:3000/api/datasources/proxy/uid/loki/loki/api/v1/query_range \
            --data-urlencode "query=$legacy_query" --data-urlencode "start=$started" \
            --data-urlencode 'limit=10' 2>/dev/null) &&
            jq -e '.status == "success" and (.data.result | length > 0)' <<< "$legacy_response" >/dev/null; then
            echo "FAIL: $service JSON startup log exists in Loki without an indexed level label." >&2
            echo "Run test-observability.sh first to load and verify Alloy, then restart keycloak/eap and rerun this test." >&2
            echo "The EAP restart retains the existing drop-and-create schema behavior." >&2
            exit 1
        fi
        echo "WAIT: $service JSON startup message found locally; no matching INFO entry in Grafana yet."
        sleep 5
    done
    [[ "$verified" == true ]] || {
        echo "FAIL: $service check timed out. See WAIT messages above for the failing stage." >&2
        exit 1
    }
done
