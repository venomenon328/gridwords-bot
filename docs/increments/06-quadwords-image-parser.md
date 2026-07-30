# Inkrement 6: QuadWords-Bildparser

**Status:** automatisiert umgesetzt; lokale Maven-Abnahme und echter Smoke-Test ausstehend  
**Issue:** #13  
**Draft-PR:** #14  
**Branch:** `feature/quadwords-image-parser`

## Ziel

QuadWords-Ergebnisbilder werden ohne OCR geometrisch und farbbasiert in vier transportneutrale, normalisierte Raster überführt und zusammen mit der Parser-Version persistent gespeichert.

## Umgesetzte Architektur

- transportneutrale `AttachmentReference` aus Channel-, Message- und Attachment-ID
- `AttachmentContentLoader`-Port mit JDA-Adapter
- Kopfzeilenprüfung und eindeutige Bildauswahl vor dem Download
- Download und Decode außerhalb des JDA-Event-Threads und außerhalb von DB-Transaktionen
- reine Java-Bildanalyse mit `ImageIO` und `BufferedImage`
- keine JDA-, Spring-, Datenbank-, Netzwerk-, OCR-, ML- oder KI-Abhängigkeit im Parser
- typisierte Domänentypen `QuadWordsBoard` und `QuadWordsBoards`
- Parser-Version `quadwords-image-v2`
- vier explizit benannte PostgreSQL-Spalten und Legacy-Kompatibilität für `quadwords-share-v1`
- keine persistente Rohbildablage; Verarbeitung ausschließlich im Arbeitsspeicher

## Parserregeln

Unterstützt:

- PNG
- JPEG

Grenzen:

```text
8 MiB Eingabebytes
4096 Pixel je Dimension
12.000.000 Pixel insgesamt
```

Der Parser erkennt eine 2×2-Anordnung mit jeweils fünf Spalten. Die kanonische Reihenfolge lautet:

1. `Oben links`
2. `Oben rechts`
3. `Unten links`
4. `Unten rechts`

Die Farbklassifikation verwendet Flächenstichproben und eine Mindestkonfidenz für `⬜`, `🟨` und `🟩`. Unsichere Geometrie, Farbe, Struktur, Ressourcenüberschreitung oder zusätzliche aktive Zeilen führen zu einem stabilen Parsefehler. Klar fehlende nachlaufende Zeilen bereits früher abgeschlossener Teilboards werden als Leerzellen normalisiert.

## Persistenz und Recovery

- vier Boards und Parser-Version werden gemeinsam mit dem Ergebnis gespeichert
- Replay bleibt idempotent
- eine Korrektur aktualisiert weiterhin denselben fachlichen Ergebnisdatensatz
- alte `quadwords-share-v1`-Ergebnisse dürfen ohne Boards bestehen bleiben
- die erste bildgestützte Korrektur aktualisiert einen solchen Legacy-Datensatz in-place
- technische Attachment-Fehler vor der Ergebnisspeicherung werden als `FAILED_RETRYABLE` persistiert
- ein erfolgreicher Replay nimmt die Submission wieder nach `VALIDATED` auf und speichert das Ergebnis
- teilweise befüllte Boardspalten sind durch Persistenzlogik und Constraint ungültig

## Discord-Verhalten

- gültiges QuadWords: Original bleibt sichtbar und erhält nach erfolgreicher Persistenz `✅`
- stabil fachlich ungültiges Bild: Original bleibt sichtbar, kein Ergebnis, `⚠️`
- technischer Attachmentfehler: Original bleibt sichtbar, kein Ergebnis, keine irreführende Reaktion
- GridWords bleibt unverändert
- keine kanonische QuadWords-Nachricht
- keine QuadWords-Quelllöschung

## Automatisierte Tests

- Domäneninvarianten für Boardanzahl, Zeilen, fünf Spalten, Symbole und Spieltyp
- reale PNG-Fixtures mit exakten `.expected.txt`-Golden-Ausgaben
- synthetische Skalierungs-, Rand-, JPEG-, Beschädigungs-, Farb- und Ressourcenfälle
- Application-Tests für Auswahl, Downloadreihenfolge, fachliche Ablehnung, technischen Retry, Replay und Korrektur
- JDA-Adaptertests für exakte IDs, Größenlimit und Fehlerübersetzung
- ArchUnit-Regeln für die Schichtgrenzen
- PostgreSQL-Integration für Migration, Round-trip, Replay, Korrektur, technischen Retry und Legacy-Upgrade
- Spring-Startup mit Profil `database`

## Noch ausstehende Abnahme

Tobias führt lokal aus:

```powershell
mvn --batch-mode --no-transfer-progress clean verify
mvn --batch-mode --no-transfer-progress -Pdatabase-integration clean verify
```

Danach folgt der echte Discord-/PostgreSQL-Smoke-Test mit mindestens:

- realem gelöstem QuadWords-Bild,
- realem `X/9`,
- visuellem Vergleich aller vier Boards und Zellfarben,
- ungültigem beziehungsweise nicht unterstütztem Bild,
- Korrektur desselben Spieltags,
- Neustart ohne Duplikate,
- unverändertem GridWords-Ablauf.

PR #14 bleibt bis zu dieser Abnahme Draft und ungemergt.

## Ausdrückliche Nicht-Ziele

- keine kanonische QuadWords-Bot-Nachricht
- keine QuadWords-Quelllöschung
- kein Tagesstatus und keine Erinnerungen
- keine Berichte, Slash-Commands oder Kommentare
- kein OCR, ML oder Laufzeit-KI
- kein Backfill alter Channelnachrichten
