# Inkrement 10 – Abgeleitete Wochen- und Monatsberichte

## Status

Vorbereitet auf Issue #25 und Branch `feature/periodic-reporting`.

## Ziel

Der produktive Bot veröffentlicht kompakte, idempotente Wochen- und Monatsberichte über vollständig abgeschlossene Kalenderperioden. Alle Kennzahlen und Serienwerte werden aus den bestehenden Spielergebnissen und historisch wirksamen Teilnahmezeiträumen abgeleitet.

Verbindlich:

- `docs/requirements/periodic-reports.md`
- `docs/requirements/series-model.md`
- `docs/requirements/dynamic-player-model.md`
- `docs/requirements/daily-status-reminders.md`
- ADR 0012
- ADR 0014
- Issue #25

## Abgrenzung

Enthalten:

- gemeinsame transportneutrale Reporting-Grundlage,
- abgeschlossene Vorwoche und abgeschlossener Vormonat,
- persönliche Spiel-, Tages- und Serienkennzahlen,
- gemeinsame Kennzahlen und Serien,
- deterministische Discord-Pagination,
- persistente idempotente Delivery,
- Scheduler, Catch-up, Ablauf und Recovery.

Nicht enthalten:

- Statistik-Slash-Commands,
- manuelle Report- oder Regenerate-Commands,
- Zeitänderung per Discord,
- persistierte Zeitkonfiguration,
- Scheduler-Neuplanung ohne Neustart,
- Gewinnerlogik oder Leaderboards,
- regelbasierte Kommentare,
- generative KI.

## Arbeitsweise mit Terra

Die Umsetzung erfolgt strikt paketweise. Terra erhält immer nur den Auftrag für das nächste Paket.

Für jedes Paket gilt:

1. Vor Beginn die verbindlichen Dokumente und `AGENTS.md` lesen.
2. Ausschließlich den Paketumfang umsetzen.
3. Keine vorauseilenden Persistenz-, Discord- oder Schedulerteile ergänzen.
4. Neue Logik mit passenden Unit-, Application-, Architektur- und gegebenenfalls PostgreSQL-Integrationstests absichern.
5. `mvn --batch-mode --no-transfer-progress clean verify` ausführen.
6. Bei Datenbankumfang zusätzlich `mvn --batch-mode --no-transfer-progress -Pdatabase-integration clean verify` ausführen.
7. Einen logisch geschlossenen Commit pushen.
8. Im Abschlussbericht geänderte Dateien, Entscheidungen, Tests, konkrete Ergebnisse und offene Punkte nennen.

Erst nach Prüfung des Abschlussberichts wird das nächste Paket beauftragt.

## Paket 0 – Fachliche Grundlage und Projektstatus

### Umfang

- Issue #25 anlegen.
- Branch `feature/periodic-reporting` anlegen.
- `docs/requirements/periodic-reports.md` anlegen.
- ADR 0014 anlegen.
- dieses Inkrementdokument anlegen.
- README, Implementierungsplan, Architekturübersicht und `AGENTS.md` auf Inkrement 10 aktualisieren.
- Draft-PR anlegen.

### Nicht enthalten

- Java-Code,
- Liquibase,
- Discord-Adapter,
- Scheduleränderungen,
- Produktionsdeployment.

### Abnahme

- Dokumente widersprechen einander nicht.
- Der Unterschied zwischen Teilnahmetag und Aktivitätstag ist eindeutig.
- Issue, Branch und Draft-PR verweisen auf dieselben Entscheidungen und Pakete.
- Keine Secrets oder Produktionsdaten.

## Paket 1 – Perioden- und Reportdomäne

### Ziel

Reine, transportneutrale Typen bestimmen Berichtstyp, Periode, Fälligkeit und Catch-up-Regeln.

### Umfang

Mindestens:

- `ReportType` mit `WEEKLY` und `MONTHLY`,
- `ReportPeriod` mit inklusivem Start und Ende,
- Regel für die vollständig abgeschlossene Vorwoche,
- Regel für den vollständig abgeschlossenen Vormonat,
- Periodenende als Statistik- und Serienstichtag,
- typisierte Fälligkeit,
- typisierte Catch-up-Dauer,
- halb offenes Catch-up-Fenster,
- injizierte `Clock` und `ZoneId`.

Transportneutrale Reporttypen dürfen bereits als kleine unveränderliche Records angelegt werden, soweit sie keine Datenbank- oder Discordannahmen enthalten.

### Tests

- normaler Wochenwechsel,
- Monatswechsel,
- Jahreswechsel,
- Februar in Schalt- und Nichtschaltjahr,
- Beginn und Ende der Sommerzeit,
- exakte Catch-up-Grenze,
- Daten nach Periodenende werden nicht Teil der Periode.

### Nicht enthalten

- Repository-Ports,
- PostgreSQL,
- Statistikberechnung,
- Serienberechnung,
- Renderer,
- Delivery und Scheduler.

## Paket 2 – Teilnehmer und mögliche Tage

### Ziel

Die dynamische Teilnehmermenge für eine abgeschlossene Periode wird korrekt aus historischen Teilnahmezeiträumen abgeleitet.

### Umfang

- kleiner Query-Port für Spieler und Teilnahmezeiträume einer Periode,
- alle Spieler mit mindestens einem Teilnahmetag,
- individuelle Teilnahmetage,
- tägliche aktive Teilnehmermenge,
- gemeinsam mögliche Tage mit mindestens zwei Aktiven,
- stabile Sortierung nach erstem Teilnahmebeginn und Discord-ID,
- aktueller gespeicherter Anzeigename als Ausgabewert,
- keine feste Spielerzahl.

### Tests

- Beitritt mitten in Woche und Monat,
- Austritt innerhalb der Periode,
- prospektiver Austritt,
- Wiedereintritt mit getrennten Perioden,
- mehrere Spieler mit wechselnder täglicher Menge,
- Spieler ohne Ergebnis bleibt enthalten,
- Spieler ohne Teilnahmetag bleibt ausgeschlossen,
- stabile Reihenfolge trotz Namensänderung.

### Datenbankintegration

PostgreSQL-Adapter und Abfragen werden gegen echte Teilnahmezeiträume geprüft. Keine Report-Delivery-Tabelle in diesem Paket.

## Paket 3 – Spielbezogene Periodenstatistiken

### Ziel

GridWords und QuadWords werden getrennt und ausschließlich über individuelle Teilnahmetage ausgewertet.

### Umfang je Spiel

- eingereicht,
- gelöst,
- nicht gelöst,
- fehlend,
- Lösungsquote,
- Summe und Anzahl der Versuche gelöster Ergebnisse,
- Summe und Anzahl der Lösungszeiten gelöster Ergebnisse,
- Bestzeit gelöster Ergebnisse.

Der transportneutrale Kern liefert keine früh gerundeten Anzeigezahlen. Der Renderer entscheidet später über Darstellung und Rundung.

### Tests

- vollständig gelöste Periode,
- `X/6` und `X/9`,
- fehlende Ergebnisse,
- keine Einreichung,
- keine Lösung,
- Zeit und Versuche nur aus gelösten Ergebnissen,
- Ergebnisse außerhalb der Teilnahme und nach Periodenende ausgeschlossen,
- GridWords und QuadWords unabhängig.

## Paket 4 – Tagesmerkmale und Serien-Snapshots

### Ziel

Persönliche und gemeinsame Tagesmerkmale sowie Serien werden endgültig zum Periodenende berechnet.

### Umfang

Pro Spieler:

- Aktivitätstage,
- komplette Tage,
- perfekte Tage,
- Stand am Periodenende aller fünf persönlichen Serien,
- Allzeitrekord bis Periodenende aller fünf persönlichen Serien.

Gemeinsam:

- gemeinsam komplette Tage,
- gemeinsam perfekte Tage,
- Stand am Periodenende beider gemeinsamer Serien,
- Allzeitrekord bis Periodenende beider gemeinsamer Serien.

### Regeln

- keine vorläufige Heute-Semantik,
- mindestens zwei aktive Spieler für gemeinsame Tage,
- alle am jeweiligen Tag aktiven Spieler müssen die gemeinsame Bedingung erfüllen,
- Ergebnisse nach Periodenende ausgeschlossen,
- keine gemeinsame Aktivitätsserie.

### Tests

Alle sieben Serienarten getrennt, wechselnde Teilnehmermenge, Lücken, `X`, fehlende Ergebnisse, Beitritt, Austritt, Wiedereintritt und zukünftige Daten.

## Paket 5 – Gemeinsamer Reporting-Use-Case

### Ziel

Die Daten aus Paketen 2 bis 4 werden in einem vollständigen transportneutralen Periodenbericht zusammengeführt.

### Umfang

- ein gemeinsamer Use Case für Woche und Monat,
- `PeriodicReport` mit persönlichen und gemeinsamen Abschnitten,
- stabile Spielerreihenfolge,
- Report mit Teilnehmern ohne Ergebnisse,
- fachlicher `NO_OP` ohne Teilnahmetage,
- ausschließlich Daten bis Periodenende,
- keine Speicherung berechneter Kennzahlen.

### Tests

- realistischer Zwei-Spieler-Bericht,
- drei oder mehr dynamische Spieler,
- nur ein Spieler,
- keine Teilnehmer,
- Teilnehmer ohne Ergebnisse,
- Woche und Monat verwenden denselben Kern,
- unveränderte Ergebnisse führen zu deterministisch gleichem Reportmodell.

## Paket 6 – Discord-Renderer und Pagination

### Ziel

Ein Periodenbericht wird deterministisch und Discord-sicher in eine oder mehrere Seiten gerendert.

### Umfang

- gleicher Renderer für Woche und Monat,
- Titel mit Berichtstyp und vollständigem Zeitraum,
- persönliche Abschnitte,
- gemeinsamer Abschnitt,
- eindeutige Seriennamen,
- neutrale Darstellung nicht definierter Quoten und Durchschnittswerte,
- Rundung ausschließlich im Renderer,
- deterministische Pagination,
- stabiler Inhalts-Fingerprint über die vollständige geordnete Seitenausgabe,
- keine Mentions und keine Allowed Mentions,
- keine technischen Footer-IDs,
- keine Rankings oder Vergleiche.

### Tests

- Golden-Tests für Woche und Monat,
- ein, zwei und viele Spieler,
- Discord-Längen- und Feldgrenzen,
- deterministische Seitenreihenfolge,
- neutrale Nullwerte,
- Fingerprintstabilität,
- keine Mention-Syntax in der Ausgabe.

## Paket 7 – Persistente Report-Delivery

### Ziel

Eine vollständige logische Reportausgabe wird über PostgreSQL duplikatsicher und crash-fortsetzbar ausgeliefert.

### Umfang

- Liquibase-Migration,
- fachlicher Unique Key,
- Delivery-Zustände einschließlich `NO_OP` und Ablauf,
- tokengebundener Claim und Lease,
- Retry und Backoff,
- permanente Fehlerkategorien,
- Inhalts-Fingerprint,
- geordnete Discord-Message-IDs,
- Publisher-Port für eine geordnete Mehrseiten-Ausgabe,
- Reconciliation unklarer und teilweise erfolgreicher Ausgänge,
- Duplikatbereinigung,
- keine Discord-I/O in Datenbanktransaktionen.

### Tests

- Erstveröffentlichung,
- Replay und Konkurrenz,
- Absturz vor, zwischen und nach Seitenauslieferungen,
- abgelaufene Lease,
- retryfähiger und permanenter Fehler,
- extern gelöschte Seite innerhalb und außerhalb zulässiger Reparatur,
- stabile NO_OP-Delivery,
- PostgreSQL-Constraints und vollständiger Spring-Start.

## Paket 8 – Wochenbericht und Scheduler

### Ziel

Der Wochenbericht wird zur konfigurierten Zeit und innerhalb seines Catch-up-Fensters ausgeliefert.

### Umfang

- Montag `WEEKLY_REPORT_TIME`,
- vorheriger Montag bis Sonntag,
- 72-Stunden-Catch-up,
- Startup- und Tick-Reconciliation,
- pro Start höchstens jüngste relevante Wochenperiode,
- ältere versäumte Wochen persistent ablaufen lassen,
- externen Löschfall nur im Catch-up-Fenster reparieren,
- keine automatischen Edits nach erfolgreicher Veröffentlichung.

### Tests

- normale Fälligkeit,
- Start vor und nach Fälligkeit,
- Catch-up innerhalb und exakt außerhalb der Grenze,
- mehrere versäumte Wochen,
- Neustart und parallele Schedulerinstanzen,
- DST-Wechsel,
- NO_OP und Report ohne Ergebnisse.

## Paket 9 – Monatsbericht und Scheduler

### Ziel

Der Monatsbericht verwendet vollständig denselben Reporting- und Deliverykern mit eigener Perioden- und Catch-up-Regel.

### Umfang

- erster Kalendertag `MONTHLY_REPORT_TIME`,
- vollständig abgeschlossener Vormonat,
- sieben Tage Catch-up,
- Startup- und Tick-Reconciliation,
- pro Start höchstens jüngste relevante Monatsperiode,
- Monats- und Jahreswechsel,
- gleichzeitige Fälligkeit mit Wochenbericht bleibt unabhängig.

### Tests

- Monate mit 28, 29, 30 und 31 Tagen,
- Jahreswechsel,
- Catch-up-Grenzen,
- gleichzeitige Wochen- und Monatsfälligkeit,
- keine duplizierte Deliverylogik.

## Paket 10 – Gesamtintegration und Produktionsfreigabe

### Umfang

- vollständige Dokumentation aktualisieren,
- Standardbuild,
- PostgreSQL-Integration,
- Architektur- und Regressionstests,
- Scheduler-, Konkurrenz-, Crash- und Recoveryvollpfad,
- Produktionsimage bauen und prüfen,
- realen Wochenbericht in Discord veröffentlichen,
- realen Monatsbericht in Discord veröffentlichen,
- Message-IDs und Duplikatschutz prüfen,
- produktive Standardzeiten wiederherstellen,
- kontrolliertes Produktionsdeployment.

### Abschlussdefinition

Der Draft-Status wird erst aufgehoben, wenn:

- alle Pakete umgesetzt und reviewt sind,
- beide Maven-Vollbuilds grün sind,
- CI und Containerworkflow grün sind,
- keine Secrets enthalten sind,
- die reale Discord-Darstellung beider Berichtstypen geprüft wurde,
- bestehende Ergebnis-, Status-, Serien-, Reminder- und Produktionsfunktionen regressionsfrei sind.
