# Implementierungsplan

Dieser Plan zerlegt die Anforderungsspezifikation in kleine, reviewbare Inkremente. Er beschreibt Reihenfolge und Grenzen, nicht zwingend die endgültige Anzahl der Pull Requests. Für Serien, Tagesmerkmale, Status und Berichte gilt zusätzlich verbindlich `docs/requirements/series-model.md`. Für lokale Infrastruktur und Datenbanktests gilt `docs/adr/0004-docker-optional-local-development.md`.

## Leitprinzipien

- Erst stabiler Build, dann Fachlogik.
- Parser und Regeln zunächst ohne Discord und Datenbank entwickeln.
- Der lokale Standardbuild bleibt ohne Docker, PostgreSQL und Discord-Token ausführbar.
- PostgreSQL-Integrationstests laufen verpflichtend in GitHub Actions über ein separates Maven-Profil.
- Persistenz und Idempotenz vor dem automatischen Löschen fremder Nachrichten fertigstellen.
- Eine Originalnachricht wird erst gelöscht, wenn der vollständige sichere Ersetzungsablauf automatisiert getestet ist.
- QuadWords-Bildparser und sichere QuadWords-Konsolidierung werden bewusst vor Tagesstatus und Erinnerungen umgesetzt; andere spätere Berichts- und Version-3-Funktionen werden nicht vorgezogen.

## Inkrement 0 – Grundgerüst stabilisieren

**Status:** abgeschlossen (PR #1 gemergt)

**Ziel:** Reproduzierbarer grüner Build und zuverlässige lokale Konfiguration.

Umgesetzt:

- reale, stabile und kompatible Dependency-Versionen
- `mvn clean verify` ohne Discord, lokale Datenbank und Container-Runtime
- Discord standardmäßig deaktiviert
- verständlicher Fehler bei aktiviertem Discord ohne Token
- funktionierender plattformtauglicher lokaler Secret-/Konfigurationsweg
- GitHub Actions grün
- erfolgreicher realer Discord-Gateway-Smoke-Test am 29. Juli 2026 ohne Docker und PostgreSQL
- keine Fachlogik

Zugehöriger Auftrag: GitHub-Issue #2.

Abschlussbedingung erfüllt:

- Offline-Build grün
- Discord-Smoke-Test durch Tobias erfolgreich
- PR #1 wurde gemergt

## Inkrement 1 – Reine Share-Textparser

**Status:** abgeschlossen (PR #4 gemergt)

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
- Architekturtests für die ersten fachlichen Pakete
- keine Datenbank
- kein JDA-Listener
- vollständig lokal ohne Docker ausführbar

Abnahmekriterium:

- alle vorhandenen echten Fixtures und definierten Fehlerfälle werden korrekt klassifiziert
- lokaler `mvn clean verify` benötigt keine externe Infrastruktur

## Inkrement 2 – Persistenzmodell und Verarbeitungszustände

**Status:** abgeschlossen (PR #6 gemergt)

**Ziel:** Ergebnisse und Submission-Ablauf idempotent speichern.

Umfang:

- erste Liquibase-Migrationen
- Tabellen/Constraints für Spieler, Ergebnis, Submission und Tagesstatus-Grundlage
- Persistence-Ports und PostgreSQL-Adapter
- `player + game type + game date` eindeutig
- Quell-Message-ID eindeutig
- persistierte Zustände des Ersetzungsablaufs
- PostgreSQL-Integrationstests gegen echtes PostgreSQL
- separates Maven-Profil `database-integration`
- vollständige Ausführung dieses Profils in GitHub Actions
- lokaler Standardbuild weiterhin ohne Container-Runtime
- optionaler manueller lokaler Start gegen nativ installiertes PostgreSQL
- keine Discord-Nachrichten löschen oder veröffentlichen

Abnahmekriterium:

- doppeltes Event beziehungsweise konkurrierender Upsert erzeugt keinen doppelten fachlichen Datensatz
- Liquibase, Constraints und Konfliktverhalten sind in CI gegen echtes PostgreSQL getestet
- die Datenbanktests werden in CI nachweislich ausgeführt und nicht nur übersprungen

## Inkrement 3 – Discord-Inbound im Beobachtungsmodus

**Status:** abgeschlossen (PR #8 gemergt)

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
- manueller Persistenztest wahlweise gegen natives PostgreSQL; Docker ist nicht erforderlich
- dokumentierter manueller Smoke-Test für Tobias vor dem Merge

Abnahmekriterium:

- echte Ergebnisse beider Nutzer werden gespeichert; andere Nachrichten und Channels bleiben vollständig unbeachtet

## Inkrement 4 – Kanonische GridWords-Nachricht, zunächst ohne Löschung

**Status:** abgeschlossen (PR #10 gemergt; manueller Discord-/PostgreSQL-Smoke-Test am 29. Juli 2026 erfolgreich)

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
- Korrekturen bearbeiten dieselbe kanonische Bot-Nachricht
- Recovery, Supersession, Delivery-Fences und Duplikatbereinigung sichern den Publish-Ablauf
- technische Publication-Keys bleiben für Recovery erhalten, werden aber nicht sichtbar dargestellt

Abnahmekriterium erfüllt:

- wiederholte Verarbeitung erzeugt keine zweite kanonische Nachricht
- die angezeigten Serien entsprechen `docs/requirements/series-model.md`
- der echte Test im Zielchannel bestätigte Veröffentlichung, Korrektur und unveränderte Originalnachrichten

## Inkrement 5 – Sichere GridWords-Ersetzung

**Status:** vorbereitet (Issue #11; Branch `feature/safe-gridwords-replacement`)

**Ziel:** Original-GridWords-Nachricht erst nach nachweislich sicherer Wiederveröffentlichung löschen.

Voraussetzungen:

- automatisierte Tests für alle Zustandsübergänge aus ADR 0002
- erfolgreicher manueller Test im Testchannel

Umfang:

- Original ausschließlich nach persistierter kanonischer Bot-Message-ID löschen
- persistierte und wiederaufnehmbare Löschphase
- Zustände `CANONICAL_MESSAGE_PUBLISHED`, `ORIGINAL_MESSAGE_DELETED`, `COMPLETED`
- Neustart-, Retry-, Replay- und Konkurrenzfälle
- `UNKNOWN_MESSAGE` als idempotenter Lösch-Erfolg
- permanente und transiente Löschfehler unterscheiden
- Delete-Events dürfen keine neue Verarbeitung auslösen
- Korrektur durch erneute Einreichung
- supersedierte sichtbare gültige Quellen erst nach bestätigter aktueller Veröffentlichung bereinigen
- GridWords-Erfolg nicht von einer Reaktion auf eine anschließend gelöschte Nachricht abhängig machen
- QuadWords und Ablehnungen unverändert lassen

Abnahmekriterium:

- Publish- oder Persistenzfehler können niemals zum Verlust der Originalnachricht führen
- erfolgreich ersetzte Quellen werden konfliktfest, idempotent und nach Neustart abschließbar gelöscht

## Inkrement 6 – QuadWords-Bildparser

**Ziel:** Vier QuadWords-Grids robust geometrisch und farbbasiert normalisieren.

Umfang:

- freigegebene Originalbild-Fixtures
- Attachment-Bytes hinter einem schmalen Port laden; keine JDA-Typen im Parser
- reine Java-Bildverarbeitung mit `ImageIO`/`BufferedImage`
- Erkennung und Gruppierung der vier Boards
- kanonische Reihenfolge und Überschriften `Oben links`, `Oben rechts`, `Unten links`, `Unten rechts`
- robuste Farbklassifikation und Unicode-Normalisierung
- Konfidenz-, Struktur- und Plausibilitätsvalidierung
- Parser-Versionierung
- Rohbildaufbewahrung für höchstens 48 Stunden
- keine Veröffentlichung oder Löschung bei unsicherem Parse
- Standardtests vollständig ohne Netzwerk, Discord und Datenbank

Abnahmekriterium:

- alle freigegebenen Originalfixtures werden exakt korrekt normalisiert
- beschädigte, unbekannte oder nicht ausreichend sichere Bilder brechen kontrolliert ab und lassen die Originalnachricht bestehen

## Inkrement 7 – Kanonische QuadWords-Konsolidierung und sichere Ersetzung

**Ziel:** QuadWords-Text und Bild in genau eine kanonische, korrigierbare Bot-Nachricht überführen und die Quelle erst danach sicher löschen.

Umfang:

- kanonische Unicode-Ausgabe aller vier Grids in eindeutiger Reihenfolge
- Spieler, Datum, Ergebnis, Versuche und Dauer darstellen
- Aktivitätsserie und QuadWords-Lösungsserie ausgeben
- bei Tagesabschluss gegebenenfalls Komplett- und Perfektserie ergänzen
- Bot-Message-ID persistieren
- Korrekturen bearbeiten dieselbe kanonische Bot-Nachricht
- Publication-Key, Recovery, Supersession und Duplikatbereinigung analog zu GridWords
- Originalnachricht mit Bildanhang erst nach persistierter kanonischer Veröffentlichung löschen
- sichere Lösch-, Retry-, Neustart- und Crashpfade analog zu Inkrement 5
- unsicher oder fehlerhaft geparste Bilder niemals löschen
- Rohbild nach der festgelegten Aufbewahrungsfrist entfernen

Abnahmekriterium:

- pro Spieler und QuadWords-Spieltag existiert höchstens eine kanonische Bot-Nachricht
- eine gültige Quelle wird erst nach sicherer Konsolidierung gelöscht
- Parse-, Publish- oder Persistenzfehler können das Originalbild niemals verlieren lassen

## Inkrement 8 – Tagesstatus, vollständige Serien und Erinnerungen

**Ziel:** Den täglichen Kernnutzen nach sicherer Konsolidierung beider Spiele vollständig herstellen.

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

## Inkrement 9 – Kernversion härten und veröffentlichen

**Ziel:** Den bis dahin vollständigen Bot produktionsreif machen.

Umfang:

- Ende-zu-Ende-Test im Testserver
- Fehlertexte und Logs
- Betriebsdokumentation
- Backup-/Restore-Hinweise
- reproduzierbarer Deploymentweg; Containerimage ist optional, nicht lokale Voraussetzung
- Hostingentscheidung
- Migration in endgültigen Channel

Nicht enthalten:

- Wochen- und Monatsberichte
- Statistik-Commands
- Kommentare

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
- sichere GridWords-Ersetzung vor sicherer QuadWords-Ersetzung
- QuadWords-Bildparser vor kanonischer QuadWords-Konsolidierung
- sichere Konsolidierung beider Spiele vor Tagesstatus und Erinnerungen
- robuste Serienlogik vor Berichten und Kommentaren
- stabiler Scheduler vor dynamischer Slash-Command-Konfiguration

Parser-Fixtures und Dokumentation können parallel gesammelt werden.

## Definition of Done pro Inkrement

- Issue-Abnahmekriterien erfüllt
- lokaler `mvn clean verify` ohne Docker erfolgreich
- GitHub Actions grün
- Datenbankintegrationsprofil in CI erfolgreich, sofern Persistenz betroffen
- keine Secrets
- relevante Dokumentation aktualisiert
- notwendige manuelle Discord- oder Persistenzprüfung dokumentiert
- PR reviewbar und ohne unangeforderten Versionsumfang
