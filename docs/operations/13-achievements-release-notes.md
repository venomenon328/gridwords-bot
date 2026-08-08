# Release 1.4.0 – Achievements

**Release:** 1.4.0  
**Produktiv ausgerollt:** 8. August 2026  
**Produktiver Merge-Commit:** `213fe15dcc59e46856ea9be7066161fdc473353a`  
**Inkrement:** 13 / Issue #86  
**Gesamt-PR:** #103

## Inhalt

Version 1.4.0 führt den kuratierten Katalog `achievements-v1` mit 60 einmalig freischaltbaren Achievements ein.

Enthalten sind insbesondere:

- rückwirkende Rekonstruktion bereits historisch erfüllter Achievements,
- genau eine historische Einführungsmeldung pro Teilnehmer und Definitionsversion,
- aggregierte öffentliche Live-Freischaltungen,
- Korrektur, Invalidierung und Reaktivierung ohne öffentliche Aberkennungsnachrichten,
- `/achievements` für aktuell aktive Achievements mit Self-/Other- und Game-Filter,
- `/achievement-list` als self-only Vollkatalogansicht mit allen 60 Definitionen und ausschließlich `✅`/`❌` als persönlichem Status,
- persistente Bootstrap-, Reconciliation-, Delivery-, Retry-, Restart- und Send-vor-ACK-Recovery,
- Liquibase-Migration 024 für Achievement-State, Events, Bootstrap und Announcement-Delivery.

## Abnahme

Vor Merge und Rollout waren vollständig grün:

```text
mvn --batch-mode --no-transfer-progress clean verify
mvn --batch-mode --no-transfer-progress -Pdatabase-integration clean verify
mvn --batch-mode --no-transfer-progress -Pmigration-clean-install verify
```

Zusätzlich wurde der vollständige `Container image`-Workflow mit Image-Runtime, Compose, Backup, Restore, Resume und Rollback erfolgreich ausgeführt.

Der reale lokale Discord-Smoke bestätigte Startup, Upgrade/Migration 024, Bootstrap bis `SUCCEEDED`, historische Introduction, `/achievements` und Restart-Idempotenz. Dabei wurden vor dem Merge zwei reale Randfälle behoben und regressionsgesichert: die Spring-Wiring-Reihenfolge der Achievement-Persistenz und sichtbare Recovery-Metadaten im Discord-Embed.

Der anschließende Produktivrollout von Version 1.4.0 wurde erfolgreich abgeschlossen. Der Betreiber bestätigte den Produktiv-Smoke/Canary als bestanden; es wurde kein Rollbackkriterium ausgelöst.

## Betrieb

Verbindliche Betriebs- und Recoveryregeln:

- [`13-achievements-operations.md`](13-achievements-operations.md)
- [`13-achievements-acceptance.md`](13-achievements-acceptance.md)
- [`13-achievements-live-canary.md`](13-achievements-live-canary.md)

Achievement-Tabellen werden bei Diagnose oder App-Rollback nicht manuell passend editiert oder gelöscht. Kanonische Ergebnisse und Teilnahmezeiträume bleiben Quelle der Wahrheit; Recovery und Reconciliation sind die vorgesehenen Reparaturmechanismen.
