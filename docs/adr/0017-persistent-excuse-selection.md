# ADR 0017: Persistente Ausredenauswahl und kanonische Aktualisierung

- Status: akzeptiert
- Datum: 2026-08-03
- Kontext: Issue #42 und Inkrement 11

## Kontext

Kontextabhängige Ausreden sind ein freiwilliges Unterhaltungsfeature nach seltenen Ergebniskonstellationen. Der Ergebnisautor erhält ephemer drei redaktionell gepflegte Vorschläge, kann einmal nach einem Stil neu würfeln, verzichten oder genau einen Text auswählen.

Eine rein flüchtige Umsetzung hätte mehrere Nachteile:

- dieselbe gerade gewählte Ausrede könnte beim nächsten Angebot sofort wieder erscheinen,
- ein Botneustart könnte Vorschläge und verbrauchten Neu-Wurf vergessen,
- manipulierte oder verspätete Interactions wären nur schwach prüfbar,
- Korrekturen könnten einen bereits gewählten Text unbemerkt unzutreffend machen,
- spätere Achievements hätten keine belastbare fachliche Grundlage,
- eine direkte Discord-Änderung aus der Interaction würde die vorhandene kanonische Claim-, Refresh- und Recovery-Pipeline umgehen.

Gleichzeitig soll weder eine zweite öffentliche Nachricht noch eine zweite fachliche Ergebniswahrheit entstehen.

Der Funktionsumfang bis einschließlich Inkrement 10 und der Zwischeninkremente 10.x ist die feature-complete Basis für Version 1.0.x beziehungsweise 1.1.0. Inkrement 11 ist eine bewusst neu priorisierte optionale Erweiterung; die früher vorgemerkten Inkremente 11 und 12 sind keine Roadmap-Verpflichtungen mehr.

## Entscheidung

Für jedes `game_result` wird genau ein persistierter Ausredenzustand geführt. Die erstmalige positive oder negative Angebotsentscheidung wird ausschließlich bei der Neuanlage des fachlichen Ergebnisses gespeichert. Bestehende Ergebnisse werden bei der Migration als `NOT_OFFERED` markiert. Replay, Korrektur, Boardanreicherung und Recovery erzeugen kein neues Angebot.

Der Zustand unterscheidet mindestens:

```text
NOT_OFFERED
AVAILABLE
SELECTED
DECLINED
EXPIRED
INVALIDATED
```

Tatsächlich angezeigte Optionen werden mit Runde, Position, Template-ID, Stil, Thema und gerendertem Text persistiert. Dadurch sind dieselben Vorschläge nach erneutem Öffnen oder Neustart rekonstruierbar und nur wirklich angebotene Texte auswählbar.

Bei einer Auswahl werden mindestens folgende unveränderlichen Snapshots gespeichert:

```text
selected_template_id
selected_style
selected_topic
selected_rendered_text
selected_at
```

Der freie Text wird später weder für Achievements analysiert noch durch Katalogänderungen rückwirkend verändert.

Der Template-Katalog bleibt eine versionierte, beim Start validierte Repository-Ressource. Es gibt keine generative KI, keine externe Text-API und kein redaktionelles Laufzeit-Backend. Stabile Template-IDs werden nicht umgedeutet; nicht mehr auswählbare Definitionen bleiben als deaktivierte Katalogeinträge erhalten, solange persistierte Verweise möglich sind.

Vorschläge, Stilwahl, Neu-Würfeln und Verzicht werden ausschließlich ephemer dargestellt. Öffentlich existiert nur die kanonische Ergebnisnachricht:

- `AVAILABLE` ergänzt einen Button,
- `SELECTED` ergänzt ausschließlich den gewählten Text ohne Stil oder Ausredenlabel,
- alle anderen Zustände ergänzen weder Button noch Text.

Eine Interaction editiert Discord niemals direkt. Sie persistiert einen autorisierten und idempotenten Zustandsübergang und fordert atomar einen dauerhaften kanonischen Refresh an. Die bestehende Publication-, Claim-, Lease-, Retry-, Recovery-, Duplikat- und Retirement-Semantik bleibt die einzige öffentliche Delivery-Pipeline. Create und Edit übertragen Embed und Komponenten gemeinsam.

Angebots- und Vergleichslogik verwendet ausschließlich die historisch wirksame Teilnehmermenge des betroffenen Spiels aus dem abgeschlossenen Zwischeninkrement 10.6.

## Folgen

- Auswahl, Verzicht, Ablauf und Neu-Wurf überstehen Neustarts.
- Doppelklicks, Konkurrenz und manipulierte Component-IDs können gegen PostgreSQL validiert werden.
- Das unmittelbar zuvor gewählte Template und weitere kürzlich gewählte Templates können zuverlässig ausgeschlossen werden.
- Zukünftige Achievements können stabile Stil-, Themen- und Template-Snapshots auswerten.
- Eine Korrektur kann verfügbare oder ausgewählte Ausreden fachlich revalidieren, ohne einen gewählten Text stillschweigend umzuschreiben.
- Der Persistenzumfang wächst um einen kleinen Zustand je Ergebnis und höchstens sechs aktive Optionen je Angebot.
- Liquibase-Migration, Backfill, Constraints, Konkurrenz und Recovery benötigen echte PostgreSQL-Integrationstests.
- Die kanonische Transportprojektion und das Discord-Gateway müssen neben dem Embed auch Action Rows als gemeinsamen Inhalt behandeln.
- Der Unterhaltungszustand bleibt vom eigentlichen Ergebnis getrennt; Ergebnis, Serien, Teilnahme und Statistik werden nicht verändert.

## Verworfene Alternativen

### Vollständig flüchtige In-Memory-Session

Verworfen wegen Neustartverlust, schwacher Manipulationsprüfung, fehlendem Wiederholungsschutz und fehlender Achievement-Grundlage.

### Nur den gewählten Text speichern

Verworfen, weil Template-, Stil- und Themenidentität verloren gingen und nicht mehr sicher nachweisbar wäre, ob ein Text tatsächlich angeboten wurde.

### Ausrede direkt in `game_result` als Freitextspalte

Verworfen, weil Angebotszustand, Ablauf, Neu-Wurf und Optionen dadurch nicht sauber modelliert wären und das Ergebnis unnötig mit einem optionalen Interaktionsworkflow vermischt würde.

### Direkter Discord-Edit aus der Interaction

Verworfen, weil er die bestehende kanonische Refresh-Generation, Recovery und Retirement-Fence umgehen und bei Crash oder Konkurrenz sichtbare und persistierte Zustände auseinanderlaufen lassen könnte.

### Generative KI zur Laufzeit

Verworfen wegen externer Kosten und Ausfälle, unvorhersehbarer Ausgaben, schlechter vollständiger Testbarkeit und fehlender redaktioneller Kontrolle.

## Verbindliche Details

Die vollständige Fachsemantik steht in `docs/requirements/excuses.md`. Die paketweise Umsetzung steht in `docs/increments/11-contextual-excuses.md`.
