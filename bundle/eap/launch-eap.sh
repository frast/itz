#!/usr/bin/env bash
set -euo pipefail

eap_home="${JBOSS_HOME:-/opt/server}"
"${eap_home}/bin/add-user.sh" -u "${EAP_MGMT_USER}" -p "${EAP_MGMT_PASSWORD}" -g SuperUser -s
"${eap_home}/bin/standalone.sh" -b 0.0.0.0 -bmanagement 0.0.0.0 &
server_pid=$!

until "${eap_home}/bin/jboss-cli.sh" --connect --command=':read-attribute(name=server-state)' 2>/dev/null | grep -q running; do
  sleep 2
done

"${eap_home}/bin/jboss-cli.sh" --connect --file=/usr/local/etc/itz/configure-datasource.cli

wait "${server_pid}"
