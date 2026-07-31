# Backup und Restore

## Sicherungsumfang

`backup.sh` erzeugt einen PostgreSQL-Custom-Dump der vollständigen Produktionsdatenbank, validiert ihn mit `pg_restore --list` und veröffentlicht ihn erst danach atomar im Backupverzeichnis. Parallel gestartete Sicherungen werden über einen Lock abgewiesen.

Standardaufbewahrung:

- 14 tägliche Dumps
- 8 zusätzlich markierte Wochendumps
- keine Monatsarchive

Backups liegen zunächst nur auf demselben VPS. Sie schützen vor Fehlbedienung und fehlerhaften Deployments, nicht vor vollständigem Server- oder Datenträgerverlust.

## Manuelles Backup

```bash
cd /opt/gridwords-bot
./scripts/backup.sh
ls -lah backups
```

Der ausgegebene Pfad verweist auf den validierten Dump. Versteckte `.tmp`-Dateien sind kein gültiges Backup und werden bei Fehlern entfernt.

Backupfähigkeit ohne Datendump prüfen:

```bash
./scripts/backup.sh --check
```

## Automatischer systemd-Timer

Die versionierten Units werden nach erfolgreichem Erstdeployment installiert:

```bash
sudo install -m 0644 /opt/gridwords-bot/ops/systemd/gridwords-backup.service /etc/systemd/system/gridwords-backup.service
sudo install -m 0644 /opt/gridwords-bot/ops/systemd/gridwords-backup.timer /etc/systemd/system/gridwords-backup.timer
sudo systemctl daemon-reload
sudo systemctl enable --now gridwords-backup.timer
```

Einen realen Lauf sofort prüfen:

```bash
sudo systemctl start gridwords-backup.service
sudo systemctl status gridwords-backup.service --no-pager
sudo journalctl -u gridwords-backup.service --since today --no-pager
systemctl list-timers gridwords-backup.timer --all
ls -lah /opt/gridwords-bot/backups
```

Der Timer läuft täglich um etwa 01:30 Uhr `Europe/Berlin`, verwendet `Persistent=true` und holt einen während eines Ausfalls verpassten Lauf nach. Eine kleine zufällige Verzögerung verhindert starre Lastspitzen.

## Restore zuerst in eine separate Datenbank testen

```bash
cd /opt/gridwords-bot
DUMP=/opt/gridwords-bot/backups/gridwords-YYYYMMDDTHHMMSSNNNNNNNNNZ.dump
./scripts/restore.sh --to-empty-database gridwords_restore_test "$DUMP"
```

Der Testmodus verweigert ausdrücklich den in `runtime.env` konfigurierten Produktionsdatenbanknamen sowie PostgreSQL-Wartungsdatenbanken.

Inhalt prüfen:

```bash
source <(grep -E '^(POSTGRES_USER|POSTGRES_DB)=' runtime.env)
docker compose --project-name gridwords-production --env-file runtime.env --env-file deployment.env -f compose.production.yaml \
  exec -T postgres psql -U "$POSTGRES_USER" -d gridwords_restore_test -c '\dt'
docker compose --project-name gridwords-production --env-file runtime.env --env-file deployment.env -f compose.production.yaml \
  exec -T postgres psql -U "$POSTGRES_USER" -d gridwords_restore_test -c 'select count(*) from databasechangelog;'
```

Die Testdatenbank danach entfernen:

```bash
docker compose --project-name gridwords-production --env-file runtime.env --env-file deployment.env -f compose.production.yaml \
  exec -T postgres psql -U "$POSTGRES_USER" -d postgres -c 'DROP DATABASE IF EXISTS gridwords_restore_test;'
unset POSTGRES_USER POSTGRES_DB
```

## Produktiven Restore durchführen

Ein produktiver Restore ist destruktiv und benötigt `--force`. Das Skript verlangt einen gestoppten Bot, validiert den Dump und erzeugt vor dem Überschreiben ein zusätzliches Sicherheitsbackup.

```bash
cd /opt/gridwords-bot
DUMP=/opt/gridwords-bot/backups/gridwords-YYYYMMDDTHHMMSSNNNNNNNNNZ.dump

docker compose --project-name gridwords-production --env-file runtime.env --env-file deployment.env -f compose.production.yaml stop bot
./scripts/restore.sh --force "$DUMP"
./scripts/verify-deployment.sh
```

Der Bot wird erst nach erfolgreicher Wiederherstellung neu gestartet. Schlägt der Restore nach dem Löschen der Datenbank fehl, bleibt der Bot gestoppt; dann nicht improvisieren, sondern Fehler und verfügbares Sicherheitsbackup prüfen.

## Schutzgrenzen

Folgende Aufrufe müssen fehlschlagen:

```bash
./scripts/restore.sh BACKUP.dump
./scripts/restore.sh --to-empty-database "$(grep '^POSTGRES_DB=' runtime.env | cut -d= -f2-)" BACKUP.dump
./scripts/restore.sh --to-empty-database postgres BACKUP.dump
```

Ein beschädigter Dump wird bereits vor jeder Datenbankänderung durch `pg_restore --list` abgelehnt.

## Retention manuell ausführen

```bash
cd /opt/gridwords-bot
./scripts/backup.sh --retention-only
```

Die Retention löscht ausschließlich zum jeweiligen Namensmuster gehörende ältere Generationen. Der systemd-Lauf und Deployments erzeugen keine parallelen Dumps, da `backup.sh` gegenseitigen Ausschluss erzwingt.

## Regelmäßiger Restore-Test

Mindestens nach relevanten Schemaänderungen und anschließend vierteljährlich:

1. aktuellen Dump erzeugen,
2. in `gridwords_restore_test` wiederherstellen,
3. `databasechangelog`, Spieler, Ergebnisse, Status- und Reminderzustände stichprobenartig prüfen,
4. Testdatenbank entfernen,
5. Ergebnis im Betriebsprotokoll festhalten.
