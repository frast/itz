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
# Compose does not recreate containers when only bind-mounted configuration changes.
"${compose[@]}" restart alloy

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
    jq -e '
        .dashboard.uid == "itz-logs" and
        ([.dashboard.templating.list[].name] | sort == ["level", "request_id", "search", "service", "source"]) and
        ([.dashboard.panels[0].targets[].refId] | sort == ["A", "B"]) and
        (.dashboard.panels[0].targets[0].expr | contains("service_name=~") and
            contains("log_source=~") and contains("level=~") and
            contains("request_id=\\\"mdc[\\\\"request.id\\\"]\\\"") and contains("${request_id:regex}")) and
        (.dashboard.panels[0].targets[1].expr | contains("|~ "))
    ' >/dev/null

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
    payload="$marker"
    if [[ "$service" == eap || "$service" == keycloak ]]; then
        payload=$(jq -cn --arg marker "$marker" --arg timestamp "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
            '{timestamp: $timestamp, level: "ERROR", loggerName: "itz.smoke", message: $marker,
              stackTrace: "java.lang.IllegalStateException: synthetic\n\tat itz.Smoke.run(Smoke.java:1)"}')
    fi
    containers+=("$(docker run -d --label "com.docker.compose.project=$project" \
        --label "com.docker.compose.service=$service" \
        --label com.docker.compose.oneoff=True \
        --entrypoint /bin/sh "$alloy_image" \
        -c 'while true; do printf "%s\n%s\n" "$1" "$2"; sleep 2; done' \
        sh "$payload" "$marker-plain {invalid")")
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
        jq -e --arg project "$project" --arg marker "$marker" '
            .status == "success" and
            ([.data.result[].stream.service_name] | unique == ["eap", "keycloak", "oracle"]) and
            ([.data.result[].stream.project] | unique == [$project]) and
            ([.data.result[] | select(.stream.level == "ERROR") |
                select(any(.values[]; (.[1] | fromjson? |
                    .message == $marker and .loggerName == "itz.smoke" and
                    .stackTrace == "java.lang.IllegalStateException: synthetic\n\tat itz.Smoke.run(Smoke.java:1)"))) |
                .stream.service_name] | unique == ["eap", "keycloak"]) and
            ([.data.result[] | select(any(.values[]; .[1] == ($marker + "-plain {invalid"))) |
                .stream.service_name] | unique == ["eap", "keycloak", "oracle"])
        ' <<< "$response" >/dev/null; then
        echo "PASS: Structured EAP/Keycloak logs, intact stacktraces and plain-text fallback reached Grafana; Oracle logs and container filters verified."
        exit 0
    fi
    sleep 2
done
echo "FAIL: Expected container logs did not reach Grafana or filtering failed." >&2
echo "Inspect: docker compose --profile observability logs alloy loki grafana" >&2
exit 1
