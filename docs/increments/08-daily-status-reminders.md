# Inkrement 8: Tagesstatus, vollstÃ¤ndige Serien und Erinnerungen

**Status:** in Umsetzung – automatisierte Review-Korrekturen validiert  
**Issue:** #21  
**Branch:** `feature/daily-status-reminders`

## Ziel

Der Bot erhÃ¤lt den vollstÃ¤ndigen tÃ¤glichen Nutzungszyklus:

- eine persistente, editierbare Tagesstatusnachricht,
- alle fÃ¼nf persÃ¶nlichen Serien je aktivem Spieler,
- beide gemeinsamen Serien Ã¼ber die tagesbezogene aktive Teilnehmermenge,
- Reminder um 18:00 und 23:00 Uhr,
- sichere ID-basierte Mentions nur fÃ¼r aktive Opt-ins mit fehlenden Spielen,
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
- beide SpielzustÃ¤nde je Spieler,
- alle fÃ¼nf persÃ¶nlichen Serien,
- gemeinsame Komplett- und Perfektserie,
- heutige vorlÃ¤ufige und historische endgÃ¼ltige Semantik,
- deterministische Spielerreihenfolge.

### 2. Tagesstatus-Renderer und Discord-Port

- kompakte Darstellung in einer Discord-Nachricht,
- mehrere Embeds nur bei Bedarf,
- keine Mentions im Status,
- vollstÃ¤ndige Discord-LimitprÃ¼fung,
- Create/Edit/Recreate-Verhalten mit stabiler Message-ID.

### 3. Persistenter Tagesstatus

- Liquibase-Migration,
- fachliche Eindeutigkeit pro Guild, Channel und Spieltag,
- Claims/Leases oder gleichwertige Konkurrenzkontrolle,
- Inhaltsfingerabdruck beziehungsweise Version,
- Retry, Recovery und Ersatz nach extern gelÃ¶schter Nachricht,
- keine Discord-Aufrufe innerhalb von DB-Transaktionen.

### 4. Reminder-Delivery

- zwei konfigurierte Stufen,
- Kandidatenselektion aus dem vorbereiteten Port,
- aggregierte Nachricht mit konkret fehlenden Spielen,
- exakt begrenzte User-Allowed-Mentions,
- persistierter No-op ohne Kandidaten,
- unabhÃ¤ngige zweite Stufe mit erneuter Kandidatenberechnung,
- Catch-up der spÃ¤testen heute fÃ¤lligen Stufe,
- Supersession frÃ¼herer gleichzeitig Ã¼berfÃ¤lliger Stufen,
- kein Nachversand vergangener Tage.

### 5. Scheduler und Reconciliation

- injizierte `Clock` und konfigurierte `ZoneId`,
- DST-sichere zonierte nÃ¤chste AusfÃ¼hrung,
- Startup-Reconciliation fÃ¼r heute und gestern,
- Tageswechsel-Finalisierung von gestern,
- identische idempotente Use Cases fÃ¼r Startup und regulÃ¤ren Scheduler,
- keine AbhÃ¤ngigkeit von der Host-Zeitzone.

### 6. Integrationshooks

Status-Refresh mindestens nach:

- erster gÃ¼ltiger Einreichung,
- weiterer Einreichung und Korrektur,
- Vortagsnachtrag,
- heute wirksamer Aktivierung,
- Reminder-Lauf,
- Tageswechsel beziehungsweise Startup-Reconciliation.

Status- oder Reminderfehler dÃ¼rfen die bestehende Ergebnisverarbeitung nicht zurÃ¼ckrollen oder deren sichere QuelllÃ¶schung verÃ¤ndern.

## Persistenz

Vorgesehen sind mindestens:

- `daily_status_message`,
- `reminder_delivery`.

Die genaue Struktur darf sich an den vorhandenen Delivery-/Claim-Mustern orientieren. Verbindlich sind fachliche Unique Constraints, konkurrenzsichere Claims, retryfÃ¤hige ZustÃ¤nde, persistierte Discord-Message-IDs und kein Hot-Loop bei permanenten Fehlern.

## Nicht-Ziele

- Wochen- und Monatsberichte,
- Statistik-Commands,
- Schedulerkonfiguration per Slash-Command,
- regelbasierte Kommentare,
- Mehrserverbetrieb,
- Ã„nderungen an Parsern, QuadWords-Bildauswertung oder kanonischen Ergebnis-Renderern.

## Automatisierte Abnahme

Die vollstÃ¤ndige Liste steht in Issue #21. Schwerpunkt:

- Status Create/Edit/Recreate ohne Duplikate,
- zwei, drei und wechselnde Teilnehmer,
- alle persÃ¶nlichen und gemeinsamen Serien,
- heutige VorlÃ¤ufigkeit und gestrige Finalisierung,
- VortagsnachtrÃ¤ge,
- Reminder-Kandidaten und sichere Mentions,
- No-op, zweite Stufe, Restart-Catch-up und Supersession,
- Konkurrenz, Retry und Recovery,
- DST-Grenzen,
- Discord-Limits,
- Liquibase und echtes PostgreSQL,
- vollstÃ¤ndige Regression der Ergebnisverarbeitung.

## Validierung

```powershell
mvn --batch-mode --no-transfer-progress clean verify
mvn --batch-mode --no-transfer-progress -Pdatabase-integration clean verify
```

Der Standardbuild bleibt ohne PostgreSQL, Discord-Verbindung, Token und `.env` ausfÃ¼hrbar. PostgreSQL-Integration verwendet die realen Liquibase-Migrationen.

## Manueller Abschluss

Nach vollstÃ¤ndig grÃ¼ner automatisierter Abnahme bleibt ein realer Discord-/PostgreSQL-Smoke-Test. GeprÃ¼ft werden mindestens drei aktive Spieler, Status-Create/Edit, alle Statussymbole, echte begrenzte Mentions, beide Reminderstufen, Neustart-Catch-up, Vortagsnachtrag und unverÃ¤nderte sichere Ergebnisersetzung.
