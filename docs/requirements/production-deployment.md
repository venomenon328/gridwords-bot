# Produktionsdeployment und Betrieb

## Status und Geltung

Dieses Dokument definiert den verbindlichen Produktions-, Container-, Secret-, Backup- und Deploymentweg.

Bei Widersprüchen zu älteren Entwicklungsanweisungen gilt:

- `compose.yaml` bleibt die bevorzugte lokale PostgreSQL-Umgebung.
- Für lokale Release-Candidate-Tests darf `compose.production.yaml` mit isoliertem Projektnamen und Testkonfiguration verwendet werden.
- Für Produktion gilt ausschließlich der hier definierte Container- und Betriebsweg.
- Produktive Secrets, Daten und Discord-IDs werden niemals in Repository, Image, Issue, PR oder Logs gespeichert.

## Zielsystem

- Anbieter: Netcup
- Tarif: VPS 500 G12
- Architektur: x86-64
- Betriebssystem: Debian 13 ohne Desktop oder Hosting-Control-Panel
- Runtime: Docker Engine und Docker-Compose-Plugin
- Zugriff ausschließlich über öffentliche IP und SSH
- keine Domain, kein Reverse Proxy und kein öffentliches HTTP/HTTPS erforderlich
- nur SSH ist öffentlich erreichbar

Bot und PostgreSQL laufen auf demselben dedizierten Host, aber in getrennten Containern und einem internen Docker-Netz.

## Produktionskomponenten

### Anwendung

- privates Image `ghcr.io/venomenon328/gridwords-bot`
- Java 21
- ausführbares Spring-Boot-JAR
- Projektversion `1.0.0`
- Runtime als Nicht-Root-Benutzer
- kein Maven, Git oder JDK auf dem Produktionsserver erforderlich
- kontrollierte JVM-Speichergrenze
- `-XX:+ExitOnOutOfMemoryError`
- Readiness und Liveness ohne öffentlichen Managementport

### PostgreSQL

- PostgreSQL Major 16
- bewusst gepinnte und unterstützte Minor-Version
- persistentes Docker-Volume
- kein Host-Port für PostgreSQL
- Zugriff ausschließlich aus dem internen Compose-Netz
- Healthcheck über `pg_isready`

### Discord

- separate Discord-Produktionsanwendung
- separater Produktionstoken
- eigene Produktions-Guild-/Channel-Konfiguration
- kein gleichzeitiger Betrieb derselben Bot-Anwendung lokal und produktiv

Für den Release Candidate wird eine getrennte Discord-Testanwendung mit Test-Guild und Testchannel verwendet.

## Repository-Artefakte

Der Produktionsweg umfasst mindestens:

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

## Image-Build, Release Candidate und Registry

### Lokaler Release Candidate

Der RC wird lokal aus einem vollständig grünen `main` gebaut. Die Projektversion bleibt `1.0.0`; die RC-Kennung wird nur im lokalen Image-Tag und im Abnahmeprotokoll geführt, beispielsweise:

```text
gridwords-bot:1.0.0-rc.1
```

Nach erfolgreicher Abnahme darf ausschließlich derselbe freigegebene Commit veröffentlicht werden. Ein späterer Workflow-Lauf erzeugt einen neuen Build dieses Commits und ist nicht automatisch byte-identisch. Eine byte-identische Promotion ist nur möglich, wenn das lokal getestete Image erhalten und direkt veröffentlicht wird.

Der lokale RC verwendet:

- separate Discord-Testanwendung,
- Test-Guild und dedizierten Testchannel,
- isolierten Compose-Projektnamen,
- isoliertes PostgreSQL-Volume,
- keine produktiven Secrets oder Daten.

### GHCR

Verbindliche veröffentlichte Tags:

- unveränderlicher Commit-Tag `sha-<vollständige Commit-SHA>`
- einmaliger SemVer-Tag, beispielsweise `1.0.0`

`latest` wird nicht als Deploymentreferenz verwendet.

### Ausschließlich manuelle Veröffentlichung

Pull Requests und Pushes auf `main`:

1. führen den vollständigen Standardbuild aus,
2. führen die PostgreSQL-Integrationstests aus,
3. bauen das Containerimage,
4. prüfen Imageinhalt und Nicht-Root-Runtime,
5. prüfen Compose, Backup, Restore, Resume und Rollback,
6. veröffentlichen **kein** Image.

Erst nach erfolgreicher RC-Abnahme wird der Workflow `Container image` bewusst per `workflow_dispatch` auf `main` mit einem neuen `MAJOR.MINOR.PATCH`-Tag gestartet. Nur dieser manuelle Lauf darf nach GHCR veröffentlichen.

Der Workflow veröffentlicht den SHA-Tag und den SemVer-Tag zusammen, erzeugt SBOM und Provenance und verweigert das Überschreiben vorhandener Release-Tags.

GitHub Actions besitzt keinen SSH-Zugang zum Produktionsserver und führt kein automatisches Produktionsdeployment aus.

## Produktions-Compose

`compose.production.yaml` enthält:

- Service `bot`
- Service `postgres`
- internes Datenbanknetz
- persistentes PostgreSQL-Volume
- `restart: unless-stopped`
- PostgreSQL-Healthcheck
- Bot-Start erst nach gesunder Datenbank
- Bot-Liveness-/Readiness-Healthcheck
- Docker-Logrotation
- Ressourcenlimits passend zu 4 GB Gesamt-RAM
- keine Host-Portfreigabe für PostgreSQL
- keinen öffentlich gebundenen Managementport

Ausgangspunkt für Ressourcenlimits:

```text
Bot:        maximal 1 GiB
PostgreSQL: maximal 1 GiB
```

Die Standardzeiten werden vollständig an den Container weitergereicht:

```text
REMINDER_FIRST_TIME=16:00
REMINDER_SECOND_TIME=22:00
DAILY_CLEANUP_TIME=06:00
WEEKLY_REPORT_TIME=08:00
MONTHLY_REPORT_TIME=08:15
TIME_ZONE=Europe/Berlin
```

## Konfigurations- und Secretmodell

Produktionsverzeichnis:

```text
/opt/gridwords-bot/
```

Trennung:

```text
compose.production.yaml     nicht geheim
runtime.env                 produktive Secrets und Bot-Konfiguration, Modus 0600
deployment.env              aktuell bestätigtes Image, keine Secrets
deployment-state.env        bestätigter Image-/Digestzustand, keine Secrets
backups/                    lokale Datenbankbackups
scripts/                    versionierte Betriebsskripte
```

`runtime.env` enthält mindestens:

- Discord-Token
- Guild-, Channel- und Admin-IDs
- Datenbankpasswort
- JDBC-Zugang
- Zeitzone und alle Schedulerzeiten

Produktive JDBC-URL:

```text
jdbc:postgresql://postgres:5432/gridwords
```

`.env.production.example` enthält alle erforderlichen Schlüssel, aber keine echten Secrets oder produktiven IDs.

Der Server authentifiziert sich bei GHCR über ein minimal berechtigtes Token mit `read:packages`.

## Deploymentprozess

Das Deployment wird bewusst durch Tobias per SSH ausgelöst, beispielsweise:

```powershell
ssh -i $env:USERPROFILE\.ssh\gridwords_netcup gridwords@SERVER_IP "/opt/gridwords-bot/scripts/deploy.sh sha-VOLLSTAENDIGE_COMMIT_SHA"
```

`deploy.sh` muss:

1. Eingabe und lokale Voraussetzungen validieren,
2. parallele Deployments verhindern,
3. PostgreSQL starten und Health abwarten,
4. vor dem Update ein validiertes Backup erstellen,
5. exakt das angeforderte Image ziehen,
6. ausschließlich den Bot-Service aktualisieren,
7. Liveness, Readiness, Datenbank, Liquibase und Imageidentität prüfen,
8. bei fehlerhaftem App-Image den zuvor laufenden App-Stand wiederherstellen,
9. bestätigten Deploymentzustand erst nach vollständiger Verifikation atomar schreiben.

Ein erneuter Aufruf mit derselben gesunden Version ist ein Verifikations-No-op.

Die Datenbank wird bei einem App-Rollback niemals automatisch zurückgerollt. Ein älteres App-Image darf nur verwendet werden, wenn es mit dem aktuellen Schema kompatibel ist.

## Backup und Restore

- Backup vor jedem Deployment
- regelmäßige lokale Backups
- validierte Archivstruktur und Metadaten
- dokumentierter Restore in eine kontrollierte PostgreSQL-Instanz
- keine ungeprüfte Überschreibung der laufenden Datenbank
- Restore- und Rollbackpfade werden im Containerworkflow automatisiert geprüft

Verbindliche Details: `docs/operations/backup-restore.md`.

## Logging und Diagnose

- Docker-Logrotation
- keine Tokens oder Passwörter in Logs
- sichere Fehlertexte für Discord- und Persistenzfehler
- Actuator-Probes nur intern erreichbar
- `scripts/verify-deployment.sh` als verbindliche Betriebsprüfung
- gespeicherter Image- und Registry-Digest zur Nachvollziehbarkeit

## Sicherheitsanforderungen

- Bot läuft nicht als Root
- Containerdateisystem ist read-only, soweit für den Betrieb möglich
- temporäre Dateien liegen in begrenztem `tmpfs`
- PostgreSQL und Actuator besitzen keine öffentlichen Hostports
- Secrets liegen ausschließlich in nicht versionierten Dateien mit restriktiven Rechten
- GHCR-Publish ist manuell und Release-Tags sind unveränderlich
- Produktion wird nicht automatisch aus GitHub Actions verändert

## Abnahme

Vor einem produktiven Release müssen erfüllt sein:

- Standardbuild grün
- PostgreSQL-Integration grün
- Container- und Betriebsworkflow grün
- lokaler RC aus demselben freigegebenen Commit gebaut
- reale Discord-Abnahme auf dem Testserver erfolgreich
- Image-ID des lokalen RC dokumentiert
- keine Quelländerung nach RC-Abnahme
- manuelle GHCR-Veröffentlichung erfolgreich
- produktives Backup vor Rollout erfolgreich
- `deploy.sh` und `verify-deployment.sh` erfolgreich
