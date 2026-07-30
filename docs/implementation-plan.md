# Implementierungsplan

Dieser Plan zerlegt die Anforderungsspezifikation in kleine, reviewbare Inkremente. Für Serien gilt `docs/requirements/series-model.md`; für dynamische Spieler und Reminder-Teilnahme `docs/requirements/dynamic-player-model.md`; für lokale Infrastruktur und Datenbanktests `docs/adr/0010-docker-available-local-development.md`.

## Leitprinzipien

- Erst stabiler Build, dann Fachlogik.
- Parser und Regeln bleiben soweit möglich unabhängig von Discord und Datenbank.
- Der schnelle Standardbuild bleibt ohne Docker, PostgreSQL und Discord-Token ausführbar.
- Docker Compose ist die bevorzugte lokale PostgreSQL-Umgebung.
- Persistenzänderungen werden lokal mit `database-integration` und zusätzlich in GitHub Actions geprüft.
- Originalnachrichten werden erst nach vollständig getesteter kanonischer Veröffentlichung gelöscht.
- QuadWords-Bildparser und sichere QuadWords-Konsolidierung werden vor Tagesstatus und Erinnerungen umgesetzt.
- Dynamische Spieler, historische Teilnahmezeiträume und Reminder-Opt-in werden vor dem Reminder-Scheduler umgesetzt.

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

**Status:** abgeschlossen, PR #14 gemergt; lokale Standard- und PostgreSQL-Tests sowie vollständiger Discord-/PostgreSQL-Smoke-Test am 30. Juli 2026 erfolgreich.

**Ziel:** QuadWords-Ergebnisbilder ohne OCR sicher in vier normalisierte Raster überführen und persistent speichern.

Umgesetzt:

- transportneutrale Attachment-Referenz aus Channel-, Message- und Attachment-ID
- schmaler `AttachmentContentLoader`-Port und JDA-Adapter
- Download erst nach erfolgreicher Kopfzeilenprüfung und eindeutiger Bildauswahl
- Download der originalen signierten Discord-CDN-Datei statt der möglicherweise transformierten Medienproxy-Variante
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

Abnahme:

- lokaler Standardbuild: 181 Tests grün
- lokales PostgreSQL-Profil: 51 Integrationstests zusätzlich grün
- beide CI-Jobs grün
- reales gelöstes und reales `X/9` visuell und in PostgreSQL geprüft
- fachliche Fehlerfälle, Korrektur und Neustart erfolgreich geprüft
- GridWords-Regressionsprüfung erfolgreich

## Inkrement 7 – Kanonische QuadWords-Konsolidierung und sichere Ersetzung

**Status:** abgeschlossen, PR #16 gemergt; lokale Vollbuilds und vollständiger realer Discord-/PostgreSQL-Smoke-Test am 30. Juli 2026 erfolgreich.

**Ziel:** QuadWords-Text und vier normalisierte Boards in genau eine korrigierbare Bot-Nachricht überführen und die Quelle danach sicher löschen.

Umgesetzter Umfang:

- kanonische Darstellung aller vier Boards sowie Spieler, Datum, Ergebnis, Dauer und verbindliche Serien
- genau eine persistierte Bot-Message-ID je Spieler, Spieltyp und Spieltag
- Korrektur durch Edit derselben Bot-Nachricht mit Erhalt bereits etablierter Kontextzeilen
- spieltypbezogener Publication-Key
- gemeinsame, begrenzt generalisierte Claims, Delivery-Fence, Retry-, Startup-Recovery-, Supersession- und Duplikatbereinigung für GridWords und QuadWords
- Quelllöschung erst nach persistierter kanonischer Veröffentlichung sowie Delete-Recovery
- explizit nicht publizierbare boardlose `quadwords-share-v1`-Ergebnisse: kein Discord-Aufruf, Delete-Handoff, Erfolgssignal oder Refresh-Hot-Loop
- PublicationContext auch dann genau einmal, wenn QuadWords als zweite Einreichung persönliche und gemeinsame Komplett-/Perfektzustände etabliert
- kein QuadWords-`✅` nach erfolgreicher Konsolidierung
- permanente Löschfehler ohne Scheduler- oder Hot-Loop sowie kontrollierte Wiederaufnahme bei Neustart oder nach einer späteren bestätigten Veröffentlichung desselben Ergebnisses
- parametrisierte gemeinsame Sicherheitsfälle statt einer kopierten GridWords-Zustandsmaschine

Abnahme:

- `mvn --batch-mode --no-transfer-progress clean verify`: 196 Tests grün
- `mvn --batch-mode --no-transfer-progress -Pdatabase-integration clean verify`: 196 Standardtests plus 56 PostgreSQL-Integrationstests grün
- GitHub Actions vollständig grün
- echte GridWords- und QuadWords-Erstveröffentlichung sowie Korrektur geprüft
- alle vier QuadWords-Boards visuell geprüft
- sichere Löschung, fachliche Ablehnung, Neustart und Duplikatschutz geprüft
- permanenter Löschfehler ohne Hot-Loop und kontrollierte Recovery nach Berechtigungswiederherstellung geprüft

## Zwischeninkrement 7.1 – Kompaktes 2×2-Layout der QuadWords-Grids

**Status:** abgeschlossen, Issue #17 und PR #18 gemergt; visueller Discord-Smoke-Test am 30. Juli 2026 erfolgreich.

**Ziel:** die vier bereits korrekt publizierten QuadWords-Grids näher an der ursprünglichen Spielanordnung und kompakter darstellen.

Umgesetzt:

- `topLeft` und `topRight` nebeneinander
- `bottomLeft` und `bottomRight` darunter nebeneinander
- keine sichtbaren Positionslabels
- sichtbare Einzelhöhe endet bei der ersten vollständig grünen Lösungszeile; ungelöste Boards behalten alle Zeilen
- Paarhöhe entspricht dem längeren Board des jeweiligen horizontalen Paares
- dunkle geometrisch stabile Platzhalterzellen für die Ausrichtung kürzerer Boards
- Monospace-Codeblock für GridWords und QuadWords
- Wiederherstellung historisch etablierter Komplett-/Perfektzeilen bei kanonischer Neuerzeugung
- Titel, Ergebnis, Dauer, Serien, Publication-Key und Korrektur-Edit unverändert
- Parser-, Persistenz- und Publish-/Delete-Zustandsmaschine unverändert

Abnahme:

- 199 Standardtests grün
- 57 PostgreSQL-Integrationstests grün
- echte Discord-Ausrichtung, GridWords-Codeblock, Kontextzeilen und sichere Löschung erfolgreich geprüft

## Zwischeninkrement 7.2 – Dynamische Spieler, Teilnahmezeiträume und Reminder-Opt-in

**Status:** abgeschlossen; automatisierte und reale Discord-/PostgreSQL-Abnahme am 30. Juli 2026 erfolgreich.

**Ziel:** die feste Zwei-Spieler-Konfiguration durch dynamische Spielerprofile und historisch stabile Teilnahmezeiträume ersetzen sowie die Reminder-Präferenzen für Inkrement 8 vorbereiten.

Umgesetzt:

- jeder menschliche Nutzer im Zielchannel kann durch ein vollständig gültiges Share Spieler werden
- ungültige Shares und normale Texte erzeugen kein Spielerprofil
- serverbezogener Discord-Anzeigename und extern konfigurierter Administratorstatus werden bei Share und Commands synchronisiert
- `player.active` als aktueller Zustand und datierte, nicht überlappende Teilnahmezeiträume
- Spielerregistrierung, Aktivierung, Ergebnis und PublicationContext werden atomar gespeichert
- erstmalige konkurrierte Registrierungen werden vor der Periodenmutation serialisiert
- automatische Aktivierung ab fachlichem Share-Spieltag
- `/participation join|leave|status` als Self-Service
- `/player activate|deactivate|status` für konfigurierte Administratoren
- `/reminders on|off|status` unabhängig vom Teilnahmezustand
- ephemere Antworten und Statusausgabe mit laufendem Teilnahmezeitraum
- gemeinsamer Komplett-/Perfekttag über alle am jeweiligen Tag aktiven Spieler, mindestens zwei Teilnehmer
- historische Serien bleiben bei späterem Beitritt oder Austritt unverändert
- transportneutraler Reminder-Kandidaten-Port mit fehlenden Spieltypen und Discord-User-ID
- Entfernung der festen `PLAYER_1_*`-/`PLAYER_2_*`-Konfiguration
- Liquibase-Migration und Backfill für bestehende Spieler
- produktiver Spring-/PostgreSQL-Startup mit genau einem dynamischen Persistenzadapter
- vollständige GridWords-/QuadWords-Publish-, Edit-, Delete-, Recovery- und Parser-Regression

Verbindlich: `docs/requirements/dynamic-player-model.md` und `docs/increments/07b-dynamic-player-participation.md`.

Abnahme:

- 207 Standardtests lokal und in GitHub Actions grün
- 63 PostgreSQL-Integrationstests lokal und in GitHub Actions grün
- keine automatisierte Discord-Verbindung oder Token-Verwendung
- realer Discord-/PostgreSQL-Smoke-Test mit mindestens drei Nutzern erfolgreich
- automatische Registrierung, Namenssynchronisierung, Commands, Reminder-Opt-in, wechselnde Teilnehmer und sichere Ergebnisersetzung geprüft

## Inkrement 8 – Tagesstatus, vollständige Serien und Erinnerungen

**Ziel:** täglichen Kernnutzen auf Basis dynamischer Teilnehmer vollständig herstellen.

- alle fünf persönlichen Serien für jeden Spieler
- gemeinsame Komplett- und Perfektserie über die pro Spieltag aktive Teilnehmermenge
- keine gemeinsame Aktivitätsserie
- eine Tagesstatusnachricht je Spieltag
- Erinnerungen um 18:00 und 23:00 Uhr
- nur aktive Reminder-Opt-ins mit fehlenden Einreichungen erwähnen
- ID-basierte User-Mentions mit strikt begrenzten Allowed Mentions
- je Spieler die konkret fehlenden Spiele nennen
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
- kompaktes QuadWords-Layout als isoliertes Renderer-Polish vor dynamischen Spielern
- dynamische Teilnahmezeiträume und Reminder-Opt-in vor Tagesstatus und Scheduler
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
