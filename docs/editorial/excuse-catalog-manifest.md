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

## Noch ausstehende technische Katalogisierung

- alle 564 Einträge in `src/main/resources/excuses/catalog.json` überführen,
- Version festlegen,
- allgemeine Themenzuordnung anwenden,
- Ersetzungen aus Qualitätsdurchgang 1 anwenden,
- exakte Dubletten verhindern,
- normalisierte Ähnlichkeiten und gleiche Prämissen prüfen,
- IDs, Spiele, Spezifität, Gewicht, Bedingungen und Platzhalter validieren,
- produktiven Loader und Coverage-Prüfung ausführen,
- Standardbuild vollständig ausführen.

## Paketstatus

Die **redaktionelle Texterstellung** ist abgeschlossen. Paket 8A bleibt offen, bis der produktive JSON-Katalog erzeugt, validiert und im Standardbuild erfolgreich geprüft wurde.
