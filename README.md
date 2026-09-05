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
