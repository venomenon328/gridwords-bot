# Verbindliches Modell für kontextabhängige Ausreden

**Status:** fachlich abgenommen  
**Stand:** 3. August 2026  
**Gültig ab:** Inkrement 11  
**Verbindliches Issue:** #42

Dieses Dokument definiert ein freiwilliges Unterhaltungsfeature für seltene, klar begründete Ergebniskonstellationen. Der Ergebnisautor erhält drei redaktionell gepflegte Ausreden zur Auswahl, kann einmal nach einem Stil neu würfeln oder verzichten. Vorschläge und Bedienung bleiben ephemer; nur der gewählte Text wird persistiert und in dieselbe kanonische Ergebnisnachricht aufgenommen.

Der Funktionsumfang bis einschließlich Inkrement 10 und der Zwischeninkremente 10.x bildet die feature-complete Basis für Version 1.0.x beziehungsweise 1.1.0. Die früher vorgemerkten Statistik-/Konfigurations-Commands und ein generisches Inkrement für regelbasierte Kommentare sind keine Roadmap-Verpflichtungen mehr. Inkrement 11 ist eine bewusst neu priorisierte optionale Produkterweiterung.

## 1. Ziel und Grundsätze

Ausreden sollen schlechte oder auffällige Ergebnisse humorvoll begleiten, ohne Ergebnisdaten zu verändern oder einen Nutzer öffentlich vorzuführen.

Verbindlich gilt:

- Das Feature ist freiwillig.
- Es gibt keinen Ausreden-Command.
- Es entsteht keine zusätzliche öffentliche Nachricht.
- Vorschläge, Stilwahl, Neu-Würfeln, Verzicht und Fehlermeldungen sind ausschließlich für den Ergebnisautor sichtbar.
- Die öffentliche Darstellung bleibt Teil der vorhandenen kanonischen Ergebnisnachricht.
- Nach einer Auswahl steht öffentlich ausschließlich der exakt gewählte Ausredentext. Stilname, Template-Schlüssel, Anlass, Überschrift und sonstige Metadaten werden nicht dargestellt.
- Ausreden verändern weder Ergebnis, Serien, Statistiken, Teilnahme noch Berichte.
- Es gibt keine generative KI zur Laufzeit und keinen externen Textdienst.
- Jede mögliche Ausgabe stammt aus einem versionierten und beim Start validierten redaktionellen Katalog.
- Fehlende Kontextwerte dürfen niemals als `null`, leerer Platzhalter oder erfundene Tatsache erscheinen.

Eine spätere Nutzung persistierter Ausreden in Achievements, Ergebnisabfragen oder Rückblicken ist möglich, aber nicht Bestandteil von Inkrement 11.

## 2. Grundlage: spielbezogene Teilnahme

Inkrement 11 baut auf dem vollständig umgesetzten und gemergten Zwischeninkrement 10.6 auf.

Ausreden verwenden ausschließlich die historisch wirksame Teilnehmermenge des konkreten Spiels am Spieltag:

```text
G(d) = GridWords-Teilnehmer
Q(d) = QuadWords-Teilnehmer
```

Insbesondere gilt:

- `player.active` ist keine zulässige Quelle für Ausredenentscheidungen oder Tagesvergleiche.
- GridWords vergleicht ausschließlich Ergebnisse von Spielern aus `G(d)`.
- QuadWords vergleicht ausschließlich Ergebnisse von Spielern aus `Q(d)`.
- Ein gültiges Share kann den Spieler gemäß `docs/requirements/game-specific-participation.md` für genau dieses Spiel ab dem Ergebnistag aktivieren und ist dadurch nicht von einem Ausredenangebot ausgeschlossen.
- Eine Teilnahme am jeweils anderen Spiel ist unerheblich.
- Cooldown und Angebotsverlauf werden pro Spieler und Spiel geführt.
- Zuletzt gewählte Templates werden dagegen spielübergreifend berücksichtigt, damit ein allgemeiner Text nicht unmittelbar bei GridWords und QuadWords wiederholt wird.

## 3. Einmalige Angebotsentscheidung

Für jedes fachliche `game_result` wird genau einmal entschieden, ob eine Ausrede angeboten wird.

Die Entscheidung wird nur bei der erstmaligen Speicherung eines vollständig gültigen neuen Ergebnisses getroffen. Sie wird im selben atomaren Persistenzpfad wie Ergebnis und Submission gespeichert. Der bestehende Replay-, Korrektur- und Konkurrenzschutz bleibt maßgeblich.

Folgen:

- Ein Replay erzeugt kein neues Angebot.
- Eine Korrektur eines vorhandenen Ergebnisses erzeugt kein erstmaliges Angebot.
- Eine reine QuadWords-Boardanreicherung erzeugt kein erstmaliges Angebot.
- Startup-Recovery und technische Neuverarbeitung erzeugen kein zweites Angebot.
- Ein später aktiviertes Feature erzeugt keine Angebote für alte Ergebnisse.
- Die Liquibase-Migration markiert alle vorhandenen `game_result`-Datensätze ausdrücklich als `NOT_OFFERED`.
- Ein Ergebnis mit negativer Erstentscheidung bleibt dauerhaft ohne Angebot, auch wenn eine spätere Korrektur erstmals einen Schwellenwert überschreitet.

Die positive Angebotsentscheidung wird nur getroffen, wenn:

```text
featureEnabled
AND newValidGameResult
AND relevantGameParticipation
AND noExclusivePositivePriorityEvent
AND cooldownSatisfied
AND atLeastOneExcuseReason
AND atLeastThreeRenderableTemplates
```

`noExclusivePositivePriorityEvent` ist eine Erweiterungsgrenze. Eine spätere positive Rekord- oder Achievement-Reaktion darf das Ausredenangebot unterdrücken. Inkrement 11 führt dafür keine vorauseilende Rekordberechnung ein.

## 4. Seltenheit und Cooldown

Der Standard-Cooldown beträgt drei Kalendertage pro Spieler und Spiel in `Europe/Berlin`.

Hat ein Nutzer für GridWords zuletzt am Montag ein persistiertes Angebot erhalten, ist das nächste GridWords-Angebot frühestens am Donnerstag möglich. Ein QuadWords-Angebot besitzt einen unabhängigen Cooldown.

Maßgeblich ist der lokale Kalendertag von `offered_at`, nicht der Spieltag des Ergebnisses.

Ein persistiertes Angebot verbraucht den Cooldown unabhängig davon, ob es anschließend ausgewählt, abgelehnt, ignoriert, abgelaufen oder durch eine Korrektur invalidiert wird.

Die Prüfung muss unter Konkurrenz konfliktfest sein. Zwei nahezu gleichzeitig verarbeitete Ergebnisse desselben Nutzers und Spiels dürfen nicht beide den Cooldown passieren.

## 5. MVP-Ausredenanlässe

Alle Schwellen sind inklusive. Mehrere gleichzeitig erfüllte Anlässe erzeugen nur ein Angebot, erweitern aber den Kontext für passendere Templates.

### 5.1 Allgemeine Anlässe

#### Nicht gelöst

`NOT_SOLVED` gilt, wenn das Ergebnis als `X/6` beziehungsweise `X/9` gespeichert ist.

#### Sehr späte Einreichung

`VERY_LATE_SUBMISSION` gilt, wenn `received_at` der erstmalig entscheidenden Submission in `Europe/Berlin` mindestens 23:30 Uhr beträgt.

Eine spätere Korrektur verwendet weiterhin den Einreichungszeitpunkt der ursprünglichen Angebotsentscheidung. Sie kann den Anlass weder neu erzeugen noch entfernen.

### 5.2 GridWords

`GRIDWORDS_LAST_ATTEMPT` gilt bei einem gelösten Ergebnis mit genau `6/6` Versuchen.

`GRIDWORDS_VERY_SLOW` gilt bei einer gespeicherten Dauer von mindestens `05:00` Minuten. `04:59` qualifiziert nicht.

### 5.3 QuadWords

`QUADWORDS_VERY_SLOW` gilt bei einer gespeicherten Dauer von mindestens `08:00` Minuten. `07:59` qualifiziert nicht.

#### Einzelner Board-Zusammenbruch

`QUADWORDS_SINGLE_BOARD_COLLAPSE` kann nur bei vollständig vorhandenen vier Boards entstehen.

Die Lösungszeile eines Boards ist die erste einbasierte Zeile, die ausschließlich aus fünf grünen Zellen besteht. Ein Board ohne solche Zeile gilt als nicht gelöst.

Der Anlass gilt in genau einer der folgenden Varianten:

```text
Variante A:
- genau drei Boards sind gelöst
- genau ein Board ist nicht gelöst

Variante B:
- alle vier Boards sind gelöst
- genau ein Board besitzt die höchste Versuchszahl
- dieses schlechteste Board wurde frühestens im achten Versuch gelöst
- Abstand zum zweitschlechtesten Board >= 3 Versuche
```

Beispiele für Variante B:

```text
2 / 4 / 5 / 8  -> qualifiziert
3 / 4 / 6 / 9  -> qualifiziert
3 / 5 / 6 / 8  -> nicht qualifiziert; Abstand nur 2
3 / 5 / 8 / 8  -> nicht qualifiziert; kein eindeutig schlechtestes Board
```

Boardlose QuadWords-Ergebnisse dürfen weder diesen Anlass noch einen Board-Platzhalter oder eine Boardbehauptung erhalten.

### 5.4 Deutlich schlechtestes bisheriges Tagesergebnis

`CLEAR_CURRENT_DAILY_OUTLIER` ist ein eigenständiger Ausredenanlass und zugleich ein möglicher Template-Kontext.

Voraussetzungen:

- mindestens zwei andere gültige Ergebnisse desselben Spiels und Spieltags liegen bereits persistiert vor,
- die anderen Ergebnisse gehören zu tatsächlichen Teilnehmern dieses Spiels am Spieltag,
- der aktuelle Nutzer wird aus der Vergleichsmenge ausgeschlossen,
- nur der zum Zeitpunkt der erstmaligen Angebotsentscheidung bereits committed sichtbare Stand wird betrachtet.

Später eingehende Ergebnisse verändern diese historische Angebotsentscheidung nicht. Templates mit diesem Fakt müssen ausdrücklich einen vorläufigen Stand formulieren, beispielsweise „unter den bisher eingegangenen Ergebnissen“. Sie dürfen niemals behaupten, der Nutzer sei endgültig Tagesletzter.

Für GridWords gilt der Fakt, wenn mindestens eine Regel erfüllt ist:

```text
- aktuelles Ergebnis nicht gelöst und alle anderen Ergebnisse gelöst

ODER

- aktuelles und alle verglichenen Ergebnisse gelöst
- aktuelles Ergebnis benötigt mindestens 5 Versuche
- aktuelles Ergebnis benötigt mindestens 2 Versuche mehr
  als das schlechteste andere Ergebnis

ODER

- aktuelle Dauer mindestens 04:00 Minuten
- aktuelle Dauer mindestens 02:00 Minuten länger
  als die längste andere Dauer
```

Für QuadWords gilt der Fakt, wenn mindestens eine Regel erfüllt ist:

```text
- aktuelles Ergebnis nicht gelöst und alle anderen Ergebnisse gelöst

ODER

- aktuelle Dauer mindestens 06:00 Minuten
- aktuelle Dauer mindestens 03:00 Minuten länger
  als die längste andere Dauer
```

Andere Nutzer werden weder namentlich genannt noch als Gewinner oder Vergleichsziel dargestellt.

## 6. Kontextmodell

Die Angebotslogik und die Template-Auswahl sind getrennte fachliche Komponenten.

Die Angebotslogik liefert mindestens:

- Ergebnis-ID,
- Spieler-ID,
- Spieltyp und Spieltag,
- ursprünglichen Einreichungszeitpunkt,
- alle erfüllten Anlasscodes,
- Ergebnisstatus, Versuchszahl und Dauer,
- vorhandene QuadWords-Boardfakten,
- gegebenenfalls den zum Angebotszeitpunkt eingefrorenen Tagesvergleich,
- Katalog- und Kontextversion.

Templatebedingungen werden ausschließlich aus einem typisierten `ExcuseContext` oder gleichwertigen transportneutralen Modell ausgewertet. Frei interpretierte String-Werte oder Zugriff auf Persistenzentitäten im Katalogrenderer sind nicht zulässig.

Der Tagesvergleich und der ursprüngliche Einreichungszeitpunkt werden als historische Angebotsfakten eingefroren. Ergebnisabhängige Fakten wie Versuchszahl, Dauer und Boards dürfen bei einer Korrektur neu bewertet werden.

## 7. Stilrichtungen

Der MVP besitzt acht stabile redaktionelle Stile:

| Stabiler Schlüssel | Sichtbarer Name | Redaktioneller Charakter |
|---|---|---|
| `TECHNICAL` | technisch | Fehlerzustände, Pipelines, Telemetrie, Deployments, Laborbedingungen |
| `TACTICAL` | taktisch | bewusste Opfer, langfristige Pläne, Ressourcenschonung, Täuschungsmanöver |
| `BUREAUCRATIC` | bürokratisch | Formulare, Zuständigkeiten, Ausschüsse, Prüfverfahren, Verwaltungsakte |
| `DRAMATIC` | dramatisch | Pathos, Verrat, Schicksal und tragische Rasterkonfrontationen |
| `COSMIC` | kosmisch | Planeten, Raumzeit, Vokalkonstellationen und höhere Mächte |
| `NORTHERN_GERMAN` | norddeutsch | kurz, trocken, emotionsarm und ohne unnötige Erklärung |
| `SPORTING` | sportlich | Pressekonferenz, Analyse, Reaktion, Trainingswoche und Saisonverlauf |
| `LEGAL` | juristisch | Haftung, Widerspruch, Beweislage, Anerkenntnis und Rechtsmittel |

Stilnamen werden in der ephemeren Auswahl angezeigt. Sie werden niemals in die kanonische Ergebnisnachricht übernommen.

Stabile Schlüssel dürfen nicht umgedeutet werden. Neue Stile benötigen eine redaktionelle und fachliche Dokumentationsänderung.

## 8. Redaktioneller Template-Katalog

Der Katalog wird versioniert im Repository gepflegt, bevorzugt als validierte Ressource unter:

```text
src/main/resources/excuses/catalog.json
```

Eine Template-Definition enthält mindestens:

```text
id
style
games
topic
specificity
weight
requiresAll
excludesAny
text
selectable
```

Beispiel:

```json
{
  "id": "quadwords.single-board-collapse.legal.01",
  "style": "LEGAL",
  "games": ["QUADWORDS"],
  "topic": "SINGLE_BOARD_BLAME",
  "specificity": 30,
  "weight": 100,
  "requiresAll": [
    "FOUR_BOARDS_PRESENT",
    "QUADWORDS_SINGLE_BOARD_COLLAPSE",
    "UNIQUE_WORST_BOARD"
  ],
  "excludesAny": [],
  "text": "Das Gesamtergebnis wird durch das Verhalten von {worstBoard} verzerrt. Rechtliche Schritte werden geprüft.",
  "selectable": true
}
```

### 8.1 Platzhalter

Der MVP verwendet nur eine kleine explizite Whitelist, beispielsweise:

```text
{game}
{score}
{duration}
{worstBoard}
```

Es gibt keine freien Ausdrücke, bedingten Textfragmente oder grammatikalischen Satzgeneratoren.

Ein Template ist nur Kandidat, wenn sämtliche `requiresAll`-Fakten vorliegen, keine `excludesAny`-Bedingung vorliegt, alle Platzhalter typisiert und vollständig auflösbar sind, Spiel und Stil unterstützt werden und der Text innerhalb der Discord-Grenzen liegt.

Kann ein Platzhalter nicht aufgelöst werden, wird das vollständige Template verworfen. Es wird niemals teilweise gerendert.

### 8.2 Katalogvalidierung

Beim Start und in einem vollständigen Katalogtest werden mindestens geprüft:

- global eindeutige, nicht leere IDs,
- ausschließlich bekannte Stile, Spiele, Themen, Fakten und Platzhalter,
- positive Gewichte und zulässige Spezifität,
- nicht leere Texte innerhalb definierter Längenlimits,
- keine unerwünschten Mentions wie `@everyone` oder `@here`,
- keine nicht auflösbaren Platzhalterkombinationen,
- mindestens sechs allgemeine Templates je Stil,
- mindestens drei allgemeine renderbare Templates je Stil für einen möglichen Stil-Neuwurf.

Der erste redaktionelle Grundstock soll ungefähr 70 bis 90 Templates enthalten. Mindestens 48 davon sind allgemeine Texte, sechs je Stil. Weitere Templates decken GridWords, QuadWords, Board-Zusammenbrüche, späte Einreichungen und bisherige Tagesausreißer ab.

Eine bestehende Template-ID wird niemals für einen anderen Witz wiederverwendet. Nicht mehr neu auswählbare Templates bleiben mit `selectable: false` im Katalog, solange persistierte Datensätze darauf verweisen können.

## 9. Deterministische Kandidatenauswahl

Die Zufallsquelle wird über einen schmalen Port oder ein gleichwertiges Interface injiziert. Tests können dadurch jede Auswahl reproduzieren.

`weight` und `specificity` haben unterschiedliche Bedeutungen:

- `specificity` bevorzugt Templates, die den tatsächlichen Ergebnis- und Boardkontext nutzen.
- `weight` variiert die Häufigkeit unter ansonsten gleich geeigneten Kandidaten.

### 9.1 Initiale Vorschläge

Beim ersten gültigen Öffnen werden genau drei unterschiedliche Templates ausgewählt und vor der ephemeren Antwort persistiert.

Auswahlregeln:

1. Nur vollständig passende und renderbare Templates sind Kandidaten.
2. Wenn kontextspezifische Kandidaten existieren, muss mindestens einer der drei Texte kontextspezifisch sein.
3. Nach Möglichkeit stammen die drei Texte aus drei unterschiedlichen Stilen.
4. Nach Möglichkeit werden drei unterschiedliche Themenfamilien verwendet.
5. Stile werden vor einzelnen Templates ausgewählt, damit ein Stil mit großem Katalog nicht allein wegen seiner Menge dominiert.
6. Innerhalb eines Stils erfolgt die Auswahl gewichtet.
7. Es wird ohne Zurücklegen ausgewählt.

Sind keine drei renderbaren Templates verfügbar, darf kein Angebot entstehen.

### 9.2 Einmaliges Neu-Würfeln nach Stil

Der Nutzer kann einmal `Anderer Stil` wählen. Das folgende Auswahlmenü enthält nur Stile, für die mindestens drei passende und bislang nicht gezeigte Templates verfügbar sind.

Nach erfolgreicher Stilwahl werden genau drei neue Texte dieses Stils ausgewählt und persistiert. Erst diese erfolgreiche Erzeugung verbraucht den Neu-Wurf.

Bereits im aktuellen Ausredenvorgang gezeigte Template-IDs sind ausgeschlossen. Ein zweiter Neu-Wurf wird abgelehnt.

### 9.3 Wiederholungsschutz über Ergebnisse hinweg

Bei einem neuen Angebot gilt:

- Das zuletzt vom Nutzer gewählte Template ist hart ausgeschlossen.
- Die weiteren neun zuletzt gewählten Templates werden zunächst weich ausgeschlossen.
- Reichen die verbleibenden Kandidaten nicht aus, werden weich ausgeschlossene Templates vom ältesten beginnend wieder zugelassen.
- Das unmittelbar vorher gewählte Template bleibt in jedem Fall ausgeschlossen.
- Kürzlich gewählte Themenfamilien werden nach Möglichkeit vermieden.

Maßgeblich sind ausschließlich weiterhin gültige Ausreden im Zustand `SELECTED`. Abgelehnte, abgelaufene und invalidierte Ausreden zählen nicht als gewählte Wiederholungshistorie.

## 10. Interaktionsablauf

### 10.1 Öffentliches Angebot

Eine qualifizierende kanonische Ergebnisnachricht enthält zusätzlich genau einen Button:

```text
Ausrede wählen
```

Die drei Texte werden nicht öffentlich vorab angezeigt.

### 10.2 Ephemere Auswahl

Nur der Ergebnisautor darf den Button verwenden. Nach erfolgreicher Autorisierung sieht er ephemer:

- drei nummerierte Ausreden mit Stilbezeichnung,
- drei Auswahlaktionen,
- `Anderer Stil`, sofern ein Stil-Neuwurf möglich ist,
- `Keine Ausrede`.

Ein fremder Nutzer erhält lediglich einen ephemeren Hinweis. Sein Klick verändert weder Zustand noch Ablaufzeit noch Neu-Wurf.

### 10.3 Stilwahl

`Anderer Stil` öffnet ephemer ein Auswahlmenü der aktuell möglichen Stile. Nach der Stilwahl ersetzt die ephemere Antwort die bisherigen Vorschläge durch drei neue Texte des gewählten Stils.

### 10.4 Auswahl

Die Auswahl ist atomar und idempotent:

- Nur ein tatsächlich für die aktuelle Kontextgeneration persistierter Text kann gewählt werden.
- Der erste erfolgreiche Übergang nach `SELECTED` gewinnt.
- Doppelklicks und konkurrierende Interactions erzeugen höchstens eine Auswahl.
- Nach erfolgreicher Persistenz wird ein kanonischer Refresh angefordert.
- Die Interaction editiert die öffentliche Discord-Nachricht niemals direkt.

### 10.5 Verzicht

`Keine Ausrede` setzt den Zustand atomar auf `DECLINED` und fordert einen kanonischen Refresh an. Es entsteht keine öffentliche Verzichtsmeldung.

### 10.6 Ablaufzeit

Ein Angebot ist standardmäßig 15 Minuten ab `offered_at` gültig. Nach Ablauf kann keine Option mehr gewählt und kein Neu-Wurf mehr ausgeführt werden.

## 11. Versionierte Komponenten und Autorisierung

Component-IDs enthalten niemals Ausredentext. Sie werden versioniert und vollständig serverseitig validiert, beispielsweise:

```text
excuse:v1:open:<gameResultId>
excuse:v1:pick:<gameResultId>:<contextGeneration>:<round>:<position>
excuse:v1:reroll:<gameResultId>:<contextGeneration>
excuse:v1:style:<gameResultId>:<contextGeneration>
excuse:v1:decline:<gameResultId>:<contextGeneration>
```

Vor jeder Aktion werden mindestens geprüft:

- konfigurierte Guild und Channel,
- aktuelle kanonische Message-ID beim öffentlichen Öffnen,
- Ergebnis-ID und Ergebnisautor,
- Actor-ID der Interaction,
- aktueller Ausredenzustand,
- Ablaufzeit,
- Kontextgeneration,
- Neu-Wurf-Status,
- tatsächlich persistierte Runde und Position,
- unveränderte fachliche Eignung des gewählten Templates.

Der JDA-Listener bestätigt sofort ephemer und delegiert sämtliche Datenbank- und Auswahlarbeit an den begrenzten Worker. JDA-Typen verlassen den Discord-Adapter nicht.

## 12. Persistenzmodell

Jedes `game_result` besitzt nach der Migration genau einen Ausredenzustand.

### 12.1 Hauptzustand

Ein Datensatz `game_result_excuse` oder eine gleichwertige Struktur enthält mindestens:

```text
game_result_id                 eindeutiger Fremdschlüssel
trigger_source_message_id      erstmalig entscheidende Submission
status
catalog_version
context_version
context_generation
offered_at
expires_at
reroll_used
selected_template_id
selected_style
selected_topic
selected_rendered_text
selected_at
created_at
updated_at
```

Zulässige Zustände:

| Zustand | Bedeutung |
|---|---|
| `NOT_OFFERED` | einmalige Entscheidung negativ; terminal |
| `AVAILABLE` | öffentliches Angebot aktiv |
| `SELECTED` | Text gewählt und öffentlich darstellbar |
| `DECLINED` | Nutzer hat ausdrücklich verzichtet; terminal |
| `EXPIRED` | Auswahlzeit abgelaufen; terminal |
| `INVALIDATED` | Ergebnisänderung machte Angebot oder Auswahl ungültig; terminal |

`selected_rendered_text` ist ein unveränderlicher Snapshot des tatsächlich gewählten Textes. Eine spätere Katalogänderung darf ihn nicht rückwirkend verändern.

`selected_style` und `selected_topic` werden ebenfalls als Snapshot gespeichert. Zukünftige Achievements analysieren niemals den freien Text.

### 12.2 Persistierte Optionen

Eine zugehörige Optionstabelle enthält für die aktive Kontextgeneration höchstens sechs Einträge:

```text
INITIAL       Position 1 bis 3
STYLE_REROLL  Position 1 bis 3
```

Jeder Eintrag enthält mindestens:

```text
game_result_id
context_generation
round
position
template_id
style
topic
rendered_text
```

Fachliche Eindeutigkeiten werden durch PostgreSQL-Constraints abgesichert:

- höchstens eine Position je Ergebnis, Generation und Runde,
- keine doppelte Template-ID innerhalb eines Ausredenvorgangs,
- nur bekannte Runden und Positionen,
- ausgewählte Template-ID muss zu einer persistierten aktuellen Option gehören.

Optionen werden vor ihrer ephemeren Anzeige persistiert. Dadurch erscheinen nach einem erneuten Öffnen oder Botneustart dieselben Vorschläge.

Ungewählte Optionen dürfen bei einer Korrektur atomar durch eine neue Kontextgeneration ersetzt werden. Ein bereits gewählter Text wird niemals ersetzt.

## 13. Kanonische Ergebnisnachricht

Die kanonische Transportprojektion unterscheidet mindestens:

```text
NONE
AVAILABLE
SELECTED(renderedText)
```

Die vollständige kanonische Nachricht wird stets aus dem aktuellen persistierten Ergebnis-, Serien- und Ausredenzustand neu gerendert.

### 13.1 Verfügbare Ausrede

`AVAILABLE` ergänzt den öffentlichen Button, aber keinen sichtbaren Ausredentext.

### 13.2 Gewählte Ausrede

`SELECTED` entfernt den Button und ergänzt am Ende der vorhandenen kanonischen Beschreibung ausschließlich den gewählten Text als Zitat, beispielsweise:

```text
„Die Buchstaben waren technisch anwesend, emotional jedoch nicht verfügbar.“
```

Nicht dargestellt werden Stil, Überschrift oder Label `Ausrede`, Anlass, Template-ID, Themenfamilie oder Auswahlzeitpunkt.

### 13.3 Andere Zustände

`NOT_OFFERED`, `DECLINED`, `EXPIRED` und `INVALIDATED` ergänzen weder Button noch Text.

Alle Änderungen verwenden dieselbe kanonische Discord-Message-ID. Die Interaction persistiert nur den Zustand und fordert anschließend die vorhandene kanonische Refresh-/Claim-/Recovery-Pipeline an. Create und Edit übertragen Embed und Action Rows gemeinsam, damit kein veralteter Button stehen bleibt.

## 14. Korrekturen und Boardanreicherung

Eine Korrektur erzeugt niemals ein zweites Angebot.

Bei `NOT_OFFERED`, `DECLINED`, `EXPIRED` oder `INVALIDATED` bleibt der Zustand unverändert.

Bei `AVAILABLE` werden aktuelle ergebnisabhängige Fakten neu berechnet. Ursprünglicher Einreichungszeitpunkt und historischer Tagesvergleich bleiben eingefroren.

- Ist kein Ausredenanlass mehr erfüllt, wechselt der Zustand nach `INVALIDATED`.
- Gibt es nicht mehr mindestens drei renderbare Templates, wechselt der Zustand nach `INVALIDATED`.
- Bleibt das Ergebnis qualifiziert, erhöht eine inhaltliche Kontextänderung die `context_generation` und ersetzt ungewählte Optionen atomar.
- `offered_at`, `expires_at` und Cooldown werden nicht zurückgesetzt.

Eine nachträgliche QuadWords-Boardanreicherung darf dadurch erstmals boardbezogene Vorschläge innerhalb desselben bereits vorhandenen Angebots ermöglichen. Sie erzeugt kein neues Angebot und verlängert die Ablaufzeit nicht.

Bei `SELECTED` wird das gewählte Template gegen den aktuellen Ergebniszustand neu geprüft und erneut gerendert. Die Auswahl bleibt nur bestehen, wenn alle Templatebedingungen weiterhin erfüllt sind und der erneut gerenderte Text exakt dem gespeicherten `selected_rendered_text` entspricht.

Andernfalls wechselt der Zustand nach `INVALIDATED`; der Text wird aus der kanonischen Nachricht entfernt. Es gibt keine automatische Ersatzwahl und kein neues Angebot. Der Bot formuliert eine bereits gewählte Ausrede niemals stillschweigend um.

## 15. Ablauf, Recovery und Konkurrenz

Ein idempotenter Ablaufdienst wird beim Startup und regelmäßig durch den bestehenden Scheduler ausgelöst.

Für jedes `AVAILABLE` mit `expires_at <= now` gilt atomar:

```text
AVAILABLE -> EXPIRED
und kanonischen Refresh anfordern
```

Die Interaction prüft die Ablaufzeit zusätzlich selbst und kann denselben Übergang konfliktfrei auslösen.

Die vorhandene kanonische Publication- und Refresh-Generation bleibt die einzige öffentliche Delivery-Pipeline:

- Discord-I/O liegt außerhalb von Datenbanktransaktionen.
- Zustandsänderung und dauerhafter Refresh-Auftrag werden atomar persistiert.
- Claims, Leases, Retry, Startup-Recovery, externe Löschung und Retirement-Fence gelten unverändert.
- Ein bereits pensioniertes Ergebnis darf durch einen Ausreden-Refresh nicht neu veröffentlicht werden.
- Doppelte, verspätete oder manipulierte Interactions bleiben ohne zusätzliche öffentliche Wirkung.

## 16. Konfiguration

Die Konfiguration wird typisiert gebunden. Verstreute Umgebungszugriffe sind nicht zulässig.

Vorgesehene Defaults:

```text
EXCUSES_ENABLED=false
EXCUSE_OFFER_LIFETIME=PT15M
EXCUSE_COOLDOWN_DAYS=3
EXCUSE_LATE_SUBMISSION_TIME=23:30
EXCUSE_GRIDWORDS_SLOW_DURATION=PT5M
EXCUSE_QUADWORDS_SLOW_DURATION=PT8M
```

Weitere fachliche Schwellen dürfen ebenfalls typisiert konfigurierbar sein, behalten aber die in diesem Dokument festgelegten Defaults. Es gibt im MVP keinen Runtime- oder Slash-Command zur Änderung.

### 16.1 Rollout-Grenze

`EXCUSES_ENABLED` bleibt in jeder produktiven Konfiguration `false`, bis eine getrennte Discord-Testanwendung mit isolierter PostgreSQL-Datenbank die dokumentierte reale Abnahme bestanden hat. Die Aktivierung gehört ausschließlich in diese nicht versionierte Testkonfiguration; sie ist weder ein Produktions-Rollout noch eine fachliche Schalteroberfläche.

## 17. Nicht Bestandteil von Inkrement 11

- generative KI oder externe Text-API,
- frei nutzbarer Ausreden-Command,
- individuelle Stilpräferenzen,
- Änderung oder Rücknahme einer bereits gewählten Ausrede,
- öffentliche Anzeige des Stils,
- Tagesabschluss-Ausreden oder endgültige Tagesplatzierungen,
- namentliche Vergleiche mit anderen Spielern,
- Aufnahme in Wochen- oder Monatsberichte,
- Ergebnisabruf und Jahresrückblick mit Ausreden,
- ausredenbezogene Achievements,
- redaktionelles Admin-Interface oder Datenbankpflege des Katalogs.

## 18. Abnahmekriterien

Inkrement 11 ist fachlich vollständig, wenn mindestens folgende Fälle automatisiert und, wo genannt, real geprüft sind:

1. Ein nicht qualifizierendes neues Ergebnis erhält atomar `NOT_OFFERED` und verändert die kanonische Nachricht nicht.
2. Ein qualifizierendes neues Ergebnis erhält höchstens ein persistiertes Angebot.
3. Replay, Korrektur, Boardanreicherung, Retry und Startup-Recovery erzeugen kein erstmaliges zweites Angebot.
4. Der Drei-Tage-Cooldown ist pro Spiel getrennt und unter Konkurrenz konfliktfest.
5. `6/6`, `05:00`, `08:00`, `23:30` und alle unmittelbar darunterliegenden Grenzen sind exakt getestet.
6. Der QuadWords-Boardausreißer verlangt einen eindeutigen schlechtesten Quadranten und mindestens drei Versuche Abstand zum zweitschlechtesten Board.
7. Boardlose QuadWords-Ergebnisse erzeugen keine Boardfakten und keine Boardtexte.
8. Der bisherige Tagesausreißer benötigt mindestens zwei andere Ergebnisse und verwendet ausschließlich die passende spielbezogene Teilnehmermenge.
9. Spätere Tagesergebnisse verändern die historische Angebotsentscheidung nicht.
10. Nur der Ergebnisautor kann öffnen, auswählen, neu würfeln oder verzichten.
11. Die initiale Auswahl enthält drei unterschiedliche Templates und möglichst drei Stile.
12. Mindestens ein kontextspezifischer Text erscheint, sofern ein solcher Kandidat existiert.
13. Ein Stil-Neuwurf liefert genau drei bislang nicht gezeigte Texte und ist nur einmal möglich.
14. Unbekannte Fakten, Platzhalter, Stile, Spiele, Mentions und doppelte IDs lassen den Katalogtest scheitern.
15. Keine öffentliche Ausgabe enthält einen unaufgelösten Platzhalter.
16. Eine Auswahl persistiert Template-, Stil-, Themen- und Text-Snapshot vor der öffentlichen Änderung.
17. In der kanonischen Nachricht erscheint ausschließlich der gewählte Text, ohne Stil oder Ausredenlabel.
18. Auswahl, Verzicht, Ablauf und Invalidierung entfernen den Button durch einen vollständigen kanonischen Refresh.
19. Doppelklicks und konkurrierende Interactions veröffentlichen höchstens eine Auswahl.
20. Korrekturen revalidieren verfügbare und gewählte Ausreden, formulieren aber niemals einen gewählten Text um.
21. Wiederholungsschutz schließt mindestens das unmittelbar zuvor gewählte Template spielübergreifend aus.
22. Zufallsauswahl ist mit einer injizierten Quelle vollständig reproduzierbar.
23. Migration, Constraints, Backfill, Optionspersistenz, Cooldown und Konkurrenz sind mit echtem PostgreSQL geprüft.
24. Standardbuild und PostgreSQL-Profil sind grün.
25. Der reale Discord-Smoke-Test umfasst öffentliches Angebot, ephemere Vorschläge, Stilwahl, Neu-Wurf, Auswahl, Verzicht, Ablauf, Neustart, Korrektur und unveränderte Message-ID.
