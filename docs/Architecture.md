# Architektur und Dateispeicher

Die Maven-Module folgen der Richtung `adapters/primary → application/usecases → application/domain`; sekundäre Adapter implementieren Ports der Anwendung. `bundle` setzt die Komponenten zusammen und deployt sie. Die Domain bleibt frei von Jakarta-, REST- und Persistenzabhängigkeiten.

Der Application Core ruft für Uploads den Port `FileStorage` auf. [file-storage/](../adapters/secondary/file-storage) implementiert ihn und bündelt Dateisystem, lokalen Mock-Virenscanner und JPA-Metadaten. Die ursprünglichen Dateinamen werden nie als Dateipfad verwendet. Der interne Speicherschlüssel hat das Format `<UUID>.bin`.

Der Adapter liest höchstens 25 MiB, schreibt zunächst in Quarantäne, prüft den Inhalt und speichert danach Datei und Metadaten. Das Dateiverzeichnis wird über `ITZ_FILE_STORAGE_DIRECTORY` oder `itz.file-storage.directory` gesetzt; Standard ist `./data/files`. Die Metadaten liegen in `uploaded_file` und umfassen UUID, Originaldateiname, Content-Type, tatsächliche Bytezahl und relativen Speicherschlüssel.

Dateisystem und Datenbank bilden keine gemeinsame atomare Transaktion. Nach einem bestätigten Datenbank-Rollback wird die Datei entfernt; bei unklarem Commit-Ergebnis bleibt sie vorsichtshalber liegen. Prozessabbrüche oder fehlgeschlagene Bereinigung können verwaiste Dateien hinterlassen. Es gibt keinen automatischen Bestandsabgleich.

Die JPA-Persistence-Unit `itzPU` nutzt lokal `drop-and-create`. Beim Neuerzeugen des Schemas können Metadaten verloren gehen, während Dateien erhalten bleiben. Bestehende, inzwischen ungültige Metadaten werden nicht automatisch migriert. Vor produktivem Betrieb sind daher eine Migrationsstrategie, ein echter Virenscanner und ein Umgang mit verwaisten Dateien nötig.
