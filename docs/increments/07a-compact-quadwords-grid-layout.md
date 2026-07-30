# Zwischeninkrement 7.1: Kompaktes 2×2-Layout der QuadWords-Grids

**Status:** vorbereitet

**Issue:** #17

**Branch:** `feature/compact-quadwords-grid-layout`

## Ziel

Die vier bereits korrekt geparsten, gespeicherten und kanonisch veröffentlichten QuadWords-Boards werden in Discord kompakt in ihrer ursprünglichen 2×2-Anordnung dargestellt.

## Verbindliches Layout

- `topLeft` links neben `topRight`
- `bottomLeft` links neben `bottomRight`
- keine sichtbaren Positionslabels
- kompakter Abstand zwischen den beiden Boards eines Paares
- klare vertikale Trennung zwischen oberem und unterem Paar

## Sichtbare Boardhöhe

- Eine vollständig grüne Zeile `🟩🟩🟩🟩🟩` beendet die sichtbare Darstellung des betreffenden Boards einschließlich dieser Zeile.
- Danach gespeicherte kanonische Leerzeilen werden nicht angezeigt.
- Ein Board ohne vollständig grüne Zeile behält alle gespeicherten Zeilen.
- Die Höhe eines horizontalen Paares entspricht dem längeren der beiden Boards.
- Das kürzere Board wird nur unsichtbar ausgerichtet; unter seiner Lösungszeile erscheinen keine zusätzlichen Kästchen.

## Unverändert

- Parser und Domänenmodell
- PostgreSQL- und Liquibase-Schema
- Publication-, Edit-, Delete-, Claim-, Recovery- und Supersession-Logik
- Titel, Ergebnis, Dauer, Serien und versteckter Publication-Key
- GridWords-Darstellung

## Validierung

```powershell
mvn --batch-mode --no-transfer-progress clean verify
mvn --batch-mode --no-transfer-progress -Pdatabase-integration clean verify
```

Danach folgt ein kurzer visueller Discord-Smoke-Test mit unterschiedlich hohen gelösten Teilboards und einer Korrektur derselben kanonischen Nachricht.
