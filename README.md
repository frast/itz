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
5. Im Dev Container starten: `mvn -pl bundle/ear -am -Pdeploy-eap install`.

Die Anwendung ist danach unter http://localhost:8080/itz/api/ping erreichbar.

## Datenbankmodus

Das lokale Persistence-Unit-Profil nutzt `drop-and-create`: Bei jedem EAP-Start wird das Anwendungsschema aus den JPA-Entities neu erzeugt. Die Datenbankdaten selbst liegen in einem Docker Named Volume. Fuer einen vollstaendigen Reset verwenden Sie `docker compose down -v`.

Liquibase ist bewusst noch nicht eingebunden. Vor dessen Einfuehrung muss die Schema-Generierung auf `validate` oder `none` umgestellt werden.

## Reproduzierbarer Neuaufbau

Die EAP-Images, der EAP-Channel, die Oracle-Dockerfile-Revision und die
Pruefsumme des Oracle-19c-Installers sind festgelegt. Ein frischer Neuaufbau
besteht aus `docker compose down -v`, dem Oracle-Image-Build und
`docker compose up -d --build`. `down -v` loescht dabei bewusst alle lokalen
Oracle-Daten; es wird nie automatisch ausgefuehrt.

## Sicherheit und Lizenzen

`.env`, Oracle-Installer, erzeugte Datenbank-Images und lokale Build-Artefakte werden nicht eingecheckt. Oracle 19c darf nur im Rahmen der fuer Sie geltenden Entwicklungs-/Testlizenz verwendet werden.
