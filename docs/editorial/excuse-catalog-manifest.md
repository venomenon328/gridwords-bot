# Manifest des redaktionellen Ausredenkatalogs

Dieses Manifest ist der Einstiegspunkt für Inkrement 11, Paket 8A. Es konsolidiert die redaktionellen Quellen, ihre Mengen, die Qualitätsregeln und den produktiven Katalog.

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

Der produktive Snapshot liegt unter:

```text
src/main/resources/excuses/catalog.json
```

Katalogversion:

```text
2026.08.04.1
```

## Ergänzende Quellen

| Datei | Funktion |
|---|---|
| `excuse-catalog-quality-guidelines.md` | verbindlicher Qualitätsmaßstab für Ausredenfunktion, Stil, Tatsachensicherheit und Dubletten |
| `excuse-catalog-review-pass-1.md` | verbindliche Textersetzungen und Ergebnis des ersten Qualitätsdurchgangs |
| `excuse-catalog-general-topic-map.md` | endgültige `ExcuseTopic`-Zuordnung der 144 allgemeinen Texte |
| `../requirements/excuse-catalog-volume.md` | fachlich abgenommener Umfang von 564 Templates; ersetzt die frühere Größenordnung von 70 bis 90 |
| `../../tools/build_excuse_catalog.py` | deterministische Erzeugung und Prüfung von `catalog.json` |

## Präzedenz bei späteren Änderungen

1. Produktiver Katalog und stabile Template-ID.
2. Direkt überarbeiteter Text in einem Familienentwurf.
3. Eine explizite Ersetzung aus `excuse-catalog-review-pass-1.md`.
4. Ursprünglicher Text aus dem jeweiligen Familienentwurf.
5. Themenzuordnung aus `excuse-catalog-general-topic-map.md`.
6. Familienmetadaten aus dem jeweiligen Entwurf.
7. Allgemeine Qualitätsregeln.

Eine bestehende ID wird nicht für eine neue Witzprämisse umgedeutet. Eine inhaltlich neue Ausrede erhält eine neue ID. Bei einem Widerspruch wird nicht geraten; er wird vor einer neuen Katalogversion redaktionell aufgelöst.

## Stabile Mengen

- 144 allgemeine Texte: 18 je Stil.
- Jeder der sieben spezifischen Anlässe verwendet alle acht Stile.
- Jede normale Anlass-Stil-Kombination besitzt mindestens sechs Texte.
- Der Board-Zusammenbruch differenziert zusätzlich universelle Texte, ein ungelöstes Board und einen gelösten deutlichen Ausreißer.
- Kein Text wurde allein zur Reduktion des Katalogumfangs entfernt.

## Qualitätsdurchgang

Der Hinweis, dass ein Text nicht bloß den Trigger wiederholen soll, wurde als verbindliche Regel übernommen. Ein Text benötigt grundsätzlich eine Ursache, Verteidigung, Umdeutung, Schuldverschiebung, Relativierung oder absurde Begründung.

Bewusst erhalten bleiben Ausnahmen, bei denen extreme Kürze, Überhöhung oder unangemessene Stilsicherheit selbst die Pointe bilden. Das betrifft insbesondere knappe norddeutsche und überdramatische Varianten.

Der Tagesausreißer-Block wurde vollständig nach diesem Maßstab geschärft. Weitere beschreibende Langsamkeits- und Allgemeintexte wurden gezielt überarbeitet. Die verbindlichen Einzeländerungen stehen in `excuse-catalog-review-pass-1.md`.

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

Befehle für spätere Katalogänderungen:

```text
python tools/build_excuse_catalog.py
python tools/build_excuse_catalog.py --check
```

## Abnahme

Der Compiler hat den vollständigen Katalog ohne Mengen-, Dubletten-, Terminologie-, Bedingungs- oder Platzhalterfehler erzeugt.

`ProductionExcuseCatalogTest` lädt denselben Snapshot über den produktiven Java-Loader und prüft:

- Version und exakt 564 Templates,
- eindeutige IDs und Texte,
- Familien- und Stilabdeckung,
- mindestens sechs Texte je Anlass und Stil,
- erlaubte Terminologie und Mentions,
- Gewichte und Auswahlstatus,
- die verbindlichen redaktionellen Überarbeitungen.

Der abschließende Standardbuild lief mit **558 Tests ohne Fehler** erfolgreich. Das PostgreSQL-Integrationsprofil war ebenfalls vollständig grün.

## Paketstatus

**Paket 8A ist abgeschlossen.** Die redaktionelle Texterstellung, der Qualitätsdurchgang, die Themenzuordnung, der deterministische Compiler, der produktive JSON-Katalog und die Laufzeitvalidierung sind vollständig umgesetzt.
