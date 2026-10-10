# Entwicklung

## Umgebung steuern

Compose-Befehle auf dem Docker-Host ausführen; Docker ist im Dev Container nicht verfügbar. Die Skripte verwenden das Compose-Projekt `itz` mit `compose.yaml` und `compose.dev.yaml`:

```bash
./scripts/stop-environment.sh
./scripts/start-environment.sh
```

Dabei bleiben die Daten-Volumes erhalten. `docker compose down -v` würde unter anderem Oracle-, Keycloak- und Logdaten löschen.

Die EAP-Management-Konsole ist auf dem Host unter `http://localhost:9990/console` erreichbar. Ihre Zugangsdaten stehen in der lokalen `.env` (`EAP_MGMT_USER`, `EAP_MGMT_PASSWORD`). Im Dev Container sind die Dienste über `eap:8080`, `eap:9990`, `keycloak:8080` und `oracle:1521` erreichbar. Die Management-API verwendet HTTP Digest.

## Build und Tests

Der Maven Wrapper verwendet Maven 3.9.11. Für Änderungen zuerst den betroffenen Modulverbund, danach das ganze Repository prüfen:

```bash
./mvnw -pl <modul> -am test
./mvnw verify
```

Für Packaging-Änderungen zusätzlich `./mvnw -pl bundle/ear -am package` ausführen. Das lokale Deployment erfolgt mit dem Befehl im [Schnellstart](README.md#schnellstart). `.mvn/settings.xml` bindet dabei die EAP-Management-Zugangsdaten aus der Umgebung ein; normale Builds benötigen diese Datei nicht.

Java-Dateien werden beim Speichern mit `config/java-formatter.xml` formatiert. `./mvnw spotless:check` prüft und `./mvnw spotless:apply` korrigiert die Formatierung. `verify` prüft sie ebenfalls. Die Kompilierung führt Error Prone und NullAway für eigenen Produktions- und Testcode aus. Generierter Code unter `target/generated-sources` ist ausgenommen und wird nicht manuell geändert.

Für reproduzierbare Release- oder CI-Artefakte kann der Zeitstempel aus dem Commit stammen:

```bash
SOURCE_DATE_EPOCH=$(git log -1 --format=%ct) ./mvnw verify
```

## Lokale Authentifizierung und API-Tests

Keycloak importiert bei der ersten Initialisierung von `keycloak-data` den Realm `itz` aus `config/keycloak/itz-realm.json`. Der Client heißt `itz-api`; lokale Testbenutzer sind `itz-user`, `itz-admin` und `itz-special`. Der Ping-Endpunkt benötigt die Berechtigung `PING`, die `itz-special` besitzt. Änderungen an der Realm-Datei werden bei einem bereits initialisierten Volume nicht automatisch importiert.

Die VS-Code-Erweiterung **REST Client** kann [requests/requests.http](requests/requests.http) ausführen. Für die Token-Anfrage `ITZ_TEST_USERNAME` und `ITZ_TEST_PASSWORD` in `.env` setzen. Die Datei speichert keine Kennwörter.

Der HTTP-Fehler-Smoke-Test läuft gegen eine bereits gestartete EAP-Instanz:

```bash
node scripts/test-http-errors.mjs
```

Er lädt auch eine synthetische Datei mit genau 25 MiB hoch. Der erfolgreiche Upload bleibt lokal gespeichert.

Nach Änderungen an der EAP-OIDC-Konfiguration oder an `bundle/eap/configure-datasource.cli` muss das EAP-Image auf dem Host neu gebaut werden. Ein erneutes Deployment kann wegen `drop-and-create` das Anwendungsschema zurücksetzen. Separate Datenbank-Resets sind für normale Builds nicht nötig.

## Lokale Daten und Zugangsdaten

Die Datenbank liegt in einem Docker Named Volume; das Anwendungsschema wird beim EAP-Start durch JPA neu erzeugt. Es gibt derzeit keine Liquibase-Migration. Ein vollständiger Neuaufbau mit `down -v` löscht lokale Daten und muss bewusst auf dem Host erfolgen.

`.env`, Oracle-Installer, generierte Datenbank-Images und Build-Artefakte gehören nicht ins Repository. Oracle 19c darf nur gemäß der geltenden Entwicklungs- und Testlizenz verwendet werden.
