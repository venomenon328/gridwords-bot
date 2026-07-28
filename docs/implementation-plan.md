# Implementierungsplan

Dieser Plan zerlegt die Anforderungsspezifikation in kleine, reviewbare Inkremente. Er beschreibt Reihenfolge und Grenzen, nicht zwingend die endgültige Anzahl der Pull Requests. Für Serien, Tagesmerkmale, Status und Berichte gilt zusätzlich verbindlich `docs/requirements/series-model.md`.

## Leitprinzipien

- Erst stabiler Build, dann Fachlogik.
- Parser und Regeln zunächst ohne Discord und Datenbank entwickeln.
- Persistenz und Idempotenz vor dem automatischen Löschen fremder Nachrichten fertigstellen.
- Eine Originalnachricht wird erst gelöscht, wenn der vollständige sichere Ersetzungsablauf automatisiert getestet ist.
- Version-2- und Version-3-Funktionen werden nicht vorgezogen.

## Inkrement 0 – Grundgerüst stabilisieren

**Ziel:** Reproduzierbarer grüner Build und zuverlässige lokale Konfiguration.

Umfang:

- reale, stabile und kompatible Dependency-Versionen
- `mvn clean verify` ohne Discord und lokale Datenbank
- Discord standardmäßig deaktiviert
- verständlicher Fehler bei aktiviertem Discord ohne Token
- funktionierender plattformtauglicher lokaler Secret-/Konfigurationsweg
- CI grün
- keine Fachlogik

Aktueller Auftrag: GitHub-Issue #2.

Abschlussbedingung:

- Offline-Build grün
- danach manueller Discord-Smoke-Test durch Tobias
- PR #1 erst nach Review und erforderlichem Smoke-Test mergen

## Inkrement 1 – Reine Share-Textparser

**Ziel:** GridWords- und QuadWords-Kopfzeilen sowie GridWords-Raster deterministisch parsen.

Umfang:

- reine Domain-/Parser-Typen
- `ParseResult` mit `NotApplicable`, `Parsed`, `Invalid`
- deutsche Monatsnamen und bestätigte Share-Formate
- gelöst: numerischer Wert
- nicht gelöst: `X/6` beziehungsweise `X/9`
- Dauer und optionale Gridgames-Flamme
- GridWords-Unicode-Raster validieren und normalisieren
- QuadWords-Anhangsanforderung als Metadatenprüfung, noch ohne Bildanalyse
- Fixture-basierte Tests
- keine Datenbank
- kein JDA-Listener

Abnahmekriterium:

- alle vorhandenen echten Fixtures und definierte Fehlerfälle werden korrekt klassifiziert

## Inkrement 2 – Persistenzmodell und Verarbeitungszustände

**Ziel:** Ergebnisse und Submission-Ablauf idempotent speichern.

Umfang:

- erste Liquibase-Migrationen
- Tabellen/Constraints für Spieler, Ergebnis, Submission und Tagesstatus-Grundlage
- Persistence-Ports und PostgreSQL-Adapter
- `player + game type + game date` eindeutig
- Quell-Message-ID eindeutig
- persistierte Zustände des Ersetzungsablaufs
- Testcontainers-Integrationstests
- keine Discord-Nachrichten löschen oder veröffentlichen

Abnahmekriterium:

- doppeltes Event beziehungsweise konkurrierender Upsert erzeugt keinen doppelten fachlichen Datensatz

## Inkrement 3 – Discord-Inbound im Beobachtungsmodus

**Ziel:** Echte Channelnachrichten sicher filtern und verarbeiten, ohne sie zu verändern.

Umfang:

- dünner JDA-Listener
- Filter für Server, Channel, Nutzer, Bots und Webhooks
- internes unveränderliches Eingabe-DTO
- begrenzter Application Executor
- Parse + Persistenz
- `✅` bei gültigem Ergebnis, `⚠️` bei eindeutig ungültigem Share-Format
- kein Löschen
- keine kanonische Wiederveröffentlichung
- Adaptertests ohne echte Netzwerkverbindung
- manueller Test im Discord-Testchannel

Abnahmekriterium:

- echte Ergebnisse beider Nutzer werden gespeichert; andere Nachrichten und Channels bleiben vollständig unbeachtet

## Inkrement 4 – Kanonische GridWords-Nachricht, zunächst ohne Löschung

**Ziel:** GridWords-Ausgabe vollständig erzeugen und veröffentlichen.

Umfang:

- transportneutrales `CanonicalResultMessage`
- Discord-Embed-/Textadapter
- GridWords ohne Gridgames-Link schön darstellen
- persönliche Aktivitätsserie und GridWords-Lösungsserie aus vorhandenen Daten berechnen
- Komplett- und Perfektserie ergänzen, wenn das Ergebnis den betreffenden Tageszustand herstellt
- keine unspezifische persönliche „Spielserie“ oder „Lösungsserie“ ausgeben
- Bot-Message-ID persistieren
- Retry und Idempotenz der Veröffentlichung
- Original bleibt weiterhin bestehen

Abnahmekriterium:

- wiederholte Verarbeitung erzeugt keine zweite kanonische Nachricht; die angezeigten Serien entsprechen `docs/requirements/series-model.md`

## Inkrement 5 – Sichere GridWords-Ersetzung

**Ziel:** Original-GridWords-Nachricht erst nach nachweislich sicherer Wiederveröffentlichung löschen.

Voraussetzungen:

- automatisierte Tests für alle Zustandsübergänge aus ADR 0002
- erfolgreicher manueller Test im Testchannel

Umfang:

- Original löschen, nachdem Bot-Message-ID persistiert ist
- Neustart-/Retry-Fälle
- Delete-Event des Bots ignorieren
- Korrektur durch erneute Einreichung
- klare Fehlerzustände

Abnahmekriterium:

- Publish- oder Persistenzfehler können niemals zum Verlust der Originalnachricht führen

## Inkrement 6 – Tagesstatus, vollständige Serien und Erinnerungen

**Ziel:** Version-1-Kernnutzen vollständig herstellen.

Umfang:

- persönliche Aktivitätsserie
- persönliche Komplettserie
- persönliche GridWords-Lösungsserie
- persönliche QuadWords-Lösungsserie
- persönliche Perfektserie
- gemeinsame Komplettserie
- gemeinsame Perfektserie
- ausdrücklich keine gemeinsame Aktivitätsserie
- eine Tagesstatusnachricht pro Spieltag mit eindeutig benannten Serien
- erste Erinnerung 18:00 Uhr
- zweite Erinnerung 23:00 Uhr
- nur fehlende Einreichungen erwähnen
- persistierte Reminder-Auslieferung
- Nachholen nach Neustart
- `Europe/Berlin` und feste `Clock` in Tests
- aktueller Tag und Vortag als Nachtragsfenster
- separate Behandlung des noch unvollständigen aktuellen Tags je Serienbedingung

Abnahmekriterium:

- keine doppelten Erinnerungen, korrekte Erwähnungen und korrekte Berechnung aller sieben Serien über Lücken, Teilaktivität, nicht gelöste Spiele, unvollständige heutige Tage und Vortagsnachträge hinweg

## Inkrement 7 – Version 1 härten und veröffentlichen

**Ziel:** Version 1 produktionsreif machen.

Umfang:

- Ende-zu-Ende-Test im Testserver
- Fehlertexte und Logs
- Betriebsdokumentation
- Backup-/Restore-Hinweise
- Containerimage oder reproduzierbarer Deploymentweg
- Hostingentscheidung
- Migration in endgültigen Channel

Nicht enthalten:

- QuadWords-Bildparser
- Berichte
- Statistik-Commands
- Kommentare

## Inkrement 8 – QuadWords-Bildparser

**Ziel:** Vier Grids robust geometrisch und farbbasiert normalisieren.

Umfang:

- Originalbild-Fixtures
- reine Java-Bildverarbeitung mit `ImageIO`/`BufferedImage`
- Board-Gruppierung
- Überschriften `Oben links`, `Oben rechts`, `Unten links`, `Unten rechts`
- Konfidenz-/Strukturvalidierung
- Parser-Versionierung
- Rohbildaufbewahrung 48 Stunden
- keine Löschung bei unsicherem Parse

Abnahmekriterium:

- alle freigegebenen Originalfixtures exakt korrekt; beschädigte oder unbekannte Bilder brechen kontrolliert ab

## Inkrement 9 – Sichere QuadWords-Ersetzung

Analog zur GridWords-Ersetzung:

- kanonische Unicode-Ausgabe
- Aktivitätsserie und QuadWords-Lösungsserie ausgeben
- bei Tagesabschluss gegebenenfalls Komplett- und Perfektserie ergänzen
- Bot-Message-ID persistieren
- erst danach Original und Bildnachricht löschen
- Retry-/Neustartpfade

## Inkrement 10 – Wochen- und Monatsberichte

**Ziel:** Version 2 abschließen.

Umfang:

- Wochenbericht Montag 08:00 Uhr
- Monatsbericht Monatserster 08:15 Uhr
- persistierte Delivery-Idempotenz
- pro Spieler Aktivitäts-, Komplett- und perfekte Tage
- aktuelle und längste persönliche Aktivitäts-, Komplett-, GridWords-Lösungs-, QuadWords-Lösungs- und Perfektserie
- gemeinsam komplette und gemeinsam perfekte Tage
- aktuelle und längste gemeinsame Komplett- und Perfektserie
- bisher vorgesehene spielbezogene Versuchs-, Lösungs- und Zeitkennzahlen
- keine Gewinnerlogik

## Inkrement 11 – Statistik- und Konfigurations-Commands

**Ziel:** Version-3-Bedienung.

Umfang:

- Statistik-Slash-Commands
- eindeutige Auswahl der Serienarten `activity`, `complete`, `gridwords-solved`, `quadwords-solved`, `perfect`, `shared-complete`, `shared-perfect`
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

## Was nicht parallelisiert werden sollte

Folgende Abhängigkeiten sind bewusst sequenziell:

- Persistenzzustände vor automatischer Löschung
- GridWords-Ersetzung vor QuadWords-Ersetzung
- robuste Serienlogik vor Berichten und Kommentaren
- stabiler Scheduler vor dynamischer Slash-Command-Konfiguration

Parser-Fixtures und Dokumentation können parallel gesammelt werden.

## Definition of Done pro Inkrement

- Issue-Abnahmekriterien erfüllt
- `mvn clean verify` erfolgreich
- GitHub Actions grün
- keine Secrets
- relevante Dokumentation aktualisiert
- notwendige manuelle Discord-Prüfung dokumentiert
- PR reviewbar und ohne unangeforderten Versionsumfang