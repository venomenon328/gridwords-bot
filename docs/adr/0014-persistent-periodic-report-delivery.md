# ADR 0014: Persistente periodische Report-Delivery

**Status:** akzeptiert  
**Datum:** 1. August 2026  
**Entscheidung für:** Inkrement 10 / Issue #25

## Kontext

Der Bot besitzt bereits persistente, idempotente Auslieferungsmechanismen für Tagesstatus und Reminder. Wochen- und Monatsberichte haben ähnliche technische Anforderungen, unterscheiden sich aber fachlich wesentlich:

- Sie fassen vollständig abgeschlossene Kalenderperioden zusammen.
- Ein Bericht kann wegen Discord-Grenzen aus mehreren geordneten Nachrichten bestehen.
- Nach erfolgreicher Veröffentlichung wird der Bericht nicht durch spätere Ergebnisse aktualisiert.
- Bei einem Bot-Ausfall sollen nur jüngste, noch relevante Berichte innerhalb begrenzter Catch-up-Fenster nachgeholt werden.
- Berechnete Statistiken müssen aus Ergebnissen und historischen Teilnahmezeiträumen abgeleitet bleiben.

Ohne eine explizite Entscheidung drohen doppelte Berichte, unkontrolliertes Nachholen alter Perioden, teilweise sichtbare Mehrseitenberichte oder eine zweite persistierte Statistikquelle.

## Entscheidung

### 1. Transportneutraler Reporting-Kern

Periodenbestimmung, Teilnehmerauswahl, Statistik- und Serienberechnung sowie Seitenmodell liegen in Domain/Application und kennen keine JDA-Typen.

Wochen- und Monatsbericht verwenden denselben Reporting-Use-Case. Der Berichtstyp liefert lediglich Periodenregel, Fälligkeit, Catch-up-Dauer und Titelkontext.

### 2. Statistiken bleiben abgeleitet

Persistiert werden keine Spieler-, Serien- oder Statistik-Snapshots des Berichts.

Bei einer Generierung werden die Werte aus `game_result` und `player_participation_period` bis einschließlich `period_end` neu berechnet.

Persistiert werden ausschließlich Delivery- und Reconciliation-Daten.

### 3. Snapshot nach erfolgreicher Veröffentlichung

Ein vollständig erfolgreich veröffentlichter Bericht wird fachlich eingefroren. Neue Ergebnisse, Korrekturen, Namens- oder Teilnahmeänderungen lösen keinen automatischen Edit aus.

Eine externe Löschung darf nur innerhalb des Catch-up-Fensters repariert werden. Dabei wird der Bericht aus dem aktuellen, weiterhin auf `period_end` begrenzten Datenbestand neu erzeugt.

### 4. Persistente Delivery-Zustandsmaschine

Der fachliche Schlüssel besteht mindestens aus:

```text
Guild-ID + Channel-ID + Berichtstyp + Periodenbeginn
```

Die Delivery persistiert zusätzlich:

- Periodenende und Fälligkeit,
- Zustand,
- Claim-Token und Lease,
- Versuchs- und Retrydaten,
- Fehlerkategorie,
- Inhalts-Fingerprint,
- geordnete Discord-Message-IDs,
- Veröffentlichungszeitpunkt,
- NO_OP- beziehungsweise Ablaufstatus.

PostgreSQL ist die Quelle der Wahrheit für Fälligkeit, Konkurrenz, Retry, Erfolg und Ablauf.

### 5. Mehrseitenbericht als eine logische Delivery

Alle Seiten eines Berichts gehören zu genau einer Delivery. Die Message-IDs werden in sichtbarer Reihenfolge persistiert.

Eine teilweise erfolgreiche Auslieferung gilt nicht als vollständiger Erfolg. Innerhalb des Catch-up-Fensters muss sie deterministisch reconciled, vervollständigt oder ersetzt werden.

Nach Ablauf des Fensters werden keine alten Seiten automatisch wiederhergestellt.

### 6. Claims, Leases und Transaktionsgrenzen

Die bewährten Muster aus ADR 0012 werden gezielt wiederverwendet:

- tokengebundene Claims,
- begrenzte Leases,
- Retry mit Backoff,
- permanente Fehler,
- kurze atomare Persistenzschritte,
- keine Discord-I/O innerhalb einer Datenbanktransaktion,
- Reconciliation unklarer externer Ausgänge,
- Duplikatbereinigung nach Replay und Konkurrenz.

Es wird kein universelles Messaging-Framework eingeführt. Gemeinsame konkrete Hilfstypen dürfen extrahiert werden, wenn Tagesstatus, Reminder und Reports dadurch nicht fachlich vermischt werden.

### 7. Catch-up und Startup

- Wochenbericht: 72 Stunden Catch-up.
- Monatsbericht: sieben Tage Catch-up.
- Das Fenster gilt halb offen: `due_at <= now < due_at + duration`.
- Pro Berichtstyp wird beim Start höchstens die jüngste noch relevante Periode nachgeholt.
- Ältere versäumte Perioden werden persistent als abgelaufen behandelt.

Der Scheduler ist nur ein wiederholter Trigger und besitzt keinen alleinigen In-Memory-Zustand.

### 8. NO_OP

Eine Periode ohne jeden Teilnahmetag wird als persistenter fachlicher NO_OP abgeschlossen. Sie erzeugt keine Discord-Nachricht und wird bei späteren Triggern nicht erneut bewertet.

Eine Periode mit Teilnehmern, aber ohne Ergebnisse ist kein NO_OP und erzeugt einen Bericht mit Fehl- und Nullwerten.

## Alternativen

### Statistikwerte als Report-Snapshot persistieren

Verworfen. Dies würde eine zweite fachliche Wahrheit neben Ergebnissen und Teilnahmezeiträumen schaffen und Korrektur-, Migration- und Konsistenzregeln unnötig verkomplizieren.

### Jeden Bericht dauerhaft editierbar halten

Verworfen. Periodische Berichte sollen abgeschlossene Zeiträume dokumentieren und nicht durch spätere Ereignisse unbemerkt umgeschrieben werden.

### Alle verpassten Berichte nachholen

Verworfen. Nach längeren Ausfällen würden mehrere alte Nachrichten den Channel fluten. Der operative Nutzen sinkt mit zunehmendem Abstand zur Periode.

### Nur eine Discord-Nachricht erlauben

Verworfen. Eine dynamische Spielerzahl kann Discord-Limits überschreiten. Deterministische Pagination ist notwendig.

### Eigenständige Delivery-Implementierung ohne Wiederverwendung

Verworfen. Claims, Leases, Retry und Reconciliation sind bereits erprobte Projektmuster. Eine Kopie würde Sicherheitslogik duplizieren.

### Vollständig generisches Notification-Framework

Verworfen. Es wäre für den kleinen modularen Monolithen eine vorauseilende Generalisierung und würde fachliche Unterschiede verwischen.

## Konsequenzen

Positiv:

- duplikatsichere und crash-fortsetzbare Auslieferung,
- keine zweite Statistikquelle,
- gemeinsame Grundlage für Woche, Monat und spätere Read-only-Statistik-Commands,
- begrenztes und nachvollziehbares Catch-up,
- sichere Unterstützung mehrerer Discord-Seiten.

Negativ beziehungsweise Aufwand:

- Mehrseiten-Reconciliation ist komplexer als eine einzelne Nachricht.
- Eine externe Löschung nach Ablauf des Catch-up-Fensters bleibt bewusst unrepariert.
- Ein eingefrorener Bericht kann von später korrigierten historischen Daten abweichen.
- Die Delivery benötigt eine neue Liquibase-Migration und PostgreSQL-Konkurrenztests.

## Umsetzungshinweise

- Die aktuelle Fachsemantik steht in [`../product/reports.md`](../product/reports.md).
- Die damalige Umsetzung ist unter [`../history/increments/10-periodic-reports.md`](../history/increments/10-periodic-reports.md) archiviert.
- Architektur- und Integrationstests müssen sicherstellen, dass Discord-Aufrufe außerhalb von Transaktionen liegen.
- Persistierte Fingerprints und Message-IDs sind Delivery-Metadaten, keine fachlichen Statistik-Snapshots.
