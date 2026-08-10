# Dokumentation

Diese Struktur trennt aktuelle Wahrheit, Entscheidungen, ausführbare Anleitungen und historische Evidenz.

## Autorität

Bei Widersprüchen gilt in dieser Reihenfolge:

1. aktueller ausdrücklicher Nutzerauftrag beziehungsweise maßgebliches Issue,
2. aktuelle Fachsemantik unter [`product/`](product/overview.md),
3. aktuelle technische Struktur unter [`architecture/`](architecture/overview.md),
4. akzeptierte, nicht abgelöste Entscheidungen unter [`adr/`](adr/README.md),
5. aktuelle Entwickler- und Betriebsanleitungen unter [`development/`](development/setup.md) und [`operations/`](operations/README.md),
6. Repository-Arbeitsregeln in [`../AGENTS.md`](../AGENTS.md).

[`history/`](history/README.md) ist ausdrücklich nicht normativ. Bei einem echten Widerspruch gleichrangiger aktueller Quellen wird nicht geraten; er wird als Blocker dokumentiert.

## Aktuelle Dokumente

### Produkt

- [`product/overview.md`](product/overview.md) – Umfang, Grundlagen und Grenzen
- [`product/results-and-publication.md`](product/results-and-publication.md) – Shares, Korrektur und kanonische Veröffentlichung
- [`product/participation.md`](product/participation.md) – spielbezogene historische Teilnahme
- [`product/streaks.md`](product/streaks.md) – persönliche und gemeinsame Serien
- [`product/daily-status-and-reminders.md`](product/daily-status-and-reminders.md) – Tagesstatus, Details, Reminder und Abschluss
- [`product/excuses.md`](product/excuses.md) – kontextabhängige Ausreden
- [`product/records.md`](product/records.md) – Rekorde und Meldungen
- [`product/achievements.md`](product/achievements.md) – vollständiger Achievement-Katalog
- [`product/reports.md`](product/reports.md) – Wochen- und Monatsberichte

### Architektur und Entscheidungen

- [`architecture/overview.md`](architecture/overview.md) – modularer Monolith und Modulgrenzen
- [`architecture/data-and-consistency.md`](architecture/data-and-consistency.md) – PostgreSQL, Transaktionen und Idempotenz
- [`architecture/discord-and-delivery.md`](architecture/discord-and-delivery.md) – Discord-Grenze und Deliveries
- [`architecture/background-processing.md`](architecture/background-processing.md) – Scheduler, Worker und Bootstrap
- [`architecture/production.md`](architecture/production.md) – produktive Containerarchitektur
- [`adr/README.md`](adr/README.md) – Index aller 20 ADRs einschließlich Ablösungsstatus

### Ausführbare Anleitungen

- [`development/setup.md`](development/setup.md), [`development/testing.md`](development/testing.md), [`development/workflow.md`](development/workflow.md), [`development/quadwords-fixtures.md`](development/quadwords-fixtures.md)
- [`operations/README.md`](operations/README.md) und die dort verlinkten aktiven Runbooks

## Historie und Content

[`history/releases.md`](history/releases.md) dokumentiert abgeschlossene Releases; frühere Requirements, Inkrementpläne und Abnahmen bleiben darunter als Evidenz erhalten. Sie werden nicht als aktueller Backlog oder heutige Produktdefinition verwendet.

Redaktionelle Ausredenquellen liegen außerhalb der normativen Dokumentation unter [`../content/excuses/`](../content/excuses/README.md) und werden deterministisch in den produktiven JSON-Katalog kompiliert.
