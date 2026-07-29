# Implementierungsplan

Dieser Plan zerlegt die Anforderungsspezifikation in kleine, reviewbare Inkremente. Für Serien, Tagesmerkmale, Status und Berichte gilt zusätzlich verbindlich `docs/requirements/series-model.md`. Für lokale Infrastruktur und Datenbanktests gilt `docs/adr/0010-docker-available-local-development.md`; ADR 0004 dokumentiert den historischen Ausgangspunkt.

## Leitprinzipien

- Erst stabiler Build, dann Fachlogik.
- Parser und Regeln zunächst ohne Discord und Datenbank entwickeln.
- Der schnelle Standardbuild bleibt ohne Docker, PostgreSQL und Discord-Token ausführbar.
- Docker Compose ist die bevorzugte lokale PostgreSQL-Umgebung.
- Bei Persistenz-, Liquibase-, Claim-, Recovery- oder PostgreSQL-spezifischen Änderungen läuft das Datenbankintegrationsprofil lokal mit Docker und zusätzlich verpflichtend in GitHub Actions.
- Persistenz und Idempotenz werden vor dem automatischen Löschen fremder Nachrichten fertiggestellt.
- Eine Originalnachricht wird erst gelöscht, wenn der vollständige sichere Ersetzungsablauf automatisiert getestet ist.
- QuadWords-Bildparser und sichere QuadWords-Konsolidierung werden bewusst vor Tagesstatus und Erinnerungen umgesetzt.

## Inkrement 0 – Grundgerüst stabilisieren

**Status:** abgeschlossen (PR #1 gemergt)

**Ziel:** reproduzierbarer grüner Build und zuverlässige lokale Konfiguration.

Umgesetzt:

- Java-21-/Spring-Boot-/JDA-Grundgerüst
- stabiler infrastrukturloser Standardbuild
- Discord standardmäßig deaktiviert
- verständlicher Fehler bei aktiviertem Discord ohne Token
- lokaler Secret-/Konfigurationsweg
- GitHub Actions grün
- realer Discord-Gateway-Smoke-Test

## Inkrement 1 – Reine Share-Textparser

**Status:** abgeschlossen (PR #4 gemergt)

**Ziel:** GridWords- und QuadWords-Kopfzeilen sowie GridWords-Raster deterministisch parsen.

Umgesetzt:

- reine Domain-/Parser-Typen
- `ParseResult` mit `NotApplicable`, `Parsed`, `Invalid`
- deutsche Monatsnamen und bestätigte Share-Formate
- gelöst und nicht gelöst
- Dauer und optionale Gridgames-Flamme
- GridWords-Unicode-Raster validieren und normalisieren
- QuadWords-Anhangsanforderung als Metadatenprüfung
- Fixture-basierte Tests

## Inkrement 2 – Persistenzmodell und Verarbeitungszustände

**Status:** abgeschlossen (PR #6 gemergt)

**Ziel:** Ergebnisse und Submission-Ablauf idempotent speichern.

Umgesetzt:

- Liquibase-Migrationen
- Tabellen und Constraints für Spieler, Ergebnis und Submission
- PostgreSQL-Adapter und Ports
- Eindeutigkeit für Spieler, Spieltyp und Spieltag
- eindeutige Quell-Message-ID
- persistierte Zustände des Ersetzungsablaufs
- separates Maven-Profil `database-integration`
- PostgreSQL-Integration in GitHub Actions

## Inkrement 3 – Discord-Inbound im Beobachtungsmodus

**Status:** abgeschlossen (PR #8 gemergt)

**Ziel:** echte Channelnachrichten sicher filtern und verarbeiten, ohne sie zu verändern.

Umgesetzt:

- dünner JDA-Listener
- Filter für Server, Channel, Nutzer, Bots und Webhooks
- internes unveränderliches Eingabe-DTO
- begrenzter Application Executor
- Parse und Persistenz
- `✅` bei gültigem Ergebnis, `⚠️` bei eindeutig ungültigem Share
- Adaptertests ohne echte Netzwerkverbindung
- manueller Discord-/PostgreSQL-Test

## Inkrement 4 – Kanonische GridWords-Nachricht ohne Löschung

**Status:** abgeschlossen (PR #10 gemergt; Smoke-Test am 29. Juli 2026 erfolgreich)

**Ziel:** GridWords-Ausgabe vollständig erzeugen und veröffentlichen.

Umgesetzt:

- transportneutrales `CanonicalResultMessage`
- Discord-Embed-Adapter
- vollständiges GridWords-Raster
- Aktivitäts- und GridWords-Lösungsserie
- kontextabhängige Komplett- und Perfektserie
- persistierte Bot-Message-ID
- Retry und Idempotenz
- Korrektur durch Edit derselben Bot-Nachricht
- Lost-Message-Recovery, Supersession, Delivery-Fences und Duplikatbereinigung
- unsichtbarer technischer Publication-Key

## Inkrement 5 – Sichere GridWords-Ersetzung

**Status:** abgeschlossen (Issue #11, PR #12; vollständiger Discord-/PostgreSQL-Smoke-Test am 30. Juli 2026 erfolgreich)

**Ziel:** Original-GridWords-Nachricht erst nach nachweislich sicherer kanonischer Veröffentlichung löschen.

Umgesetzt:

- Quelllöschung ausschließlich nach persistierter kanonischer Bot-Message-ID
- getrennte, persistierte und wiederaufnehmbare Löschphase
- Zustände `CANONICAL_MESSAGE_PUBLISHED`, `ORIGINAL_MESSAGE_DELETED`, `COMPLETED`
- token- und lease-geschützte Claims
- Neustart-, Retry-, Replay-, Konkurrenz- und Crash-Recovery
- `UNKNOWN_MESSAGE` als idempotenter Erfolg
- transiente und permanente Discord-Fehler
- synchrone JDA-Berechtigungsfehler als permanent
- automatischer Wake-up nach aktiver Lease
- Handoff nach erfolgreichem asynchronem Publish-Retry
- Korrektur durch Edit derselben kanonischen Nachricht
- gezielte Bereinigung älterer, nun sicher löschbarer supersedierter Quellen
- keine GridWords-`✅`-Reaktion auf eine verschwindende Quelle
- unverändertes QuadWords- und Ablehnungsverhalten
- lokaler Standardbuild, lokales PostgreSQL-Integrationsprofil und GitHub Actions grün

Abnahme bestätigt:

- kein Fehler vor der persistierten kanonischen Veröffentlichung kann das Original löschen
- sichere Quellen werden konfliktfest, idempotent und nach Neustart abgeschlossen
- fehlende Löschberechtigung lässt das Original sichtbar und stuft die Veröffentlichung nicht zurück
- nach Wiederherstellung der Berechtigung werden aktuelle und ältere festhängende Quellen kontrolliert bereinigt
- keine offenen Claims, Leases oder Delivery-Attempts nach abgeschlossenem Smoke-Test

## Inkrement 6 – QuadWords-Bildparser

**Status:** als nächstes geplant

**Ziel:** vier QuadWords-Grids robust geometrisch und farbbasiert normalisieren.

Umfang:

- freigegebene Originalbild-Fixtures
- Attachment-Bytes hinter einem schmalen Port laden; keine JDA-Typen im Parser
- reine Java-Bildverarbeitung mit `ImageIO` und `BufferedImage`
- Erkennung der vier Boards
- kanonische Reihenfolge `Oben links`, `Oben rechts`, `Unten links`, `Unten rechts`
- robuste Farbklassifikation
- Normalisierung in eine transportneutrale Grid-Darstellung
- Konfidenz-, Struktur- und Plausibilitätsvalidierung
- Parser-Versionierung
- kontrollierte Größen-, Format- und Ressourcenlimits
- Rohbildaufbewahrung für höchstens 48 Stunden vorbereiten beziehungsweise implementieren, soweit für den Parser erforderlich
- keine kanonische Veröffentlichung oder Löschung in diesem Inkrement
- unsicherer Parse lässt die Originalnachricht unangetastet
- Fixture-Tests vollständig ohne echte Discord-Verbindung

Abnahmekriterien:

- alle freigegebenen Originalfixtures werden exakt korrekt normalisiert
- beschädigte, unbekannte, zu große oder nicht ausreichend sichere Bilder brechen kontrolliert ab
- keine JDA-Typen im fachlichen Parser
- keine Veröffentlichung und keine Quelllöschung
- Standardbuild grün
- bei Persistenzänderungen zusätzlich lokales Datenbankintegrationsprofil mit Docker und CI grün

## Inkrement 7 – Kanonische QuadWords-Konsolidierung und sichere Ersetzung

**Ziel:** QuadWords-Text und Bild in genau eine kanonische, korrigierbare Bot-Nachricht überführen und die Quelle erst danach sicher löschen.

Umfang:

- kanonische Ausgabe aller vier Grids
- Spieler, Datum, Ergebnis, Versuche und Dauer
- Aktivitäts- und QuadWords-Lösungsserie
- kontextabhängige Komplett- und Perfektserie
- persistierte Bot-Message-ID
- Korrekturen bearbeiten dieselbe kanonische Nachricht
- Publication-Key, Recovery, Supersession und Duplikatbereinigung analog zu GridWords
- Originalnachricht mit Bildanhang erst nach persistierter kanonischer Veröffentlichung löschen
- sichere Lösch-, Retry-, Neustart- und Crashpfade analog zu Inkrement 5
- unsicher oder fehlerhaft geparste Bilder niemals löschen
- Rohbild nach der festgelegten Aufbewahrungsfrist entfernen

Abnahmekriterien:

- pro Spieler und QuadWords-Spieltag höchstens eine kanonische Bot-Nachricht
- gültige Quelle erst nach sicherer Konsolidierung löschen
- Parse-, Publish- oder Persistenzfehler können das Originalbild niemals verlieren lassen

## Inkrement 8 – Tagesstatus, vollständige Serien und Erinnerungen

**Ziel:** täglichen Kernnutzen nach sicherer Konsolidierung beider Spiele vollständig herstellen.

Umfang:

- persönliche Aktivitätsserie
- persönliche Komplettserie
- persönliche GridWords-Lösungsserie
- persönliche QuadWords-Lösungsserie
- persönliche Perfektserie
- gemeinsame Komplettserie
- gemeinsame Perfektserie
- ausdrücklich keine gemeinsame Aktivitätsserie
- eine Tagesstatusnachricht pro Spieltag
- Erinnerungen um 18:00 und 23:00 Uhr
- nur fehlende Einreichungen erwähnen
- persistierte Reminder-Auslieferung
- Nachholen nach Neustart
- `Europe/Berlin` und feste `Clock` in Tests
- heute und gestern als Nachtragsfenster

Abnahmekriterium:

- keine doppelten Erinnerungen und korrekte Berechnung aller sieben Serien über Lücken, Teilaktivität, nicht gelöste Spiele und Vortagsnachträge

## Inkrement 9 – Kernversion härten und veröffentlichen

**Ziel:** den bis dahin vollständigen Bot produktionsreif machen.

Umfang:

- Ende-zu-Ende-Test im Testserver
- Fehlertexte und Logs
- Betriebsdokumentation
- Backup-/Restore-Hinweise
- reproduzierbarer Deploymentweg
- Hostingentscheidung
- Migration in den endgültigen Channel

Nicht enthalten:

- Wochen- und Monatsberichte
- Statistik-Commands
- Kommentare

## Inkrement 10 – Wochen- und Monatsberichte

**Ziel:** Version 2 abschließen.

Umfang:

- Wochenbericht Montag 08:00 Uhr
- Monatsbericht am Monatsersten 08:15 Uhr
- persistierte Delivery-Idempotenz
- persönliche Aktivitäts-, Komplett-, Lösungs- und Perfektmetriken
- gemeinsame Komplett- und Perfektmetriken
- aktuelle und längste Serien
- keine Gewinnerlogik

## Inkrement 11 – Statistik- und Konfigurations-Commands

**Ziel:** Version-3-Bedienung.

Umfang:

- Statistik-Slash-Commands
- eindeutige Auswahl der sieben Serienarten
- Zeiteinstellungen per Slash-Command
- Autorisierung nur für konfigurierte Admins
- persistente oder klar definierte Konfigurationsquelle
- Scheduler-Neuplanung ohne Neustart

## Inkrement 12 – Regelbasierte Kommentare

**Ziel:** Version 3 vervollständigen.

Umfang:

- regelbasierte Kategorien und Textvarianten
- Auslöser für eindeutig benannte persönliche und gemeinsame Serien
- perfekte und gemeinsam perfekte Tage als mögliche Auslöser
- definierte Nachrichtenlimits
- keine generative KI
- vollständig testbare Auswahl- und Auslöselogik

## Bewusste Reihenfolge

Nicht parallelisieren:

- Persistenzzustände vor automatischer Löschung
- sichere GridWords-Ersetzung vor sicherer QuadWords-Ersetzung
- QuadWords-Bildparser vor kanonischer QuadWords-Konsolidierung
- sichere Konsolidierung beider Spiele vor Tagesstatus und Erinnerungen
- robuste Serienlogik vor Berichten und Kommentaren
- stabiler Scheduler vor dynamischer Slash-Command-Konfiguration

Parser-Fixtures und Dokumentation können parallel gesammelt werden.

## Definition of Done pro Inkrement

- Issue-Abnahmekriterien erfüllt
- lokaler Standardbuild erfolgreich
- bei Persistenzumfang lokales Datenbankintegrationsprofil mit Docker erfolgreich
- GitHub Actions vollständig grün
- keine Secrets
- relevante Dokumentation aktualisiert
- notwendige manuelle Discord- oder Persistenzprüfung durchgeführt beziehungsweise klar dokumentiert
- PR reviewbar und ohne unangeforderten Versionsumfang
