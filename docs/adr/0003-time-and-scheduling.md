# ADR 0003: Explizite Uhr, Europe/Berlin und persistierte Auslieferungen

- **Status:** akzeptiert
- **Datum:** 2026-07-28

## Kontext

Der Bot ordnet Ergebnisse anhand des im Share-Text enthaltenen Datums zu, akzeptiert nur heute und gestern und versendet Erinnerungen zu lokalen Uhrzeiten. Deutschland wechselt zwischen Sommer- und Winterzeit. Außerdem dürfen Neustarts keine doppelten oder dauerhaft ausgelassenen Erinnerungen verursachen.

Direkte Aufrufe von `LocalDate.now()` und verstreute Systemzeitnutzung erschweren reproduzierbare Tests. Ein einfacher in-memory Scheduler allein kann nach einem Neustart nicht erkennen, welche Auslieferung bereits erfolgt ist.

## Entscheidung

- Fachliche Zeitzone ist immer `Europe/Berlin`.
- Fachlicher Code erhält eine injizierte `java.time.Clock`.
- Das im Share-Ergebnis enthaltene Datum ist der Spieltag.
- Automatisch akzeptiert werden nur aktueller und unmittelbar vorheriger Spieltag in der fachlichen Zeitzone.
- Erinnerungs- und Berichtsauslieferungen werden persistent protokolliert und über Datenbank-Eindeutigkeiten gegen Doppelversand abgesichert.
- Beim Start prüft eine Recovery-Logik, ob eine heute fällige Auslieferung noch fehlt und nachgeholt werden muss.
- Version 1 liest Uhrzeiten beim Start aus Konfiguration.
- Die Scheduling-Grenze wird so gestaltet, dass Version 3 die Zeiten ohne Neustart neu planen kann.

Für die technische Planung wird zunächst Spring `TaskScheduler` bevorzugt. Quartz ist nicht vorgesehen, solange kein konkreter Bedarf für dessen zusätzliche Persistenz- und Clusterfunktionen besteht.

## Testregeln

- Zeitabhängige Tests verwenden `Clock.fixed(...)` oder eine kontrollierbare Test-Clock.
- Tests decken mindestens Mitternacht, Vortagsfenster und Zeitzonenwechsel ab.
- Scheduler-Tests prüfen die fachliche Fälligkeit getrennt von der tatsächlichen Threadplanung.
- Doppelte Auslieferungen werden durch Repository-/Constraint-Tests abgesichert.

## Konsequenzen

### Positiv

- Datums- und Serienlogik ist deterministisch testbar.
- Sommer-/Winterzeit wird über `ZoneId` statt feste UTC-Offsets behandelt.
- Neustarts führen nicht automatisch zu Doppelversand.
- Spätere dynamische Uhrzeiten erfordern keinen Umbau der Fachlogik.

### Negativ

- Zeit muss bewusst durch Services gereicht beziehungsweise injiziert werden.
- Recovery- und Delivery-Tabellen erhöhen den Implementierungsumfang geringfügig.

## Verworfene Alternativen

### Fester UTC-Offset

Ungeeignet wegen Sommer-/Winterzeit.

### Direkte Nutzung der Systemzeit in jeder Klasse

Schlecht testbar und anfällig für inkonsistente Tagesgrenzen.

### Nur `@Scheduled` ohne Delivery-Persistenz

Einfach, aber nach Neustarts oder mehrfach gestarteten Instanzen nicht zuverlässig idempotent.

### Quartz von Beginn an

Für einen einzelnen kleinen Prozess zunächst unnötige Komplexität.