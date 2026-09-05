#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."
for tool in docker curl jq; do
    command -v "$tool" >/dev/null || { echo "Required command missing: $tool" >&2; exit 1; }
done

compose=(docker compose --profile observability)
"${compose[@]}" config --quiet
project=$("${compose[@]}" config --format json | jq -r '.name')
alloy_image=$("${compose[@]}" config --format json | jq -r '.services.alloy.image')
"${compose[@]}" run --rm --no-deps alloy validate /etc/alloy/config.alloy
"${compose[@]}" run --rm --no-deps loki -config.file=/etc/loki/config.yaml -verify-config=true
# Explicit services avoid starting or recreating EAP, Oracle or Keycloak.
"${compose[@]}" up -d --no-deps loki alloy grafana

grafana_url=http://127.0.0.1:3000
ready=false
for ((attempt = 0; attempt < 60; attempt++)); do
    if curl -fsS --max-time 5 "$grafana_url/api/health" 2>/dev/null | jq -e '.database == "ok"' >/dev/null; then
        ready=true
        break
    fi
    sleep 2
done
[[ "$ready" == true ]] || { echo "Grafana did not become ready" >&2; exit 1; }
curl -fsS --max-time 10 "$grafana_url/api/dashboards/uid/itz-logs" |
    jq -e '.dashboard.uid == "itz-logs"' >/dev/null

marker="itz-observability-smoke-$(date +%s)-$$"
containers=()
cleanup() {
    if ((${#containers[@]})); then
        docker rm -f "${containers[@]}" >/dev/null
    fi
}
trap cleanup EXIT

# Synthetic containers exercise discovery, project/service filtering and ingestion.
# No application container is modified and no real log payload is printed.
for service in eap keycloak oracle dev; do
    containers+=("$(docker run -d --label "com.docker.compose.project=$project" \
        --label "com.docker.compose.service=$service" \
        --label com.docker.compose.oneoff=True \
        --entrypoint /bin/sh "$alloy_image" \
        -c 'while true; do echo "$1"; sleep 2; done' sh "$marker")")
done
containers+=("$(docker run -d --label "com.docker.compose.project=$marker" \
    --label com.docker.compose.service=eap --entrypoint /bin/sh "$alloy_image" \
    -c 'while true; do echo "$1"; sleep 2; done' sh "$marker")")

# Allow two discovery intervals so exclusion checks include all test containers.
sleep 10
for ((attempt = 0; attempt < 60; attempt++)); do
    if response=$(curl -fsS --max-time 5 --get \
        "$grafana_url/api/datasources/proxy/uid/loki/loki/api/v1/query_range" \
        --data-urlencode "query={environment=\"development\"} |= \"$marker\"" \
        --data-urlencode 'since=5m' --data-urlencode 'limit=1000' 2>/dev/null) &&
        jq -e --arg project "$project" '
            .status == "success" and
            ([.data.result[].stream.service_name] | unique == ["eap", "keycloak", "oracle"]) and
            ([.data.result[].stream.project] | unique == [$project])
        ' <<< "$response" >/dev/null; then
        echo "PASS: EAP, Keycloak and Oracle markers reached Grafana through Alloy and Loki; unrelated containers excluded."
        exit 0
    fi
    sleep 2
done
echo "FAIL: Expected container logs did not reach Grafana or filtering failed." >&2
echo "Inspect: docker compose --profile observability logs alloy loki grafana" >&2
exit 1
