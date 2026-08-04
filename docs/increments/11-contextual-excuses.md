# Inkrement 11: Kontextabhängige Ausreden

**Status:** abgeschlossen  
**Issue:** #42 – geschlossen  
**Kumulativer PR:** #46 – nach `main` gemergt  
**Produktionsrelease:** 1.2.0 – veröffentlicht und ausgerollt  
**Abschlussdatum:** 4. August 2026

## Ziel

Bei klar definierten auffälligen GridWords- und QuadWords-Ergebnissen kann der Ergebnisautor freiwillig eine Ausrede auswählen.

Der Bot zeigt privat drei redaktionelle Vorschläge. Der Nutzer kann einmal Vorschläge eines anderen Stils anfordern, eine Ausrede wählen oder verzichten. Öffentlich erscheint ausschließlich der gewählte Text am Ende derselben kanonischen Ergebnisnachricht.

## Umgesetzter Umfang

Inkrement 11 umfasst:

- versionierten und vollständig validierten redaktionellen Katalog ohne Laufzeit-KI,
- klare Ergebnis-, Zeit-, Board- und Tagesausreißerregeln,
- spielbezogenen Cooldown,
- persistierten Zustand je Ergebnis,
- persistierte tatsächlich gezeigte Optionen,
- spielübergreifenden Wiederholungsschutz für gewählte Templates,
- öffentlichen Button in der kanonischen Ergebnisnachricht,
- ausschließlich ephemere Vorschläge und Stilwahl,
- genau einen Stilwechsel,
- Auswahl, Verzicht, Ablauf und Korrekturrevalidierung,
- Boardanreicherung und sichere Ablehnung veralteter Interaktionen,
- Aktualisierung ausschließlich über die kanonische Refresh- und Recovery-Pipeline,
- produktiven Katalog `2026.08.04.1` mit 564 auswählbaren Templates,
- vollständige Unit-, Adapter-, Architektur- und PostgreSQL-Abdeckung,
- reale Discord-Abnahme und produktiven Rollout.

## Paketabschluss

- [x] Paket 0 – Requirement, ADR, Inkrementplan, Roadmap und Arbeitsregeln
- [x] Paket 1 – Ausredendomäne, Katalogmechanik, Validierung und Zufallsquelle
- [x] Paket 2 – Angebotsgründe, Kontextanalyse, Boardanalyse und Tagesausreißer
- [x] Paket 3 – Liquibase-Schema, PostgreSQL-Zustand, Optionen und Verlauf
- [x] Paket 4 – Einmalige Ergebnisintegration, Korrekturrevalidierung und kanonische Projektion
- [x] Paket 5 – Kanonische Komponenten, Codec und autorisierter Öffnungsflow
- [x] Paket 6 – Vorschläge, Stilwechsel, Auswahl, Verzicht und Refresh-Handoff
- [x] Paket 7 – Ablauf, Recovery, Konkurrenz, Boardanreicherung und Wiederholungsschutz
- [x] Paket 8A – Redaktioneller Vollkatalog
- [x] Paket 8B – Gesamtintegration, reale Abnahme und Abschluss

## Abnahme

Bestanden wurden:

- Standardbuild mit 582 Tests,
- vollständiges PostgreSQL-Profil,
- Produktionsimage- und Nicht-Root-Prüfung,
- Compose-Konfigurationsprüfung,
- Backup-, Restore-, Resume- und Rollbackpfad,
- reale Discord-Abnahme mit separater Testanwendung und isolierter PostgreSQL-Datenbank,
- Produktivdeployment des unveränderlichen SHA-Images.

Das vollständige Protokoll steht unter:

- `docs/operations/11-contextual-excuses-acceptance.md`

## Historischer Paketplan

Der während der Umsetzung verbindliche detaillierte Paket-, Test- und Abnahmeplan bleibt zu Dokumentationszwecken erhalten:

- `docs/increments/archive/11-contextual-excuses-package-plan.md`

Die vorliegende Datei beschreibt den endgültigen abgenommenen Zustand und ist für den aktuellen Projektstatus maßgeblich.

## Verbindliche Dokumente

- `docs/requirements/excuses.md`
- `docs/requirements/game-specific-participation.md`
- `docs/adr/0017-persistent-excuse-selection.md`
- `docs/architecture.md`
- `docs/operations/11-contextual-excuses-acceptance.md`

## Abgrenzung

Nicht Bestandteil von Inkrement 11 sind insbesondere:

- generative KI oder externe Textdienste,
- ein Ausreden-Command,
- öffentliche Vorschlagslisten,
- Bearbeiten oder Zurückziehen einer gewählten Ausrede,
- öffentliche Stilbezeichnungen,
- ausredenbezogene Achievements oder Rückblicke,
- ein allgemeines Plugin- oder Event-Framework.

Spätere Unterhaltungsfeatures können die vorhandenen kanonischen Integrationsgrenzen verwenden, benötigen aber ein eigenes priorisiertes Issue und eine neue fachliche Spezifikation.
