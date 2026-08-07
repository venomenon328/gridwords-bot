# Unveröffentlichte Release Notes: Inkrement 12

**Status:** für RC- und Releasevorbereitung freigegeben; noch nicht veröffentlicht.

## Neu

- Allzeitrekorde für GridWords und QuadWords: wenigste Versuche mit Dauer-Tie-Breaker, schnellste und langsamste erfolgreiche Lösung.
- Persönliche, serverweite individuelle und gemeinsame positive Serienrekorde sowie negative X-Durststrecken und Tage ohne perfekten Tag.
- Seltene aggregierte Rekordmeldungen mit persistenter Delivery, Edit, Teilreduktion und Delete nach Korrekturen.
- Ephemerer, strikt lesender `/records`-Command mit `user`, `game` und `category:results|series`; sinnvolle Filterkombinationen bleiben möglich.
- Persistenter Bootstrap, Claim/Lease/Retry/Recovery und `/records` auf materialisiertem aktuellem Zustand.
- Normaler kanonischer Ergebnis-Create und ID-basierte Korrekturen vermeiden channelweite Discord-History-Scans; die teure Discovery bleibt auf den seltenen mehrdeutigen Recovery-Fall beschränkt.

## Betrieb und Rollout

- Bootstrap, Backfills, Importe und unveränderte Replays bleiben öffentlich still.
- Öffentliche Rekordmeldungen sind separat schaltbar. Für den Produktivrollout wird zuerst mit deaktivierten öffentlichen Meldungen migriert und der Bootstrap-/`/records`-Stand geprüft; erst danach werden neue öffentliche Meldungen aktiviert.
- Während deaktivierter öffentlicher Meldungen entstandene Ereignisse werden nach der Reaktivierung nicht als Backlog nachgeliefert.
- Bei einem Problem der öffentlichen Delivery kann diese wieder deaktiviert werden, ohne Record-State oder den lesenden `/records`-Command abzuschalten.

## Abnahme

- Paket 12.10 wurde technisch und auf einem separaten Discord-Testserver repräsentativ abgenommen.
- Ergebnisrekord-Create/Edit/Delete, Restart/Recovery, Suppression ohne Backlog, `/records`, positive Serienüberschreitung und Serien-Reconciliation durch `X` wurden real geprüft.
- Seltene weitere Serienvarianten wie gemeinsame Serie, negative Durststrecke und realer 06:00-Day-Close bleiben zusätzlich unter Beobachtung im laufenden Betrieb; ihre Fachlogik und Zeitsemantik sind automatisiert abgedeckt.

## Nicht enthalten

- Es gibt keine Ranglisten, Achievements, neuen Rekordmetriken oder generische Regel-/Messaging-Plattform.
- RC, Registry-Tag und Produktionsrollout erfolgen bewusst erst nach dem Merge in separaten Schritten.
