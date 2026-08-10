# Rekorde

## Quellen und Version

Die aktive Definitionsversion ist `records-v1`. Kanonische Quellen sind Spielergebnisse und historische Teilnahmezeiträume. `record_state` ist eine materialisierte Projektion und Konkurrenzanker, kein Ersatz für diese Quellen. Rekordzustand, unveränderliches Auditereignis und Discord-Ankündigungsprojektion sind getrennte persistente Ebenen.

## Ergebnisrekorde

Ergebnisrekorde berücksichtigen ausschließlich gelöste Ergebnisse und werden für GridWords und QuadWords getrennt geführt.

| Definition | Vergleich |
|---|---|
| Wenigste Versuche | Versuche aufsteigend, bei Gleichstand Dauer aufsteigend |
| Schnellste Lösung | Dauer aufsteigend |
| Langsamste erfolgreiche Lösung | Dauer absteigend |

Scopes sind persönlich und serverweit-individuell. Für einen persönlichen Rekord müssen mindestens fünf frühere vergleichbare Ergebnisse vorhanden sein. Für einen serverweiten Rekord sind mindestens zehn frühere Ergebnisse von mindestens zwei Spielern erforderlich. Exakte Gleichstände ändern die kanonische Erstbelegung nicht und bleiben öffentlich still.

## Serienrekorde

Positive Serienrekorde verwenden exakt die fünf persönlichen und vier gemeinsamen Serien aus [`streaks.md`](streaks.md). Zusätzlich werden folgende negative Serien abgeleitet:

- persönliche und serverweite GridWords-`X`-Durststrecke,
- persönliche und serverweite QuadWords-`X`-Durststrecke,
- persönliche und serverweite Tage ohne perfekten Tag.

Serienläufe werden aus Quellen neu abgeleitet und über eine stabile Run-ID identifiziert. Persönliche Vergleiche benötigen einen früher abgeschlossenen Lauf; serverweite Vergleiche frühere Läufe von mindestens zwei Spielern; gemeinsame Vergleiche einen früheren gemeinsamen Lauf. Positive Serien und Tage ohne perfekten Tag benötigen sieben Tage, `X`-Durststrecken drei Tage, bevor ein öffentlicher Rekord möglich ist.

Beim strikten Überschreiten eines laufenden Referenzrekords wird genau einmal ein Crossing-Ereignis erzeugt; Gleichstand während eines laufenden Laufs bleibt still. Beim Ende entstehen je nach Vergleich Rekordbruch, Gleichstand oder Near Miss. Ein Near Miss liegt höchstens `max(1, ceil(Referenz × 10 %))` unter der Referenz und vergleicht nicht mit dem gerade beendeten Lauf selbst.

## Stille und öffentliche Ursprünge

Historischer Bootstrap, Import, Backfill, unverändertes Replay und administrative Reparatur sind öffentlich still. Öffentliche Meldungen sind bis zum erfolgreichen Bootstrap der aktiven Definitionsversion gesperrt. Normale Live-Korrekturen dürfen Meldungen erzeugen, editieren, teilweise reduzieren oder vollständig löschen. Es gibt keine öffentliche Aberkennungsnachricht.

Zusammengehörige Rekordfakten werden in einer Meldung aggregiert. Die Delivery ist dauerhaft geclaimt, retryfähig und reconciled externe Löschungen sowie unbekannte Discord-Ausgänge. Inhalte erzeugen keine Mentions.

## `/records` und Ergebnisdetails

`/records` antwortet ephemer aus der aktuellen Projektion, ohne die gesamte Historie neu zu scannen. Filter umfassen Spiel, Kategorie und Scope. Persönliche Rekorde eines anderen Nutzers sind nur über die dafür berechtigte administrative Ansicht zugänglich.

Die Ergebnisdetailansicht zeigt ausschließlich aktuell gültige Rekorde, die das gewählte Ergebnis hält.

## Berichtshighlights

Berichte verwenden nur gültige, öffentlich zulässige Ereignisse der Typen `RESULT_RECORD_BROKEN`, `SERIES_RECORD_CROSSED` und `RECORD_SERIES_FINISHED`. Crossing und Finish desselben Laufs und derselben Rekorddefinition werden innerhalb einer Periode fachlich dedupliziert.
