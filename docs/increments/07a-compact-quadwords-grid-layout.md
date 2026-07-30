# Zwischeninkrement 7.1: Kompaktes 2×2-Layout der QuadWords-Grids

**Status:** umgesetzt; abschließender visueller Discord-Smoke-Test offen

**Issue:** #17

**Branch:** `feature/compact-quadwords-grid-layout`

## Ziel

Die vier bereits korrekt geparsten, gespeicherten und kanonisch veröffentlichten QuadWords-Boards werden in Discord kompakt in ihrer ursprünglichen 2×2-Anordnung dargestellt. GridWords verwendet für eine konsistente Rasterdarstellung ebenfalls einen Monospace-Codeblock.

## Verbindliches Layout

- `topLeft` links neben `topRight`
- `bottomLeft` links neben `bottomRight`
- keine sichtbaren Positionslabels
- kompakter Abstand zwischen den beiden Boards eines Paares
- klare vertikale Trennung zwischen oberem und unterem Paar
- fehlende Zeilen des kürzeren Boards werden mit kanonischen Leerzellen `⬜⬜⬜⬜⬜` aufgefüllt; dadurch bleibt die zweite Spalte in Discord unabhängig von dessen Emoji-Breiten stabil ausgerichtet
- GridWords- und QuadWords-Raster stehen jeweils in einem Monospace-Codeblock

## Sichtbare Boardhöhe

- Eine vollständig grüne Zeile `🟩🟩🟩🟩🟩` beendet den fachlichen Inhalt des betreffenden Boards einschließlich dieser Zeile.
- Ein Board ohne vollständig grüne Zeile behält alle gespeicherten Zeilen.
- Die Höhe eines horizontalen Paares entspricht dem längeren der beiden Boards.
- Unter einem früher gelösten Board erscheinen ausschließlich die für die stabile 2×2-Ausrichtung nötigen kanonischen Leerzellen.

## Kontextzeilen

Die persönliche beziehungsweise gemeinsame Komplett-/Perfektinformation wird weiterhin nur durch die Submission etabliert, die den jeweiligen Tageszustand erstmals herstellt. Muss eine kanonische Discord-Nachricht später neu erzeugt werden, liest der Discord-Adapter den für dasselbe Ergebnis historisch gespeicherten PublicationContext und stellt die weiterhin laufenden Kontextzeilen wieder her. Eine Korrektur etabliert dadurch keinen Zustand erneut.

## Unverändert

- Parser und Domänenmodell
- PostgreSQL- und Liquibase-Schema
- fachliche Seriensemantik
- Publication-, Delete-, Claim-, Recovery- und Supersession-Zustandsmaschine
- Titel, Ergebnis, Dauer und versteckter Publication-Key
- fachlicher GridWords-Inhalt und dessen Publish-/Edit-/Delete-Verhalten

## Validierung

```powershell
mvn --batch-mode --no-transfer-progress clean verify
mvn --batch-mode --no-transfer-progress -Pdatabase-integration clean verify
```

Automatisierte Abnahme am 30. Juli 2026:

- 199 Standardtests, 0 Fehler, 0 Fehlschläge
- 57 PostgreSQL-Integrationstests, 0 Fehler, 0 Fehlschläge
- Rendererfälle für Paarhöhen 7/9 und 4/6, stabile Leerzellenauffüllung, ungelöstes neunzeiliges Board, 2×2-Reihenfolge, fehlende Positionslabels, Korrekturkontext, Publication-Key und Monospace-Blöcke beider Spiele
- JDA-Test für die Wiederherstellung historisch etablierter Komplett-/Perfektzeilen bei Neuerzeugung
- PostgreSQL-Test für die Aggregation des historischen PublicationContext über mehrere Submissions desselben Ergebnisses

Offen bleibt ausschließlich Tobias' kurzer visueller Discord-Smoke-Test: Beide QuadWords-Spalten müssen ohne Versatz stehen, GridWords muss im Codeblock bleiben, Kontextzeilen müssen bei der erneuten Korrektur beziehungsweise Neuerzeugung erscheinen und die sichere Quelllöschung muss unverändert funktionieren.
