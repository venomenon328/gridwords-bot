# Manifest des redaktionellen Ausredenkatalogs

Dieses Manifest ist der Einstiegspunkt für Inkrement 11, Paket 8A. Es konsolidiert die verteilten Arbeitsquellen, ihre Mengen und die redaktionelle Präzedenz bis zur Erzeugung des produktiven JSON-Katalogs.

## Gesamtbestand

| Familie | Datei | Anzahl |
|---|---|---:|
| Allgemein und `NOT_SOLVED` | `excuse-catalog-draft.md` | 208 |
| `VERY_LATE_SUBMISSION` | `excuse-catalog-draft-very-late-submission.md` | 58 |
| `GRIDWORDS_LAST_ATTEMPT` | `excuse-catalog-draft-gridwords-last-attempt.md` | 56 |
| `GRIDWORDS_VERY_SLOW` | `excuse-catalog-draft-gridwords-very-slow.md` | 56 |
| `QUADWORDS_VERY_SLOW` | `excuse-catalog-draft-quadwords-very-slow.md` | 56 |
| `QUADWORDS_SINGLE_BOARD_COLLAPSE` | `excuse-catalog-draft-quadwords-single-board-collapse.md` | 72 |
| `CLEAR_CURRENT_DAILY_OUTLIER` | `excuse-catalog-draft-current-daily-outlier.md` | 58 |
| **Gesamt** |  | **564** |

## Ergänzende Quellen

| Datei | Funktion |
|---|---|
| `excuse-catalog-quality-guidelines.md` | verbindlicher Qualitätsmaßstab für Ausredenfunktion, Stil, Tatsachensicherheit und Dubletten |
| `excuse-catalog-review-pass-1.md` | verbindliche Textersetzungen und Ergebnis des ersten Qualitätsdurchgangs |
| `excuse-catalog-general-topic-map.md` | endgültige `ExcuseTopic`-Zuordnung der 144 allgemeinen Texte |
| `../requirements/excuse-catalog-volume.md` | fachlich abgenommener Umfang von 564 Templates; ersetzt die frühere Größenordnung von 70 bis 90 |
| `../../tools/build_excuse_catalog.py` | deterministische Erzeugung und Prüfung von `catalog.json` |

## Präzedenz bei der Katalogisierung

1. Direkt überarbeiteter Text in einem Familienentwurf.
2. Eine explizite Ersetzung aus `excuse-catalog-review-pass-1.md`.
3. Ursprünglicher Text aus dem jeweiligen Familienentwurf.
4. Themenzuordnung aus `excuse-catalog-general-topic-map.md`.
5. Familienmetadaten aus dem jeweiligen Entwurf.
6. Allgemeine Qualitätsregeln.

Bei einem Widerspruch wird nicht geraten. Der Konflikt wird vor Erzeugung des produktiven Katalogs redaktionell aufgelöst.

## Stabile Mengen

- 144 allgemeine Texte: 18 je Stil.
- Jeder der sieben spezifischen Anlässe verwendet alle acht Stile.
- Jede normale Anlass-Stil-Kombination besitzt mindestens sechs Texte.
- Der Board-Zusammenbruch differenziert zusätzlich universelle Texte, ein ungelöstes Board und einen gelösten deutlichen Ausreißer.
- Kein Text wird allein zur Reduktion des Katalogumfangs entfernt.

## Compiler-Invarianten

`tools/build_excuse_catalog.py` prüft vor der Ausgabe mindestens:

- genau 564 Templates und die festgelegten Familienmengen,
- genau 18 allgemeine Texte je Stil,
- mindestens sechs Texte je Anlass und Stil,
- eindeutige IDs,
- keine exakten oder normalisierten Textdubletten,
- ausschließlich bekannte Themen, Bedingungen und Platzhalter,
- `{worstBoard}` nur bei nachweislich eindeutig schlechtestem Board,
- keine unerwünschten Mentions,
- keine sichtbare Verwendung von „Raster“,
- Textlängen innerhalb der Discord-/Schemaschranke,
- Anwendung des Qualitätsdurchgangs und der allgemeinen Themenzuordnung.

Vorgesehene Befehle:

```text
python tools/build_excuse_catalog.py
python tools/build_excuse_catalog.py --check
```

## Noch ausstehende technische Katalogisierung

- den Compiler auf dem vollständigen Repository ausführen,
- erzeugtes `src/main/resources/excuses/catalog.json` committen,
- gegebenenfalls vom Compiler gemeldete Dubletten oder Metadatenkonflikte redaktionell auflösen,
- produktiven Loader und Coverage-Prüfung ausführen,
- Standardbuild vollständig ausführen.

## Paketstatus

Die **redaktionelle Texterstellung**, der erste Qualitätsdurchgang, die Themenzuordnung und der deterministische Compiler sind abgeschlossen. Paket 8A bleibt offen, bis der produktive JSON-Katalog erzeugt, validiert und im Standardbuild erfolgreich geprüft wurde.
