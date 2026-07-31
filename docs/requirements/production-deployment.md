# Produktionsdeployment und Betrieb

## Status und Geltung

Dieses Dokument ist für Inkrement 9 verbindlich. Es konkretisiert den reproduzierbaren Produktionsbetrieb der nach Inkrement 8 vollständig fachlich abgenommenen Kernversion.

Bei Widersprüchen zu älteren lokalen Entwicklungsanweisungen gilt:

- `compose.yaml` bleibt die bevorzugte lokale PostgreSQL-Umgebung.
- Für Produktion gilt ausschließlich der hier definierte Container-, Secret-, Deployment- und Backupweg.
- Produktive Secrets, Daten und Discord-IDs werden niemals in Repository, Image, Issue, PR oder Logs gespeichert.

## Zielsystem

- Anbieter: Netcup
- Tarif: VPS 500 G12
- Architektur: x86-64
- Betriebssystem: Debian 13 ohne Desktop oder Hosting-Control-Panel
- Runtime: Docker Engine und Docker-Compose-Plugin
- Zugriff zunächst ausschließlich über öffentliche IP und SSH
- keine Domain, kein Reverse Proxy und kein öffentliches HTTP/HTTPS in diesem Inkrement
- nur SSH ist öffentlich erreichbar

Der Server ist ein dedizierter Bot-Host. Bot und PostgreSQL laufen gemeinsam auf diesem VPS, aber in getrennten Containern und einem internen Docker-Netz.

## Produktionskomponenten

### Anwendung

- privates Image `ghcr.io/venomenon328/gridwords-bot`
- Java 21
- ausführbares Spring-Boot-JAR
- Runtime als Nicht-Root-Benutzer
- kein Maven, Git oder JDK auf dem Produktionsserver erforderlich
- kontrollierte JVM-Speichergrenze
- `-XX:+ExitOnOutOfMemoryError`
- Readiness und Liveness ohne öffentlichen Managementport

### PostgreSQL

- PostgreSQL Major 16
- aktuelle, bewusst gepinnte und unterstützte Minor-Version
- persistentes Docker-Volume
- kein Host-Port für PostgreSQL
- Zugriff ausschließlich aus dem internen Compose-Netz
- Healthcheck über `pg_isready`
- frische Produktionsdatenbank ohne lokale Testdaten

### Discord

- separate Discord-Produktionsanwendung
- separater Produktionstoken
- eigene Produktions-Guild-/Channel-Konfiguration
- kein gleichzeitiger Betrieb derselben Bot-Anwendung lokal und produktiv

## Repository-Artefakte

Inkrement 9 stellt mindestens bereit:

```text
Dockerfile
.dockerignore
compose.production.yaml
.env.production.example

scripts/deploy.sh
scripts/backup.sh
scripts/restore.sh
scripts/verify-deployment.sh

docs/operations/server-bootstrap.md
docs/operations/deployment.md
docs/operations/backup-restore.md
docs/operations/troubleshooting.md

.github/workflows/container-image.yml
```

Weitere kleine Hilfsdateien, Systemd-Units oder Testskripte sind erlaubt, wenn sie den definierten Betrieb vereinfachen und vollständig dokumentiert sind.

## Image-Build und Registry

### GHCR

Das Bot-Image wird privat in GitHub Container Registry veröffentlicht.

Verbindliche Tags:

- unveränderlicher Commit-Tag, etwa `sha-<commit>`
- expliziter Release-Tag, etwa `0.9.0`

`latest` darf höchstens als Komfortalias existieren und wird niemals als reproduzierbare Deploymentreferenz verwendet. Deployments verwenden einen expliziten Tag; der tatsächlich gezogene Digest wird protokolliert.

### GitHub Actions

Die Pipeline:

1. führt den vollständigen Standardbuild aus,
2. führt die PostgreSQL-Integrationstests aus,
3. baut das Containerimage,
4. startet und prüft das Image ohne echten Discord-Token,
5. veröffentlicht erst nach grünen Prüfungen in GHCR.

Für Pull Requests wird das Image gebaut und geprüft, aber nicht veröffentlicht. Ein Push nach `main` darf einen Commit-Tag veröffentlichen. Ein manuell ausgelöster Workflow darf zusätzlich einen validierten Release-Tag veröffentlichen.

GitHub Actions erhält in diesem Inkrement keinen SSH-Zugang zum Produktionsserver und führt kein Produktionsdeployment aus.

## Produktions-Compose

`compose.production.yaml` enthält mindestens:

- Service `bot`
- Service `postgres`
- internes Netzwerk
- persistentes PostgreSQL-Volume
- `restart: unless-stopped`
- PostgreSQL-Healthcheck
- Bot-Start erst nach gesunder Datenbank
- Bot-Liveness-/Readiness-Healthcheck
- Docker-Logrotation
- Ressourcenlimits passend zu 4 GB Gesamt-RAM
- keine Host-Portfreigabe für PostgreSQL
- keinen öffentlich gebundenen Managementport

Ausgangspunkt für die Ressourcenlimits:

```text
Bot:        maximal 1 GiB
PostgreSQL: maximal 1 GiB
```

Die Werte dürfen nach gemessener Serverlast konservativer gesetzt werden. Mindestens etwa 2 GiB bleiben für Debian, Docker, Dateicache und temporäre Spitzen verfügbar.

## Konfigurations- und Secretmodell

Auf dem Server liegt das Deployment unter:

```text
/opt/gridwords-bot/
```

Empfohlene Trennung:

```text
compose.production.yaml     nicht geheim
runtime.env                 produktive Secrets und Bot-Konfiguration, Modus 0600
deployment.env              aktuell auszurollendes Image, keine Secrets
backups/                    lokale Datenbankbackups
scripts/                    versionierte Betriebsskripte
```

`runtime.env` enthält mindestens:

- Discord-Token
- Guild-, Channel- und Admin-IDs
- Datenbankpasswort
- JDBC-Zugang
- Zeitzone und Reminderzeiten

Produktive JDBC-URL:

```text
jdbc:postgresql://postgres:5432/gridwords
```

`.env.production.example` enthält alle erforderlichen Schlüssel, aber niemals echte Secrets oder produktive IDs.

Der Server authentifiziert sich bei GHCR über ein minimal berechtigtes Zugriffstoken mit ausschließlich notwendigem `read:packages`-Zugriff. Zugangsdaten werden nicht in Shell-History oder Skripten hinterlegt.

## Deploymentprozess

Das Deployment wird bewusst lokal durch Tobias ausgelöst, beispielsweise:

```powershell
ssh gridwords@SERVER_IP "/opt/gridwords-bot/scripts/deploy.sh 0.9.0"
```

`deploy.sh` muss:

1. Eingabe und lokale Voraussetzungen validieren,
2. den aktuell laufenden Image-Tag und Digest dokumentieren,
3. vor dem Update ein gültiges Datenbankbackup erstellen,
4. exakt den angeforderten privaten Image-Tag ziehen,
5. das Compose-Projekt kontrolliert aktualisieren,
6. auf PostgreSQL- und Bot-Health warten,
7. bei Erfolg die aktive Version protokollieren,
8. bei Fehler verständlich abbrechen und die vorherige App-Version wiederherstellbar lassen.

Wiederholtes Deployment derselben Version ist idempotent und erzeugt weder Datenverlust noch zusätzliche Discord-Nachrichten.

### Rollback

Ein App-Rollback erfolgt durch erneutes Deployment eines früheren unveränderlichen Image-Tags.

Liquibase-Migrationen sind vorwärtsgerichtet. Deshalb:

- vor jedem Deployment wird ein Backup erstellt,
- Schemaänderungen werden nach Möglichkeit rückwärtskompatibel gestaltet,
- ein Datenbankrollback erfolgt ausschließlich über den dokumentierten Restoreweg,
- destruktive Migrationen benötigen eine eigene explizite Betriebsentscheidung.

## Backups

### Umfang

Es wird die vollständige PostgreSQL-Datenbank gesichert, einschließlich:

- Spieler und Teilnahmezeiträume
- Ergebnisse und Submissions
- kanonische Discord-Message-IDs
- Tagesstatus- und Reminderzustände
- Claims, Delivery-Fences und Recoveryzustände
- Liquibase-Metadaten

### Format

- `pg_dump` im Custom-Format
- keine Ownerbindung
- atomare Erstellung über temporäre Datei und abschließendes Rename
- Prüfung des Dumps über `pg_restore --list`
- verständlicher Fehlerstatus und keine teilweise gültig wirkende Zieldatei

### Aufbewahrung

- tägliches lokales Backup
- 14 tägliche Stände
- zusätzlich 8 wöchentliche Stände
- wöchentlicher Stand ist ein bewusst markierter beziehungsweise kopierter erfolgreicher Tagesstand
- keine Monatsarchive in diesem Inkrement

Die Zeit ist konfigurierbar; empfohlener Default ist nachts nach dem Berliner Tageswechsel und außerhalb der Reminderzeiten.

Backups liegen zunächst ausschließlich lokal auf dem VPS. Diese Lösung schützt vor Fehlbedienung und fehlerhaften Deployments, aber nicht vor vollständigem Server- oder Datenträgerverlust. Ein Offsite-Backup, etwa auf das NAS, ist ausdrücklich ein späterer Betriebsbaustein.

## Restore

`restore.sh` muss:

- den Dump vorab validieren,
- standardmäßig destruktive Ausführung verweigern,
- eine explizite Bestätigung beziehungsweise `--force` verlangen,
- sicherstellen, dass der Bot während des produktiven Restores gestoppt ist,
- vor Überschreiben der Produktion ein zusätzliches Sicherheitsbackup anbieten beziehungsweise erzwingen,
- Datenbank kontrolliert neu erstellen und mit `pg_restore` wiederherstellen,
- anschließend Liquibase-/Anwendungsstart und Health prüfen.

Automatisierte Tests stellen zusätzlich einen Dump in eine separate leere Testdatenbank wieder her und vergleichen zentrale Tabelleninhalte.

## Serverhärtung

Die Bootstrap-Dokumentation umfasst mindestens:

- Debian 13 aktualisieren
- administrativen Nicht-Root-Benutzer anlegen
- SSH-Key-Zugang prüfen
- Root-Login über SSH deaktivieren
- Passwortauthentifizierung erst nach erfolgreichem Key-Test deaktivieren
- Host-Firewall: eingehend nur SSH
- optional ergänzende Netcup-Firewallregel dokumentieren
- automatische Sicherheitsupdates
- Docker aus einer dokumentierten vertrauenswürdigen Paketquelle
- Benutzer nur mit erforderlichen Docker-/Dateirechten ausstatten
- Dateirechte für Secrets und Backups

Das Root-Passwort, private SSH-Schlüssel, Discord-Token und GHCR-Token werden niemals in Chat, Repository, Issues, PRs, Screenshots oder Logs übertragen.

## Logging und Diagnose

Docker-Logs werden rotiert, beispielsweise:

```text
max-size: 10m
max-file: 5
```

Logs dürfen keine Secrets, vollständigen signierten Discord-CDN-URLs oder komplette Environment-Dumps enthalten.

`verify-deployment.sh` prüft mindestens:

- erwartete aktive Image-Version und Digest
- laufende/gesunde Container
- PostgreSQL-Erreichbarkeit aus dem internen Netz
- Bot-Readiness/Liveness
- erfolgreicher Liquibase-Startup
- keine öffentlich gebundene PostgreSQL-Schnittstelle
- grundlegende Backupfähigkeit

## Abnahmegrenze

### Vor Serverbereitstellung automatisierbar

- Dockerfile und Compose
- Imagepipeline und GHCR-Konfiguration
- Start aus leerem Volume
- Healthchecks
- Secretfreiheit des Images
- Backup-/Restore-Test
- Deployment-/Rollback-Skripttests
- Betriebsdokumentation
- vollständige bestehende Bot-Regression

### Erst auf dem Netcup-Server manuell abnehmbar

- Debian-13-Bootstrap
- SSH-/Firewallhärtung
- privater GHCR-Pull
- frische Produktionsdatenbank
- Verbindung der separaten Discord-Produktionsanwendung
- realer Ende-zu-Ende-Test
- Server-Reboot
- Backup, Restore und App-Rollback

Der Pull Request bleibt Draft und ungemergt, bis beide Bereiche vollständig erfolgreich abgenommen sind.

## Nicht Bestandteil

- Domain oder DNS-Abhängigkeit
- Reverse Proxy oder TLS-Terminierung
- öffentliches Monitoringendpoint
- automatisches Deployment aus GitHub Actions
- SSH-Schlüssel des Servers als GitHub Secret
- lokale Testdatenmigration
- NAS-Offsite-Backup
- Wochen-/Monatsberichte
- neue Statistik- oder Konfigurationscommands
- regelbasierte Kommentare
