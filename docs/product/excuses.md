# Kontextabhängige Ausreden

## Zweck und Aktivierung

Ausreden sind eine optionale Ergänzung für ein neu angenommenes Ergebnis. Die Funktion ist standardmäßig deaktiviert. Sie nutzt einen redaktionell gepflegten, beim Start vollständig validierten Katalog; generative KI, externe Textdienste und freie Laufzeit-Templates sind ausgeschlossen.

Das Feature besitzt keinen frei aufrufbaren Ausreden-Command und erzeugt keine zusätzliche öffentliche Nachricht. Angebot, Vorschläge, Stilwahl, Neu-Wurf, Verzicht und Fehlermeldungen bleiben ephemer für den Ergebnisautor; nur ein tatsächlich gewählter Text kann in dieselbe kanonische Ergebnisnachricht übernommen werden.

Für jedes Ergebnis wird genau einmal eine positive oder negative Erstentscheidung persistiert. Bestandsdaten sind `NOT_OFFERED`. Replay, Korrektur, Boardanreicherung und Recovery dürfen kein erstmaliges Angebot erzeugen. Eine negative Erstentscheidung bleibt negativ, auch wenn eine spätere Korrektur erstmals einen Schwellenwert erfüllen würde.

## Angebotsgründe

Ein Angebot kann durch einen der folgenden Kontexte entstehen:

- Ergebnis ist nicht gelöst,
- Einreichung ab 23:30 Uhr lokaler Zeit,
- GridWords erst im sechsten Versuch gelöst,
- GridWords-Lösung dauert mindestens fünf Minuten,
- QuadWords-Lösung dauert mindestens acht Minuten,
- QuadWords besitzt einen einzelnen Board-Zusammenbruch: entweder genau drei Boards gelöst und genau eines ungelöst, oder alle vier gelöst mit eindeutig schlechtestem Board ab Versuch acht und mindestens drei Versuchen Abstand zum zweitschlechtesten,
- Tagesausreißer gegenüber mindestens zwei anderen Ergebnissen desselben Spiels aus der historisch wirksamen Teilnehmermenge.

Die Lösungszeile eines QuadWords-Boards ist die erste einbasierte Zeile aus ausschließlich fünf grünen Zellen. Ein Board ohne solche Zeile ist ungelöst. Direkte Schwellen und Untergrenzen gelten exakt; ein Abstand von zwei Versuchen genügt nicht, und ein Gleichstand beim schlechtesten Board ist kein eindeutiger Ausreißer. Boardlose historische oder aktuelle Ergebnisse können keinen boardabhängigen Grund erfüllen.

Der Tagesausreißer wird beim erstmaligen Angebot aus dem bereits committed sichtbaren Stand eingefroren. Er verlangt mindestens zwei andere gültige Ergebnisse desselben Spiels und Spieltags von tatsächlich teilnehmenden Spielern; der aktuelle Nutzer ist nicht Teil der Vergleichsmenge. Spätere Ergebnisse ändern die Entscheidung nicht, und Texte dürfen nur von den „bisher eingegangenen Ergebnissen“ sprechen.

Für GridWords genügt genau eine der folgenden Konstellationen:

- aktuelles Ergebnis `X`, alle anderen gelöst,
- aktuelles und alle verglichenen Ergebnisse gelöst, aktuelles Ergebnis mindestens fünf Versuche und mindestens zwei mehr als das schlechteste andere,
- aktuelle Dauer mindestens vier Minuten und mindestens zwei Minuten länger als die längste andere Dauer.

Für QuadWords genügt:

- aktuelles Ergebnis `X`, alle anderen gelöst, oder
- aktuelle Dauer mindestens sechs Minuten und mindestens drei Minuten länger als die längste andere Dauer.

Andere Spieler werden weder namentlich genannt noch als Gewinner oder Vergleichsziel dargestellt.

## Cooldown und Laufzeit

Der Standard-Cooldown beträgt drei lokale Kalendertage **pro Spieler und Spiel** in `Europe/Berlin`: Nach einem GridWords-Angebot am Montag ist das nächste GridWords-Angebot frühestens am Donnerstag zulässig; QuadWords besitzt einen unabhängigen Cooldown. Maßgeblich ist der lokale Kalendertag von `offered_at`. Ein persistiertes Angebot verbraucht den Cooldown unabhängig davon, ob es später ausgewählt, abgelehnt, ignoriert, abgelaufen oder durch eine Korrektur invalidiert wird.

Ein aktives Angebot ist standardmäßig **15 Minuten** verfügbar (`EXCUSE_OFFER_LIFETIME=PT15M`). Korrektur oder Boardanreicherung verlängern `expires_at` nicht. Startup und Scheduler lassen fällige `AVAILABLE`-Zustände idempotent nach `EXPIRED` wechseln und fordern denselben kanonischen Refresh an; die Interaction prüft die Ablaufzeit zusätzlich selbst.

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

Vor der ephemeren Ausgabe werden **genau drei** tatsächlich gezeigte initiale Optionen mit Runde, Position und Kontextgeneration persistiert. Soweit ein passender Katalog dies zulässt, sind die Templates verschieden und stilistisch divers. Der Nutzer kann genau einmal einen Stil wählen und dafür **genau drei bislang nicht gezeigte** Texte als `STYLE_REROLL` erhalten; ein zweiter Neu-Wurf ist unzulässig.

Wiederholungsschutz vermeidet kürzlich verwendete Templates und Texte, soweit der passende Katalog dies zulässt. Tatsächlich persistierte Optionen bleiben nach erneutem Öffnen oder Botneustart dieselben; eine Auswahl ist nur aus der aktuellen persistierten Generation, Runde und Position zulässig.

Nur der Ergebnisautor darf öffnen, wählen, neu würfeln oder verzichten. Serverseitig werden Guild, Channel, aktuelle kanonische Message-ID, Ergebnis-ID, Autor, Status, Ablauf, Generation, Runde und Position geprüft. Abgelaufene, manipulierte oder konkurrierende Interaktionen bleiben ohne unzulässige Zustandsänderung.

## Dauerhafter Zustand

Zustände sind `NOT_OFFERED`, `AVAILABLE`, `SELECTED`, `DECLINED`, `EXPIRED` und `INVALIDATED`. Bei Auswahl werden Template, Stil, Thema und gerenderter Text als Snapshot gespeichert. Die Interaktion editiert Discord nicht direkt, sondern persistiert atomar den Zustandswechsel und einen dauerhaften Refresh-Auftrag.

Ein gewählter Text wird bei einer Korrektur nur beibehalten, wenn sein Template weiterhin fachlich anwendbar ist und ein erneutes Rendering exakt denselben Text ergibt. Andernfalls wird die Auswahl invalidiert und öffentlich entfernt; sie wird niemals still umformuliert oder automatisch ersetzt. Eine bereits gewählte Ausrede kann nicht nachträglich gegen eine andere ausgetauscht werden.

In der kanonischen Nachricht erscheint ausschließlich der gewählte Text, ohne Ausredenlabel, Überschrift oder Stilname. `NOT_OFFERED`, `DECLINED`, `EXPIRED` und `INVALIDATED` zeigen weder Button noch Text. Terminale Zustände lassen keinen veralteten Button zurück; ein bereits pensioniertes Ergebnis wird durch einen Ausreden-Refresh nicht neu veröffentlicht.
