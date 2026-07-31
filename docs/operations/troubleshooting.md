# Produktionsdiagnose

Alle Befehle werden als Benutzer `gridwords` unter `/opt/gridwords-bot` ausgeführt, sofern nicht ausdrücklich `sudo` angegeben ist.

```bash
cd /opt/gridwords-bot
COMPOSE="docker compose --project-name gridwords-production --env-file runtime.env --env-file deployment.env -f compose.production.yaml"
```

Die Variable ist nur eine lokale Komfortabkürzung. Secrets niemals mit `env`, `set`, `docker inspect` oder vollständigen Konfigurationsausgaben in Tickets kopieren.

## Gesamtzustand

```bash
./scripts/verify-deployment.sh
$COMPOSE ps
$COMPOSE logs --tail=200 bot
$COMPOSE logs --tail=100 postgres
cat deployment.env
cat deployment-state.env
```

`deployment.env` und `deployment-state.env` enthalten keine Zugangsdaten, aber konkrete Betriebsinformationen. Discord-Token, Passwörter, private Schlüssel und signierte Discord-CDN-URLs dürfen niemals geteilt werden.

## Bot ist unhealthy

```bash
BOT_ID="$($COMPOSE ps -q bot)"
docker inspect "$BOT_ID" --format '{{.Config.Image}} {{.Image}} {{.State.Status}} {{if .State.Health}}{{.State.Health.Status}}{{end}}'
$COMPOSE exec -T bot curl --fail --silent http://127.0.0.1:8081/actuator/health/liveness
$COMPOSE exec -T bot curl --fail --silent http://127.0.0.1:8081/actuator/health/readiness
$COMPOSE logs --tail=300 bot
```

Bei einem unmittelbar nach Deployment fehlerhaften Image den früheren expliziten `sha-...`-Tag erneut über `deploy.sh` ausrollen. Kein `latest` verwenden.

## PostgreSQL ist unhealthy

```bash
POSTGRES_ID="$($COMPOSE ps -q postgres)"
docker inspect "$POSTGRES_ID" --format '{{.State.Status}} {{if .State.Health}}{{.State.Health.Status}}{{end}}'
$COMPOSE logs --tail=300 postgres
$COMPOSE exec -T postgres pg_isready -U "$(grep '^POSTGRES_USER=' runtime.env | cut -d= -f2-)" -d "$(grep '^POSTGRES_DB=' runtime.env | cut -d= -f2-)"
df -h
docker system df
```

Das PostgreSQL-Volume niemals als Reparaturversuch löschen. Vor einem Restore die Ursache, den freien Speicherplatz und einen validierten Dump prüfen.

## Deployment wurde unterbrochen

Denselben Zieltag erneut ausführen:

```bash
./scripts/deploy.sh sha-TARGET_40_HEX_COMMIT
```

Das Skript erkennt ausschließlich eine tatsächlich laufende und gesunde Zielversion als No-op. Eine vorzeitig geschriebene oder veraltete Metadatendatei reicht nicht aus.

Falls ein exklusiver Operationslock gemeldet wird:

```bash
ps -ef | grep -E '[d]eploy.sh|[r]estore.sh|[b]ackup.sh'
ls -l .production-operation.lock backups/.backup.lock
```

Lockdateien allein bedeuten nicht, dass ein Prozess läuft; `flock` hält die Sperre nur während des aktiven Prozesses. Dateien nicht blind löschen, solange ein passender Prozess existiert.

## Backupfehler

```bash
sudo systemctl status gridwords-backup.service --no-pager
sudo journalctl -u gridwords-backup.service --since yesterday --no-pager
./scripts/backup.sh --check
ls -lah backups
```

Versteckte `.tmp`-Dateien sind keine erfolgreichen Backups. Nach Behebung der Ursache einen neuen Lauf starten und dessen Dump mit `restore.sh --to-empty-database` testen.

## GHCR-Pull schlägt fehl

```bash
docker logout ghcr.io
read -rsp 'GHCR read token: ' GHCR_TOKEN
echo
printf '%s' "$GHCR_TOKEN" | docker login ghcr.io --username venomenon328 --password-stdin
unset GHCR_TOKEN
chmod 0600 ~/.docker/config.json
```

Der Token benötigt Zugriff auf das private Paket und mindestens `read:packages`, aber keine Schreibrechte.

## Netzwerk und Firewall

```bash
sudo ss -lntup
sudo ufw status verbose
```

Öffentlich lauschen darf nur SSH. PostgreSQL, Anwendungsport und Actuator besitzen keine Host-Portbindung.

## Vollständiger Reset ausschließlich einer Testinstallation

Nur mit einem eindeutig separaten Testprojektnamen und niemals für `gridwords-production`:

```bash
BOT_IMAGE=gridwords-bot:test docker compose \
  --project-name gridwords-disposable-test \
  --env-file scripts/test/production-runtime.test.env \
  -f compose.production.yaml down -v --remove-orphans
```

Der Befehl `down -v` ist für Produktion verboten, weil er das Datenbankvolume löscht.
