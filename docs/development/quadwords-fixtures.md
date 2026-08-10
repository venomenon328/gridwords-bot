# QuadWords-Parser-Fixtures

## Ablage

Fixtures liegen unter `fixtures/quadwords/`:

```text
fixtures/quadwords/
  invalid/
  solved/
  synthetic/
  unsolved/
```

[`../../fixtures/README.md`](../../fixtures/README.md) beschreibt die vorhandenen Beispiele. Neue Binärfixtures werden nur aufgenommen, wenn sie eine konkrete Parsergrenze dauerhaft belegen und keine fremden oder geheimen Inhalte enthalten.

## Abgedeckte Eingaben

Fixture-basierte Tests unterscheiden mindestens:

- gültige gelöste und nicht gelöste QuadWords-Shares,
- gültige PNG- und JPEG-Anhänge,
- beschädigte, abgeschnittene oder anderweitig ungültige Bilder,
- unzulässige Dimensionen und Pixelzahlen,
- Boardreihenfolge oben links, oben rechts, unten links, unten rechts,
- Skalierung, Ränder, Kompressionsartefakte und Grenzwerte des deterministischen Board-Parsers,
- synthetische Fehler- und Ressourcenlimitfälle.

Boardlose aktuelle und historische QuadWords-Kompatibilität wird in Parser-, Application- und Persistenztests abgedeckt; dafür gibt es keinen künstlichen `legacy/`-Fixtureordner. Die produktiven Regeln stehen in [`../product/results-and-publication.md`](../product/results-and-publication.md).

Parser und Fixturetests verwenden keine Discord-Verbindung, Datenbank, OCR, externe API oder nicht deterministische Bilderkennung.

## Pflege

Ein Fixture erhält einen sprechenden Dateinamen und einen Test, der seinen Zweck nennt. Freigegebene reale Bildfixtures verwenden, wo vorgesehen, explizit geprüfte Golden-/Expected-Werte; diese dürfen nicht blind aus einer aktuellen Parserausgabe regeneriert werden. Änderungen am Parser benötigen Erfolgs-, Nicht-gelöst- und Fehlerfälle. Generierte Zwischenartefakte werden nicht committed.
