# Produktionsdeployment und App-Rollback

## Voraussetzungen

- Docker und Compose sind nach `server-bootstrap.md` installiert.
- `/opt/gridwords-bot/runtime.env` enthält ausschließlich Produktionswerte und hat Modus `0600`.
- Der Benutzer `gridwords` ist mit einem auf `read:packages` begrenzten Token bei GHCR angemeldet.
- Das gewünschte Image wurde durch den grünen `main`-Workflow als `sha-<40 hex>` oder bewusst als SemVer-Tag veröffentlicht.
- `deployment.env` wird **nicht** vor dem ersten erfolgreichen Deployment angelegt. Das Skript schreibt diese Datei erst nach vollständiger Verifikation.

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

Den gewünschten Tag lokal bewusst auswählen und per SSH auslösen:

```powershell
ssh -i $env:USERPROFILE\.ssh\gridwords_netcup gridwords@SERVER_IP "/opt/gridwords-bot/scripts/deploy.sh sha-REPLACE_WITH_40_LOWERCASE_HEX_CHARACTERS"
```

Danach optional separat prüfen:

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

## Manuell veröffentlichter SemVer-Tag

Der Workflow `Container image` kann über `workflow_dispatch` einen Tag wie `0.9.0` veröffentlichen. Der Workflow akzeptiert dies ausschließlich von `main`, nach vollständiger CI und nur, wenn der Release-Tag in GHCR noch nicht existiert. Vorhandene SemVer-Tags werden nicht überschrieben. Für maximale Nachvollziehbarkeit bleibt der zugehörige `sha-...`-Tag die bevorzugte Deploymentreferenz.

## Diagnose

```bash
cd /opt/gridwords-bot
./scripts/verify-deployment.sh
docker compose --project-name gridwords-production --env-file runtime.env --env-file deployment.env -f compose.production.yaml logs --tail=200 bot
docker inspect "$(docker compose --project-name gridwords-production --env-file runtime.env --env-file deployment.env -f compose.production.yaml ps -q bot)" --format '{{.Config.Image}} {{.Image}} {{.State.Health.Status}}'
```

`verify-deployment.sh` prüft zusätzlich beide internen Actuator-Probes, den lokalen Imageinhalt gegen den erwarteten Tag und – nach abgeschlossenem Deployment – den gespeicherten Image-/Digestzustand.
