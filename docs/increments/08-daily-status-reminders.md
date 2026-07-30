# Inkrement 8: Tagesstatus, vollständige Serien und Erinnerungen

**Status:** vorbereitet  
**Issue:** #21  
**Branch:** `feature/daily-status-reminders`

## Ziel

Der Bot erhält den vollständigen täglichen Nutzungszyklus:

- eine persistente, editierbare Tagesstatusnachricht,
- alle fünf persönlichen Serien je aktivem Spieler,
- beide gemeinsamen Serien über die tagesbezogene aktive Teilnehmermenge,
- Reminder um 18:00 und 23:00 Uhr,
- sichere ID-basierte Mentions nur für aktive Opt-ins mit fehlenden Spielen,
- idempotente Zustellung, Recovery und Startup-Catch-up.

Verbindlich:

- [`../requirements/series-model.md`](../requirements/series-model.md)
- [`../requirements/dynamic-player-model.md`](../requirements/dynamic-player-model.md)
- [`../requirements/daily-status-reminders.md`](../requirements/daily-status-reminders.md)
- Issue #21

## Umsetzungspakete

### 1. Fachliche Statusprojektion

- transportneutrales Tagesstatusmodell,
- aktive Teilnehmer je Spieltag,
- beide Spielzustände je Spieler,
- alle fünf persönlichen Serien,
- gemeinsame Komplett- und Perfektserie,
- heutige vorläufige und historische endgültige Semantik,
- deterministische Spielerreihenfolge.

### 2. Tagesstatus-Renderer und Discord-Port

- kompakte Darstellung in einer Discord-Nachricht,
- mehrere Embeds nur bei Bedarf,
- keine Mentions im Status,
- vollständige Discord-Limitprüfung,
- Create/Edit/Recreate-Verhalten mit stabiler Message-ID.

### 3. Persistenter Tagesstatus

- Liquibase-Migration,
- fachliche Eindeutigkeit pro Guild, Channel und Spieltag,
- Claims/Leases oder gleichwertige Konkurrenzkontrolle,
- Inhaltsfingerabdruck beziehungsweise Version,
- Retry, Recovery und Ersatz nach extern gelöschter Nachricht,
- keine Discord-Aufrufe innerhalb von DB-Transaktionen.

### 4. Reminder-Delivery

- zwei konfigurierte Stufen,
- Kandidatenselektion aus dem vorbereiteten Port,
- aggregierte Nachricht mit konkret fehlenden Spielen,
- exakt begrenzte User-Allowed-Mentions,
- persistierter No-op ohne Kandidaten,
- unabhängige zweite Stufe mit erneuter Kandidatenberechnung,
- Catch-up der spätesten heute fälligen Stufe,
- Supersession früherer gleichzeitig überfälliger Stufen,
- kein Nachversand vergangener Tage.

### 5. Scheduler und Reconciliation

- injizierte `Clock` und konfigurierte `ZoneId`,
- DST-sichere zonierte nächste Ausführung,
- Startup-Reconciliation für heute und gestern,
- Tageswechsel-Finalisierung von gestern,
- identische idempotente Use Cases für Startup und regulären Scheduler,
- keine Abhängigkeit von der Host-Zeitzone.

### 6. Integrationshooks

Status-Refresh mindestens nach:

- erster gültiger Einreichung,
- weiterer Einreichung und Korrektur,
- Vortagsnachtrag,
- heute wirksamer Aktivierung,
- Reminder-Lauf,
- Tageswechsel beziehungsweise Startup-Reconciliation.

Status- oder Reminderfehler dürfen die bestehende Ergebnisverarbeitung nicht zurückrollen oder deren sichere Quelllöschung verändern.

## Persistenz

Vorgesehen sind mindestens:

- `daily_status_message`,
- `reminder_delivery`.

Die genaue Struktur darf sich an den vorhandenen Delivery-/Claim-Mustern orientieren. Verbindlich sind fachliche Unique Constraints, konkurrenzsichere Claims, retryfähige Zustände, persistierte Discord-Message-IDs und kein Hot-Loop bei permanenten Fehlern.

## Nicht-Ziele

- Wochen- und Monatsberichte,
- Statistik-Commands,
- Schedulerkonfiguration per Slash-Command,
- regelbasierte Kommentare,
- Mehrserverbetrieb,
- Änderungen an Parsern, QuadWords-Bildauswertung oder kanonischen Ergebnis-Renderern.

## Automatisierte Abnahme

Die vollständige Liste steht in Issue #21. Schwerpunkt:

- Status Create/Edit/Recreate ohne Duplikate,
- zwei, drei und wechselnde Teilnehmer,
- alle persönlichen und gemeinsamen Serien,
- heutige Vorläufigkeit und gestrige Finalisierung,
- Vortagsnachträge,
- Reminder-Kandidaten und sichere Mentions,
- No-op, zweite Stufe, Restart-Catch-up und Supersession,
- Konkurrenz, Retry und Recovery,
- DST-Grenzen,
- Discord-Limits,
- Liquibase und echtes PostgreSQL,
- vollständige Regression der Ergebnisverarbeitung.

## Validierung

```powershell
mvn --batch-mode --no-transfer-progress clean verify
mvn --batch-mode --no-transfer-progress -Pdatabase-integration clean verify
```

Der Standardbuild bleibt ohne PostgreSQL, Discord-Verbindung, Token und `.env` ausführbar. PostgreSQL-Integration verwendet die realen Liquibase-Migrationen.

## Manueller Abschluss

Nach vollständig grüner automatisierter Abnahme bleibt ein realer Discord-/PostgreSQL-Smoke-Test. Geprüft werden mindestens drei aktive Spieler, Status-Create/Edit, alle Statussymbole, echte begrenzte Mentions, beide Reminderstufen, Neustart-Catch-up, Vortagsnachtrag und unveränderte sichere Ergebnisersetzung.
