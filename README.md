# itz

Lokale Experimentierumgebung mit JBoss EAP 8.1, Oracle Database 19c und VS Code Dev Containers.

## Voraussetzungen

- Docker Desktop gestartet, WSL-Integration fuer `Debian` aktiviert
- VS Code mit Erweiterung **Dev Containers**
- Red-Hat-Zugang fuer `registry.redhat.io`
- Oracle Database 19c Linux x64 Installer, falls das lokale Oracle-Image noch nicht vorhanden ist

## Einmalige Einrichtung

1. `cp .env.example .env` und sichere Kennwoerter eintragen.
2. Bei Red Hat anmelden: `docker login registry.redhat.io`.
3. Das lokale Oracle-Image erstellen: `./scripts/build-oracle-image.sh /pfad/LINUX.X64_193000_db_home.zip`.
4. In VS Code den Ordner oeffnen und **Dev Containers: Reopen in Container** waehlen.
5. Im Dev Container starten: `./mvnw -s .mvn/settings.xml -pl bundle/ear -am -Pdeploy-eap install`.

Die Anwendung ist danach unter http://localhost:8080/itz/api/ping erreichbar. Die
API erwartet einen gültigen JWT-Bearer-Token aus dem lokalen Keycloak.

## Lokale JWT-Authentifizierung

Keycloak wird für die lokale Entwicklung automatisch aus
`config/keycloak/itz-realm.json` importiert. Der Realm `itz` enthält den Client
`itz-api`, die Testbenutzer `itz-user`, `itz-admin` und `itz-special` sowie die
Rollen `user`, `admin` und `special`. Nur `itz-special` besitzt die
Berechtigung für den Ping-Use-Case; dafür ist keine Admin-Rolle erforderlich.
Diese Zugangsdaten sind ausschließlich für lokale Tests gedacht.

Nach dem ersten Hinzufügen oder Ändern der EAP-OIDC-Konfiguration muss das
EAP-Image neu gebaut werden:

```bash
docker compose up -d --build eap
```

Bei späteren Starts genügt `docker compose up -d`. Danach kann ein Access Token
angefordert werden:

```bash
token=$(curl -sS -X POST http://keycloak:8080/realms/itz/protocol/openid-connect/token \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode 'client_id=itz-api' \
  --data-urlencode 'username=itz-user' \
  --data-urlencode 'password=itz-user' \
  --data-urlencode 'grant_type=password' | jq -r .access_token)
curl -i http://localhost:8080/itz/api/ping -H "Authorization: Bearer ${token}"
```

Ohne Token oder mit einem ungültigen/abgelaufenen Token antwortet die API mit
`401 Unauthorized`. Die API ist standardmäßig geschützt; öffentliche Endpunkte
müssen ausdrücklich als solche konfiguriert werden. Sobald ein Endpunkt eine
Rolle verlangt, führt ein gültiger Token ohne diese Rolle zu `403 Forbidden`.
Die Rollen aus Keycloak werden bereits als Jakarta-Sicherheitsrollen verfügbar
gemacht und können später beispielsweise mit `@RolesAllowed("admin")` geprüft
werden.

Das importierte Realm wird nur beim ersten Initialisieren des persistenten
Keycloak-Volumes wirksam.

## REST-Requests in VS Code

Der Dev Container installiert die Erweiterung **REST Client**. Die Requests
liegen in [requests/requests.http](requests/requests.http) und können dort über
`Send Request` einzeln ausgeführt werden. Der erste Request holt ein Token und
speichert es automatisch für den geschützten Ping.

Für die Token-Anfrage müssen `ITZ_TEST_USERNAME` und `ITZ_TEST_PASSWORD` aus
`.env.example` in die lokale `.env` übernommen werden. Diese Werte werden über
`{{$dotenv ...}}` gelesen und nicht in `requests.http` gespeichert.

## REST-API und OpenAPI

Der REST-Vertrag liegt unter
`adapters/primary/rest/src/main/openapi/openapi.yaml`. Das Maven-Plugin
`openapi-generator-maven-plugin` erzeugt daraus waehrend der Phase
`generate-sources` Jakarta-JAX-RS-Interfaces und validierte Transportmodelle im
Verzeichnis `adapters/primary/rest/target/generated-sources/openapi`.

Generierter Code wird nicht eingecheckt und nicht manuell bearbeitet. Die
REST-Resources implementieren die generierten Interfaces und bilden explizit
zwischen den OpenAPI-Transportmodellen und den Domain-Typen ab.

Nach einer Aenderung am Vertrag kann die Generierung gezielt ausgefuehrt werden:

```bash
./mvnw -pl adapters/primary/rest -am generate-sources
```

Der eingecheckte Maven Wrapper verwendet Maven 3.9.11. Die Datei
`.mvn/settings.xml` ordnet die lokalen Management-Zugangsdaten aus
`EAP_MGMT_USER` und `EAP_MGMT_PASSWORD` der Maven-Server-ID `local-eap` zu.
Sie wird nur beim lokalen Deployment explizit mit `-s .mvn/settings.xml` verwendet,
damit normale Builds weiterhin benutzerspezifische Maven-Mirrors und Proxies nutzen.
Kennwoerter werden nicht in Maven-Dateien gespeichert.

Die Kommandohistorien von Bash und Zsh liegen in einem Docker Named Volume und
bleiben bei einem Rebuild des Dev Containers erhalten. `docker compose down -v`
loescht dieses Volume zusammen mit den anderen lokalen Daten.

Die lokale EAP-Management-Konsole ist unter http://localhost:9990/console
erreichbar. Die Zugangsdaten sind `EAP_MGMT_USER` und `EAP_MGMT_PASSWORD` aus
der lokalen Datei `.env`. Der Port ist bewusst nur an `127.0.0.1` gebunden und
nicht im lokalen Netzwerk veroeffentlicht.

## Zentrale Logs (lokale Entwicklung)

Das optionale Compose-Profil `observability` sammelt die Konsolenausgaben von
`eap`, `keycloak` und `oracle` ueber Grafana Alloy in Loki. Grafana stellt eine
vorkonfigurierte Datenquelle und das Dashboard **ITZ Container Logs** bereit.
Die Images sind auf feste Versionen gesetzt; die Konfiguration liegt unter
`config/observability`.

Auf dem Docker-Host aus dem Projektverzeichnis starten (mit vorhandener `.env`):

```bash
docker compose --profile observability up -d --no-deps loki alloy grafana
```

Dieser Befehl startet nur den Logging-Stack. Bereits laufende Anwendungscontainer
werden automatisch entdeckt; EAP, Keycloak und Oracle werden nicht neu gestartet.
Im Dev Container sind Docker-CLI und Docker-Socket derzeit nicht eingebunden,
deshalb die Docker-Befehle im Host-Terminal ausfuehren.

Dashboard: http://localhost:3000/d/itz-logs

Das Dashboard bietet Filter fuer Service, Source (`container`, `oracle_alert`,
`oracle_listener`), Level, Request ID und eine freie Text search. Die Request ID
wird aus dem verschachtelten JSON-Feld `mdc["request.id"]` extrahiert. Die freie Text search wird
als escaped Regex in einer LogQL-Line-Filterung verwendet; fuer komplexe LogQL-Ausdruecke
Grafana Explore verwenden.

### Request correlation

REST-Anfragen erhalten eine `X-Request-ID`. Eine gueltige vom Client gesendete
ID mit maximal 128 Zeichen (`A-Z`, `a-z`, Ziffern, `.`, `_`, `:` und `-`) wird
uebernommen; alle anderen Werte werden durch eine UUID ersetzt. Die ID steht in
der Response und im EAP-JSON-MDC-Feld `mdc.request.id` bereit. Damit kannst du
beispielsweise mit folgender LogQL-Abfrage alle Servermeldungen eines Requests
suchen:

```logql
{service_name="eap"} | json request_id="mdc[\"request.id\"]" | request_id="<request-id>"
```

Die Request-ID wird nach der Response aus dem MDC entfernt. Die Anwendung loggt
keine Authorization-Header oder vollstaendige Request-Payloads.

Der geschuetzte Ping-Endpunkt schreibt zu Testzwecken zwei INFO-Eintraege:
`Handling ping request` und `Ping request completed`. Mit einer REST-Client-Anfrage
wie der in [requests/requests.http](requests/requests.http) kannst du die
zugehoerige ID aus der Response kopieren und in Grafana suchen:

```logql
{service_name="eap", level="INFO"} | json | mdc_request_id="<request-id>"
```

Grafana erlaubt lokal anonymen Lesezugriff ohne Anmeldung; es wird kein initialer
Admin angelegt. Datenquelle und Dashboard werden ueber Dateien verwaltet.
Port 3000 ist nur an `127.0.0.1` gebunden, Loki und Alloy veroeffentlichen keine
Host-Ports. Dies ist eine lokale Entwicklungskonfiguration, keine Konfiguration
fuer einen gemeinsam genutzten oder oeffentlichen Server.

In Grafana Explore die Datenquelle **Loki** waehlen, zum Beispiel:

```logql
{service_name="eap"}
{service_name=~"eap|keycloak", level="ERROR"}
{service_name="eap"} | json | __error__="" | loggerName="org.jboss.as"
{service_name="oracle"} |= "ORA-"
```

Die Labels `project`, `service_name` und `environment="development"` ermoeglichen
die gemeinsame Suche. Alloy beschraenkt die Erfassung auf das aktuelle
Compose-Projekt und diese drei Services. Request-IDs oder Benutzer-IDs werden
nicht als Index-Labels verwendet. EAP und Keycloak schreiben strukturierte
JSON-Konsolenlogs mit `timestamp`, `level`, `loggerName` und `message`. Alloy
uebernimmt `level` als zusaetzliches Label; die vollstaendige JSON-Meldung bleibt
erhalten und kann mit `| json` ausgewertet werden. Stacktraces des JSON-Formatters
stehen mit escapten Zeilenumbruechen in einem Eintrag. Der Loki-Zeitstempel bleibt
der Docker-Zeitstempel; der Zeitstempel des Loggers steht im JSON-Feld `timestamp`.
Unstrukturierte Startskript-Ausgaben und Oracle-Logs bleiben als Text erhalten,
auch wenn sie kein gueltiges JSON sind. Alte Textlogs werden nicht nachtraeglich
umgewandelt und haben kein von Alloy erfasstes `level`-Label.
Zusaetzlich liest Alloy Oracle-Alert- und Listener-Dateien aus dem gemeinsamen
Diagnose-Volume (Aktivierung siehe unten). Browserfehler sind noch keine Source.

Loki speichert Daten in `loki-data` und loescht Logs nach sieben Tagen asynchron
ueber den Compactor. `alloy-data` bewahrt Lesepositionen und `grafana-data` den
Grafana-Zustand bei Neustarts. Die Aufbewahrungszeit ist kein festes Speicherlimit;
Docker verwaltet seine eigenen Container-Logs unabhaengig davon. Bei laengeren
Ausfaellen koennen bereits von Docker entfernte Logs nicht nachgelesen werden.
Keine Tokens, Kennwoerter oder sensiblen Nutzdaten in Anwendungslogs ausgeben.

Alloy greift als root auf den Docker-Socket zu. Der `:ro`-Mount verhindert keine
schreibenden Docker-API-Aufrufe; der Collector hat dadurch weitreichenden Zugriff
auf den lokalen Docker-Host. Diese Anbindung ist fuer die lokale Umgebung gedacht.

Konfiguration und Transport mit synthetischen Logs pruefen (Bash, Docker Compose,
curl und jq auf dem Docker-Host erforderlich):

```bash
bash scripts/test-observability.sh
```

Der Test validiert Alloy und Loki, startet nur den Logging-Stack, startet Alloy
zum Laden der aktuellen Konfiguration neu und prueft das
Dashboard sowie die Abfrage synthetischer Logs aller drei Service-Namen durch
Grafana. Er prueft JSON-Felder, das `level`-Label, einen vollstaendigen Stacktrace,
den Erhalt unstrukturierter Ausgaben sowie den Ausschluss anderer Services und
Compose-Projekte.
Die temporaeren Testcontainer werden anschliessend entfernt; der Logging-Stack
bleibt laufen. Damit werden Transport und Filter getestet, nicht die fachlichen
Logausgaben der echten Services. Diese anschliessend im Dashboard kontrollieren.

### Strukturierte Logs aktivieren und pruefen

Bei einer bestehenden Umgebung muss das EAP-Image fuer den JSON-Formatter neu
gebaut und Keycloak fuer `KC_LOG_CONSOLE_OUTPUT=json` neu erstellt werden.
Der Formatter wird beim EAP-Image-Bau mit dem eingebetteten Server konfiguriert,
bevor die erste regulaere Serverinstanz startet. Anwendungslogs ueber das
EAP-Logging-Subsystem erhalten damit ebenfalls das JSON-Format.

Auf dem Docker-Host ausfuehren; Oracle muss bereits laufen:

```bash
bash scripts/test-observability.sh
docker compose up -d --no-deps --force-recreate keycloak
docker compose up -d --no-deps --build eap
bash scripts/test-structured-logging.sh
```

Alloy muss die neue Verarbeitung vor den Serverstarts geladen haben. Der erste
Test stellt das durch einen Alloy-Neustart und synthetische Logs sicher.
Bereits eingelesene Startmeldungen erhalten nachtraeglich kein `level`-Label.
Wurden die Server vor Alloy aktualisiert, zuerst den synthetischen Test ausfuehren,
dann `docker compose restart keycloak eap` und den strukturierten Test wiederholen.
Dabei bleiben die Container erhalten; fuer neue Startmeldungen ist kein erneuter
Image-Bau erforderlich.

Der EAP-Neustart verwendet weiterhin das bestehende `drop-and-create`-Profil:
Ein erneutes Deployment kann deshalb das Anwendungsschema neu erzeugen. Ein
ueber die Management-Schnittstelle bereitgestelltes EAR muss nach dem Ersetzen
des EAP-Containers gegebenenfalls erneut mit dem dokumentierten Maven-Befehl
deployt werden. Keycloak behaelt seine Daten im vorhandenen Volume.

`test-structured-logging.sh` benoetigt zusaetzlich GNU `timeout` (unter Linux in
coreutils enthalten) und startet keine Services. Es prueft die echten
Startmeldungen der aktuell laufenden EAP- und Keycloak-Container sowohl auf
JSON-Felder als auch auf ihre Abfragbarkeit in Grafana mit `level="INFO"`.
Es meldet den Fortschritt getrennt fuer Container-Logs und Grafana, begrenzt
Docker-Aufrufe auf 15 Sekunden und wartet pro Service etwa eine Minute.
HTTP-Fehler der Grafana-Abfrage werden mit Statuscode sofort gemeldet.
Es gibt keine Log-Payloads aus. Die Pruefung direkt nach dem Neuaufbau ausfuehren,
solange die Startmeldungen noch in den letzten 5000 Container-Logzeilen und
innerhalb der Loki-Aufbewahrungszeit liegen.

### Oracle-Diagnoselogs aktivieren und pruefen

Oracle schreibt sein ADR-Verzeichnis nach `/opt/oracle/diag`. Das separate
Volume `oracle-diagnostics` macht dieses Verzeichnis persistent und fuer Alloy
unter `/var/log/oracle` ausschliesslich lesbar. Der einmalig laufende Service
`oracle-diagnostics-init` setzt den Besitzer des Volume-Verzeichnisses auf den
Oracle-Benutzer aus demselben Image. Er startet keine Datenbank.

**Bestehende Umgebung:** Vor dem ersten `docker compose up`, das Oracle mit dem
neuen Mount neu erstellen wuerde, auf dem Docker-Host ausfuehren:

```bash
bash scripts/enable-oracle-diagnostics.sh
docker compose ps oracle
# Sobald Oracle healthy ist:
bash scripts/test-oracle-logging.sh
bash scripts/test-observability.sh
```

Das Aktivierungsskript initialisiert das Diagnose-Volume, stoppt Oracle sauber,
kopiert das bisherige ADR-Verzeichnis aus dem gestoppten Container und ersetzt
erst danach den Container mit dem neuen Mount. Dabei entsteht eine kurze
Datenbankunterbrechung. Das bestehende `oracle-data`-Volume wird weiterverwendet;
EAP und Keycloak werden nicht neu gestartet. Aktive Datenbankverbindungen koennen
waehrend der Unterbrechung fehlschlagen. Bei bereits eingebundenem Diagnose-Volume
wird die Kopie uebersprungen. Fuer eine frische Umgebung reicht der normale
Compose-Start; dort ist keine Uebernahme alter Diagnosedateien erforderlich.

Bei einem Kopierfehler bleibt der urspruengliche Container gestoppt erhalten.
Den Fehler beheben und das Skript wiederholen oder den vorhandenen Container mit
`docker compose start oracle` starten. Bis die Uebernahme erfolgreich ist, Oracle
nicht mit `up` neu erstellen. Das Skript setzt weder die Datenbank zurueck noch
loescht es Volumes.

Die erfassten Sources lassen sich in Grafana getrennt abfragen:

```logql
{service_name="oracle", log_source="oracle_alert"}
{service_name="oracle", log_source="oracle_listener"}
{service_name="oracle", log_source="container"}
```

Alloy liest nur `rdbms/*/*/trace/alert*.log` und
`tnslsnr/*/*/trace/listener.log`. Andere Trace-Dateien, XML-Kopien und Auditdateien
werden nicht eingesammelt. Das Label `filename` zeigt die Quelldatei. Alert-Zeilen
werden ab einer ISO-Zeitstempelzeile zusammengefasst, maximal 256 Zeilen je Eintrag;
Listener-Ausgaben bleiben zeilenweise erhalten. Das Oracle-Image gibt das Alert-Log
auch auf der Konsole aus: Dieselbe Meldung kann deshalb unter `container` und
`oracle_alert` vorkommen. Fuer Auswertungen eine Source auswaehlen.

Beim erstmaligen Entdecken wird eine Datei ab Anfang eingelesen, danach setzt
Alloy anhand der Lesepositionen in `alloy-data` fort. Der Loki-Zeitstempel ist bei
diesen Dateiquellen die Lesezeit; die urspruenglichen Oracle-Zeitangaben bleiben
im Text. Dadurch erscheinen uebernommene historische Eintraege beim Import als
neue Eintraege. Die Loki-Aufbewahrung von sieben Tagen loescht keine Oracle-Dateien
im Diagnose-Volume; deren Bereinigung bleibt Aufgabe der Oracle-ADR-Verwaltung.

`test-oracle-logging.sh` ist eine reine Lesepruefung: Es verlangt eine gesunde
Datenbank mit Diagnose-Mount, findet nichtleere Alert-/Listener-Dateien und prueft
passende Dateiquellen in Grafana. Direkt nach der Aktivierung ausfuehren; das
Abfragefenster betraegt eine Stunde. Fortschritt und Fehler werden ohne
Log-Payloads ausgegeben. Voraussetzungen: Bash, Docker Compose, curl, jq und
GNU timeout. Fehlende Dateien oder eine ungesunde Datenbank fuehren sofort zu
einem Fehler statt zu einer stillen Warteschleife.

Nur den Logging-Stack anhalten, Daten behalten:

```bash
docker compose --profile observability stop alloy grafana loki
```

`docker compose down -v` entfernt auch die Logging-Volumes und die bestehenden
Datenbank-Volumes. Fuer das Einrichten oder Testen der Logs ist das nicht notwendig.

## Codeformatierung

VS Code formatiert Java-Dateien beim Speichern mit dem eingecheckten Profil
`config/java-formatter.xml`. Maven verwendet dasselbe Profil und prueft die
Formatierung waehrend der `verify`-Phase.

```bash
./mvnw spotless:check  # Formatierung pruefen
./mvnw spotless:apply  # Formatierung korrigieren
./mvnw verify          # Vollstaendiger Build inklusive Formatierungspruefung
```

## Nullness-Pruefung

Eigener Java-Code ist paketweise mit JSpecify `@NullMarked` als standardmaessig
nicht-null markiert. Nur Typen, fuer die `null` ein gueltiger Zustand ist, werden
explizit mit `@Nullable` annotiert; `@NonNull` wird nicht verwendet.

VS Code aktiviert die annotationsbasierte Null-Analyse automatisch. Maven prueft
Produktions- und Testcode bei jeder Kompilierung verbindlich mit NullAway. Die vom
OpenAPI Generator erzeugten Quellen unter `target/generated-sources` sind davon
ausgenommen und bleiben bewusst unmarkiert.

Eclipse JDT ignoriert dabei nur ungepruefte Nullness-Konvertierungen aus externen,
nicht JSpecify-annotierten APIs. Eindeutige Nullzugriffe und Nullness-Vertragsverletzungen
werden weiterhin als Java-Diagnosen angezeigt; die vollstaendige Build-Pruefung uebernimmt
NullAway.

```bash
./mvnw verify  # inklusive NullAway-Pruefung
```

## Datenbankmodus

Das lokale Persistence-Unit-Profil nutzt `drop-and-create`: Bei jedem EAP-Start wird das Anwendungsschema aus den JPA-Entities neu erzeugt. Die Datenbankdaten selbst liegen in einem Docker Named Volume. Fuer einen vollstaendigen Reset verwenden Sie `docker compose down -v`.

Liquibase ist bewusst noch nicht eingebunden. Vor dessen Einfuehrung muss die Schema-Generierung auf `validate` oder `none` umgestellt werden.

## Reproduzierbarer Neuaufbau

Die EAP-Images, der EAP-Channel, die Oracle-Dockerfile-Revision und die
Pruefsumme des Oracle-19c-Installers sind festgelegt. Ein frischer Neuaufbau
besteht aus `docker compose down -v`, dem Oracle-Image-Build und
`docker compose up -d --build`. `down -v` loescht dabei bewusst alle lokalen
Oracle-Daten; es wird nie automatisch ausgefuehrt.

Der EAP-Channel `eap-8.1:1.7.1.GA-redhat-00001` und der Maven-BOM
`8.1.7.GA-redhat-00004` gehoeren zum selben EAP-8.1.7-Patchstand und muessen
bei einem EAP-Update gemeinsam aktualisiert werden.

Maven verwendet lokal einen stabilen Fallback-Zeitstempel fuer reproduzierbare
Archive. Fuer Release- und CI-Artefakte kann der Zeitstempel automatisch aus
dem aktuellen Commit abgeleitet werden:

```bash
SOURCE_DATE_EPOCH=$(git log -1 --format=%ct) ./mvnw verify
```

## Sicherheit und Lizenzen

`.env`, Oracle-Installer, erzeugte Datenbank-Images und lokale Build-Artefakte werden nicht eingecheckt. Oracle 19c darf nur im Rahmen der fuer Sie geltenden Entwicklungs-/Testlizenz verwendet werden.
