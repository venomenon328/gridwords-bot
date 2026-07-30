# Implementierungsplan

Dieser Plan zerlegt die Anforderungsspezifikation in kleine, reviewbare Inkremente. Für Serien gilt `docs/requirements/series-model.md`; für lokale Infrastruktur und Datenbanktests `docs/adr/0010-docker-available-local-development.md`.

## Leitprinzipien

- Erst stabiler Build, dann Fachlogik.
- Parser und Regeln bleiben soweit möglich unabhängig von Discord und Datenbank.
- Der schnelle Standardbuild bleibt ohne Docker, PostgreSQL und Discord-Token ausführbar.
- Docker Compose ist die bevorzugte lokale PostgreSQL-Umgebung.
- Persistenzänderungen werden lokal mit `database-integration` und zusätzlich in GitHub Actions geprüft.
- Originalnachrichten werden erst nach vollständig getesteter kanonischer Veröffentlichung gelöscht.
- QuadWords-Bildparser und sichere QuadWords-Konsolidierung werden vor Tagesstatus und Erinnerungen umgesetzt.

## Inkremente 0 bis 5

### Inkrement 0 – Grundgerüst

**Status:** abgeschlossen, PR #1 gemergt.

Java 21, Spring Boot, JDA, Maven, externe Konfiguration, infrastrukturunabhängiger Build und Gateway-Smoke-Test.

### Inkrement 1 – Share-Textparser

**Status:** abgeschlossen, PR #4 gemergt.

Deterministische GridWords-/QuadWords-Kopfzeilen, GridWords-Raster, Ergebnis, Dauer, Datum und Flamme.

### Inkrement 2 – Persistenzmodell

**Status:** abgeschlossen, PR #6 gemergt.

Liquibase, PostgreSQL-Adapter, Submission-Zustände, Idempotenz und Datenbankintegrationsprofil.

### Inkrement 3 – Discord-Inbound

**Status:** abgeschlossen, PR #8 gemergt.

Gefilterter Listener, begrenzter Executor, Parse/Persistenz, `✅` und `⚠️`, noch keine Ersetzung.

### Inkrement 4 – Kanonische GridWords-Nachricht

**Status:** abgeschlossen, PR #10 gemergt; Smoke-Test am 29. Juli 2026 erfolgreich.

Kanonisches Embed, Serien, persistierte Bot-Message-ID, Korrektur-Edit, Claims, Recovery und Duplikatbereinigung.

### Inkrement 5 – Sichere GridWords-Ersetzung

**Status:** abgeschlossen, PR #12 gemergt; vollständiger Smoke-Test am 30. Juli 2026 erfolgreich.

Quelllöschung erst nach persistierter kanonischer Veröffentlichung, Lease-/Token-Claims, Retry, Startup-Recovery, permanente Fehler und gezielte Superseded-Reconciliation.

## Inkrement 6 – QuadWords-Bildparser

**Status:** automatisiert umgesetzt in Issue #13 / Draft-PR #14; lokale Maven-Abnahme und echter Discord-/PostgreSQL-Smoke-Test durch Tobias stehen noch aus.

**Ziel:** QuadWords-Ergebnisbilder ohne OCR sicher in vier normalisierte Raster überführen und persistent speichern.

Umgesetzt:

- transportneutrale Attachment-Referenz aus Channel-, Message- und Attachment-ID
- schmaler `AttachmentContentLoader`-Port und JDA-Adapter
- Download erst nach erfolgreicher Kopfzeilenprüfung und eindeutiger Bildauswahl
- Download und Decode außerhalb des JDA-Event-Threads
- reine Java-Bildverarbeitung mit `ImageIO` und `BufferedImage`
- Unterstützung von PNG und JPEG
- stabile Ablehnung nicht unterstützter oder beschädigter Formate
- 8-MiB-, 4096×4096- und 12-Megapixel-Grenzen
- Erkennung einer 2×2-Anordnung mit genau fünf Spalten je Board
- kanonische Reihenfolge `Oben links`, `Oben rechts`, `Unten links`, `Unten rechts`
- robuste Flächenstichproben für `⬜`, `🟨`, `🟩`
- kontrollierte Fehler für Geometrie, Struktur, Zeilenzahl und Farbunsicherheit
- Normalisierung klar fehlender nachlaufender Zeilen als Leerzellen
- typisiertes Domänenmodell `QuadWordsBoards`/`QuadWordsBoard`
- Parser-Version `quadwords-image-v2`
- Liquibase-Persistenz aller vier Boards und Parser-Version
- Kompatibilität mit boardlosen `quadwords-share-v1`-Ergebnissen
- technischer Pre-Result-Retry über `FAILED_RETRYABLE`
- Golden-Tests für alle freigegebenen PNG-Fixtures
- synthetische Tests für Skalierung, Ränder, JPEG, beschädigte Dateien, unsichere Farben und Ressourcenlimits
- Application-, Discord-Adapter-, Architektur- und PostgreSQL-Integrationstests
- keine Rohbildpersistenz: Bytes werden nur im Arbeitsspeicher verarbeitet

Discord-Verhalten:

- sicher geparstes und gespeichertes QuadWords: Original bleibt sichtbar, `✅`
- stabil fachlich ungültiges Bild: Original bleibt sichtbar, `⚠️`
- technischer Attachmentfehler: Original bleibt sichtbar, keine irreführende Reaktion, retryfähig
- GridWords bleibt unverändert
- keine kanonische QuadWords-Nachricht und keine QuadWords-Quelllöschung

Abnahme vor Merge:

- lokaler Standardbuild grün
- lokales PostgreSQL-Profil mit Docker grün
- beide CI-Jobs grün
- reales gelöstes und reales `X/9` visuell und in PostgreSQL geprüft
- Korrektur und Neustart ohne Duplikate
- GridWords-Regressionsprüfung

## Inkrement 7 – Kanonische QuadWords-Konsolidierung und sichere Ersetzung

**Ziel:** QuadWords-Text und vier normalisierte Boards in genau eine korrigierbare Bot-Nachricht überführen und die Quelle danach sicher löschen.

Umfang:

- kanonische Darstellung aller vier Boards
- Spieler, Datum, Ergebnis, Dauer und Serien
- persistierte Bot-Message-ID
- Korrektur durch Edit derselben Bot-Nachricht
- Publication-Key, Claims, Recovery, Supersession und Duplikatbereinigung analog zu GridWords
- Quelllöschung erst nach persistierter kanonischer Veröffentlichung
- kein Löschen bei unsicherem oder fehlerhaftem Bildparse

## Inkrement 8 – Tagesstatus, vollständige Serien und Erinnerungen

**Ziel:** täglichen Kernnutzen vollständig herstellen.

- alle fünf persönlichen Serien
- gemeinsame Komplett- und Perfektserie
- keine gemeinsame Aktivitätsserie
- eine Tagesstatusnachricht je Spieltag
- Erinnerungen um 18:00 und 23:00 Uhr
- nur fehlende Einreichungen erwähnen
- persistierte Delivery-Idempotenz und Neustart-Nachholung
- `Europe/Berlin`, feste `Clock`, heute und gestern als Nachtragsfenster

## Inkrement 9 – Kernversion härten und veröffentlichen

- Ende-zu-Ende-Test
- Logs und Fehlertexte
- Betriebs-, Backup- und Restore-Dokumentation
- reproduzierbarer Deploymentweg
- Hostingentscheidung und Migration in den endgültigen Channel

## Inkrement 10 – Wochen- und Monatsberichte

- Wochenbericht Montag 08:00
- Monatsbericht am Monatsersten 08:15
- persistierte Delivery-Idempotenz
- persönliche und gemeinsame Kennzahlen und längste Serien
- keine Gewinnerlogik

## Inkrement 11 – Statistik- und Konfigurations-Commands

- Statistik-Slash-Commands
- eindeutige Auswahl der sieben Serienarten
- Zeitkonfiguration
- Admin-Autorisierung
- Scheduler-Neuplanung ohne Neustart

## Inkrement 12 – Regelbasierte Kommentare

- regelbasierte Kategorien und Textvarianten
- Serien-, Komplett- und Perfekt-Auslöser
- definierte Nachrichtenlimits
- keine generative KI

## Bewusste Reihenfolge

- Persistenzzustände vor automatischer Löschung
- GridWords-Ersetzung vor QuadWords-Ersetzung
- QuadWords-Bildparser vor QuadWords-Konsolidierung
- sichere Konsolidierung beider Spiele vor Tagesstatus und Erinnerungen
- robuste Serienlogik vor Berichten und Kommentaren

## Definition of Done

- Issue-Abnahmekriterien erfüllt
- lokaler Standardbuild grün
- bei Persistenzumfang lokales Datenbankprofil mit Docker grün
- GitHub Actions vollständig grün
- keine Secrets
- Dokumentation aktualisiert
- notwendiger manueller Smoke-Test erfolgreich
- PR reviewbar, Draft-Status erst nach vollständiger Abnahme aufheben
