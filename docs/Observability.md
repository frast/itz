# Lokale Logs

Das optionale Compose-Profil `observability` sammelt Logs von EAP, Keycloak und Oracle über Alloy und Loki. Grafana ist auf dem Docker-Host unter `http://localhost:3000/d/itz-logs` erreichbar. Compose-Befehle auf dem Host ausführen:

```bash
docker compose --profile observability up -d --no-deps loki alloy grafana
```

Das Dashboard kann nach Service, Level, Quelle und Request-ID filtern. REST-Antworten tragen eine `X-Request-ID`; in strukturierten EAP-Logs steht sie im MDC-Feld `request.id`. Für die Suche in Grafana Explore zum Beispiel:

```logql
{service_name="eap", level="INFO"} | json | mdc_request_id="<request-id>"
```

EAP und Keycloak schreiben JSON-Konsolenlogs, Oracle- und Startskript-Ausgaben können Text bleiben. Oracle-Alert- und Listener-Logs erscheinen unter `log_source="oracle_alert"` und `log_source="oracle_listener"`. Dieselbe Oracle-Meldung kann zusätzlich in `log_source="container"` stehen.

## Einrichtung und Prüfung

```bash
bash scripts/test-observability.sh
bash scripts/test-structured-logging.sh
bash scripts/test-oracle-logging.sh
```

Diese Skripte benötigen eine laufende lokale Umgebung; `test-observability.sh` startet den Logging-Stack und verwendet synthetische Logs. Für strukturierte Logs einer bestehenden Umgebung müssen EAP und Keycloak nach der Konfigurationsänderung auf dem Host neu gebaut beziehungsweise neu erstellt werden. Bei einer bestehenden Oracle-Umgebung das Diagnose-Volume vor einem erneuten Oracle-`up` mit `bash scripts/enable-oracle-diagnostics.sh` aktivieren. Das Skript unterbricht dafür Oracle kurz und übernimmt vorhandene Diagnosedateien. Bei einer frischen Umgebung genügt der normale Compose-Start.

Loki bewahrt Logs sieben Tage auf; Oracle-Diagnosedateien werden dadurch nicht gelöscht. Grafana erlaubt in dieser lokalen Konfiguration anonymen Lesezugriff. Alloy hat über den Docker-Socket weitreichenden Zugriff auf den Host. Deshalb ist der Stack nur für lokale Entwicklung vorgesehen. Keine Tokens, Kennwörter oder sensiblen Nutzdaten protokollieren.

Nur den Logging-Stack anhalten, ohne Volumes zu löschen:

```bash
docker compose --profile observability stop alloy grafana loki
```
