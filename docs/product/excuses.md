# Kontextabhängige Ausreden

## Zweck und Aktivierung

Ausreden sind eine optionale Ergänzung für ein neu angenommenes Ergebnis. Die Funktion ist standardmäßig deaktiviert. Sie nutzt einen redaktionell gepflegten, beim Start vollständig validierten Katalog; generative KI, externe Textdienste und freie Laufzeit-Templates sind ausgeschlossen.

Für jedes Ergebnis wird genau einmal eine positive oder negative Erstentscheidung persistiert. Bestandsdaten sind `NOT_OFFERED`. Replay, Korrektur, Boardanreicherung und Recovery dürfen kein erstmaliges Angebot erzeugen.

## Angebotsgründe

Ein Angebot kann durch einen der folgenden Kontexte entstehen:

- Ergebnis ist nicht gelöst,
- Einreichung ab 23:30 Uhr lokaler Zeit,
- GridWords erst im sechsten Versuch gelöst,
- GridWords-Lösung dauert mindestens fünf Minuten,
- QuadWords-Lösung dauert mindestens acht Minuten,
- QuadWords besitzt einen einzelnen Board-Zusammenbruch: entweder genau drei Boards gelöst und genau eines ungelöst, oder alle vier gelöst mit eindeutig schlechtestem Board ab Versuch acht und mindestens drei Versuchen Abstand zum zweitschlechtesten,
- Tagesausreißer gegenüber mindestens zwei anderen Ergebnissen desselben Spiels aus der historisch wirksamen Teilnehmermenge.

Die Lösungszeile eines QuadWords-Boards ist die erste einbasierte Zeile aus ausschließlich fünf grünen Zellen. Ein Board ohne solche Zeile ist ungelöst. Direkte Schwellen und Untergrenzen gelten exakt; ein Abstand von zwei Versuchen genügt nicht, und ein Gleichstand beim schlechtesten Board ist kein eindeutiger Ausreißer. Boardlose historische Ergebnisse können keinen boardabhängigen Grund erfüllen.

Der Tagesausreißer wird beim erstmaligen Angebot aus dem bereits committed sichtbaren Stand eingefroren. Er verlangt mindestens zwei andere gültige Ergebnisse desselben Spiels und Spieltags von tatsächlich teilnehmenden Spielern; der aktuelle Nutzer ist nicht Teil der Vergleichsmenge. Spätere Ergebnisse ändern die Entscheidung nicht, und Texte dürfen nur von den „bisher eingegangenen Ergebnissen“ sprechen.

Für GridWords genügt genau eine der folgenden Konstellationen:

- aktuelles Ergebnis `X`, alle anderen gelöst,
- aktuelles und alle verglichenen Ergebnisse gelöst, aktuelles Ergebnis mindestens fünf Versuche und mindestens zwei mehr als das schlechteste andere,
- aktuelle Dauer mindestens vier Minuten und mindestens zwei Minuten länger als die längste andere Dauer.

Für QuadWords genügt:

- aktuelles Ergebnis `X`, alle anderen gelöst, oder
- aktuelle Dauer mindestens sechs Minuten und mindestens drei Minuten länger als die längste andere Dauer.

Andere Spieler werden weder namentlich genannt noch als Gewinner oder Vergleichsziel dargestellt.

Zwischen Angeboten an denselben Spieler für dasselbe Spiel liegen mindestens drei lokale Kalendertage. Die Auswahl verwendet eine injizierte Zufallsquelle und ist dadurch reproduzierbar testbar.

## Katalog

Der produktive Katalog enthält exakt 564 vollständig auflösbare Einträge in acht Stilen. Allgemeine Einträge enthalten 18 Texte je Stil. Jeder spezifische Angebotsgrund enthält mindestens sechs Texte je Stil.

| Bereich | Einträge |
|---|---:|
| Allgemein | 144 |
| Nicht gelöst | 64 |
| Sehr spät eingereicht | 58 |
| GridWords letzter Versuch | 56 |
| GridWords langsam | 56 |
| QuadWords langsam | 56 |
| QuadWords Board-Ausreißer | 72 |
| Tagesausreißer | 58 |
| **Gesamt** | **564** |

Die acht stabilen Stilfamilien sind technisch (`TECHNICAL`), taktisch (`TACTICAL`), bürokratisch (`BUREAUCRATIC`), dramatisch (`DRAMATIC`), kosmisch (`COSMIC`), norddeutsch (`NORTHERN_GERMAN`), sportlich (`SPORTING`) und juristisch (`LEGAL`). Redaktionelle Quellen und Qualitätsregeln liegen unter [`../../content/excuses/`](../../content/excuses/README.md).

## Auswahlablauf

Vor der ephemeren Ausgabe werden drei tatsächlich gezeigte Optionen mit Runde, Position und Kontextgeneration persistiert. Ein einmaliger Stil-Neuwurf erzeugt eine neue Runde. Wiederholungsschutz vermeidet kürzlich verwendete Templates und Texte, soweit der passende Katalog dies zulässt.

Nur der Ergebnisautor darf wählen oder ablehnen. Serverseitig werden Guild, Channel, aktuelle kanonische Message-ID, Ergebnis-ID, Autor, Status, Ablauf, Generation, Runde und Position geprüft. Abgelaufene oder konkurrierende Interaktionen bleiben ohne unzulässige Zustandsänderung.

## Dauerhafter Zustand

Zustände sind `NOT_OFFERED`, `AVAILABLE`, `SELECTED`, `DECLINED`, `EXPIRED` und `INVALIDATED`. Bei Auswahl werden Template, Stil, Thema und gerenderter Text als Snapshot gespeichert. Die Interaktion editiert Discord nicht direkt, sondern persistiert atomar den Zustandswechsel und einen dauerhaften Refresh-Auftrag.

In der kanonischen Nachricht erscheint ausschließlich der gewählte Text, ohne Ausredenlabel, Überschrift oder Stilname. Terminale Zustände lassen keinen veralteten Button zurück.
