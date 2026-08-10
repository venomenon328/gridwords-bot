# QuadWords-Parser-Fixtures

## Ablage

Fixtures liegen unter `fixtures/quadwords/`:

```text
fixtures/quadwords/
  valid/
  invalid/
  legacy/
```

[`../../fixtures/README.md`](../../fixtures/README.md) beschreibt die vorhandenen Beispiele. Neue Binärfixtures werden nur aufgenommen, wenn sie eine konkrete Parsergrenze dauerhaft belegen und keine fremden oder geheimen Inhalte enthalten.

## Abgedeckte Eingaben

Fixture-basierte Tests unterscheiden mindestens:

- gültige gelöste und nicht gelöste QuadWords-Shares,
- gültige PNG- und JPEG-Anhänge,
- fehlende, mehrdeutige, beschädigte oder zu große Bilder,
- unzulässige Dimensionen und Pixelzahlen,
- Boardreihenfolge oben links, oben rechts, unten links, unten rechts,
- Grenzwerte des deterministischen Board-Parsers,
- historische boardlose Ergebnisse.

Die produktiven Grenzen stehen in [`../product/results-and-publication.md`](../product/results-and-publication.md). Parser und Fixturetests verwenden keine Discord-Verbindung, Datenbank, OCR, externe API oder nicht deterministische Bilderkennung.

## Pflege

Ein Fixture erhält einen sprechenden Dateinamen und einen Test, der seinen Zweck nennt. Änderungen am Parser benötigen Erfolgs-, Nicht-gelöst- und Fehlerfälle. Erwartungswerte werden im Test oder einer kleinen Begleitdatei dokumentiert; generierte Zwischenartefakte werden nicht committed.
