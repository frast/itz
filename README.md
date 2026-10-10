# itz

[![Maven CI](https://github.com/frast/itz/actions/workflows/maven.yml/badge.svg?branch=main)](https://github.com/frast/itz/actions/workflows/maven.yml)
![Java 21](https://img.shields.io/badge/Java-21-blue)
[![License: MIT](https://img.shields.io/github/license/frast/itz)](LICENSE)

Lokale Experimentierumgebung mit Java 21, JBoss EAP 8.1, Oracle Database 19c und VS Code Dev Containers.

## Schnellstart

Vorausgesetzt werden Docker Desktop mit WSL-Integration für Debian, VS Code mit **Dev Containers**, ein Zugang zu `registry.redhat.io` und für den ersten Aufbau der Oracle-19c-Installer.

1. `cp .env.example .env` ausführen und die Kennwörter in `.env` setzen.
2. Auf dem Docker-Host `docker login registry.redhat.io` ausführen.
3. Falls das Oracle-Image noch fehlt: `./scripts/build-oracle-image.sh /pfad/LINUX.X64_193000_db_home.zip` ausführen.
4. Das Projekt in VS Code mit **Dev Containers: Reopen in Container** öffnen.
5. Im Dev Container deployen:

   ```bash
   ./mvnw -s .mvn/settings.xml -pl bundle/ear -am -Pdeploy-eap install
   ```

Die API ist danach unter `http://localhost:8080/itz/api` erreichbar. Alle Endpunkte benötigen einen JWT-Bearer-Token aus dem lokalen Keycloak. Beispielanfragen stehen in [requests/requests.http](requests/requests.http).

## Dokumentation

- [Development.md](Development.md): Entwicklungsumgebung, Builds, Tests und lokale Authentifizierung
- [API.md](docs/API.md): REST-Vertrag, Uploads und Fehlerantworten
- [Architecture.md](docs/Architecture.md): Module, Dateispeicher und Persistenzgrenzen
- [Observability.md](docs/Observability.md): lokale Logs, Grafana und Oracle-Diagnose
- [AGENTS.md](AGENTS.md): Arbeitsregeln für Coding-Agenten

Die lokale Persistence Unit verwendet `drop-and-create`: Ein erneuter EAP-Start kann das Anwendungsschema neu erzeugen. Dateien im Dateispeicher können dabei erhalten bleiben. Die Umgebung ist für lokale Entwicklung und Tests gedacht.
