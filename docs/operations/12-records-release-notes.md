# Unveröffentlichte Release Notes: Inkrement 12

**Status:** nur für die spätere RC- und Releasevorbereitung; nicht veröffentlicht.

## Neu

- Allzeitrekorde für GridWords und QuadWords: wenigste Versuche mit Dauer-Tie-Breaker, schnellste und langsamste erfolgreiche Lösung.
- Persönliche, serverweite individuelle und gemeinsame positive Serienrekorde sowie negative X-Durststrecken und Tage ohne perfekten Tag.
- Seltene aggregierte Rekordmeldungen mit persistenter Delivery, Edit, Teilreduktion und Delete nach Korrekturen.
- Ephemerer, strikt lesender `/records`-Command mit `user`, `game` und `category:results|series`; sinnvolle Filterkombinationen bleiben möglich.
- Persistenter Bootstrap, Claim/Lease/Retry/Recovery und `/records` auf materialisiertem aktuellem Zustand.

## Hinweise

- Es gibt keine Ranglisten, Achievements, neue Rekordmetriken oder generische Regel-/Messaging-Plattform.
- Bootstrap, Backfills, Importe und unveränderte Replays bleiben öffentlich still.
- Eine reale Discord-Abnahme, ein RC, ein Registry-Tag und ein Produktionsrollout sind bewusst nicht Teil von Issue #83.
