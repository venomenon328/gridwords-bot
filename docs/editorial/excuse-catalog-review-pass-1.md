# Redaktioneller Qualitätsdurchgang 1

Dieser Durchgang prüft den Arbeitsbestand von 564 Ausreden gegen `excuse-catalog-quality-guidelines.md`. Die Arbeitsdokumente bleiben die Primärquelle; die hier festgehaltenen Ersetzungen sind bei der Konsolidierung in den produktiven JSON-Katalog verbindlich, sofern sie nicht vorher direkt in das jeweilige Dokument eingearbeitet wurden.

## Ergebnis

- Keine Anlassfamilie und kein Stil wird verkleinert.
- Bewusst knappe norddeutsche Texte bleiben erhalten.
- Bewusst überdramatische Texte bleiben erhalten.
- Der vollständige Tagesausreißer-Block wurde direkt überarbeitet, weil dort die größte Gefahr einer bloßen Triggerbeschreibung bestand.
- Mehrere beschreibende GridWords-Langsamkeitstexte wurden direkt zu Ursachen, Verteidigungen oder Umdeutungen geschärft.
- Die übrigen Anlassfamilien besitzen bereits überwiegend eine klare Schuldverschiebung, Strategie, Verfahrensverteidigung, absurde Ursache oder starke Stilpointe.

## Verbindliche Ersetzungen im allgemeinen Fundus

### `general.tactical.09`

Bisher:

> Heute wurde bewusst in spätere Überperformance investiert.

Neu:

> Ich habe das Grid heute gezielt zu einer falschen Analyse meiner Muster verleitet.

Begründung: Der bisherige Text lag zu nah an `general.tactical.04` und `general.tactical.15`. Die neue Variante stärkt den Grid-Konflikt und besitzt eine eigenständige Täuschungsprämisse.

### `general.dramatic.15`

Bisher:

> Das Schicksal hatte entschieden, bevor ich den ersten Buchstaben sah.

Neu:

> Die Tragödie begann in dem Moment, als das Grid vorgab, sie ließe sich noch verhindern.

Begründung: Der bisherige Text lag zu nah an `general.dramatic.03`. Die neue Variante verschiebt die Verantwortung auf ein täuschendes Grid.

### `general.cosmic.17`

Bisher:

> Das Ergebnis ist nur aus einer höherdimensionalen Perspektive sinnvoll.

Neu:

> Die kosmische Buchhaltung hat dieses Ergebnis offenbar in der falschen Dimension verbucht.

Begründung: Der bisherige Text lag zu nah an `general.cosmic.02`. Die neue Variante liefert eine konkrete absurde Ursache.

### `general.sporting.15`

Bisher:

> Der Fokus liegt ab sofort auf dem nächsten Grid.

Neu:

> Die heutige Leistung war offenbar Teil einer unangekündigten Regenerationseinheit.

Begründung: Der bisherige Text wiederholte die Zukunftsorientierung von `general.sporting.01`. Die neue Variante entschuldigt die Leistung über Belastungssteuerung.

## Weitere verbindliche Ersetzung

### `gridwords-very-slow.sporting.04`

Bisher:

> Wir haben lange nach verwertbaren Ansätzen gesucht und dabei kaum einfache Räume gefunden.

Neu:

> Wir haben uns Zeit genommen, weil ein überhasteter Angriff nur noch mehr Ballverluste im Kopf produziert hätte.

Begründung: Der bisherige Text beschrieb den Spielverlauf stärker, als er ihn erklärte, und lag nach dem ersten Pass zu nah an `gridwords-very-slow.sporting.03`.

## Familienbewertung

### Allgemeine Texte

Überwiegend geeignet. Die Prämissen reichen von technischen Ausfällen über langfristige Strategien und Zuständigkeitsabwehr bis zu Grid-Konflikten. Die oben genannten vier Ersetzungen reduzieren die auffälligsten Dopplungen.

### `NOT_SOLVED`

Geeignet. Selbst technisch oder sportlich beschreibende Varianten führen das Scheitern auf Resolver, Verfahren, Beweislage, Matchplan, kosmische Umstände oder das Lösungswort zurück. Norddeutsche Tatsachenfeststellungen besitzen ausreichend starke Stilpointen.

### `VERY_LATE_SUBMISSION`

Geeignet. Die Texte erklären die Uhrzeit durch Queue, Wartungsfenster, Strategie, Zuständigkeiten, Nachtbetrieb, Raumzeit, Nachspielzeit oder fehlende Verpflichtung zur früheren Abgabe. Kein Text behauptet eine verpasste Frist.

### `GRIDWORDS_LAST_ATTEMPT`

Geeignet. Die vollständige Ausschöpfung wird als Iterationsbedarf, Aufklärung, Verfahrensgang, Dramaturgie, kosmische Notwendigkeit, Ressourcenverwertung, Nachspielzeit oder regelkonforme Fristwahrung umgedeutet.

### `GRIDWORDS_VERY_SLOW`

Nach direkter Überarbeitung geeignet. Reine Zeitbeschreibungen bleiben nur dort bestehen, wo Kürze oder Überhöhung selbst die Pointe bilden.

### `QUADWORDS_VERY_SLOW`

Geeignet. Die Dauer wird überwiegend mit Parallelverarbeitung, Kontextwechseln, Vierfrontenstrategie, Koordination, Zeitdilatation oder Mehrkampf erklärt.

### `QUADWORDS_SINGLE_BOARD_COLLAPSE`

Geeignet. Bereits die eindeutige Schuldzuweisung an `{worstBoard}` erfüllt die Ausredenfunktion. Die beiden Untervarianten bleiben tatsachensicher getrennt.

### `CLEAR_CURRENT_DAILY_OUTLIER`

Nach vollständiger direkter Überarbeitung geeignet. Jeder nicht als bewusste Stilpointe gesetzte Text verteidigt, relativiert oder erklärt den vorläufigen Rückstand nun ausdrücklich.

## Noch offene Schritte

1. Die fünf hier definierten Ersetzungen bei der Konsolidierung anwenden.
2. Den 144 allgemeinen Texten endgültige `ExcuseTopic`-Werte zuweisen.
3. Alle Arbeitsdokumente in einen produktiven JSON-Katalog überführen.
4. Exakte Textdubletten und normalisierte Ähnlichkeiten automatisiert prüfen.
5. Katalogvalidierung, Coverage-Test und vollständigen Standardbuild ausführen.
