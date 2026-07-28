# Anforderungsspezifikation: GridGames-Discord-Bot

**Version:** 0.4 – abgenommene fachliche Grundlage  
**Stand:** 28. Juli 2026  
**Status:** Fachliche Grundanforderungen, Versionsgrenzen, Zeitpläne, Discord-Zielkonfiguration und das präzisierte Serienmodell sind bestätigt. Das vollständige und verbindliche Serienmodell steht in [`docs/requirements/series-model.md`](requirements/series-model.md).  
**Zielplattform:** Discord-Server, dedizierter Textkanal, zwei fest konfigurierte Spieler

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
7. Ergebnisbeiträge bereinigen und in einer einheitlichen Bot-Darstellung veröffentlichen,
8. QuadWords-Ergebnisbilder ab Version 2 in Unicode-Raster umwandeln,
9. ab Version 2 Wochen- und Monatsberichte erstellen,
10. ab Version 3 abrufbare Statistiken, konfigurierbare Slash-Commands und regelbasierte Kommentare bereitstellen.

Eine eigene Discord Activity ist nicht Bestandteil des Projekts.

---

## 2. Systemgrenzen

### 2.1 Im Projektumfang

- Ein Discord-Bot für genau einen konfigurierten Discord-Server
- Ein dedizierter Textkanal
- Genau zwei konfigurierte Spieler
- GridWords und QuadWords
- Verarbeitung neuer Ergebnisnachrichten
- Sichere kanonische Wiederveröffentlichung erkannter Ergebnisse
- Persistente Speicherung von Ergebnissen, Status, Erinnerungen und Berichten
- Ableitung der Serien aus den gespeicherten Spielergebnissen
- Robustes Verhalten bei Neustarts und mehrfach zugestellten Discord-Events
- Erinnerungen um konfigurierbare Uhrzeiten
- Lokaler Testbetrieb und späterer Dauerbetrieb auf einem geeigneten Host

### 2.2 Nicht im Projektumfang

- Nachbau der Spiele
- Direktes Auslesen des Spielzustands von gridgames.app
- Discord-DMs oder Gruppen-DMs
- Eigene Discord Activity
- Unterstützung beliebig vieler Server, Kanäle oder Spieler
- Generative KI zur Laufzeit
- Spoilerfreie Einreichung in den ersten drei Versionen
- Urlaubstage, Joker oder manuelle Serienrettung
- Gemeinsamer Startmodus
- Automatische Gewinner- oder Punktelogik
- Gemeinsame Aktivitätsserie

---

## 3. Zielumgebung

### 3.1 Testserver

Für Entwicklung und erste Tests wird Tobias' bestehender, derzeit kaum genutzter Discord-Server verwendet.

Bestätigte Zielkonfiguration:

```text
Discord-Server-ID: 255064124902473729
Discord-Channel-ID: 1531398793713549494
Tobias-User-ID: 255063936410451978
Georgia-User-ID: 451773931351834634
```

Der Zielkanal ist als dedizierter privater Testkanal vorgesehen. Nach erfolgreichem Test kann der Bot in einen endgültigen Kanal auf Tobias' Server oder auf Georgias Server umziehen. Alle IDs bleiben externe Konfiguration und werden nicht im Anwendungscode fest verdrahtet.

### 3.2 Zeitzone

Alle fachlichen Zeitberechnungen verwenden:

```text
Europe/Berlin
```

Sommer- und Winterzeit müssen korrekt berücksichtigt werden.

---

## 4. Begriffe und fachliche Definitionen

### 4.1 Spieltag

Der Spieltag ist das im geteilten Gridgames-Ergebnis angegebene Datum. Nicht der Discord-Zeitstempel bestimmt die fachliche Zuordnung.

Beispiel: Ein Ergebnis wird am 28. Juli um 00:03 Uhr gepostet, enthält aber „27. Juli 2026“. Es wird dem 27. Juli zugeordnet.

### 4.2 Eingereichtes beziehungsweise gespieltes Spiel

Ein Spiel gilt als **eingereicht** beziehungsweise für die Serienlogik als **gespielt**, wenn für Spieler, Spieltyp und Spieltag ein syntaktisch und fachlich gültiges Ergebnis gespeichert wurde.

Ein nicht gelöstes, aber korrekt geteiltes Ergebnis zählt als eingereicht und gespielt.

### 4.3 Gelöstes Spiel

Ein Spiel gilt als **gelöst**, wenn das Gridgames-Share-Format an der Ergebnisposition eine numerische Versuchszahl ausweist, beispielsweise `5/6` oder `9/9`.

Ein Spiel gilt als **nicht gelöst**, wenn an derselben Position `X` verwendet wird.

Durch ein reales QuadWords-Beispiel bestätigt:

```text
QuadWords (8. Juli 2026) X/9 in 9:47
```

Für GridWords wird bis zu einem eigenen realen Fixture analog `X/6` angenommen. Diese Annahme muss durch einen automatisierten Parser-Test dokumentiert und später durch ein echtes GridWords-Beispiel bestätigt werden.

### 4.4 Tagesmerkmale und Serien

Die vollständigen Definitionen, Anzeigevorgaben, Regeln für den noch unvollständigen aktuellen Tag und Testfälle stehen verbindlich in [`docs/requirements/series-model.md`](requirements/series-model.md).

Kurzfassung:

- **Aktivitätstag:** mindestens eines der beiden Spiele wurde eingereicht.
- **Kompletter Tag:** GridWords und QuadWords wurden eingereicht, unabhängig vom Lösungsstatus.
- **Perfekter Tag:** beide Spiele wurden eingereicht und gelöst.

Pro Spieler werden unabhängig berechnet:

1. Aktivitätsserie
2. Komplettserie
3. GridWords-Lösungsserie
4. QuadWords-Lösungsserie
5. Perfektserie

Gemeinsam werden berechnet:

1. gemeinsame Komplettserie
2. gemeinsame Perfektserie

Es gibt keine gemeinsame Aktivitätsserie und keine unspezifische persönliche „Lösungsserie“, die GridWords und QuadWords vermischt.

Ein am aktuellen Tag noch fehlendes Ergebnis beendet eine bis gestern laufende Serie nicht vor Tagesende. Ein heute bereits eingereichtes, nicht gelöstes Ergebnis beendet dagegen sofort die betroffene spielbezogene Lösungsserie und gegebenenfalls die Perfektserie. Die Regel wird für jede Serie separat angewendet.

### 4.5 Gridgames-Flammenserie

Die im Share-Text angezeigte Flammenserie, beispielsweise `🔥2`, wird als separates Metadatum gespeichert. Sie ist nicht maßgeblich für die Bot-eigenen Serien.

---

## 5. Benutzer und Rollen

### 5.1 Spieler

- Tobias
- Georgia

Beide werden über ihre Discord-User-ID konfiguriert.

### 5.2 Bot-Administratoren

Tobias und Georgia dürfen administrative Bot-Funktionen verwenden, sobald diese ab Version 3 bereitgestellt werden.

### 5.3 Bot

Der Bot:

- verarbeitet ausschließlich den konfigurierten Channel,
- verarbeitet ausschließlich Beiträge der beiden Spieler,
- ignoriert eigene Bot-Nachrichten, Webhook-Nachrichten und Beiträge anderer Nutzer,
- speichert Resultate vor jeder Veränderung der Discord-Nachricht,
- löscht Originalbeiträge nur nach erfolgreicher kanonischer Wiederveröffentlichung.

---

## 6. Versionsplanung

## 6.1 Version 1 – Kernfunktion und GridWords-Bereinigung

### Muss-Funktionen

1. Discord-Verbindung über einen Bot-Account
2. Externe Konfiguration von:
   - Server-ID
   - Channel-ID
   - zwei Spieler-IDs
   - Anzeigenamen
   - Administrator-IDs
   - Zeitzone
   - erster Erinnerungszeit
   - zweiter Erinnerungszeit
3. Standardzeiten:
   - erste Erinnerung: 18:00 Uhr
   - zweite Erinnerung: 23:00 Uhr
4. Erkennung der GridWords-Kopfzeile
5. Erkennung der QuadWords-Kopfzeile
6. Erkennung des Gelöst-Status: numerischer Share-Wert bedeutet gelöst, `X` bedeutet nicht gelöst; `X/9` ist für QuadWords bestätigt, `X/6` für GridWords vorläufig analog
7. Speicherung aller aus der Kopfzeile gewonnenen Daten
8. Speicherung und Validierung des GridWords-Unicode-Rasters
9. QuadWords wird anhand der Kopfzeile als eingereicht erkannt; das Bild wird noch nicht in Unicode umgewandelt
10. Persistente Tagesstatusverwaltung
11. Persönliche Aktivitäts-, Komplett-, GridWords-Lösungs-, QuadWords-Lösungs- und Perfektserie
12. Gemeinsame Komplett- und Perfektserie
13. Keine gemeinsame Aktivitätsserie
14. Erinnerungen nur bei fehlenden Einreichungen
15. Idempotente Verarbeitung
16. Persistenz über Neustarts hinweg
17. Grundlegende Fehlerprotokollierung
18. Sichere kanonische Bereinigung von GridWords-Nachrichten:
    - Ergebnis vollständig parsen und validieren
    - Ergebnis in der Datenbank speichern
    - bereinigte Bot-Nachricht erfolgreich senden
    - erst danach Originalnachricht löschen
19. Kanonische GridWords-Ausgabe ohne Gridgames-Link und ohne Gridgames-Linkvorschau
20. Kanonische GridWords-Ausgabe mit Ergebnis, Raster, Aktivitätsserie und GridWords-Lösungsserie
21. Kontextabhängige Ergänzung der Komplett- und Perfektserie, wenn das Ergebnis den betreffenden Tageszustand herstellt
22. QuadWords-Originalnachrichten bleiben in Version 1 bestehen, weil das Rasterbild noch nicht ersetzt werden kann
23. Erfolgreich verarbeitete QuadWords-Nachrichten erhalten mindestens eine `✅`-Reaktion
24. Keine Slash-Commands zur Konfiguration; Änderungen erfolgen über externe Konfiguration und Neustart

### Nicht Bestandteil von Version 1

- QuadWords-Bildparser
- QuadWords-Unicode-Ausgabe
- Löschen und Ersetzen von QuadWords-Originalnachrichten
- Wochen- oder Monatsberichte
- Statistik-Commands
- Regelbasierte Kommentare
- Konfigurationsänderungen per Slash-Command

---

## 6.2 Version 2 – QuadWords-Normalisierung und Berichte

### Zusätzliche Muss-Funktionen

1. Download und Auswertung des originalen QuadWords-Ergebnisbildes
2. Erkennung der vier Teilraster
3. Umwandlung in Unicode:
   - weißes Feld: `⬜`
   - gelbes Feld: `🟨`
   - grünes Feld: `🟩`
4. Ausgabe der vier Raster untereinander mit exakt diesen Überschriften:
   - `Oben links`
   - `Oben rechts`
   - `Unten links`
   - `Unten rechts`
5. Kein OCR-Einsatz; die Auswertung erfolgt geometrisch und farbbasiert
6. Ein fehlgeschlagener Bildparser darf die bereits erkannte Einreichung nicht verwerfen
7. Normalisiertes QuadWords-Raster wird gespeichert
8. Sichere kanonische Bereinigung von QuadWords-Nachrichten:
   - Kopfzeile und Bild vollständig parsen und validieren
   - Ergebnis und normalisierte Raster speichern
   - bereinigte Bot-Nachricht erfolgreich senden
   - erst danach Originalnachricht löschen
9. Kanonische QuadWords-Ausgabe ohne Gridgames-Link und ohne Originalbild
10. Kanonische QuadWords-Ausgabe mit Ergebnis, vier Unicode-Rastern, Aktivitätsserie und QuadWords-Lösungsserie
11. Kontextabhängige Ergänzung der Komplett- und Perfektserie
12. Bei unsicherem oder fehlgeschlagenem Bildparser bleibt die Originalnachricht bestehen
13. Wochenbericht am Montagmorgen für die abgeschlossene Woche Montag bis Sonntag
14. Monatsbericht am ersten Tag des Folgemonats
15. Parser-Versionierung
16. Möglichkeit zur kontrollierten Neuverarbeitung noch vorhandener Rohbilder
17. Automatische Löschung temporär gespeicherter QuadWords-Rohbilder nach 48 Stunden

### Standardzeiten für Berichte

- Wochenbericht: Montag 08:00 Uhr
- Monatsbericht: erster Kalendertag 08:15 Uhr

Beide Zeiten werden extern konfigurierbar umgesetzt. Die leichte Zeitversetzung verhindert doppelte Posts zur exakt gleichen Minute, falls der Monat an einem Montag beginnt.

---

## 6.3 Version 3 – Statistik, Kommentare und Discord-Konfiguration

### Zusätzliche Muss-Funktionen

1. Slash-Commands zum Abruf von Statistiken
2. Zeiträume:
   - aktueller Tag
   - aktuelle Woche
   - aktueller Monat
   - gesamter Zeitraum
3. Slash-Commands zur Änderung mindestens folgender Konfiguration:
   - erste Erinnerungszeit
   - zweite Erinnerungszeit
   - Wochenberichtzeit
   - Monatsberichtzeit
4. Regelbasierte, maßgeschneiderte Kommentare
5. Kommentarkategorien:
   - besonders wenige Versuche
   - letzter möglicher Versuch
   - persönliche Bestzeit
   - persönliche Bestleistung
   - identische Ergebnisse beider Spieler
   - kompletter oder perfekter Tag eines Spielers
   - gemeinsam kompletter oder gemeinsam perfekter Tag
   - neue längste Aktivitäts-, Komplett-, spielbezogene Lösungs- oder Perfektserie
6. Mehrere Textvarianten pro Kategorie
7. Höchstens ein Ergebniskommentar pro verarbeitetem Ergebnis
8. Höchstens ein gemeinsamer Abschlusskommentar pro Spieltag
9. Keine externe KI-API

### Beispielhafte Slash-Commands

```text
/status
/statistik zeitraum:woche
/statistik zeitraum:monat
/serie
/bericht woche
/bericht monat
/einstellungen erinnerung-erste 18:00
/einstellungen erinnerung-zweite 23:00
```

---

## 7. Verarbeitung eingehender Nachrichten

## 7.1 Vorbedingungen

Eine Nachricht wird nur geprüft, wenn:

1. sie auf dem konfigurierten Server gepostet wurde,
2. sie im konfigurierten Channel gepostet wurde,
3. ihr Autor einer der beiden Spieler ist,
4. ihr Autor kein Bot und kein Webhook ist.

## 7.2 Zulässiger Spieltag

Beim Eingang eines Ergebnisses wird ausschließlich akzeptiert:

- der aktuelle Spieltag oder
- der unmittelbar vorherige Kalendertag.

Maßgeblich ist `Europe/Berlin`.

Ältere Ergebnisse werden nicht automatisch übernommen. Der Bot antwortet knapp, dass nur Ergebnisse für heute oder gestern nachgetragen werden können.

## 7.3 Erkennungs- und Verarbeitungsreihenfolge

1. Nachricht auf GridWords-Kopfzeile prüfen
2. Nachricht auf QuadWords-Kopfzeile prüfen
3. Daten syntaktisch validieren
4. Spieltag gegen das zulässige Zeitfenster prüfen
5. GridWords-Raster beziehungsweise QuadWords-Anhang validieren
6. Gelöst-Status bestimmen
7. Ergebnis idempotent speichern oder aktualisieren
8. alle betroffenen Serien und den Tagesstatus neu berechnen
9. kanonische Bot-Nachricht erzeugen, soweit die jeweilige Version dies unterstützt
10. Veröffentlichung der Bot-Nachricht bestätigen lassen
11. erst danach Originalnachricht löschen
12. falls keine sichere Ersetzung möglich ist: Originalnachricht unverändert lassen und mit `✅` oder `⚠️` reagieren
13. Tagesstatus aktualisieren
14. ab Version 3 gegebenenfalls Kommentar erzeugen

## 7.4 Zu erfassende Daten

Für beide Spiele:

- Spieltyp
- Spieltag
- eingereicht: ja/nein
- gelöst: ja/nein
- verwendete Versuche beziehungsweise Share-Wert
- maximal verfügbare Versuche
- Dauer in Sekunden
- Gridgames-Flammenserie, sofern vorhanden
- normalisiertes Raster, soweit verfügbar
- Discord-Original-Message-ID
- Discord-ID der kanonischen Bot-Nachricht
- Discord-Channel-ID
- Discord-User-ID
- Empfangszeitpunkt
- Parser-Version
- unveränderter relevanter Rohtext
- optionale temporäre Rohbildreferenz

Die Bot-eigenen Serien werden aus diesen Ergebnissen abgeleitet und müssen nicht als eigenständige Quelle fachlicher Wahrheit gespeichert werden. Persistierte Cache- oder Snapshot-Werte sind nur zulässig, wenn sie jederzeit reproduzierbar sind.

## 7.5 Fehlertoleranz

Der Textparser toleriert mindestens:

- zusätzliche Leerzeichen
- unterschiedliche Zeilenumbrüche
- fehlende Flammenserie
- fehlenden Link
- Link vor oder nach dem Ergebnis
- Unicode-Varianten bei Emojis
- mehrfach zugestellte Discord-Events

Fehlt eine zwingende Information, wird kein gültiges Ergebnis angelegt.

## 7.6 Fehlerverhalten

Wenn eine Nachricht klar wie ein Gridgames-Ergebnis aussieht, aber nicht vollständig geparst werden kann:

- keine Löschung,
- keine kanonische Ersetzung,
- keine Erledigt-Markierung,
- keine Serienänderung,
- Reaktion `⚠️` oder kurze Bot-Antwort,
- strukturierter Logeintrag mit Fehlercode.

Bei einem Fehler der QuadWords-Bildauswertung ab Version 2:

- gültige Kopfdaten bleiben gespeichert,
- das Spiel gilt weiterhin als eingereicht,
- der Gelöst-Status wird nur gesetzt, wenn er ohne unsichere Bildinterpretation zuverlässig feststeht,
- Originalnachricht und Bild bleiben bestehen,
- der Bot weist knapp auf die fehlgeschlagene Rasterumwandlung hin,
- temporäre Dateien werden innerhalb der Aufbewahrungsfrist zur Diagnose vorgehalten.

---

## 8. Kanonische Ergebnisnachrichten

## 8.1 Allgemeine Anforderungen

Die kanonische Nachricht wird vom Bot veröffentlicht und muss eindeutig erkennen lassen, welcher Spieler das Ergebnis eingereicht hat.

Empfohlene Darstellung: Discord-Embed mit:

- Spielername als Autor oder Überschrift
- optional Discord-Avatar des Spielers
- Spielname und Spieltag
- Ergebnis und Dauer
- Unicode-Raster
- Aktivitätsserie
- Lösungsserie des gerade geposteten Spiels
- optional Komplett- und Perfektserie, wenn das Ergebnis den jeweiligen Tageszustand herstellt

Die Nachricht enthält keinen Link zu Gridgames. Sie enthält keine unspezifischen Bezeichnungen wie „Spielserie“ oder „Lösungsserie“ ohne eindeutigen Bezug.

## 8.2 Beispiel GridWords

```text
Tobias · GridWords · 27. Juli 2026
Gelöst in 5/6 · 1:25

⬜⬜🟨⬜🟨
⬜⬜⬜⬜⬜
…

🔥 Aktivität: 12 Tage · GridWords gelöst: 9 Tage
```

## 8.3 Beispiel QuadWords ab Version 2

```text
Tobias · QuadWords · 27. Juli 2026
Gelöst in 9/9 · 4:18

Oben links
⬜⬜⬜🟨⬜
⬜⬜⬜🟨⬜
…

Oben rechts
🟨⬜⬜⬜⬜
⬜🟨⬜⬜🟨
…

Unten links
…

Unten rechts
…

🔥 Aktivität: 12 · Komplett: 8 · QuadWords gelöst: 4
```

## 8.4 Sichere Löschreihenfolge

Eine Originalnachricht darf nur gelöscht werden, wenn alle folgenden Schritte erfolgreich waren:

1. Parser und Validierung erfolgreich
2. Datenbanktransaktion erfolgreich abgeschlossen
3. kanonische Bot-Nachricht erfolgreich veröffentlicht
4. ID der Bot-Nachricht gespeichert

Schlägt die Löschung anschließend fehl, bleibt die doppelte Darstellung bestehen und wird protokolliert. Das gespeicherte Ergebnis darf dadurch nicht verloren gehen.

## 8.5 Korrekturen und erneute Einreichung

Da die Originalnachricht nach erfolgreicher Ersetzung gelöscht ist, erfolgt eine Korrektur durch erneutes Posten eines gültigen Share-Ergebnisses für denselben Spieler, Spieltyp und Spieltag.

Dann gilt:

- der fachliche Ergebnisdatensatz wird aktualisiert,
- die bestehende kanonische Bot-Nachricht wird nach Möglichkeit bearbeitet,
- andernfalls wird eine neue kanonische Nachricht gesendet und die alte Bot-Nachricht anschließend gelöscht,
- Tagesstatus und alle betroffenen Serien werden neu berechnet.

---

## 9. GridWords-Parser

## 9.1 Erwartetes Share-Format

Erfolgreiches Beispiel:

```text
GridWords (27. Juli 2026) 5/6 in 1:25 🔥2
```

Vorläufig erwartetes nicht gelöstes Format:

```text
GridWords (27. Juli 2026) X/6 in 6:42
```

Darunter folgt ein Unicode-Raster sowie regelmäßig ein Gridgames-Link. Das `X/6`-Format ist durch die bestätigte QuadWords-Analogie fachlich vorgesehen, benötigt aber noch ein eigenes reales Fixture.

## 9.2 Anforderungen

Der Parser extrahiert:

- Datum
- Share-Ergebnis
- Maximalversuche
- Dauer
- optionale Gridgames-Serie
- Rasterzeilen
- Gelöst-Status

Das Raster wird auf erlaubte Symbole normalisiert und mit erhaltenen Zeilenumbrüchen gespeichert.

## 9.3 Validierung

- Datum muss parsebar sein.
- Versuchswerte müssen zum bestätigten Share-Format passen.
- Dauer muss parsebar und nicht negativ sein.
- Rasterzeilen müssen eine plausible Struktur besitzen.
- Nicht erkannte Unicode-Zeichen dürfen nicht stillschweigend als gültige Felder gespeichert werden.
- Nicht gelöste Share-Formate müssen durch eigene Fixtures abgesichert werden.

---

## 10. QuadWords-Parser

## 10.1 Version 1

In Version 1 werden Kopfzeile und Metadaten ausgewertet.

Erfolgreiches Beispiel:

```text
QuadWords (27. Juli 2026) 9/9 in 4:18 🔥2
```

Bestätigtes nicht gelöstes Beispiel:

```text
QuadWords (8. Juli 2026) X/9 in 9:47
```

`X` wird als nicht gelöst gespeichert. Die Zeit wird auch bei einem nicht gelösten Ergebnis erfasst.

Das Bild bleibt in der Originalnachricht sichtbar. Der Bot speichert nur eine temporäre Referenz beziehungsweise lädt es optional zur Vorbereitung von Fixtures herunter.

## 10.2 Version 2 – Bildanalyse

### Grundprinzip

1. Bild über den Discord-Anhang laden
2. dunklen Hintergrund erkennen
3. weiße, gelbe und grüne Rechtecke erkennen
4. Rechtecke anhand ihrer Lage in vier Gruppen einteilen
5. Felder innerhalb jeder Gruppe nach Zeile und Spalte ordnen
6. Farbklasse bestimmen
7. Unicode-Raster erzeugen
8. Struktur und Konfidenz validieren

### Robustheitsanforderungen

- keine ausschließlich hart codierten absoluten Pixelkoordinaten
- Toleranz gegenüber moderater Skalierung
- Toleranz gegenüber Kompressionsartefakten
- Toleranz gegenüber geringfügig veränderten Abständen
- Validierung der Feldgrößen und Rasterstruktur
- Abbruch statt falscher Ausgabe bei unzureichender Sicherheit

### Vorgesehene technische Umsetzung

Bevorzugt reine Java-Bildverarbeitung mit `ImageIO` und `BufferedImage`:

- Farbschwellen
- Connected Components oder vergleichbare Flächenerkennung
- Clustering anhand von x-/y-Positionen
- keine native OpenCV-Abhängigkeit in der ersten Umsetzung

OpenCV darf später eingesetzt werden, falls die Fixture-Tests mit der reinen Java-Lösung nicht ausreichend robust sind.

---

## 11. Tagesstatus

## 11.1 Lebenszyklus

Pro Spieltag existiert höchstens eine aktive Tagesstatusnachricht des Bots.

Sie wird erzeugt:

- beim ersten erkannten Ergebnis des Tages oder
- spätestens bei der ersten fälligen Erinnerung.

Sie wird nach jeder relevanten Änderung bearbeitet.

## 11.2 Beispiel

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

Die genaue visuelle Verdichtung darf der Discord-Ausgabeadapter bestimmen. Die Bezeichnungen müssen eindeutig bleiben und Aktivität darf nicht mit vollständiger Erledigung verwechselt werden.

## 11.3 Statusregeln

- `✅`: gültiges Ergebnis vorhanden und gelöst
- `❌`: gültiges Ergebnis vorhanden, aber nicht gelöst
- `⬜`: noch kein gültiges Ergebnis vorhanden
- Ergebniswerte werden nur angezeigt, wenn vorhanden.
- Statusaktualisierungen lösen keine erneuten Erwähnungen aus.

---

## 12. Erinnerungslogik

## 12.1 Konfiguration

Standard:

- erste Erinnerung: 18:00 Uhr
- zweite Erinnerung: 23:00 Uhr
- Zeitzone: `Europe/Berlin`
- täglich, einschließlich Wochenende

Die Zeiten müssen ohne Codeänderung konfigurierbar sein. In Version 1 genügt eine Konfiguration über Umgebungsvariablen oder eine lokale Konfigurationsdatei mit anschließendem Neustart.

Slash-Commands zur Änderung folgen in Version 3.

## 12.2 Erinnerungsgegenstand

Erinnert wird ausschließlich an **noch nicht eingereichte** Spiele.

Ein bereits eingereichtes, aber nicht gelöstes Spiel löst keine weitere Erinnerung aus, weil die Einreichung bereits erfolgt ist.

Die Aktivitätsserie beeinflusst die Erinnerung nicht: Auch wenn ein Spieler bereits einen Aktivitätstag erreicht hat, wird weiterhin an das zweite fehlende Spiel erinnert.

## 12.3 Erste Erinnerung

Um 18:00 Uhr:

- keine Nachricht, wenn alle vier Ergebnisse eingereicht sind,
- ansonsten Erwähnung aller Spieler, bei denen mindestens ein Spiel fehlt,
- Auflistung der jeweils fehlenden Spiele,
- Erzeugung oder Aktualisierung des Tagesstatus.

## 12.4 Zweite Erinnerung

Um 23:00 Uhr:

- keine Nachricht, wenn alle vier Ergebnisse eingereicht sind,
- Erwähnung ausschließlich der weiterhin unvollständigen Spieler,
- Auflistung ausschließlich der weiterhin fehlenden Spiele.

## 12.5 Zuverlässigkeit

- Jede Erinnerungsstufe darf pro Spieltag höchstens einmal gesendet werden.
- Erinnerungen werden persistent protokolliert.
- Nach einem Neustart wird eine fällige, noch nicht gesendete Erinnerung nachgeholt.
- Die zweite Erinnerung ist unabhängig von der ersten.
- Erinnerungsnachrichten und Tagesstatus sind getrennte Nachrichten.

---

## 13. Nachträgliche Ergebnisse

### 13.1 Zulässiges Fenster

Ergebnisse dürfen nur für heute oder gestern eingereicht werden.

### 13.2 Auswirkungen

- Nach einem zulässigen Nachtrag werden alle betroffenen persönlichen und gemeinsamen Serien vollständig neu berechnet.
- Bereits gesendete Erinnerungen werden nicht gelöscht.
- Bereits veröffentlichte Berichte werden zunächst nicht automatisch korrigiert.
- Eine spätere Admin-Korrekturfunktion kann ab Version 3 ergänzt werden.

---

## 14. Berichte ab Version 2

## 14.1 Wochenbericht

Zeitraum: abgeschlossene Kalenderwoche Montag bis Sonntag.

Versand: standardmäßig Montag 08:00 Uhr.

Mindestinhalt pro Spieler:

- Anzahl Aktivitätstage
- Anzahl kompletter Tage
- Anzahl perfekter Tage
- aktuelle und längste Aktivitätsserie
- aktuelle und längste Komplettserie
- aktuelle und längste GridWords-Lösungsserie
- aktuelle und längste QuadWords-Lösungsserie
- aktuelle und längste Perfektserie
- Anzahl GridWords-Einreichungen und Lösungen
- durchschnittliche GridWords-Versuche für gelöste Spiele
- durchschnittliche GridWords-Dauer für gelöste Spiele
- Anzahl QuadWords-Einreichungen und Lösungen
- durchschnittliche QuadWords-Versuche für gelöste Spiele
- durchschnittliche QuadWords-Dauer für gelöste Spiele

Gemeinsam:

- Anzahl gemeinsam kompletter Tage
- Anzahl gemeinsam perfekter Tage
- aktuelle und längste gemeinsame Komplettserie
- aktuelle und längste gemeinsame Perfektserie

## 14.2 Monatsbericht

Zeitraum: abgeschlossener Kalendermonat.

Versand: standardmäßig am ersten Tag des Folgemonats um 08:15 Uhr.

Mindestinhalt analog zum Wochenbericht, zusätzlich:

- Gesamtzahl möglicher Tage
- Anteil der Aktivitäts-, kompletten und perfekten Tage je Spieler
- Anteil gemeinsam kompletter und gemeinsam perfekter Tage
- längste persönliche und gemeinsame Serien innerhalb des Monats

## 14.3 Vergleichslogik

- keine Gesamtgewinner
- keine Rangliste
- keine Zeit als Tie-Breaker
- sachliche Gegenüberstellung
- keine erfundenen Kategorien wie „dramatischster Moment“

---

## 15. Datenmodell

## 15.1 `player`

- Discord-User-ID
- Anzeigename
- aktiv
- Administratorstatus
- erstellt
- geändert

## 15.2 `game_result`

- interne ID
- Spieler-ID
- Spieltyp: `GRIDWORDS` oder `QUADWORDS`
- Spieltag
- eingereicht
- gelöst
- Share-Ergebnis beziehungsweise verwendete Versuche
- Maximalversuche
- Dauer in Sekunden
- Gridgames-Serie
- normalisiertes Raster
- Rohtext
- optionale temporäre Rohbildreferenz
- Original-Discord-Message-ID
- kanonische Bot-Message-ID
- Discord-Channel-ID
- Discord-Guild-ID
- Parser-Version
- Parse-Status
- Parse-Fehlercode
- erstmals empfangen
- zuletzt aktualisiert

### Eindeutigkeitsregel

```text
Spieler + Spieltyp + Spieltag
```

ist fachlich eindeutig.

Serien werden aus den `game_result`-Datensätzen berechnet. Eine spätere Optimierung durch persistierte Snapshots darf das Ergebnis nicht zu einer zweiten, widersprüchlichen Quelle fachlicher Wahrheit machen.

## 15.3 `daily_status_message`

- Spieltag
- Discord-Message-ID
- Discord-Channel-ID
- erstellt
- zuletzt aktualisiert

## 15.4 `reminder_delivery`

- Spieltag
- Erinnerungsstufe: `FIRST` oder `SECOND`
- geplanter Zeitpunkt
- tatsächlicher Sendezeitpunkt
- Status
- Fehlertext

Eindeutigkeit:

```text
Spieltag + Erinnerungsstufe
```

## 15.5 `report_delivery`

Ab Version 2:

- Berichtstyp
- Beginn des Berichtszeitraums
- Ende des Berichtszeitraums
- Discord-Message-ID
- Sendezeitpunkt
- Status

## 15.6 `source_artifact`

Für temporäre QuadWords-Bilder:

- interne ID
- Ergebnis-ID
- lokaler oder externer Speicherpfad
- Prüfsumme
- Dateityp
- gespeichert am
- automatisch zu löschen am
- gelöscht am

Standard-Aufbewahrung: 48 Stunden.

---

## 16. Verhalten bei Duplikaten und Änderungen

### 16.1 Doppelte Zustellung desselben Events

Es entsteht kein zweiter Ergebnisdatensatz und keine zweite kanonische Nachricht.

### 16.2 Zweite Einreichung für denselben Tag

Bei einer weiteren gültigen Einreichung desselben Spielers für denselben Spieltyp und Spieltag:

- wird der bestehende Datensatz aktualisiert,
- wird die bestehende kanonische Bot-Nachricht bearbeitet oder sicher ersetzt,
- werden Tagesstatus und alle betroffenen Serien neu berechnet.

### 16.3 Manuell gelöschte kanonische Bot-Nachricht

Der Ergebnisdatensatz bleibt bestehen. Beim nächsten Statusaufbau kann der Bot die fehlende kanonische Nachricht protokollieren, muss sie aber in Version 1 nicht automatisch wiederherstellen.

### 16.4 Vom Bot gelöschte Originalnachricht

Das entsprechende Discord-Delete-Event darf den gespeicherten Ergebnisdatensatz nicht entfernen.

---

## 17. Nichtfunktionale Anforderungen

### 17.1 Sicherheit

- Bot-Token niemals im Repository speichern
- Token ausschließlich über Secret oder Umgebungsvariable
- `.env` und lokale Secret-Dateien in `.gitignore`
- Token nicht in Logs ausgeben
- Bot erhält keine Administratorberechtigung
- Berechtigungen auf den Zielkanal begrenzen
- nur erlaubte Nutzer und Kanäle verarbeiten
- aus Nutzereingaben übernommene Texte gegen unerwünschte Erwähnungen absichern

### 17.2 Datenschutz und Datenminimierung

- keine Verarbeitung anderer Channels
- keine Speicherung allgemeiner Chatnachrichten
- nur relevante Gridgames-Daten speichern
- QuadWords-Rohbilder nach 48 Stunden automatisch löschen
- Logs enthalten standardmäßig keine vollständigen fremden Nachrichtentexte

### 17.3 Zuverlässigkeit

- Datenbanktransaktionen für Ergebnisverarbeitung
- idempotente Eventverarbeitung
- zuerst speichern, dann veröffentlichen, zuletzt löschen
- keine verlorenen gültigen Ergebnisse bei Discord-Ausgabefehlern
- Fehler in Status, Berichten oder Kommentaren rollen die Ergebnisspeicherung nicht zurück
- Serien sind deterministisch aus den Ergebnissen und einer expliziten `Clock` berechenbar

### 17.4 Wartbarkeit

Klare Trennung zwischen:

- Discord-Adapter
- Textparser
- Bildparser
- Ergebnisservice
- kanonischem Nachrichtenservice
- Tagesstatusservice
- Serienservice
- Erinnerungsservice
- Berichtsservice
- Statistikservice
- Persistenz
- Konfiguration

Parser müssen mit versionierten Fixtures automatisiert testbar sein. Serienregeln müssen unabhängig von Discord und Datenbank testbar sein.

### 17.5 Beobachtbarkeit

- strukturierte Logs
- eindeutige Fehlercodes
- Start- und Verbindungsstatus im Log
- optionaler Health-Endpunkt später
- Diagnose-Command ab Version 3 möglich

---

## 18. Technologie-Stack

Vorgesehen:

- Java 21 LTS
- Spring Boot
- JDA
- PostgreSQL
- Liquibase
- Spring Data JPA
- JUnit 5
- Testcontainers
- Maven
- Docker Compose
- Java `ImageIO`/`BufferedImage` für Version 2

Die Anwendung läuft als nicht-webbasierte Spring-Boot-Anwendung und empfängt Discord-Ereignisse über die Gateway-Verbindung.

Ein öffentlicher HTTP-Endpunkt ist für die vorgesehene Bot-Funktion nicht erforderlich.

---

## 19. Externe Konfiguration

Mindestens:

```text
DISCORD_BOT_TOKEN
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

Bestätigte nicht geheime Zielwerte für die Testumgebung:

```text
DISCORD_GUILD_ID=255064124902473729
DISCORD_CHANNEL_ID=1531398793713549494
PLAYER_1_USER_ID=255063936410451978
PLAYER_1_DISPLAY_NAME=Tobias
PLAYER_2_USER_ID=451773931351834634
PLAYER_2_DISPLAY_NAME=Georgia
ADMIN_USER_IDS=255063936410451978,451773931351834634
```

Standardwerte:

```text
REMINDER_FIRST_TIME=18:00
REMINDER_SECOND_TIME=23:00
WEEKLY_REPORT_TIME=08:00
MONTHLY_REPORT_TIME=08:15
TIME_ZONE=Europe/Berlin
RAW_IMAGE_RETENTION_HOURS=48
```

Token und Discord-IDs besitzen keine produktiven Standardwerte.

---

## 20. Testdaten und Qualitätssicherung

## 20.1 Text-Fixtures

Mindestens:

- 10 echte erfolgreiche GridWords-Share-Texte
- mindestens 2 nicht gelöste GridWords-Share-Texte; bis zum ersten echten Beispiel wird `X/6` als vorläufiges Fixture verwendet
- 10 echte erfolgreiche QuadWords-Share-Texte
- mindestens 2 nicht gelöste QuadWords-Share-Texte; ein reales Beispiel mit `X/9` liegt bereits vor
- Varianten mit und ohne Flammenserie
- Link an unterschiedlichen Positionen
- mindestens ein Ergebnis kurz nach Mitternacht für den Vortag
- fehlerhafte und unvollständige Beispiele

## 20.2 Bild-Fixtures für Version 2

Mindestens 15 bis 20 originale QuadWords-Ergebnisbilder:

- unterschiedliche Versuchszahlen
- unterschiedliche Farbverteilungen
- erfolgreiche und nicht erfolgreiche Spiele
- möglichst unterschiedliche Bildgrößen, falls Gridgames solche erzeugt
- möglichst Originalbilder statt Discord-Screenshots
- erwartete Unicode-Ausgabe als separate Textdatei

## 20.3 Abnahmetests Version 1

1. Gültiges GridWords-Ergebnis wird erkannt, gespeichert, kanonisch veröffentlicht und erst danach im Original gelöscht.
2. Die kanonische GridWords-Nachricht enthält keinen Gridgames-Link.
3. Gültiges QuadWords-Ergebnis wird anhand der Kopfzeile erkannt und gespeichert.
4. QuadWords-Originalnachricht bleibt in Version 1 bestehen.
5. Erfolgreiche und nicht erfolgreiche Ergebnisse werden korrekt unterschieden.
6. Aktivitätstag, kompletter Tag und perfekter Tag werden korrekt bestimmt.
7. Persönliche Aktivitäts-, Komplett-, GridWords-Lösungs-, QuadWords-Lösungs- und Perfektserie werden separat korrekt berechnet.
8. Gemeinsame Komplett- und Perfektserie werden korrekt berechnet; eine gemeinsame Aktivitätsserie wird nicht erzeugt.
9. Der unvollständige aktuelle Tag wird für jede Serie entsprechend dem verbindlichen Serienmodell behandelt.
10. Vortagsnachträge berechnen alle betroffenen Serien neu.
11. Erinnerung erwähnt nur Spieler mit fehlenden Einreichungen.
12. Ein nicht gelöstes, aber eingereichtes Spiel erzeugt keine erneute Erinnerung.
13. Ergebnisse für heute und gestern werden akzeptiert.
14. Ältere Ergebnisse werden abgelehnt.
15. Doppeltes Event erzeugt keinen doppelten Datensatz oder Bot-Post.
16. Neustart erzeugt keine doppelten Erinnerungen.
17. Nachrichten anderer Nutzer oder Channels werden ignoriert.
18. Schlägt die Bot-Veröffentlichung fehl, bleibt die Originalnachricht bestehen.

## 20.4 Abnahmetests Version 2

1. Jedes freigegebene QuadWords-Testbild wird korrekt in vier Raster zerlegt.
2. Die Raster werden korrekt als `Oben links`, `Oben rechts`, `Unten links` und `Unten rechts` ausgegeben.
3. Alle Feldfarben werden korrekt normalisiert.
4. Unbekannte oder beschädigte Bilder führen zu kontrollierten Parserfehlern.
5. Bei Parserfehler bleibt die Originalnachricht bestehen.
6. Bei erfolgreichem Parser wird die kanonische Bot-Nachricht gesendet und erst danach das Original gelöscht.
7. Wochen- und Monatsbericht verwenden exakt den vorgesehenen Zeitraum und die Begriffe des Serienmodells.
8. Kein Bericht wird doppelt gesendet.
9. Rohbilder werden nach 48 Stunden gelöscht.

---

## 21. Discord-Voraussetzungen

Benötigt werden:

1. Discord-Application mit Bot-User
2. aktivierter Message-Content-Intent
3. Installation des Bots auf Tobias' Testserver
4. dedizierter privater Testkanal
5. aktivierter Discord-Entwicklermodus zum Kopieren der IDs
6. sicher lokal gespeicherter Bot-Token

Application, Bot-Installation und Channelrechte sind eingerichtet. Der reale Gateway-Smoke-Test mit lokal gesetztem Token steht noch aus.

### Minimale Channel-Berechtigungen

- Channel sehen
- Nachrichten senden
- Nachrichtenverlauf lesen
- Reaktionen hinzufügen
- Nachrichten verwalten
- Links einbetten
- Dateien anhängen

Der Bot soll ausdrücklich keine Administratorberechtigung erhalten.

---

## 22. Betrieb und Hosting

### 22.1 Lokale Entwicklung

Version 1 wird zunächst lokal entwickelt und gestartet. Für Parser- und Discord-Tests genügt ein laufender Entwicklungsrechner.

### 22.2 Dauerbetrieb

Für zuverlässige Erinnerungen muss der Bot später dauerhaft laufen und benötigt:

- einen langfristig laufenden Prozess
- ausgehenden Internetzugriff zu Discord
- persistenten Speicher
- eine PostgreSQL-Datenbank
- Neustart nach Abstürzen oder Host-Reboots
- sichere Secret-Verwaltung

### 22.3 Netcup-Webhosting

Das vorhandene Produkt ist nach aktuellem Kenntnisstand wahrscheinlich **Webhosting 2000**.

Dieser Tarif ist für den vorgesehenen Java-/JDA-Bot nicht als Dauerlaufzeit einzuplanen:

- Er ist als klassisches Webhosting für Webseiten und PHP-Anwendungen positioniert.
- Java ist keine ausgewiesene Anwendungslaufzeit des Tarifs.
- Der Bot benötigt einen dauerhaft laufenden Prozess mit permanenter Discord-Gateway-Verbindung.
- Cronjobs ersetzen keinen kontinuierlich verbundenen Bot-Prozess.

Das Webhosting kann gegebenenfalls ergänzend für statische Projektseiten oder Downloads verwendet werden, aber nicht als primärer Bot-Host.

Ein kleiner VPS oder Root-Server ist technisch geeignet. Die konkrete Hostingentscheidung wird erst vor dem produktiven Dauerbetrieb getroffen und blockiert die lokale Entwicklung nicht.

### 22.4 WD My Cloud

Auf dem vorhandenen Gerät läuft nach Nutzerangabe **My Cloud OS 5**. Die genaue Modellbezeichnung und Firmwareversion werden später nachgereicht.

SSH kann bei My-Cloud-OS-5-Geräten grundsätzlich verfügbar sein, Änderungen über SSH und Drittsoftware sind jedoch kein bevorzugter, wartungsarmer Betriebsweg. Ohne genaue Modellbezeichnung wird das NAS nicht als Zielplattform eingeplant.

---

## 23. Repository

Repository:

```text
venomenon328/gridwords-bot
```

Das Repository ist privat. Tobias und der verbundene GitHub-Zugriff besitzen Schreibrechte.

Aktuelle Dokumentationsstruktur:

```text
docs/
  anforderungsspezifikation.md
  architecture.md
  implementation-plan.md
  development-guide.md
  requirements/
    series-model.md
  adr/

AGENTS.md
README.md
```

Die fachliche Spezifikation ist mit Version 0.4 einschließlich des referenzierten Serienmodells abgenommen.

---

## 24. Noch benötigter Input

### 24.1 Nicht blockierend für die nächsten Entwicklungsinkremente

1. Exakte Modellbezeichnung und Firmwareversion des WD-My-Cloud-Geräts
2. Ein direktes originales QuadWords-Ergebnisbild ohne umgebenden Discord-Screenshot für robuste Bildparser-Fixtures
3. Ein echtes nicht gelöstes GridWords-Share-Ergebnis zur Bestätigung von `X/6`
4. Weitere reale Share-Beispiele für automatisierte Parser-Tests

### 24.2 Vor dem ersten echten Discord-Verbindungstest

1. Bot-Token ausschließlich lokal als Secret konfigurieren
2. PostgreSQL lokal starten
3. Anwendung mit aktiviertem Discord und Datenbankprofil starten
4. Online-Status, Gateway-Verbindung und Zugriff auf den Testchannel prüfen

### 24.3 Geheimnisse

Der Bot-Token wird benötigt, darf aber weder im Chat noch im Repository geteilt werden. Er wird ausschließlich lokal beziehungsweise später als Hosting-Secret gesetzt.

---

## 25. Nächster konkreter Schritt für Tobias

Auf dem privaten Rechner:

1. Branch `setup/project-scaffold` auschecken und aktualisieren.
2. `.env.example` nach `.env` kopieren.
3. den Bot-Token ausschließlich lokal in `.env` setzen und Discord aktivieren.
4. PostgreSQL über Docker Compose starten.
5. Offline-Build und anschließend den Discord-Gateway-Smoke-Test ausführen.

Der Bot-Token darf niemals in einen Codex-Prompt, Chat, Screenshot oder Git-Commit aufgenommen werden.
