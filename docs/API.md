# REST-API

Der Vertrag liegt in [openapi.yaml](../adapters/primary/rest/src/main/openapi/openapi.yaml). Maven erzeugt daraus in `generate-sources` Jakarta-JAX-RS-Interfaces und Transportmodelle. REST-Resources implementieren die Interfaces und bilden explizit auf Domain-Typen ab. Generierte Dateien unter `target/generated-sources` werden nicht eingecheckt oder manuell bearbeitet.

```bash
./mvnw -pl adapters/primary/rest -am generate-sources
```

Die API unter `/itz/api` verlangt einen JWT-Bearer-Token. Ohne gültigen Token folgt `401`, bei fehlender Rolle `403`. `GET /ping` benötigt `special` beziehungsweise die Berechtigung `PING`; `POST /files` benötigt `user`.

## Datei-Upload

`POST /itz/api/files` erwartet Multipart mit dem Feld `file`. Die Datei darf höchstens 25 MiB, der gesamte Multipart-Request höchstens 27 MiB groß sein. Der EAP-Listener begrenzt alle Requests zusätzlich auf 32 MiB. Der lokale Mock-Scanner weist den EICAR-Testmarker mit `422` ab; er ist kein produktiver Virenschutz. Bei einem Scanner-Ausfall wird der Upload abgebrochen.

Der Originaldateiname darf höchstens 255 Unicode-Codepoints enthalten und muss ein einzelner gültiger Name sein. Fehlende Multipart-Dateinamen werden durch `upload.bin` ersetzt. Der Content-Type darf höchstens 512 Codepoints umfassen und muss eine gültige Medientyp-Syntax haben. Ungültige Angaben führen vor dem Lesen des Inhalts zu `400` mit `INVALID_FILE_NAME` beziehungsweise `INVALID_CONTENT_TYPE`. Der vom Client gesendete Content-Type ist keine Inhaltserkennung.

Der OpenAPI-Vertrag beschreibt die Datei als `string` mit `format: binary`. Ein kleines Generator-Template bildet sie auf Jakarta `EntityPart` ab. Nach Generator-Upgrades muss diese Signatur geprüft werden.

## Fehlerantworten

Fehler innerhalb der Webanwendung haben ein JSON-Objekt mit `code` und `message` sowie `Cache-Control: no-store` und `X-Request-ID`. Interne Exception- und Datenbankdetails werden nicht ausgegeben. Protokollheader wie `Allow` bei `405` und `WWW-Authenticate` bei `401` bleiben erhalten. Vor der Anwendung abgewiesene Requests oder bereits begonnene Antworten können davon abweichen.

Eine gültige vom Client gesendete `X-Request-ID` wird übernommen; sonst erzeugt die Anwendung eine UUID. Weitere Beispiele stehen in [requests/requests.http](../requests/requests.http).
