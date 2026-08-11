# ADR 0021: Gezielte Recovery persistierter Parser-Ablehnungen

- **Status:** akzeptiert
- **Datum:** 2026-08-11

## Kontext

Parser-Ablehnungen werden als `PARSE_REJECTED` mit stabilem Fehlercode persistiert. Das ist für echte fachlich ungültige Shares richtig, verhindert aber zugleich, dass ein bereits eingegangener Share nach der Korrektur eines Parserfehlers von selbst wieder aufgenommen wird.

Der konkrete Anlass ist ein gültiger GridWords-Share mit der von Gridgames ausgegebenen Dauer `7:38:28`. Der bisherige Parser akzeptiert nur `M:SS` und hat den Share deshalb als `INVALID_DURATION` verworfen. Eine normale erneute Verarbeitung nach einem späteren Neustart darf die allgemeine 06:00-Zulassungsgrenze nicht aufweichen: Reparaturen müssen gemäß aktueller Architektur ein eigener stiller Wartungsweg bleiben.

## Entscheidung

1. Der gemeinsame Share-Header-Parser akzeptiert zusätzlich zum bisherigen `M:SS` das Gridgames-Format `H:MM:SS`. Minuten und Sekunden im Uhrzeitformat bleiben strikt auf `00..59` begrenzt.
2. Für Parserkorrekturen gibt es einen expliziten, eng begrenzten Wartungsweg. Er ist vom normalen eingehenden `process`-Vorgang getrennt und darf die Tagesfensterprüfung nur nach einer zuvor persistiert vorbereiteten Parser-Recovery umgehen.
3. Ein kleiner Persistenz-Port verwaltet diese Recovery. Für den aktuellen Fix werden ausschließlich Submissions mit Fehlercode `INVALID_DURATION` betrachtet. Vor der eigentlichen Wiederaufnahme prüft der aktuelle GridWords-Parser anhand des persistierten Rohtexts, dass der Kandidat jetzt tatsächlich parsebar ist. Echte ungültige Werte wie `1:99` werden daher nicht geöffnet.
4. Die Vorbereitung wechselt einen passenden Datensatz atomar von `PARSE_REJECTED` nach `RECEIVED`, lässt den bisherigen Parserfehler aber zunächst als dauerhaften Recovery-Marker stehen. Ein bereits so vorbereiteter Datensatz ist idempotent erneut vorbereitbar.
5. Die Wartungsverarbeitung verlangt diesen Marker defensiv und speichert das Ergebnis über den bestehenden Ergebnis- und Achievement-Lifecycle. Die ebenfalls zeitlich begrenzten Phasen für kanonische Veröffentlichung und Quelllöschung besitzen eigene explizite Maintenance-Einstiege; dadurch wird ausschließlich für diesen gefenceten Reparaturvorgang die normale Spieltagszulassung umgangen, während die regulären Publish-, Retry- und Delete-Pfade unverändert bleiben.
6. Der Recovery-Marker wird erst entfernt, wenn Ergebnisverarbeitung, kanonische Veröffentlichung und Quelllöschung einen dauerhaft abgeschlossenen beziehungsweise supersedierten Zustand erreicht haben. Ein Fehler nach dem Result-Write bleibt deshalb als `INVALID_DURATION`-markierte Post-Result-Arbeit auffindbar.
7. Stirbt der Prozess nach der Vorbereitung, bleibt `RECEIVED + INVALID_DURATION` beziehungsweise ein späterer markierter Zwischenzustand als wiederauffindbare Recovery-Arbeit erhalten. Der nächste Start kann denselben Kandidaten oder den noch offenen Delivery-Teil erneut aufnehmen. Ein weiterhin parserseitig ungültiger Share fällt wieder auf `PARSE_REJECTED` zurück.
8. Die Discord-Nachricht wird erst nach Abschluss des normalen Application-Startups über JDA erneut geladen. Ist die Quelle vor dem Result-Write nicht mehr verfügbar, bleibt die persistierte Ablehnung unverändert; es wird keine Ersatzwahrheit aus der öffentlichen Historie konstruiert. Nach einem bereits gespeicherten Ergebnis werden verbleibende Delivery-Schritte aus ihren persistierten Zuständen fortgesetzt und benötigen den Quelltext nicht erneut.
9. Es ist keine Schemaänderung erforderlich. Der vorhandene Submission-Zustand und der bestehende Fehlercode reichen als Recovery-Marker aus.

## Folgen

- Der betroffene reale Share wird beim ersten Bot-Start mit der korrigierten Version automatisch erneut verarbeitet, auch wenn sein Spieltag inzwischen außerhalb des normalen Annahmefensters liegt.
- Normale Replays, Korrekturen und Retries behalten unverändert die 06:00-Regel.
- Andere `INVALID_DURATION`-Shares werden nur dann angefasst, wenn der aktuelle GridWords-Parser sie nach der Korrektur vollständig akzeptiert; echte Fehlangaben erzeugen keinen endlosen Reparaturzyklus.
- Die Recovery ist restart-sicher und verwendet weiterhin die vorhandenen idempotenten Persistenz-, Claim- und Discord-Delivery-Grenzen.
- Eine spätere Verallgemeinerung auf andere Parserfehler muss ausdrücklich erweitert und getestet werden; dieser ADR führt keinen generischen Backfill-Mechanismus ein.
