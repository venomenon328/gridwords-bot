# Anforderungsspezifikation: GridGames-Discord-Bot

**Version:** 0.5 – abgenommene fachliche Grundlage  
**Stand:** 29. Juli 2026  
**Status:** Fachliche Grundanforderungen, Versionsgrenzen, Zeitpläne, Discord-Zielkonfiguration, Serienmodell und Docker-optionale lokale Entwicklung sind bestätigt. Der reale Discord-Gateway-Smoke-Test wurde erfolgreich ohne Docker und PostgreSQL durchgeführt.  
**Zielplattform:** Discord-Server, dedizierter Textkanal, zwei fest konfigurierte Spieler

Verbindliche Ergänzungen und Architekturentscheidungen:

- [`docs/requirements/series-model.md`](requirements/series-model.md) – vollständiges Serienmodell
- [`docs/adr/0002-idempotent-message-replacement.md`](adr/0002-idempotent-message-replacement.md) – sichere Nachrichtenersetzung
- [`docs/adr/0004-docker-optional-local-development.md`](adr/0004-docker-optional-local-development.md) – lokale Entwicklung ohne Docker-Zwang

---

## 1. Ziel und Zweck

Der Bot unterstützt Tobias und Georgia beim täglichen Spielen von **GridWords** und **QuadWords** auf Gridgames.

Er soll:

1. an noch nicht eingereichte Spiele erinnern,
2. geteilte Spielergebnisse automatisch erkennen,
3. gültige Ergebnisse sicher speichern,
4. den Tagesstatus beider Spieler verwalten,
5. persönliche Aktivitäts-, Komplett-, spielbezogene Lösungs- und Perfektserien berechnen,
6. gemeinsame Komplett- und Perfektserien berechnen,
7. Ergebnisbeiträge bereinigen und einheitlich wiederveröffentlichen,
8. QuadWords-Ergebnisbilder ab Version 2 in Unicode-Raster umwandeln,
9. ab Version 2 Wochen- und Monatsberichte erstellen,
10. ab Version 3 Statistiken, konfigurierbare Slash-Commands und regelbasierte Kommentare bereitstellen.

Eine eigene Discord Activity ist nicht Bestandteil des Projekts.

---

## 2. Systemgrenzen

### 2.1 Im Projektumfang

- genau ein konfigurierter Discord-Server
- genau ein dedizierter Textkanal
- genau zwei konfigurierte Spieler
- GridWords und QuadWords
- Verarbeitung neuer Ergebnisnachrichten
- sichere kanonische Wiederveröffentlichung erkannter Ergebnisse
- persistente Speicherung von Ergebnissen, Verarbeitungszuständen, Status, Erinnerungen und Berichten
- Ableitung der Serien aus gespeicherten Spielergebnissen
- robustes Verhalten bei Neustarts und mehrfach zugestellten Discord-Events
- Erinnerungen und Berichte zu konfigurierbaren Uhrzeiten
- lokale Entwicklung ohne verpflichtende Container-Runtime
- späterer Dauerbetrieb auf einem geeigneten Host

### 2.2 Nicht im Projektumfang

- Nachbau der Spiele
- direktes Auslesen des Spielzustands von gridgames.app
- Discord-DMs oder Gruppen-DMs
- eigene Discord Activity
- Unterstützung beliebig vieler Server, Kanäle oder Spieler
- generative KI zur Laufzeit
- spoilerfreie Einreichung in den ersten drei Versionen
- Urlaubstage, Joker oder manuelle Serienrettung
- gemeinsamer Startmodus
- automatische Gewinner- oder Punktelogik
- gemeinsame Aktivitätsserie

---

## 3. Zielumgebung

### 3.1 Discord-Testumgebung

```text
Discord-Server-ID: 255064124902473729
Discord-Channel-ID: 1531398793713549494
Tobias-User-ID: 255063936410451978
Georgia-User-ID: 451773931351834634
```

Der Bot ist auf dem Testserver installiert und besitzt die erforderlichen Channelrechte.

### 3.2 Zeitzone

Alle fachlichen Zeitberechnungen verwenden:

```text
Europe/Berlin
```

Sommer- und Winterzeit müssen korrekt berücksichtigt werden.

### 3.3 Lokale Entwicklungsumgebung

Für den normalen lokalen Build und die Discord-/Fachlogikentwicklung werden benötigt:

- Git
- JDK 21
- Maven 3.9 oder neuer
- geeignete IDE, bevorzugt VS Code mit Codex

**Docker Desktop, eine andere Container-Runtime und PostgreSQL sind keine Voraussetzungen für den lokalen Standardbuild.**

Der verbindliche lokale Standardbuild lautet:

```bash
mvn --batch-mode --no-transfer-progress clean verify
```

Er muss ohne Discord-Token, Discord-Verbindung, PostgreSQL und Container-Runtime erfolgreich sein.

Für einen späteren manuellen lokalen Persistenzstart wird eine nativ installierte PostgreSQL-Instanz unterstützt. `compose.yaml` bleibt als optionale Alternative erhalten.

PostgreSQL-Integrationstests werden ab dem Persistenzinkrement über ein eigenes Maven-Profil in GitHub Actions verpflichtend gegen echtes PostgreSQL ausgeführt. Details stehen in ADR 0004.

### 3.4 Erfolgreicher Discord-Smoke-Test

Am 29. Juli 2026 wurde die Anwendung auf Tobias' Entwicklungsrechner erfolgreich gestartet mit:

- Spring-Profil `offline`,
- `DISCORD_ENABLED=true`,
- lokalem, nicht versioniertem Bot-Token,
- ohne Docker,
- ohne PostgreSQL.

Der Bot erschien auf dem vorgesehenen Server online und JDA meldete eine erfolgreiche Gatewayverbindung.

---

## 4. Begriffe und fachliche Definitionen

### 4.1 Spieltag

Der Spieltag ist das im geteilten Gridgames-Ergebnis angegebene Datum. Nicht der Discord-Zeitstempel bestimmt die fachliche Zuordnung.

Ein Ergebnis, das kurz nach Mitternacht gepostet wird, aber das Datum des Vortags enthält, wird dem Vortag zugeordnet.

### 4.2 Eingereichtes beziehungsweise gespieltes Spiel

Ein Spiel gilt als **eingereicht** beziehungsweise für die Serienlogik als **gespielt**, wenn für Spieler, Spieltyp und Spieltag ein syntaktisch und fachlich gültiges Ergebnis gespeichert wurde.

Ein nicht gelöstes, aber korrekt geteiltes Ergebnis zählt als eingereicht und gespielt.

### 4.3 Gelöstes Spiel

Ein Spiel gilt als **gelöst**, wenn das Share-Format eine numerische Versuchszahl ausweist, beispielsweise `5/6` oder `9/9`.

Ein Spiel gilt als **nicht gelöst**, wenn an derselben Position `X` verwendet wird.

Bestätigtes QuadWords-Beispiel:

```text
QuadWords (8. Juli 2026) X/9 in 9:47
```

Für GridWords wird bis zu einem echten Fixture analog `X/6` angenommen.

### 4.4 Tagesmerkmale und Serien

Die vollständigen Definitionen, Anzeigevorgaben, Regeln für den noch unvollständigen aktuellen Tag und Testfälle stehen in [`docs/requirements/series-model.md`](requirements/series-model.md).

Kurzfassung:

- **Aktivitätstag:** mindestens eines der beiden Spiele wurde eingereicht.
- **Kompletter Tag:** GridWords und QuadWords wurden eingereicht, unabhängig vom Lösungsstatus.
- **Perfekter Tag:** beide Spiele wurden eingereicht und gelöst.

Persönliche Serien:

1. Aktivitätsserie
2. Komplettserie
3. GridWords-Lösungsserie
4. QuadWords-Lösungsserie
5. Perfektserie

Gemeinsame Serien:

1. gemeinsame Komplettserie
2. gemeinsame Perfektserie

Es gibt keine gemeinsame Aktivitätsserie und keine unspezifische persönliche Lösungsserie, die beide Spiele vermischt.

### 4.5 Gridgames-Flammenserie

Die im Share-Text angezeigte Flammenserie, beispielsweise `🔥2`, wird separat gespeichert. Sie ist nicht maßgeblich für die Bot-eigenen Serien.

---

## 5. Benutzer und Rollen

### 5.1 Spieler und Administratoren

- Tobias
- Georgia

Beide werden über ihre Discord-User-ID konfiguriert und dürfen ab Version 3 administrative Bot-Funktionen verwenden.

### 5.2 Bot

Der Bot:

- verarbeitet ausschließlich den konfigurierten Server und Channel,
- verarbeitet ausschließlich Beiträge der beiden Spieler,
- ignoriert eigene Bot-Nachrichten, Webhooks und Beiträge anderer Nutzer,
- speichert Resultate vor jeder Veränderung einer Discord-Nachricht,
- löscht Originalbeiträge nur nach erfolgreicher kanonischer Wiederveröffentlichung und Persistierung der Bot-Message-ID.

---

## 6. Versionsplanung

## 6.1 Version 1 – Kernfunktion und GridWords-Bereinigung

### Muss-Funktionen

1. Discord-Verbindung über Bot-Account
2. externe Konfiguration von Server, Channel, Spielern, Administratoren, Zeitzone und Erinnerungszeiten
3. erste Erinnerung standardmäßig 18:00 Uhr
4. zweite Erinnerung standardmäßig 23:00 Uhr
5. Erkennung von GridWords- und QuadWords-Kopfzeilen
6. Erkennung von gelösten und nicht gelösten Ergebnissen
7. Speicherung aller aus der Kopfzeile gewonnenen Daten
8. Speicherung und Validierung des GridWords-Unicode-Rasters
9. QuadWords wird über Kopfzeile und plausiblen Bildanhang erkannt; keine Bildumwandlung in Version 1
10. persistente Tagesstatusverwaltung
11. alle fünf persönlichen Serien
12. beide gemeinsamen Serien
13. Erinnerungen nur bei fehlenden Einreichungen
14. idempotente Verarbeitung und Persistenz über Neustarts
15. grundlegende Fehlerprotokollierung
16. sichere kanonische Bereinigung von GridWords-Nachrichten
17. GridWords-Ausgabe ohne Gridgames-Link und Linkvorschau
18. GridWords-Ausgabe mit Ergebnis, Raster, Aktivitätsserie und GridWords-Lösungsserie
19. kontextabhängige Ergänzung von Komplett- und Perfektserie
20. QuadWords-Originalnachrichten bleiben in Version 1 bestehen
21. gültige QuadWords-Nachrichten erhalten mindestens eine `✅`-Reaktion
22. keine Konfigurationsänderung per Slash-Command

### Nicht Bestandteil von Version 1

- QuadWords-Bildparser und Unicode-Ausgabe
- Löschen und Ersetzen von QuadWords-Originalnachrichten
- Wochen- und Monatsberichte
- Statistik-Commands
- regelbasierte Kommentare
- dynamische Konfiguration per Slash-Command

## 6.2 Version 2 – QuadWords-Normalisierung und Berichte

Zusätzlich:

1. Download und Auswertung des originalen QuadWords-Ergebnisbildes
2. Erkennung der vier Teilraster
3. Umwandlung in `⬜`, `🟨`, `🟩`
4. Ausgabe mit den Überschriften `Oben links`, `Oben rechts`, `Unten links`, `Unten rechts`
5. geometrische und farbbasierte Auswertung ohne OCR
6. kontrollierter Abbruch statt falscher Ausgabe bei unzureichender Sicherheit
7. sichere kanonische QuadWords-Ersetzung erst nach erfolgreichem Parse und Persistierung
8. keine Löschung bei unsicherem Bildparser
9. Wochenbericht montags 08:00 Uhr
10. Monatsbericht am ersten Kalendertag 08:15 Uhr
11. Parser-Versionierung und kontrollierte Neuverarbeitung
12. automatische Löschung temporärer Rohbilder nach 48 Stunden

## 6.3 Version 3 – Statistik, Kommentare und Discord-Konfiguration

Zusätzlich:

1. Statistik-Slash-Commands für Tag, Woche, Monat und Gesamtzeitraum
2. Konfiguration der Erinnerungs- und Berichtzeiten per Slash-Command
3. Autorisierung nur für konfigurierte Administratoren
4. regelbasierte Kommentare ohne externe KI
5. Kategorien für Bestleistungen, identische Ergebnisse, komplette und perfekte Tage sowie Serienrekorde
6. mehrere Textvarianten je Kategorie
7. höchstens ein Ergebniskommentar pro Ergebnis
8. höchstens ein gemeinsamer Abschlusskommentar pro Spieltag

---

## 7. Verarbeitung eingehender Nachrichten

### 7.1 Vorbedingungen

Eine Nachricht wird nur geprüft, wenn:

1. Server-ID stimmt,
2. Channel-ID stimmt,
3. Autor ist Tobias oder Georgia,
4. Autor ist kein Bot und kein Webhook.

### 7.2 Zulässiger Spieltag

Akzeptiert werden ausschließlich:

- jederzeit der heutige Spieltag oder
- der unmittelbar vorherige Kalendertag nur zwischen 00:00:00 und 05:59:59 Uhr.

Ab 06:00:00 ist für normale Nutzervorgänge ausschließlich der heutige Spieltag zulässig. Dies gilt ebenso für Korrektur, Retry, Replay und Recovery eines zuvor begonnenen Vortagsvorgangs; weder Annahmezeitpunkt noch ein bestehender Ergebniszustand bilden eine Ausnahme. Maßgeblich ist `Europe/Berlin`.

### 7.3 Verarbeitungsreihenfolge

1. Spieltyp erkennen
2. Nachricht syntaktisch parsen
3. Spieltag validieren
4. Raster beziehungsweise Anhang validieren
5. Gelöst-Status bestimmen
6. Ergebnis idempotent speichern oder aktualisieren
7. betroffene Serien neu berechnen
8. kanonische Bot-Nachricht erzeugen, soweit unterstützt
9. Bot-Nachricht erfolgreich veröffentlichen
10. Bot-Message-ID persistieren
11. erst danach Originalnachricht löschen
12. Tagesstatus aktualisieren
13. ab Version 3 gegebenenfalls Kommentar erzeugen

Kann eine sichere Ersetzung nicht garantiert werden, bleibt die Originalnachricht bestehen.

### 7.4 Zu erfassende Daten

- Spieltyp
- Spieltag
- eingereicht und gelöst
- Versuchswert und Maximalversuche
- Dauer in Sekunden
- Gridgames-Flammenserie
- normalisiertes Raster, soweit verfügbar
- Discord-Original-Message-ID
- kanonische Bot-Message-ID
- Guild-, Channel- und User-ID
- Empfangszeitpunkt
- Parser-Version
- relevanter Rohtext
- optionale temporäre Rohbildreferenz
- Parse- und Verarbeitungsstatus

Serien werden aus den Ergebnissen abgeleitet und sind keine unabhängige Quelle fachlicher Wahrheit.

### 7.5 Fehlerverhalten

Bei einem erkennbaren, aber ungültigen Share-Format:

- keine Löschung,
- keine kanonische Ersetzung,
- keine Serienänderung,
- `⚠️` oder kurze Bot-Antwort,
- strukturierter Logeintrag.

Bei einem Fehler der QuadWords-Bildauswertung:

- gültige Kopfdaten bleiben gespeichert,
- Einreichungsstatus bleibt erhalten,
- Originalnachricht und Bild bleiben bestehen,
- Rohbild kann innerhalb der 48-Stunden-Frist zur Diagnose vorgehalten werden.

---

## 8. Kanonische Ergebnisnachrichten

### 8.1 Allgemeine Anforderungen

Empfohlene Darstellung als Discord-Embed mit:

- Spielername und optional Avatar
- Spielname und Spieltag
- Ergebnis und Dauer
- Unicode-Raster
- Aktivitätsserie
- Lösungsserie des geposteten Spiels
- optional Komplett- und Perfektserie bei entsprechendem Tagesabschluss

Keine Gridgames-Links und keine unspezifischen Bezeichnungen wie nur „Spielserie“ oder „Lösungsserie“.

### 8.2 Beispiel GridWords

```text
Tobias · GridWords · 27. Juli 2026
Gelöst in 5/6 · 1:25

⬜⬜🟨⬜🟨
⬜⬜⬜⬜⬜
…

🔥 Aktivität: 12 Tage · GridWords gelöst: 9 Tage
```

### 8.3 Beispiel QuadWords ab Version 2

```text
Tobias · QuadWords · 27. Juli 2026
Gelöst in 9/9 · 4:18

Oben links
…

Oben rechts
…

Unten links
…

Unten rechts
…

🔥 Aktivität: 12 · Komplett: 8 · QuadWords gelöst: 4
```

### 8.4 Sichere Löschreihenfolge

Eine Originalnachricht darf nur gelöscht werden, wenn:

1. Parse und Validierung erfolgreich sind,
2. Ergebnis persistiert ist,
3. kanonische Bot-Nachricht erfolgreich veröffentlicht ist,
4. Bot-Message-ID persistiert ist.

Schlägt die Löschung danach fehl, bleibt die doppelte Darstellung bestehen und wird protokolliert.

### 8.5 Korrekturen

Eine Korrektur erfolgt innerhalb der Zulässigkeitsgrenze aus Abschnitt 7.2 durch erneutes Posten eines gültigen Shares für denselben Spieler, Spieltyp und Spieltag. Der vorhandene Datensatz und die kanonische Bot-Nachricht werden aktualisiert oder sicher ersetzt; Tagesstatus und Serien werden neu berechnet. Administrative Backfills und Reparaturen sind davon getrennte Wartungsvorgänge.

---

## 9. Parser

## 9.1 GridWords

Erfolgreiches Beispiel:

```text
GridWords (27. Juli 2026) 5/6 in 1:25 🔥2
```

Vorläufig erwartetes nicht gelöstes Format:

```text
GridWords (27. Juli 2026) X/6 in 6:42
```

Der Parser extrahiert Datum, Share-Ergebnis, Maximalversuche, Dauer, optionale Flammenserie, Raster und Gelöst-Status.

Das Raster wird auf erlaubte Unicode-Symbole normalisiert und strukturell validiert.

## 9.2 QuadWords Version 1

Erfolgreiches Beispiel:

```text
QuadWords (27. Juli 2026) 9/9 in 4:18 🔥2
```

Nicht gelöst:

```text
QuadWords (8. Juli 2026) X/9 in 9:47
```

In Version 1 werden Kopfzeile, Metadaten und ein plausibler Bildanhang validiert. Das Bild bleibt sichtbar.

## 9.3 QuadWords Version 2

Bevorzugte Umsetzung mit `ImageIO` und `BufferedImage`:

- Farbschwellen
- Flächen-/Komponentenerkennung
- Gruppierung anhand von x-/y-Positionen
- Validierung von Feldgrößen und Rasterstruktur
- Toleranz gegenüber moderater Skalierung und Kompressionsartefakten

OpenCV wird nur bei nachgewiesenem Bedarf eingeführt.

---

## 10. Tagesstatus

Pro Spieltag existiert höchstens eine aktive Tagesstatusnachricht. Sie wird beim ersten Ergebnis oder spätestens bei der ersten Erinnerung erzeugt und anschließend bearbeitet.

Beispiel:

```text
Wortspiele · 27. Juli 2026

Tobias
✅ GridWords · gelöst · 5/6 · 1:25
⬜ QuadWords
Aktivität: 11 · Komplett: 7
GridWords gelöst: 8 · QuadWords gelöst: 3 · Perfekt: 2

Georgia
✅ GridWords · gelöst · 4/6 · 1:47
✅ QuadWords · gelöst · 8/9 · 3:51
Aktivität: 12 · Komplett: 12
GridWords gelöst: 10 · QuadWords gelöst: 6 · Perfekt: 5

Gemeinsam komplett: 7 Tage
Gemeinsam perfekt: 2 Tage
```

Statussymbole:

- `✅`: eingereicht und gelöst
- `❌`: eingereicht, aber nicht gelöst
- `⬜`: noch nicht eingereicht

---

## 11. Erinnerungslogik

### 11.1 Standardzeiten

- erste Erinnerung: 18:00 Uhr
- zweite Erinnerung: 23:00 Uhr
- täglich in `Europe/Berlin`

### 11.2 Regeln

- Erinnert wird nur an fehlende Einreichungen.
- Ein eingereichtes, aber nicht gelöstes Spiel erzeugt keine weitere Erinnerung.
- Eine bereits erreichte Aktivitätsserie verhindert keine Erinnerung an das zweite Spiel.
- Jede Erinnerungsstufe wird pro Spieltag höchstens einmal gesendet und persistent protokolliert.
- Nach Neustart werden fällige, noch nicht gesendete Erinnerungen nachgeholt.
- Erinnerungsnachricht und Tagesstatus sind getrennte Nachrichten.

---

## 12. Nachträge

Ergebnisse dürfen jederzeit nur für heute und bis 05:59:59 `Europe/Berlin` zusätzlich für gestern eingereicht werden. Ab 06:00:00 ist jeder normale Nutzervorgang für gestern – auch Korrektur, Retry, Replay und Recovery – unzulässig.

Ein zulässiger Vortagsnachtrag führt zur vollständigen Neuberechnung aller betroffenen persönlichen und gemeinsamen Serien. Bereits gesendete Erinnerungen und Berichte werden zunächst nicht rückwirkend gelöscht oder geändert.

---

## 13. Berichte ab Version 2

### 13.1 Wochenbericht

- abgeschlossene Kalenderwoche Montag bis Sonntag
- Versand montags um 08:00 Uhr

### 13.2 Monatsbericht

- abgeschlossener Kalendermonat
- Versand am ersten Tag des Folgemonats um 08:15 Uhr

### 13.3 Mindestinhalt

Pro Spieler:

- Aktivitäts-, komplette und perfekte Tage
- aktuelle und längste Aktivitätsserie
- aktuelle und längste Komplettserie
- aktuelle und längste GridWords-Lösungsserie
- aktuelle und längste QuadWords-Lösungsserie
- aktuelle und längste Perfektserie
- spielbezogene Einreichungs-, Lösungs-, Versuchs- und Zeitstatistiken

Gemeinsam:

- gemeinsam komplette und perfekte Tage
- aktuelle und längste gemeinsame Komplettserie
- aktuelle und längste gemeinsame Perfektserie

Keine Rangliste, kein Gesamtgewinner und keine Zeit als Tie-Breaker.

---

## 14. Datenmodell

### 14.1 `player`

- Discord-User-ID
- Anzeigename
- aktiv
- Administratorstatus
- Zeitstempel

### 14.2 `game_result`

- interne ID
- Spieler-ID
- Spieltyp
- Spieltag
- eingereicht und gelöst
- Share-Ergebnis und Maximalversuche
- Dauer
- Gridgames-Serie
- normalisiertes Raster
- Rohtext und optionale Rohbildreferenz
- Original- und Bot-Message-ID
- Guild- und Channel-ID
- Parser- und Verarbeitungsstatus
- Zeitstempel

Fachliche Eindeutigkeit:

```text
Spieler + Spieltyp + Spieltag
```

### 14.3 Weitere Persistenzobjekte

- `submission` beziehungsweise persistierter Verarbeitungszustand
- `daily_status_message`
- `reminder_delivery`
- `report_delivery`
- `source_artifact` mit 48-Stunden-Aufbewahrung

Schemaänderungen erfolgen ausschließlich über Liquibase.

---

## 15. Nichtfunktionale Anforderungen

### 15.1 Sicherheit und Datenschutz

- Bot-Token niemals committen oder in Chat, Issue, Screenshot oder Codex-Prompt einfügen
- Token nur lokal beziehungsweise im Secret Store des Hosts
- Bot erhält keine Administratorberechtigung
- Verarbeitung nur erlaubter Server, Channels und Nutzer
- keine allgemeine Speicherung fremder Chats
- keine Tokens oder Passwörter in Logs
- QuadWords-Rohbilder nach 48 Stunden löschen

### 15.2 Zuverlässigkeit

- idempotente Eventverarbeitung
- zuerst speichern, dann veröffentlichen, zuletzt löschen
- keine verlorenen gültigen Ergebnisse bei Discord-Fehlern
- kurze Datenbanktransaktionen ohne wartende Discord-Aufrufe
- Fehler in Status, Bericht oder Kommentar rollen Ergebnisse nicht zurück
- Serien deterministisch aus Ergebnissen und expliziter `Clock` berechnen

### 15.3 Wartbarkeit und Testbarkeit

Klare Trennung von:

- Domain
- Parser
- Application Services
- Discord-Adapter
- Persistence-Adapter
- Scheduling
- Konfiguration

Parser, Serienlogik und Application Services müssen ohne Discord, Datenbank und Container-Runtime testbar sein.

---

## 16. Technologie und Teststrategie

### 16.1 Stack

- Java 21 LTS
- Spring Boot
- JDA
- PostgreSQL
- Liquibase
- Spring Data JPA
- JUnit 5
- Maven
- GitHub Actions
- Testcontainers für verpflichtende PostgreSQL-Integrationstests in CI, sofern zweckmäßig
- optionale Docker-Compose-Konfiguration
- `ImageIO`/`BufferedImage` für Version 2

### 16.2 Lokale Tests

Der lokale Standardbuild führt ohne Docker aus:

- Unit-Tests
- Parser- und Fixture-Tests
- Domain- und Serienregeln
- Application-Tests mit Fakes
- Architekturtests
- Discord-Adaptertests ohne Netzwerk

### 16.3 Datenbankintegration

Ab dem Persistenzinkrement:

- eigenes Maven-Profil `database-integration`
- echtes PostgreSQL
- reale Liquibase-Migrationen
- Tests für Constraints, Upserts, Konkurrenz und Idempotenz
- verpflichtende Ausführung in GitHub Actions
- kein unbemerkter Skip in CI
- optionaler manueller lokaler Start gegen nativ installiertes PostgreSQL

H2 ersetzt diese Integrationstests nicht.

---

## 17. Externe Konfiguration

Mindestens:

```text
DISCORD_BOT_TOKEN
DISCORD_ENABLED
DISCORD_GUILD_ID
DISCORD_CHANNEL_ID
PLAYER_1_USER_ID
PLAYER_1_DISPLAY_NAME
PLAYER_2_USER_ID
PLAYER_2_DISPLAY_NAME
ADMIN_USER_IDS
REMINDER_FIRST_TIME
REMINDER_SECOND_TIME
WEEKLY_REPORT_TIME
MONTHLY_REPORT_TIME
TIME_ZONE
DATABASE_URL
DATABASE_USERNAME
DATABASE_PASSWORD
RAW_IMAGE_RETENTION_HOURS
```

Bestätigte nicht geheime Testwerte:

```text
DISCORD_GUILD_ID=255064124902473729
DISCORD_CHANNEL_ID=1531398793713549494
PLAYER_1_USER_ID=255063936410451978
PLAYER_1_DISPLAY_NAME=Tobias
PLAYER_2_USER_ID=451773931351834634
PLAYER_2_DISPLAY_NAME=Georgia
ADMIN_USER_IDS=255063936410451978,451773931351834634
```

Standardzeiten:

```text
REMINDER_FIRST_TIME=18:00
REMINDER_SECOND_TIME=23:00
WEEKLY_REPORT_TIME=08:00
MONTHLY_REPORT_TIME=08:15
TIME_ZONE=Europe/Berlin
RAW_IMAGE_RETENTION_HOURS=48
```

---

## 18. Qualitätssicherung und Abnahme

### 18.1 Text-Fixtures

Mindestens:

- erfolgreiche und nicht gelöste GridWords-Beispiele
- erfolgreiche und nicht gelöste QuadWords-Beispiele
- Varianten mit und ohne Flammenserie
- unterschiedliche Linkpositionen und Zeilenumbrüche
- Ergebnis für den Vortag kurz nach Mitternacht
- ungültige und unvollständige Beispiele

### 18.2 Bild-Fixtures ab Version 2

Mindestens 15 bis 20 originale QuadWords-Bilder mit unterschiedlichen Versuchszahlen, Farbverteilungen und Bildgrößen sowie erwarteter Unicode-Ausgabe.

### 18.3 Abnahmekriterien Version 1

1. gültiges GridWords-Ergebnis wird erkannt, gespeichert, kanonisch veröffentlicht und erst danach im Original gelöscht
2. GridWords-Ausgabe enthält keinen Gridgames-Link
3. gültiges QuadWords-Ergebnis wird in Version 1 anhand von Kopfzeile und Anhang erkannt
4. QuadWords-Original bleibt in Version 1 bestehen
5. gelöste und nicht gelöste Ergebnisse werden korrekt unterschieden
6. alle persönlichen und gemeinsamen Serien werden korrekt berechnet
7. unvollständiger aktueller Tag und Vortagsnachtrag werden korrekt behandelt
8. Erinnerungen betreffen ausschließlich fehlende Einreichungen
9. doppelte Events erzeugen keine doppelten Datensätze oder Bot-Posts
10. Neustarts erzeugen keine doppelten Erinnerungen
11. andere Nutzer und Channels werden ignoriert
12. bei Veröffentlichungsfehler bleibt das Original bestehen
13. lokaler Standardbuild läuft ohne Docker und PostgreSQL
14. Datenbankintegrationstests laufen in CI gegen echtes PostgreSQL, sobald Persistenz implementiert ist

---

## 19. Betrieb und Hosting

### 19.1 Lokale Entwicklung

Die lokale Entwicklung erfolgt standardmäßig ohne Docker Desktop. Der Discord-Gateway-Smoke-Test ist in dieser Konfiguration erfolgreich.

Für manuelle Persistenzstarts wird natives PostgreSQL unterstützt. Docker Compose bleibt optional.

### 19.2 Dauerbetrieb

Der spätere Host benötigt:

- dauerhaft laufenden Prozess
- ausgehenden Discord-Zugriff
- persistente PostgreSQL-Datenbank
- Neustart nach Absturz oder Reboot
- sichere Secret-Verwaltung
- Backup- und Restore-Konzept

Das vorhandene Netcup Webhosting 2000 ist nicht als primärer Bot-Host eingeplant. Ein kleiner VPS oder Root-Server ist technisch geeigneter.

Auf der vorhandenen WD My Cloud läuft My Cloud OS 5. Eine abschließende Bewertung erfolgt nach Bekanntgabe des exakten Modells und der Firmware; das NAS ist derzeit keine eingeplante Zielplattform.

---

## 20. Repository und aktueller Stand

Repository:

```text
venomenon328/gridwords-bot
```

Dokumentationsstruktur:

```text
docs/
  anforderungsspezifikation.md
  architecture.md
  implementation-plan.md
  development-guide.md
  requirements/
    series-model.md
  adr/
    0001-...
    0002-...
    0003-...
    0004-docker-optional-local-development.md

AGENTS.md
README.md
```

Aktueller Stand:

- Grundgerüst stabilisiert
- CI grün
- Serienmodell abgenommen
- Docker-optionale lokale Entwicklung dokumentiert
- Discord-Gateway-Smoke-Test erfolgreich
- Ergebnisparser und Fachlogik noch nicht implementiert

---

## 21. Noch benötigter Input

Nicht blockierend für das nächste Inkrement:

1. echtes nicht gelöstes GridWords-Share-Ergebnis zur Bestätigung von `X/6`
2. weitere reale GridWords- und QuadWords-Share-Beispiele
3. später ein originales QuadWords-Ergebnisbild ohne umgebenden Discord-Screenshot
4. später exakte Modellbezeichnung und Firmware der WD My Cloud

Der Bot-Token darf niemals in einen Codex-Prompt, Chat, Screenshot oder Git-Commit aufgenommen werden.

---

## 22. Nächster Entwicklungsschritt

Nach Abschluss und Merge von PR #1 folgt **Inkrement 1: reine Share-Textparser**.

Dieses Inkrement:

- benötigt keine Datenbank,
- benötigt keinen Discord-Listener,
- benötigt kein Docker,
- führt die ersten reinen Domain- und Parsertypen ein,
- ergänzt ArchUnit-Regeln,
- wird mit Fixture-basierten Tests umgesetzt.
