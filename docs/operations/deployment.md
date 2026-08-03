# Produktionsdeployment und App-Rollback

## Voraussetzungen

- Docker und Compose sind nach `server-bootstrap.md` installiert.
- `/opt/gridwords-bot/runtime.env` enthält ausschließlich Produktionswerte und hat Modus `0600`.
- Der Benutzer `gridwords` ist mit einem auf `read:packages` begrenzten Token bei GHCR angemeldet.
- Der gewünschte Quellstand wurde als Release Candidate lokal gebaut und auf dem separaten Discord-Testserver erfolgreich abgenommen.
- Das gewünschte Image wurde danach bewusst über den manuell gestarteten GitHub-Actions-Workflow `Container image` auf `main` veröffentlicht.
- Das Deployment verwendet einen unveränderlichen Tag `sha-<40 hex>` oder den dabei einmalig erzeugten SemVer-Tag.
- `deployment.env` wird **nicht** vor dem ersten erfolgreichen Deployment angelegt. Das Skript schreibt diese Datei erst nach vollständiger Verifikation.

Ein normaler Push auf `main` baut und prüft das Produktionsimage, veröffentlicht es aber nicht nach GHCR.

## Manuelle GHCR-Veröffentlichung

Nach erfolgreicher RC-Abnahme in GitHub:

1. `Actions` öffnen.
2. Workflow `Container image` auswählen.
3. `Run workflow` wählen.
4. Branch `main` wählen.
5. den neuen Release-Tag im Format `MAJOR.MINOR.PATCH`, beispielsweise `1.0.0`, eingeben.
6. Workflow starten und vollständig grün abwarten.

Der Workflow veröffentlicht:

- den unveränderlichen Commit-Tag `sha-<vollständige main-SHA>`,
- den einmaligen SemVer-Tag, beispielsweise `1.0.0`,
- SBOM und Provenance.

Vorhandene SemVer-Tags werden nicht überschrieben. Für das Deployment bleibt der zugehörige SHA-Tag die bevorzugte Referenz.

## Erstdeployment

Auf dem Server:

```bash
cd /opt/gridwords-bot
./scripts/deploy.sh sha-REPLACE_WITH_40_LOWERCASE_HEX_CHARACTERS
./scripts/verify-deployment.sh
```

Der Ablauf ist:

1. Eingabe und Exklusivlock validieren.
2. PostgreSQL starten und Health abwarten.
3. Ein validiertes Datenbankbackup erstellen.
4. Exakt das angeforderte Image ziehen.
5. Nur den Bot-Service aktualisieren.
6. Liveness, Readiness, Datenbank, Liquibase und fehlende Hostports prüfen.
7. Erst danach `deployment.env` und `deployment-state.env` atomar aktualisieren.

`deployment-state.env` protokolliert Tag, lokale Image-ID, den zur angeforderten Repository-Referenz gehörenden Registry-Repo-Digest, vorherige Version und Zeitpunkt. Die Datei ist Betriebszustand und wird nicht versioniert.

## Normales Update

Den gewünschten SHA-Tag lokal bewusst auswählen und per SSH auslösen:

```powershell
ssh -i $env:USERPROFILE\.ssh\gridwords_netcup gridwords@SERVER_IP "/opt/gridwords-bot/scripts/deploy.sh sha-REPLACE_WITH_40_LOWERCASE_HEX_CHARACTERS"
```

Danach separat prüfen:

```powershell
ssh -i $env:USERPROFILE\.ssh\gridwords_netcup gridwords@SERVER_IP "/opt/gridwords-bot/scripts/verify-deployment.sh"
```

Ein erneuter Aufruf mit derselben bereits gesunden Version ist ein Verifikations-No-op und erzeugt keinen neuen Container.

## Unterbrochenes Deployment

Wird SSH getrennt, der Prozess beendet oder der Server neu gestartet, kann derselbe Befehl erneut ausgeführt werden. Ein unvollständiger Rollout wird nicht fälschlich als bestätigt behandelt.

```bash
cd /opt/gridwords-bot
./scripts/deploy.sh GEWÜNSCHTER_TAG
```

Das gilt auch für einen sehr späten Abbruch: Läuft der Zielcontainer bereits gesund und wurde `deployment.env` geschrieben, während `deployment-state.env` noch fehlt oder veraltet ist, verifiziert derselbe Aufruf den tatsächlichen Container und repariert ausschließlich den bestätigten Zustandsdatensatz. Der Bot-Container wird dabei nicht neu erzeugt.

Vor einer manuellen Reparatur zunächst prüfen:

```bash
cat deployment.env 2>/dev/null || true
cat deployment-state.env 2>/dev/null || true
docker compose --project-name gridwords-production --env-file runtime.env --env-file deployment.env -f compose.production.yaml ps
```

Die Dateien enthalten keine Secrets, aber konkrete Betriebsstände und sollten nicht öffentlich gepostet werden.

## Automatischer Rückweg bei fehlerhaftem App-Image

Wird das neue Image nicht gesund, versucht `deploy.sh`, das zuvor tatsächlich laufende Image erneut zu starten. Der Rückweg gilt nur dann als erfolgreich, wenn dieses Image anschließend denselben vollständigen Health- und Identitätscheck besteht. Schlägt auch der Rückweg fehl, wird keine Erfolgsmeldung ausgegeben und der Bot-Service gestoppt.

Die Datenbank wird dabei niemals automatisch zurückgerollt. Liquibase-Migrationen sind vorwärtsgerichtet.

## Bewusster App-Rollback

Einen früheren unveränderlichen Tag wie ein normales Deployment angeben:

```bash
cd /opt/gridwords-bot
./scripts/deploy.sh sha-PREVIOUS_40_HEX_COMMIT
```

Vorher muss geklärt sein, dass das ältere App-Image mit dem aktuellen Datenbankschema kompatibel ist. Bei destruktiven oder inkompatiblen Migrationen ist stattdessen der kontrollierte Datenbank-Restore nach `backup-restore.md` erforderlich.

## Diagnose

```bash
cd /opt/gridwords-bot
./scripts/verify-deployment.sh
docker compose --project-name gridwords-production --env-file runtime.env --env-file deployment.env -f compose.production.yaml logs --tail=200 bot
docker inspect "$(docker compose --project-name gridwords-production --env-file runtime.env --env-file deployment.env -f compose.production.yaml ps -q bot)" --format '{{.Config.Image}} {{.Image}} {{.State.Health.Status}}'
```

`verify-deployment.sh` prüft zusätzlich beide internen Actuator-Probes, den lokalen Imageinhalt gegen den erwarteten Tag und – nach abgeschlossenem Deployment – den gespeicherten Image-/Digestzustand.
