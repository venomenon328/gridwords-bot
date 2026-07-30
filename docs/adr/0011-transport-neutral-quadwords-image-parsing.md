# ADR 0011: Transportneutrale QuadWords-Bildanalyse

- **Status:** akzeptiert
- **Datum:** 30. Juli 2026
- **Entscheidungsträger:** Tobias / Projektarchitektur
- **Betrifft:** Inkrement 6, Issue #13

## Kontext

QuadWords teilt das fachliche Ergebnis in einer Textkopfzeile, die vier Spielraster jedoch ausschließlich als Bildanhang. Der bisherige Version-1-Ablauf prüfte nur, dass ein plausibles Bild vorhanden war, und speicherte deshalb noch keine normalisierten Boards.

Die Bildanalyse muss gleichzeitig:

- unabhängig von JDA und Discord-CDN-URLs bleiben,
- keine lange Operation auf dem JDA-Event-Thread ausführen,
- bei unbekannten Layouts oder Farben kontrolliert abbrechen,
- mit echten PostgreSQL-Ergebnissen, Replay und Korrekturen zusammenarbeiten,
- bereits vorhandene `quadwords-share-v1`-Datensätze erhalten,
- ohne OCR, ML oder Laufzeit-KI auskommen.

## Entscheidung

### 1. Transportneutrale Attachment-Referenz

Der unveränderliche Inbound-Snapshot enthält für abrufbare Anhänge eine `AttachmentReference` aus:

- Channel-ID,
- Source-Message-ID,
- Attachment-ID.

Die Referenz enthält keine JDA-Typen und keine URL. Ein schmaler `AttachmentContentLoader`-Port lädt die Bytes. Der JDA-Adapter löst exakt diese Referenz auf.

Der Download beginnt erst nach erfolgreichem QuadWords-Kopfparse und eindeutiger Auswahl genau eines plausiblen Ergebnisbilds. Er läuft im Application-Executor, nicht auf dem JDA-Event-Thread und nicht innerhalb einer Datenbanktransaktion.

### 2. Reine Java-Bildanalyse

Der Parser verwendet ausschließlich Java-Standardmittel, insbesondere `ImageIO` und `BufferedImage`.

Unterstützte Formate:

- PNG,
- JPEG.

WebP, GIF, BMP und andere Formate werden stabil abgelehnt. Es wird keine zusätzliche Decoderbibliothek eingeführt.

Der Parser erkennt vier Boards in einer 2×2-Anordnung, jeweils mit fünf Spalten. Er normalisiert sie in der Reihenfolge:

1. oben links,
2. oben rechts,
3. unten links,
4. unten rechts.

Zellen werden anhand mehrerer Flächenstichproben und einer Mindestkonfidenz in `⬜`, `🟨` und `🟩` klassifiziert. Einzelne fest codierte Pixel sind nicht maßgeblich. Unsichere Geometrie, Struktur oder Farbe wird nicht geraten.

Klar fehlende nachlaufende Zeilen eines bereits früher abgeschlossenen Teilboards werden als kanonische Leerzellen ergänzt. Zusätzliche erkannte aktive Zeilen oberhalb der aus der Kopfzeile erwarteten Versuchszahl werden abgelehnt.

### 3. Ressourcenlimits

Zentral gelten:

```text
maximale Eingabegröße: 8 MiB
maximale Breite:       4096 Pixel
maximale Höhe:         4096 Pixel
maximale Fläche:       12.000.000 Pixel
```

Das Byte-Limit wird vor und während des Downloads geprüft. Dimensionen werden vor dem vollständigen Decode über den `ImageReader` validiert.

### 4. Domänen- und Persistenzmodell

`QuadWordsBoard` beschreibt ein normalisiertes Raster mit genau fünf Spalten und kanonischen Symbolen. `QuadWordsBoards` enthält genau vier benannte Boards in der festgelegten Reihenfolge.

Die Parser-Version lautet:

```text
quadwords-image-v2
```

PostgreSQL speichert die Boards in vier explizit benannten Spalten. Neue bildgestützte QuadWords-Ergebnisse müssen alle vier Spalten befüllen; teilweise befüllte Sätze sind ungültig.

Bestehende Ergebnisse mit `quadwords-share-v1` dürfen weiterhin ohne Boards existieren. Die erste gültige bildgestützte Korrektur aktualisiert denselben fachlichen Ergebnisdatensatz in-place und setzt Boards und Parser-Version.

### 5. Fehlersemantik

Stabile fachliche Bildfehler führen zu:

- persistierter Parserablehnung,
- keinem gültigen Ergebnis,
- sichtbarer Originalnachricht,
- `⚠️`.

Technische Attachment-Fehler vor der Ergebnisspeicherung führen zu:

- `FAILED_RETRYABLE`,
- keinem gültigen Ergebnis,
- keiner `✅`- oder `⚠️`-Reaktion,
- möglicher Wiederaufnahme derselben Submission.

Ein sicher geparstes und persistiertes QuadWords-Ergebnis erhält weiterhin `✅`. In Inkrement 6 wird die Originalnachricht weder kanonisch ersetzt noch gelöscht.

### 6. Keine Rohbildpersistenz in Inkrement 6

Die Bytes werden nur im Arbeitsspeicher verarbeitet. Es gibt keine Rohbildtabelle, keinen Dateispeicher und keinen Bereinigungsjob. Dadurch wird die maximale Aufbewahrungsdauer von 48 Stunden sicher unterschritten.

Eine spätere persistente Rohbildablage benötigt ein eigenes ADR mit Ablaufzeitpunkt, idempotenter Bereinigung und Datenschutzregeln.

## Begründung

- Opaque IDs bleiben stabiler und weniger infrastrukturspezifisch als temporäre URLs.
- Der Application-Kern bleibt ohne JDA testbar.
- Standard-Java-Bildverarbeitung reicht für das bestätigte farbige Rasterlayout aus.
- Fail-closed-Verhalten ist wichtiger als maximale Erkennungsquote.
- Vier benannte Spalten und typisierte Boards machen Reihenfolge und Vollständigkeit nachvollziehbar.
- Legacy-Kompatibilität verhindert, dass eine Schemaänderung bereits gespeicherte Version-1-Ergebnisse unlesbar macht.
- In-Memory-Verarbeitung vermeidet unnötige Speicherung privater Anhänge und einen zusätzlichen Lifecycle.

## Folgen

### Positiv

- Parser-, Application- und Adapterlogik sind getrennt testbar.
- Reale Fixtures besitzen exakte Golden-Ausgaben.
- Technische Discord-Probleme werden nicht als Nutzerfehler dargestellt.
- Replay und Korrektur können denselben fachlichen Datensatz fortsetzen.
- Es entsteht keine neue externe Bibliothek und keine Rohbildaufbewahrung.

### Negativ

- WebP wird zunächst nicht unterstützt.
- Der Parser ist bewusst konservativ und kann unbekannte Layoutänderungen ablehnen.
- Geometrie- und Farbschwellen müssen bei tatsächlichen Gridgames-Layoutänderungen anhand neuer Fixtures angepasst werden.
- Legacy-Kompatibilität benötigt bis zur vollständigen bildgestützten Neuverarbeitung einen speziellen Lese-/Upgradepfad.

## Alternativen

### OCR oder ML

Verworfen. Buchstaben sind fachlich irrelevant; zusätzliche Abhängigkeiten, Unsicherheit und Laufzeitkomplexität sind nicht gerechtfertigt.

### Discord-CDN-URL im Application-Modell

Verworfen. URLs sind transport- und zeitabhängig und würden die Adaptergrenze verletzen.

### Zusätzliche WebP-Bibliothek

Vorläufig verworfen. Die bestätigten Fixtures sind PNG; PNG und JPEG werden von der Standardbibliothek unterstützt. Eine neue Bibliothek benötigt einen konkreten realen Bedarf.

### Rohbild immer persistieren

Verworfen. Für den aktuellen Live-Ablauf ist die persistierte Submission plus erneut zugestelltes beziehungsweise wiederholbares Attachment ausreichend; dauerhafte Bildspeicherung wäre zusätzlicher Datenschutz- und Bereinigungsaufwand.

### Ein generisches beliebig großes Boardmodell

Verworfen. Genau vier benannte Boards sind fachlich klarer, sicherer und leichter zu testen.

## Verwandte Dokumente

- `docs/architecture.md`
- `docs/implementation-plan.md`
- `docs/increments/06-quadwords-image-parser.md`
- ADR 0002 zur sicheren Nachrichtenersetzung
- ADR 0010 zur Docker-verfügbaren lokalen Entwicklung
