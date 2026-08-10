# Inkrement 9 – Reproduzierbares Produktionsdeployment und Betriebshärtung

## Status

Vorbereitet auf Issue #23 und Branch `feature/production-deployment-hardening`.

## Ziel

Die nach Inkrement 8 fachlich vollständige Kernversion wird als private, reproduzierbare Containeranwendung auf einem Netcup VPS 500 G12 unter Debian 13 betreibbar. Ein leerer Host kann nach Dokumentation installiert, mit einer frischen Datenbank gestartet, aktualisiert, gesichert, wiederhergestellt und auf eine frühere App-Version zurückgesetzt werden.

Verbindlich:

- `docs/requirements/production-deployment.md`
- ADR 0013
- Issue #23
- bestehende Sicherheits- und Recoveryentscheidungen der Inkremente 0 bis 8

## Abgrenzung

Dieses Inkrement ergänzt Betriebsartefakte und nur die dafür zwingend erforderlichen kleinen Anwendungsänderungen. Die bestehende Fachlogik, Discord-Darstellung, Parser-, Publish-, Delete-, Serien-, Status- und Reminderlogik bleibt unverändert.

Nicht enthalten:

- Wochen- und Monatsberichte
- neue Statistik- oder Zeitkonfigurationscommands
- regelbasierte Kommentare
- Domain, Reverse Proxy oder öffentliche Weboberfläche
- automatisches Produktionsdeployment aus GitHub Actions
- Offsite-Backup
- Import lokaler Testdaten

## Umsetzungspakete

Jedes Paket endet in einem kompilierenden, gepushten Commit. Die Bearbeitung ist erst nach Paket F und vollständiger CI abgeschlossen.

### Paket A – Containerisiertes Bot-Image

- Multi-Stage-Dockerfile für Java 21
- `.dockerignore`
- Nicht-Root-Runtime
- ausführbares Spring-Boot-JAR
- keine Quellen, Maven-Caches oder Secrets im Runtimeimage
- reproduzierbare Labels für Commit, Version und Buildzeit
- kontrollierte JVM-Ressourcen
- Liveness-/Readiness-Healthcheck
- lokaler Image-Build und Start ohne Discord-Verbindung

Tests:

- Image lässt sich auf x86-64 bauen
- Container läuft ohne Root
- erwartete Dateien sind vorhanden, Quellen und `.env` fehlen
- Healthcheck wird ohne echten Discord-Token sinnvoll ausgewertet

### Paket B – Produktions-Compose und Konfiguration

- `compose.production.yaml`
- Bot und PostgreSQL in internem Netz
- keine PostgreSQL-Portfreigabe
- persistentes Volume
- Healthchecks und Startreihenfolge
- `restart: unless-stopped`
- Ressourcenlimits und Logrotation
- `.env.production.example`
- getrennte Runtime-Secrets und Image-/Deploymentkonfiguration
- Produktionsprofil und zwingende Konfigurationsvalidierung

Tests:

- `docker compose config` ist mit Beispieldaten gültig
- vollständiger Start aus leerem Volume
- Liquibase migriert erfolgreich
- Bot-Container wird gesund beziehungsweise liefert einen klaren erwartbaren Zustand ohne echten Discord-Token
- PostgreSQL ist nicht an Hostinterfaces veröffentlicht
- Containerneustart erhält Daten

### Paket C – GHCR-Imagepipeline

- `.github/workflows/container-image.yml`
- Standard- und Datenbankintegrationstests vor Imagepush
- PR: Image bauen und testen, nicht pushen
- `main`: privaten Commit-Tag veröffentlichen
- `workflow_dispatch`: validierten Release-Tag veröffentlichen
- GHCR-Login ausschließlich über GitHub-Token mit minimalen Workflowpermissions
- SBOM beziehungsweise nachvollziehbare Image-Metadaten, sofern mit Standardmitteln praktikabel
- Concurrency- und Cachekonfiguration ohne geheime Daten

Tests:

- Workflow-Syntax validiert
- PR-Workflow baut das Image
- kein Push aus fremden beziehungsweise untrusted PR-Kontexten
- veröffentlichte Tags und Labels sind deterministisch

### Paket D – Deployment und Rollback

- `scripts/deploy.sh`
- `scripts/verify-deployment.sh`
- Eingabevalidierung und sichere Shelloptionen
- Backup vor Deployment
- exakter Image-Tag und gezogener Digest
- kontrolliertes Compose-Update
- begrenztes Warten auf Healthchecks
- verständlicher Fehlerabbruch
- persistierte Information über vorherige und aktuelle App-Version
- erneutes Deployment derselben Version als No-op beziehungsweise idempotenter Erfolg
- App-Rollback auf früheren Image-Tag

Tests:

- Shell-Lint beziehungsweise äquivalente statische Prüfung
- Happy Path mit lokal gebautem Testimage
- ungültiger Tag wird abgelehnt
- fehlgeschlagenes Pull/Health führt nicht zu falscher Erfolgsmeldung
- gleiche Version erzeugt keinen unnötigen Datenverlust oder Reinitialisierung
- Rollbackweg wird automatisiert mit zwei Testtags geprüft

### Paket E – Backup und Restore

- `scripts/backup.sh`
- `scripts/restore.sh`
- Custom-Format-Dumps
- atomare Erstellung und Dumpvalidierung
- 14 tägliche und 8 wöchentliche Generationen
- konfigurierbare Backupzeit
- Restore nur nach expliziter Bestätigung/Force
- Bot muss für produktiven Restore gestoppt sein
- Sicherheitsbackup vor Überschreiben
- Wiederherstellung in separate leere Testdatenbank

Tests:

- Testdaten schreiben
- Backup erzeugen
- Quelltabellen beziehungsweise Testdaten verändern
- Dump in leere Datenbank restaurieren
- zentrale Tabellen und Liquibase-Stand vergleichen
- fehlerhafter Dump wird abgelehnt
- Retention löscht nur abgelaufene Generationen

### Paket F – Betriebshandbuch und Gesamtverifikation

Neu:

- `docs/operations/server-bootstrap.md`
- `docs/operations/deployment.md`
- `docs/operations/backup-restore.md`
- `docs/operations/troubleshooting.md`

Dokumentiert werden mindestens:

- Debian-13-Erstinstallation
- SSH-Key, Nicht-Root-Admin, Firewall und automatische Sicherheitsupdates
- Dockerinstallation
- Verzeichnis- und Dateirechte
- GHCR-Login mit `read:packages`
- produktive Discord-Anwendung und Konfiguration
- Erstdeployment mit frischer Datenbank
- normales Update
- App-Rollback
- Backup und Restore
- Logzugriff und typische Fehler
- vollständiges Entfernen beziehungsweise Neuaufsetzen einer Testinstallation

Gesamtverifikation:

- Standardbuild
- PostgreSQL-Integration
- Containerimage
- Produktions-Compose ab leerem Volume
- Backup/Restore
- Upgrade/Rollback
- Dokumentationskommandos stichprobenartig automatisiert oder in sauberer Testumgebung ausgeführt

## Anwendungsänderungen

Zulässig sind nur eng begrenzte Betriebsänderungen, beispielsweise:

- Produktionsprofil
- interne Healthinformationen
- Readiness nach erfolgreichem Datenbank-/Liquibase-Startup
- sichere Build-/Versionsinformationen
- Startup-Validierung produktiver Pflichtkonfiguration

Nicht zulässig ohne neuen belegten Blocker:

- Umbau der Domänen- oder Applicationlogik
- neue fachliche Zustände
- Änderung der Discord-Nachrichten
- Änderung der Serienberechnung
- Änderung der Parser oder Bildverarbeitung
- Änderung der bestehenden Delivery-/Delete-/Recoverysemantik

## Automatisierte Abnahmekriterien

```text
mvn --batch-mode --no-transfer-progress clean verify
mvn --batch-mode --no-transfer-progress -Pdatabase-integration clean verify
```

Zusätzlich:

- Dockerimage erfolgreich gebaut und geprüft
- Runtime läuft als Nicht-Root
- keine Secrets im Image oder Buildkontext
- Produktions-Compose startet aus leerem Zustand
- Daten bleiben nach Neustart erhalten
- PostgreSQL besitzt keine Host-Portfreigabe
- Healthchecks werden grün
- private GHCR-Pipeline ist korrekt begrenzt
- Backup-/Restore-Vollpfad grün
- Deploy- und App-Rollback-Pfad grün
- bestehende Bot-Tests unverändert grün

## Manuelle Serverabnahme

Erst nach Bereitstellung des Netcup-Servers:

1. Debian 13 nach Handbuch installieren und aktualisieren.
2. SSH-Key-Zugang sowie Nicht-Root-Admin prüfen.
3. Root-/Passwort-SSH nach erfolgreichem Key-Test deaktivieren.
4. Firewall so setzen, dass nur SSH eingehend erlaubt ist.
5. Docker installieren.
6. Verzeichnis `/opt/gridwords-bot` und Secretrechte anlegen.
7. Privates GHCR-Image mit Read-only-Package-Token ziehen.
8. Separate Discord-Produktionsanwendung konfigurieren.
9. Frische PostgreSQL-Datenbank und Bot starten.
10. GridWords und QuadWords Ende-zu-Ende prüfen.
11. Tagesstatus und beide Reminderstufen prüfen.
12. Container- und vollständigen Serverneustart prüfen.
13. Backup erstellen und in leerer Testdatenbank verifizieren.
14. Produktiven Restore kontrolliert durchführen beziehungsweise in isolierter Produktionskopie simulieren.
15. Upgrade auf zweite Version und App-Rollback auf erste Version prüfen.
16. Sicherstellen, dass keine Testdaten oder Testchannel-IDs übernommen wurden.

## Abschlussdefinition

Der PR bleibt Draft und ungemergt, solange mindestens einer dieser Punkte fehlt:

- vollständige Automatisierung und Dokumentation
- beide Maven-Builds
- Container-/Compose-/Backup-/Restore-/Rollback-Tests
- beide grünen CI-Jobs auf dem finalen Head
- reale Netcup-/Discord-/PostgreSQL-Abnahme

Nur externe Bereitstellungszeiten des bestellten Servers dürfen zwischen automatisierter Fertigstellung und realer Abnahme offen bleiben. Alle Repositoryarbeiten müssen vorher vollständig abgeschlossen und gepusht sein.
