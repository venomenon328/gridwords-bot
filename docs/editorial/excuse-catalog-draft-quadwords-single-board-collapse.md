# Redaktioneller Ausredenkatalog – Anlass `QUADWORDS_SINGLE_BOARD_COLLAPSE`

Dieses Dokument setzt den redaktionellen Arbeitsstand aus `docs/editorial/excuse-catalog-draft.md` für Inkrement 11, Paket 8A fort. Es ist noch nicht der produktive JSON-Katalog. Die Texte werden bei Abschluss von Paket 8A in den Gesamtkatalog konsolidiert.

## Verbindliche Semantik

Der Anlass setzt vier analysierbare QuadWords-Boards und genau ein eindeutig schlechtestes Board voraus. Er entsteht in zwei Varianten:

```text
Variante A:
- genau drei Boards sind gelöst
- genau ein Board ist nicht gelöst

Variante B:
- alle vier Boards sind gelöst
- genau ein Board besitzt die höchste Versuchszahl
- dieses Board wurde frühestens im achten Versuch gelöst
- Abstand zum zweitschlechtesten Board mindestens drei Versuche
```

Weitere Regeln:

- `{worstBoard}` bezeichnet ausschließlich das nachweislich eindeutig schlechteste Board.
- Universelle Texte dürfen bei beiden Varianten verwendet werden.
- Texte für Variante A behaupten ausdrücklich oder sinngemäß ein ungelöstes Board und dürfen nur mit `THREE_BOARDS_SOLVED_ONE_UNSOLVED` verwendet werden.
- Texte für Variante B müssen anerkennen, dass letztlich alle Boards gelöst wurden, und dürfen nur mit `ALL_BOARDS_SOLVED` sowie `SIGNIFICANT_WORST_BOARD_GAP` verwendet werden.
- Boardlose Einreichungen und Fälle ohne eindeutiges schlechtestes Board sind ausgeschlossen.
- Im sichtbaren Sprachgebrauch des Bots wird vom **Grid** gesprochen, nicht vom Raster.
- Bewusst knappe norddeutsche und bewusst überdramatische Texte sind stilprägend.

## Metadaten – universelle Texte

```text
games: [QUADWORDS]
topic: SINGLE_BOARD_BLAME
specificity: 30
weight: 100
requiresAll:
  - QUADWORDS_SINGLE_BOARD_COLLAPSE
  - FOUR_BOARDS_PRESENT
  - UNIQUE_WORST_BOARD
excludesAny: []
selectable: true
```

## Metadaten – Variante A: ein Board ungelöst

```text
games: [QUADWORDS]
topic: SINGLE_BOARD_BLAME
specificity: 40
weight: 100
requiresAll:
  - QUADWORDS_SINGLE_BOARD_COLLAPSE
  - FOUR_BOARDS_PRESENT
  - UNIQUE_WORST_BOARD
  - THREE_BOARDS_SOLVED_ONE_UNSOLVED
excludesAny: []
selectable: true
```

## Metadaten – Variante B: alle gelöst, ein deutlicher Ausreißer

```text
games: [QUADWORDS]
topic: SINGLE_BOARD_BLAME
specificity: 40
weight: 100
requiresAll:
  - QUADWORDS_SINGLE_BOARD_COLLAPSE
  - FOUR_BOARDS_PRESENT
  - UNIQUE_WORST_BOARD
  - ALL_BOARDS_SOLVED
  - SIGNIFICANT_WORST_BOARD_GAP
excludesAny: []
selectable: true
```

## Verteilung

| Stil | Universell | Ein Board ungelöst | Gelöster Ausreißer | Gesamt |
|---|---:|---:|---:|---:|
| Technisch | 4 | 4 | 4 | 12 |
| Taktisch | 3 | 3 | 3 | 9 |
| Bürokratisch | 3 | 3 | 3 | 9 |
| Dramatisch | 2 | 3 | 3 | 8 |
| Kosmisch | 2 | 3 | 3 | 8 |
| Norddeutsch | 2 | 2 | 2 | 6 |
| Sportlich | 2 | 3 | 3 | 8 |
| Juristisch | 4 | 4 | 4 | 12 |
| **Gesamt** | **22** | **25** | **25** | **72** |

# Texte

## Technisch

### Universell

- `quadwords-single-board-collapse.technical.general.01` – Die Gesamtauswertung zeigt einen klar isolierten Fehlercluster bei {worstBoard}.
- `quadwords-single-board-collapse.technical.general.02` – Drei Teilprozesse lieferten plausible Ergebnisse; {worstBoard} erzeugte den dominanten Laufzeitfehler.
- `quadwords-single-board-collapse.technical.general.03` – Die Systemleistung wurde durch einen einzelnen instabilen Board-Worker bei {worstBoard} verzerrt.
- `quadwords-single-board-collapse.technical.general.04` – Das Grid war grundsätzlich betriebsbereit. Nur {worstBoard} lief offenbar auf abweichender Firmware.

### Variante A: ein Board ungelöst

- `quadwords-single-board-collapse.technical.unsolved.01` – Der Worker für {worstBoard} beendete den Prozess ohne gültigen Lösungswert.
- `quadwords-single-board-collapse.technical.unsolved.02` – Für {worstBoard} blieb der Resolver bis zum Ende in einem nicht terminalen Fehlerzustand.
- `quadwords-single-board-collapse.technical.unsolved.03` – Drei Boards committed; {worstBoard} hinterließ lediglich einen offenen Incident.
- `quadwords-single-board-collapse.technical.unsolved.04` – Das Teilsystem {worstBoard} konnte trotz vollständiger Retry-Sequenz nicht erfolgreich abgeschlossen werden.

### Variante B: alle gelöst, ein deutlicher Ausreißer

- `quadwords-single-board-collapse.technical.solved-outlier.01` – Der Worker für {worstBoard} lieferte zwar Erfolg, überschritt das gemeinsame Latenzbudget aber deutlich.
- `quadwords-single-board-collapse.technical.solved-outlier.02` – Alle Teilprozesse endeten erfolgreich; {worstBoard} benötigte nur verdächtig viele Iterationen.
- `quadwords-single-board-collapse.technical.solved-outlier.03` – Das Board {worstBoard} erreichte den grünen Zustand erst nach einem klaren Performance-Einbruch.
- `quadwords-single-board-collapse.technical.solved-outlier.04` – Die Gesamttelemetrie ist formal grün, enthält bei {worstBoard} aber einen massiven Ausreißer.

## Taktisch

### Universell

- `quadwords-single-board-collapse.tactical.general.01` – Drei Boards hielten die Linie. {worstBoard} band bewusst den Großteil der gegnerischen Aufmerksamkeit.
- `quadwords-single-board-collapse.tactical.general.02` – Das Gesamtergebnis folgt einem Vierfrontenplan, bei dem {worstBoard} die undankbare Rolle des sichtbaren Problems übernahm.
- `quadwords-single-board-collapse.tactical.general.03` – {worstBoard} wurde strategisch exponiert, damit die übrigen Boards ungestört arbeiten konnten.

### Variante A: ein Board ungelöst

- `quadwords-single-board-collapse.tactical.unsolved.01` – Drei Boards wurden gesichert; {worstBoard} blieb als kontrolliert aufgegebener Außenposten zurück.
- `quadwords-single-board-collapse.tactical.unsolved.02` – Die Operation war auf drei erfolgreiche Fronten ausgelegt. {worstBoard} gehörte nicht zum Primärziel.
- `quadwords-single-board-collapse.tactical.unsolved.03` – {worstBoard} wurde bis zum Ende gebunden, ohne unnötige Ressourcen in eine aussichtslose Rückeroberung zu investieren.

### Variante B: alle gelöst, ein deutlicher Ausreißer

- `quadwords-single-board-collapse.tactical.solved-outlier.01` – Alle vier Boards wurden genommen; {worstBoard} verlangte lediglich eine taktisch großzügige Nachspielzeit.
- `quadwords-single-board-collapse.tactical.solved-outlier.02` – {worstBoard} fiel im Zeitplan zurück, wurde aber noch vor Abschluss der Gesamtoperation gesichert.
- `quadwords-single-board-collapse.tactical.solved-outlier.03` – Die übrigen Boards waren der schnelle Vorstoß. {worstBoard} war der bewusst langsame Belagerungsabschnitt.

## Bürokratisch

### Universell

- `quadwords-single-board-collapse.bureaucratic.general.01` – Das Gesamtergebnis wird durch einen einzelnen Sondervorgang bei {worstBoard} unverhältnismäßig belastet.
- `quadwords-single-board-collapse.bureaucratic.general.02` – Drei Boardstellen arbeiteten im Regelbetrieb; {worstBoard} wurde in ein gesondertes Klärungsverfahren überführt.
- `quadwords-single-board-collapse.bureaucratic.general.03` – Für {worstBoard} bestand ein abweichender Bearbeitungsbedarf, der die Gesamtstatistik sichtbar beeinflusst.

### Variante A: ein Board ungelöst

- `quadwords-single-board-collapse.bureaucratic.unsolved.01` – Der Teilvorgang {worstBoard} musste mangels abschließender Lösungsfeststellung offen geschlossen werden.
- `quadwords-single-board-collapse.bureaucratic.unsolved.02` – Bei {worstBoard} konnte innerhalb der vorgesehenen Prüfschritte kein bestandskräftiger Abschluss hergestellt werden.
- `quadwords-single-board-collapse.bureaucratic.unsolved.03` – Drei Boardakten wurden erledigt; {worstBoard} verbleibt bis auf Weiteres im Status ungeklärt.

### Variante B: alle gelöst, ein deutlicher Ausreißer

- `quadwords-single-board-collapse.bureaucratic.solved-outlier.01` – Der Vorgang {worstBoard} wurde zwar abgeschlossen, überschritt jedoch den vorgesehenen Bearbeitungsrahmen erheblich.
- `quadwords-single-board-collapse.bureaucratic.solved-outlier.02` – Alle vier Boardverfahren sind erledigt; bei {worstBoard} bestand lediglich ein außergewöhnlicher Nachbearbeitungsaufwand.
- `quadwords-single-board-collapse.bureaucratic.solved-outlier.03` – Die Sachentscheidung zu {worstBoard} erging verspätet, aber noch innerhalb der maximal zulässigen Verfahrensstufen.

## Dramatisch

### Universell

- `quadwords-single-board-collapse.dramatic.general.01` – Drei Boards standen an meiner Seite. {worstBoard} wählte einen anderen Weg.
- `quadwords-single-board-collapse.dramatic.general.02` – Das Grid war beinahe im Gleichgewicht, bis {worstBoard} seine eigene Tragödie begann.

### Variante A: ein Board ungelöst

- `quadwords-single-board-collapse.dramatic.unsolved.01` – Drei Boards fanden Erlösung. {worstBoard} blieb im Dunkel zurück.
- `quadwords-single-board-collapse.dramatic.unsolved.02` – Ich rettete drei Viertel des Grids und musste {worstBoard} den Buchstaben überlassen.
- `quadwords-single-board-collapse.dramatic.unsolved.03` – Als alles endete, war {worstBoard} noch immer ein offenes Versprechen und eine geschlossene Wunde.

### Variante B: alle gelöst, ein deutlicher Ausreißer

- `quadwords-single-board-collapse.dramatic.solved-outlier.01` – Auch {worstBoard} wurde am Ende gerettet, aber nicht ohne den Rest des Grids altern zu lassen.
- `quadwords-single-board-collapse.dramatic.solved-outlier.02` – Alle vier Boards überlebten. {worstBoard} bestand dennoch darauf, daraus eine Tragödie zu machen.
- `quadwords-single-board-collapse.dramatic.solved-outlier.03` – {worstBoard} erreichte das Ziel zuletzt, schwer gezeichnet und mit der Würde eines gefallenen Königreichs.

## Kosmisch

### Universell

- `quadwords-single-board-collapse.cosmic.general.01` – Drei Boards teilten eine stabile Umlaufbahn; {worstBoard} entwickelte sein eigenes Gravitationsproblem.
- `quadwords-single-board-collapse.cosmic.general.02` – Die QuadWords-Konstellation war beinahe harmonisch, bis {worstBoard} aus der kosmischen Ordnung ausscherte.

### Variante A: ein Board ungelöst

- `quadwords-single-board-collapse.cosmic.unsolved.01` – {worstBoard} verschwand hinter einem Ereignishorizont, während die übrigen Boards noch beobachtbar blieben.
- `quadwords-single-board-collapse.cosmic.unsolved.02` – Drei Zeitlinien führten zur Lösung. Die von {worstBoard} endete in kalter Leere.
- `quadwords-single-board-collapse.cosmic.unsolved.03` – Das Board {worstBoard} konnte in diesem Universum nicht auf einen grünen Endzustand kollabieren.

### Variante B: alle gelöst, ein deutlicher Ausreißer

- `quadwords-single-board-collapse.cosmic.solved-outlier.01` – {worstBoard} erreichte die Lösung erst nach mehreren zusätzlichen Umläufen um das Offensichtliche.
- `quadwords-single-board-collapse.cosmic.solved-outlier.02` – Alle vier Boards konvergierten, doch {worstBoard} benötigte eine deutlich längere kosmische Epoche.
- `quadwords-single-board-collapse.cosmic.solved-outlier.03` – Die Lösung von {worstBoard} kam aus großer zeitlicher Entfernung, aber immerhin noch aus demselben Universum.

## Norddeutsch

### Universell

- `quadwords-single-board-collapse.northern-german.general.01` – {worstBoard} war das Problem.
- `quadwords-single-board-collapse.northern-german.general.02` – Drei gingen ordentlich. {worstBoard} nicht so.

### Variante A: ein Board ungelöst

- `quadwords-single-board-collapse.northern-german.unsolved.01` – {worstBoard} blieb offen. Ist dann so.
- `quadwords-single-board-collapse.northern-german.unsolved.02` – Drei gelöst. {worstBoard} hatte frei.

### Variante B: alle gelöst, ein deutlicher Ausreißer

- `quadwords-single-board-collapse.northern-german.solved-outlier.01` – {worstBoard} ging auch. Nur später.
- `quadwords-single-board-collapse.northern-german.solved-outlier.02` – Alle grün. {worstBoard} mit Umweg.

## Sportlich

### Universell

- `quadwords-single-board-collapse.sporting.general.01` – Drei Boards hatten einen guten Tag. Bei {worstBoard} lief ein anderes Spiel.
- `quadwords-single-board-collapse.sporting.general.02` – Die Mannschaft war auf drei Positionen stabil; {worstBoard} blieb die klare Problemzone.

### Variante A: ein Board ungelöst

- `quadwords-single-board-collapse.sporting.unsolved.01` – Drei Boards brachten das Ergebnis über die Linie. {worstBoard} blieb ohne Abschluss.
- `quadwords-single-board-collapse.sporting.unsolved.02` – Wir haben auf drei Feldern geliefert, aber bei {worstBoard} keinen Zugriff auf die Partie bekommen.
- `quadwords-single-board-collapse.sporting.unsolved.03` – {worstBoard} war heute die Position, auf der uns bis zum Schlusspfiff die entscheidende Aktion fehlte.

### Variante B: alle gelöst, ein deutlicher Ausreißer

- `quadwords-single-board-collapse.sporting.solved-outlier.01` – Alle vier Boards wurden gewonnen, doch {worstBoard} ging deutlich tiefer in die Verlängerung.
- `quadwords-single-board-collapse.sporting.solved-outlier.02` – {worstBoard} kam spät ins Spiel und brauchte mehrere zusätzliche Anläufe bis zum Abschluss.
- `quadwords-single-board-collapse.sporting.solved-outlier.03` – Die Gesamtleistung war ordentlich; {worstBoard} produzierte nur einen sehr langen individuellen Ausreißer.

## Juristisch

### Universell

- `quadwords-single-board-collapse.legal.general.01` – Das Gesamtergebnis wird durch das Verhalten von {worstBoard} verzerrt. Rechtliche Schritte werden geprüft.
- `quadwords-single-board-collapse.legal.general.02` – Eine gesamtschuldnerische Haftung aller Boards wird angesichts des Sonderfalls {worstBoard} ausdrücklich bestritten.
- `quadwords-single-board-collapse.legal.general.03` – Die Verantwortlichkeit für die auffällige Abweichung ist nach vorläufiger Prüfung bei {worstBoard} zu verorten.
- `quadwords-single-board-collapse.legal.general.04` – Die übrigen Boards dürfen für die Vorgänge bei {worstBoard} nicht in Mithaftung genommen werden.

### Variante A: ein Board ungelöst

- `quadwords-single-board-collapse.legal.unsolved.01` – Das Board {worstBoard} hat seine Pflicht zur rechtzeitigen Lösungsmitwirkung nicht erfüllt.
- `quadwords-single-board-collapse.legal.unsolved.02` – Mangels abschließender Lösung verbleibt {worstBoard} als gesonderter Streitgegenstand.
- `quadwords-single-board-collapse.legal.unsolved.03` – Drei Boards haben ihre Leistung erbracht; gegenüber {worstBoard} bleiben sämtliche Ansprüche vorbehalten.
- `quadwords-single-board-collapse.legal.unsolved.04` – Die Nichterledigung von {worstBoard} ist isoliert zu würdigen und darf das übrige Ergebnis nicht entwerten.

### Variante B: alle gelöst, ein deutlicher Ausreißer

- `quadwords-single-board-collapse.legal.solved-outlier.01` – {worstBoard} wurde zwar gelöst, die ungewöhnlich späte Leistungserbringung bleibt jedoch erklärungsbedürftig.
- `quadwords-single-board-collapse.legal.solved-outlier.02` – Die Erfüllung durch {worstBoard} erfolgte erst nach erheblicher Verzögerung und wird nur unter Vorbehalt anerkannt.
- `quadwords-single-board-collapse.legal.solved-outlier.03` – Alle Boards haben letztlich geleistet; bei {worstBoard} wird dennoch Verzug festgestellt.
- `quadwords-single-board-collapse.legal.solved-outlier.04` – Die Wirksamkeit der Lösung von {worstBoard} wird nicht bestritten, ihre auffällige Verspätung jedoch ausdrücklich gerügt.

## Redaktioneller Stand

Mit diesem Anlass liegen zusätzlich zu den bisher gesicherten 434 Texten weitere **72 Texte** vor. Der gesicherte Gesamtstand beträgt damit **506 Texte**.
