# ADR 0016: Spielbezogene historische Teilnahme

- Status: akzeptiert
- Datum: 2026-08-03
- Kontext: Issue #39 und Zwischeninkrement 10.6

## Kontext

Das bisherige Modell führt je Spieler einen globalen Teilnahmezeitraum, der zugleich für GridWords und QuadWords verwendet wird. Dadurch gilt ein Spieler nach einem gültigen Share automatisch für beide Spiele als Teilnehmer. Reminder, gemeinsame Serien, Tagesstatus, Ergebnisdetails sowie Wochen- und Monatsberichte behandeln ein beim Spieler nicht gewünschtes Spiel deshalb als fehlend.

Der neue Anwendungsfall verlangt unabhängige Teilnahme an GridWords, QuadWords oder beiden Spielen, ohne die bestehende Historie rückwirkend zu verändern.

## Entscheidung

Teilnahme wird künftig historisch pro Spieler und Spieltyp persistiert. Jeder Zeitraum trägt `player_id`, `game_type`, `active_from` und `inactive_from`. PostgreSQL verhindert je `(player_id, game_type)` offene Doppelzeiträume und Überschneidungen.

Die bisherige globale Historie wird verlustfrei migriert: Jeder vorhandene Zeitraum wird mit identischen Datumsgrenzen einmal für GridWords und einmal für QuadWords angelegt. Es wird nicht aus vorhandenen Ergebnissen auf frühere Präferenzen geschlossen.

`player.active` bleibt als abgeleitete Kompatibilitätsinformation bestehen und bedeutet ausschließlich, dass der Spieler am aktuellen Berlin-Tag an mindestens einem Spiel teilnimmt. Fachliche Teilnehmermengen werden stets aus den spielbezogenen Zeiträumen berechnet.

Für einen Tag entstehen drei relevante Mengen:

```text
GridWords-Teilnehmer
QuadWords-Teilnehmer
Zwei-Spiele-Teilnehmer als Schnittmenge
```

Spielbezogene Lösungsserien und Reminder verwenden die Teilnehmermenge des jeweiligen Spiels. Komplett- und Perfektmetriken bleiben echte Zwei-Spiele-Metriken und verwenden ausschließlich die Schnittmenge.

Der globale Reminderstatus bleibt erhalten. Das Hinzufügen eines zweiten Spiels bewahrt einen bestehenden Opt-out; nur der Wiedereintritt nach vollständiger Inaktivität schaltet Reminder standardmäßig wieder ein.

## Folgen

- Single-Game-Teilnahme ist historisch korrekt und symmetrisch für beide Spiele darstellbar.
- Status, Menüs und Berichte müssen „nicht teilgenommen“ von „fehlend“ unterscheiden.
- Berichte benötigen getrennte Spielnenner und einen eigenen Nenner für Zwei-Spiele-Tage.
- Die bestehende Teilnahme-Persistenz und mehrere Read-Modelle werden migriert beziehungsweise erweitert.
- `both`-Commands und Share-Aktivierung benötigen atomare PostgreSQL-Operationen.
- Die sichere Ergebnisveröffentlichung, Quelllöschung, Delivery-Recovery und Parser bleiben unverändert.
- Es entsteht kein generisches Modell für beliebig viele Spiele und kein Reminderstatus pro Spiel.

## Verbindliche Details

Die vollständige Fachsemantik steht in `docs/requirements/game-specific-participation.md`. Die paketweise Umsetzung steht in `docs/increments/10.6-game-specific-participation.md`.
