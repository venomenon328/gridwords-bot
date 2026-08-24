# ADR 0022: Stilles `achievements-v2`-Katalogupgrade

**Status:** akzeptiert
**Stand:** 24. August 2026
**Entscheidung für:** Issue #132
**Aktuelle fachliche Grundlage:** [`../product/achievements.md`](../product/achievements.md)
**Ergänzt:** [ADR 0020](0020-achievement-state-reconciliation-and-delivery.md)

## Kontext

Issue #132 erweitert den bisherigen 60er-Katalog um zwei boardabhängige Definitionen. Die Katalogerweiterung muss historische kanonische Evidenz auswerten können, darf aber nicht die öffentliche Einführung des ursprünglichen Katalogs wiederholen.

Die persistierte fachliche Identität eines Award-State besteht bereits aus Guild, Teilnehmer und stabilem Achievement-Schlüssel. Die Definitionsversion ist dagegen die Version der aktuell ausgewerteten Projektion. Das bestehende `achievements-v1`-Bootstrap, seine append-only Events und eine noch offene historische Introduction können daher nicht durch eine zweite unabhängige Award-Wahrheit ersetzt werden.

## Entscheidung

Die aktive Definitionsversion ist `achievements-v2` mit 62 Definitionen. Der vollständige `achievements-v1`-Katalog mit seinen unveränderten 60 Definitionen bleibt für Audit, bestehende Zustände und Delivery erhalten.

Der v2-Bootstrap verwendet wie bisher die komplette kanonische Teilnehmerhistorie und dieselbe Reconciliation. Dadurch werden die beiden neuen Definitionen historisch mit ihrem tatsächlichen `earned_on` erkannt. Vor erfolgreichem v2-Bootstrap bleiben normale Live-Unlocks gesperrt.

Der v2-Bootstrap ist jedoch ein stilles Katalogupgrade:

- Er erzeugt keine `HISTORICAL_INTRODUCTION` und keine öffentliche historische Unlock-Meldung.
- Er lässt den v1-Bootstrap-State und alle v1-Events unverändert als Audit bestehen.
- Eine vorhandene, noch offene v1-Introduction bleibt ihre bestehende logische Delivery und kann regulär weiter ausgeliefert oder revalidiert werden.
- Bereits vorhandene Award-States werden unter ihrem stabilen Schlüssel zur v2-Projektion reconciled; sie werden nicht dupliziert. Nur tatsächlich neue, fachlich belegte Schlüssel erhalten ihre normalen append-only Events.

Die beiden neuen Regeln lesen ausschließlich Boarddaten aus `game_result`: GridWords aus `normalized_board`, QuadWords aus den vier kanonischen Boardspalten. Fehlende QuadWords-Boards sind bei boardabhängigen Regeln ein normaler Non-Match. Rohshares, Versuchszahlen und andere Heuristiken ergänzen keine fehlenden Boarddaten.

## Konsequenzen

- Historische Boardmuster werden korrekt nachgezogen, ohne eine zweite öffentliche Einführungsflut.
- `/achievement-list`, `/achievements`, Ergebnisdetails und Report-Zählungen lesen weiterhin nur aktuellen Katalog und aktuellen Award-State.
- Kein neues Schema, keine zusätzliche Ergebnisquelle und keine allgemeine Regel-DSL sind erforderlich.
- Korrekturen und Reaktivierungen bleiben durch die bestehende participant-weite PostgreSQL-Reconciliation auditierbar und idempotent.

## Verworfene Alternativen

### Zweite historische Introduction für v2

Verworfen, weil sie bereits bekannte v1-Awards erneut öffentlich ankündigen und damit die ursprüngliche Einführungssemantik verletzen würde.

### Eigene Award-States je Definitionsversion

Verworfen, weil dadurch derselbe stabile Achievement-Schlüssel mehrfach fachlich vergeben würde und die bestehende Award-Identität aufweichen würde.

### Rekonstruktion boardloser QuadWords-Ergebnisse

Verworfen, weil Versuchszahl oder Rohshare keine kanonische Board-Evidenz für eine Zeilenregel sind.
