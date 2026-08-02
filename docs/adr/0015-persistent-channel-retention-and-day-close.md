# ADR 0015: Persistente Channel-Retention und Tagesabschluss

- Status: akzeptiert
- Datum: 2026-08-02
- Kontext: Issue #30 und Draft-PR #35

## Kontext

Sichtbare Ergebnis- und Reminder-Nachrichten müssen nach dem Spieltag reproduzierbar verschwinden, ohne gespeicherte fachliche Daten zu verändern oder absichtlich entfernte Nachrichten durch Recovery wiederzuerzeugen.

## Entscheidung

Kanonische Ergebnisse und Reminder erhalten getrennte Retirement-Projektionen mit ACTIVE, CLAIMED, RETRYABLE, RETIRED und PERMANENT, tokengebundenen Claims, Leases, Backoff und sicheren Fehlern. Ein nicht aktiver Ergebniszustand sperrt jede Publication-, Korrektur- und Recovery-Neuerzeugung.

Ein einzelner Cleanup-Orchestrator wird durch Startup und Scheduler aufgerufen. Ab 06:00 Uhr finalisiert er zuerst gestern, pensioniert danach alte Ergebnisnachrichten, dann alte Reminder und erstellt zuletzt den heutigen Statusanker. Discord-I/O bleibt außerhalb jeder Datenbanktransaktion. Unknown Message zählt als idempotenter Erfolg.

## Folgen

- Abstürze zwischen Claim, Discord-Löschung und Abschluss sind über Leases fortsetzbar.
- Ergebnisse, Boards, Rohshares und Reporting-Grundlagen bleiben unverändert.
- Es entsteht kein generisches Messaging-Framework.
