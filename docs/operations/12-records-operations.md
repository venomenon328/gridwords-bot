# Rekorde: Betrieb, Recovery und sichere Wiederholung

Dieses Runbook ergänzt [record-live-evaluation.md](record-live-evaluation.md), [troubleshooting.md](troubleshooting.md) und den allgemeinen [Backup-/Restoreweg](backup-restore.md). Alle folgenden Abfragen sind lesend. Sie enthalten keine Tokens und geben weder `claim_token` noch `safe_error` aus.

Setze auf dem Server zunächst den vorhandenen Compose-Alias aus `troubleshooting.md`:

```bash
COMPOSE="docker compose --project-name gridwords-production --env-file runtime.env --env-file deployment.env -f compose.production.yaml"
```

## Zustand lesen

Bootstrap und Rekordzustand:

```bash
$COMPOSE exec -T postgres psql -U "$(grep '^POSTGRES_USER=' runtime.env | cut -d= -f2-)" -d "$(grep '^POSTGRES_DB=' runtime.env | cut -d= -f2-)" -c "SELECT guild_id, definition_version, bootstrap_state, attempt_count, started_at, completed_at, next_retry_at, updated_at FROM record_bootstrap ORDER BY guild_id, definition_version;"
$COMPOSE exec -T postgres psql -U "$(grep '^POSTGRES_USER=' runtime.env | cut -d= -f2-)" -d "$(grep '^POSTGRES_DB=' runtime.env | cut -d= -f2-)" -c "SELECT definition_version, count(*) AS states, max(updated_at) AS newest_state FROM record_state GROUP BY definition_version ORDER BY definition_version;"
```

Offene Arbeit, gealterte Claims und Deliveryzustände:

```bash
$COMPOSE exec -T postgres psql -U "$(grep '^POSTGRES_USER=' runtime.env | cut -d= -f2-)" -d "$(grep '^POSTGRES_DB=' runtime.env | cut -d= -f2-)" -c "SELECT evaluation_state, count(*) FROM record_live_evaluation GROUP BY evaluation_state ORDER BY evaluation_state;"
$COMPOSE exec -T postgres psql -U "$(grep '^POSTGRES_USER=' runtime.env | cut -d= -f2-)" -d "$(grep '^POSTGRES_DB=' runtime.env | cut -d= -f2-)" -c "SELECT close_state, count(*) FROM record_day_close GROUP BY close_state ORDER BY close_state;"
$COMPOSE exec -T postgres psql -U "$(grep '^POSTGRES_USER=' runtime.env | cut -d= -f2-)" -d "$(grep '^POSTGRES_DB=' runtime.env | cut -d= -f2-)" -c "SELECT delivery_state, count(*) FROM record_announcement GROUP BY delivery_state ORDER BY delivery_state;"
$COMPOSE exec -T postgres psql -U "$(grep '^POSTGRES_USER=' runtime.env | cut -d= -f2-)" -d "$(grep '^POSTGRES_DB=' runtime.env | cut -d= -f2-)" -c "SELECT 'bootstrap' AS work, count(*) AS expired FROM record_bootstrap WHERE bootstrap_state = 'CLAIMED' AND claim_until < now() UNION ALL SELECT 'live_evaluation', count(*) FROM record_live_evaluation WHERE evaluation_state = 'CLAIMED' AND claim_until < now() UNION ALL SELECT 'day_close', count(*) FROM record_day_close WHERE close_state = 'CLAIMED' AND claim_until < now() UNION ALL SELECT 'announcement', count(*) FROM record_announcement WHERE delivery_state = 'CLAIMED' AND claim_until < now();"
```

`SUCCEEDED` Bootstrap ist die Voraussetzung für öffentliche Meldungen und eine vollständige `/records`-Sicht. Ein abgelaufener Claim wird durch den nächsten Worker übernommen; weder Claim-Tokens noch Zustände werden manuell auf `SUCCEEDED` gesetzt.

## Recovery

1. Bei `RETRYABLE` zuerst die Datenbank-, Discord- oder Netzwerkursache anhand der sicheren Bot-Logs und der Metriken prüfen. Den Backoff respektieren.
2. Bei einem unbekannten Fehler Stacktrace und Ursache beheben. Ein unbekannter technischer Fehler darf nicht durch SQL-Manipulation zu `RETRYABLE`, `FAILED_PERMANENT` oder Erfolg umetikettiert werden.
3. Bei gealterten Claims den Bot kontrolliert neu starten. Bootstrap, Live-Auswertung, Day Close und Delivery übernehmen abgelaufene Leases tokengebunden und idempotent.
4. Bei einer extern entfernten, zuvor bestätigten Rekordmeldung `EXTERNALLY_REMOVED` respektieren. Sie wird nicht manuell erneut als neue Rekordmeldung erzeugt; `/records` bleibt der aktuelle Nachweis.
5. Bei einer fachlichen Korrektur den normalen Korrekturpfad verwenden. Dieser berechnet gewünschte Edit-/Reduktions-/Delete-Projektionen erneut; niemals Discord-Nachrichten oder Event-Gültigkeit direkt in PostgreSQL ändern.

Ein manueller Rebuild einer Definitionsversion ist eine ausdrücklich autorisierte Wartung und bleibt öffentlich still. Er benötigt ein eigenes, getestetes Wartungswerkzeug beziehungsweise eine nachfolgende Migration; dieses Paket führt keinen ungesicherten SQL-Command dafür ein.

## Backup, Restore und Rollback

Vor jeder Wiederherstellung zunächst `./scripts/backup.sh --check` ausführen und den Dump gemäß [backup-restore.md](backup-restore.md) in eine kontrollierte leere Datenbank prüfen. `restore.sh` überschreibt keine laufende Produktionsdatenbank ungeprüft.

Ein Application-Rollback rollt weder Liquibase noch Rekorddaten automatisch zurück. Nach einem Restore oder Rollback immer ausführen:

```bash
./scripts/verify-deployment.sh
```

Danach Bootstrap-, Claim- und Deliveryabfragen dieses Dokuments erneut lesen. Erst ein gesunder Container, erfolgreiche Liquibase-Prüfung und ein konsistenter persistenter Zustand beenden den Recovery-Vorgang.
