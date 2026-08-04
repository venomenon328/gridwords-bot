# Verbindliches Rekordmodell

**Status:** fachlich abgenommen  
**Stand:** 4. August 2026  
**Gültig ab:** Inkrement 12  
**Verbindliches Issue:** #58  
**Definitionsversion:** `records-v1`

Dieses Dokument definiert die fachliche Wahrheit für persönliche, serverweite individuelle und gemeinsame Rekorde. Es ergänzt insbesondere:

- [`series-model.md`](series-model.md),
- [`game-specific-participation.md`](game-specific-participation.md),
- [`daily-status-reminders.md`](daily-status-reminders.md),
- [`periodic-reports.md`](periodic-reports.md),
- ADR 0018.

Bei widersprüchlichen älteren Formulierungen zu Rekorden, Serienendmeldungen oder der Zulässigkeit neuer Vortagsergebnisse nach dem Tagesabschluss gilt dieses Dokument.

---

## 1. Ziel und Grundsätze

Rekordmeldungen sollen selten genug bleiben, um als besondere Ereignisse wahrgenommen zu werden. Ein Rekord ist kein Tagesrang und keine vorübergehende Führung, sondern ein historischer Extremwert innerhalb eines eindeutig definierten Vergleichsraums.

Jede Rekorddefinition besitzt mindestens:

1. einen stabilen Definitionsschlüssel und eine fachliche Definitionsversion,
2. eine Metrik mit totaler oder ausdrücklich tie-breakbarer Ordnung,
3. einen Vergleichsraum,
4. eine Regel für geeignete Quellen,
5. eine Mindesthistorie für öffentliche Meldungen,
6. einen aktuellen gültigen Rekordstand,
7. ein auslösendes Ergebnis oder einen abgeleiteten Serienlauf.

Der Bot erhält die vollständige relevante Historie des Servers. `/records` darf die Werte daher ohne einschränkenden Hinweis wie „seit Beginn der Aufzeichnung“ als Allzeitrekorde bezeichnen.

Rekordzustand, historisches Rekordereignis und sichtbare Discord-Meldung sind drei getrennte Ebenen:

- **Rekordzustand:** der aktuell gültige Extremwert,
- **Rekordereignis:** eine auditierbare fachliche Veränderung oder Abschlussklassifikation,
- **Rekordmeldung:** eine korrigierbare Discord-Projektion eines oder mehrerer Ereignisse.

Eine Discord-Meldung ist niemals Quelle fachlicher Wahrheit.

---

## 2. Vergleichsräume

In Inkrement 12 existieren folgende Vergleichsräume:

### 2.1 Persönlich

Der Rekord gilt für genau einen Spieler. Ergebnisrekorde sind zusätzlich nach Spiel getrennt. Serienrekorde sind nach eindeutigem Serientyp getrennt.

### 2.2 Serverweit individuell

Der Rekord vergleicht geeignete Ergebnisse oder persönliche Serienläufe aller Spieler des konfigurierten Servers. Der Rekord besitzt einen einzelnen kanonischen Halter beziehungsweise Quelllauf.

### 2.3 Gemeinsam

Der Rekord vergleicht die bestehenden gemeinsamen Serienläufe des Servers. Er gehört der Gruppe und besitzt keinen einzelnen Spieler als Rekordhalter.

Es gibt keine Rekorde über mehrere Discord-Server hinweg.

---

## 3. Kanonische Quellen und historische Gültigkeit

Rekordfähig sind ausschließlich aktuell gültige kanonische `game_result`-Datensätze und daraus unter Verwendung der historischen Teilnahmezeiträume deterministisch abgeleitete Serienläufe.

Nicht rekordfähig sind insbesondere:

- Parserablehnungen,
- unvollständige oder technisch vorläufige Ergebnisse,
- unveränderte Replays,
- Import- oder Backfill-Verarbeitung als öffentliches Ereignis,
- supersedete oder invalidierte Ergebnisversionen,
- teilweise QuadWords-Boards,
- Discord-Nachrichten als solche.

Boardlose, aber fachlich vollständige QuadWords-Ergebnisse sind für die in diesem Dokument definierten Ergebnis- und Serienrekorde geeignet. Einzelne QuadWords-Boardrekorde sind nicht Bestandteil von Inkrement 12.

Ein historischer Rekord bleibt gültig, wenn sein Halter:

- die Teilnahme an einem Spiel beendet,
- aktuell an keinem Spiel teilnimmt,
- den Server verlässt.

Eine Teilnahmeänderung verändert frühere geeignete Ergebnisse und abgeschlossene Serien nicht rückwirkend. Eine ausdrückliche Datenschutzlöschung ist davon getrennt: Der Wert bleibt erhalten, soweit dies rechtlich und fachlich zulässig ist, die Identität wird jedoch anonymisiert.

---

## 4. Ergebnisrekorde

Für GridWords und QuadWords werden jeweils drei Metriken geführt. Jede Metrik existiert persönlich und serverweit individuell.

| Metrik | Geeignete Ergebnisse | Vergleich |
|---|---|---|
| Wenigste Versuche | nur erfolgreich gelöst | Versuchszahl aufsteigend, Dauer aufsteigend als Tie-Breaker |
| Schnellste Lösung | nur erfolgreich gelöst | Dauer aufsteigend |
| Langsamste erfolgreiche Lösung | nur erfolgreich gelöst | Dauer absteigend |

Ein nicht gelöstes Ergebnis mit `X/6` beziehungsweise `X/9` ist für keine dieser drei Metriken geeignet.

### 4.1 Wenigste Versuche

Der kanonische Vergleichswert ist das Tupel:

```text
(Versuche aufsteigend, Dauer aufsteigend)
```

Beispiele:

```text
3/6 in 8:00 ist besser als 4/6 in 0:40.
3/6 in 1:20 ist besser als 3/6 in 1:35.
3/6 in 1:20 ist gleichwertig mit 3/6 in 1:20.
```

Wird bei unverändert minimaler Versuchszahl nur die zugehörige Dauer verbessert, ist dies ein echtes striktes Übertreffen derselben Rekorddefinition.

### 4.2 Schnellste Lösung

Es zählt ausschließlich die Lösungsdauer. Die Versuchszahl ist weder Teil des Vergleichswerts noch Tie-Breaker.

```text
5/6 in 0:40 ist schneller als 2/6 in 0:45.
```

Bei identischer Dauer liegt ein Gleichstand vor. Er erzeugt keine Meldung und ersetzt nicht die kanonische erste Quelle.

### 4.3 Langsamste erfolgreiche Lösung

Es zählt ausschließlich die Lösungsdauer in umgekehrter Richtung. Die Versuchszahl ist sichtbarer Kontext, aber weder Vergleichskriterium noch Tie-Breaker.

```text
3/6 in 20:00 ist ein stärkerer Negativrekord als 6/6 in 18:00.
```

Die Metrik ist bewusst humoristisch, setzt aber weiterhin eine erfolgreiche Lösung voraus.

### 4.4 Gleichstände und kanonische Quelle

Vollständig gleichwertige Ergebniswerte werden nicht öffentlich gemeldet. Der zuerst erreichte gültige Wert bleibt die kanonische Quelle des Rekordstands.

Muss diese Quelle später invalidiert werden, wird der gültige Rekord deterministisch neu bestimmt. Bei mehreren gleichwertigen Quellen gilt in dieser Reihenfolge:

1. früherer fachlicher Spieltag,
2. früherer Zeitpunkt der erstmaligen gültigen Annahme,
3. stabile Ergebnis-ID.

### 4.5 Mindesthistorie

Der Rekordstand wird ab dem ersten geeigneten Ergebnis geführt. Eine öffentliche Meldung setzt jedoch eine Vergleichsbasis **vor** dem Kandidaten voraus:

| Vergleichsraum | Mindestbasis |
|---|---|
| persönlich | mindestens 5 frühere gelöste Ergebnisse desselben Spielers und Spiels |
| serverweit individuell | mindestens 10 frühere gelöste Ergebnisse desselben Spiels |
| zusätzliche Serverbedingung | die früheren Ergebnisse stammen von mindestens 2 unterschiedlichen Spielern |

Der Kandidat selbst zählt nicht zur Mindestbasis. Die Basis gilt pro Spiel, aber gemeinsam für die drei Ergebnisdefinitionen, da alle ausschließlich gelöste Ergebnisse verwenden.

---

## 5. Positive Serienrekorde

Serien werden weiterhin ausschließlich aus Ergebnissen und historisch wirksamen Teilnahmezeiträumen abgeleitet. Das Rekordmodul führt keine zweite Serie als mutierten Zähler.

### 5.1 Persönliche Serien

Für jeden Spieler werden Rekorde dieser fünf bestehenden Serienarten geführt:

1. Aktivitätsserie,
2. Komplettserie,
3. GridWords-Lösungsserie,
4. QuadWords-Lösungsserie,
5. Perfektserie.

Für jede Art existieren:

- der persönliche Rekord des Spielers,
- der serverweite individuelle Rekord über alle Spieler.

### 5.2 Gemeinsame Serien

Für diese vier bestehenden gemeinsamen Serienarten wird jeweils ein gemeinsamer Serverrekord geführt:

1. gemeinsame GridWords-Lösungsserie,
2. gemeinsame QuadWords-Lösungsserie,
3. gemeinsame Komplettserie,
4. gemeinsame Perfektserie.

Die Teilnehmermenge darf sich innerhalb eines gemeinsamen Serienlaufs gemäß den historisch wirksamen Teilnahmezeiträumen ändern. Ein gemeinsamer Tag setzt weiterhin mindestens zwei aktive Spieler in der für die Serienart maßgeblichen Teilnehmermenge voraus.

Es gibt weiterhin keine gemeinsame Aktivitätsserie.

---

## 6. Negative Serienrekorde

Inkrement 12 ergänzt drei ausschließlich für das Rekordmodul abgeleitete negative Serienarten. Sie werden nicht automatisch Bestandteil von Tagesstatus, kanonischen Ergebnisnachrichten oder periodischen Berichten.

### 6.1 GridWords-Durststrecke ohne Lösung

Eine GridWords-Durststrecke besteht aus aufeinanderfolgenden Kalendertagen, an denen der Spieler:

- laut historischer Teilnahme an GridWords teilnimmt und
- ein gültiges, aber nicht gelöstes GridWords-Ergebnis `X/6` einreicht.

Sie beginnt mit einem `X/6`, verlängert sich mit einem `X/6` am unmittelbar folgenden geeigneten Tag und endet durch:

- eine erfolgreiche GridWords-Lösung,
- ein beim Tagesabschluss fehlendes GridWords-Ergebnis,
- das Ende der GridWords-Teilnahme.

Ein fehlendes Ergebnis verlängert die Durststrecke nicht.

### 6.2 QuadWords-Durststrecke ohne Lösung

Die Definition entspricht 6.1 mit QuadWords und `X/9`.

### 6.3 Serie ohne perfekten Tag

Diese Serie betrachtet ausschließlich Kalendertage, an denen der Spieler laut historischer Teilnahme an **beiden** Spielen teilnimmt und daher grundsätzlich einen perfekten Tag erreichen kann.

Ein solcher Tag verlängert die Serie, wenn er nicht perfekt endet, also insbesondere wenn:

- mindestens ein eingereichtes Spiel nicht gelöst ist oder
- beim Tagesabschluss mindestens ein erforderliches Ergebnis fehlt.

Ein perfekter Tag beendet die Serie sofort, sobald beide erfolgreichen Ergebnisse vorliegen.

Ein Tag mit Teilnahme an nur einem Spiel gehört nicht zum Vergleichsraum. Der Übergang aus der Zwei-Spiele-Teilnahme in eine Ein-Spiel- oder Nichtteilnahme beendet den bisherigen Lauf; die Serie pausiert nicht unsichtbar.

### 6.4 Vergleichsräume negativer Serien

Für jede der drei negativen Serienarten existieren:

- ein persönlicher Rekord je Spieler,
- ein serverweiter individueller Rekord über alle Spieler.

Es gibt in Inkrement 12 keine gemeinsame negative Serie.

---

## 7. Serienlauf und Identität

Ein Serienlauf wird deterministisch identifiziert durch:

```text
Guild-ID
Serienart
Vergleichsraum
gegebenenfalls Spieler-ID
Startdatum
```

Zusätzlich werden abgeleitet:

- aktuelles beziehungsweise endgültiges Enddatum,
- aktuelle beziehungsweise endgültige Länge,
- laufend oder abgeschlossen,
- bei gemeinsamen Serien die täglich wirksamen Teilnehmermengen.

Ändert eine Korrektur das Startdatum oder verbindet beziehungsweise trennt sie Läufe, entstehen fachlich neue Laufidentitäten. Ereignisse und Meldungsprojektionen der alten Identität werden invalidiert und reconciled.

---

## 8. Mindestbasis und öffentliche Schwellen für Serien

Der Rekordstand wird unabhängig von Meldungsschwellen korrekt geführt.

### 8.1 Persönliche Rekorde

Eine persönliche Rekordmeldung benötigt mindestens einen früheren abgeschlossenen vergleichbaren Lauf. Der erste beobachtete Lauf initialisiert den Rekordstand still.

### 8.2 Serverweite individuelle Rekorde

Vor dem Kandidaten müssen vergleichbare historische Läufe von mindestens zwei unterschiedlichen Spielern existieren. Der Kandidatenlauf selbst zählt nicht zu dieser Nutzerbasis.

### 8.3 Gemeinsame Rekorde

Eine gemeinsame Rekordmeldung benötigt mindestens einen früheren abgeschlossenen gemeinsamen Lauf derselben Art. Die Mehrpersonenbedingung folgt bereits aus dem gemeinsamen Serienmodell.

### 8.4 Längenschwellen

Für öffentliche Meldungen gelten:

| Kategorie | Schwelle |
|---|---:|
| positive persönliche, serverweite individuelle und gemeinsame Serie | Referenzrekord beziehungsweise neuer Rekord mindestens 7 Tage |
| Serie ohne perfekten Tag | Referenzrekord beziehungsweise neuer Rekord mindestens 7 Tage |
| GridWords-/QuadWords-Durststrecke | neuer Rekord oder Gleichstand mindestens 3 Tage |

Eine Near-Miss-Meldung darf bei positiven Serien beziehungsweise der Serie ohne perfekten Tag unter sieben Tagen enden, sofern der Referenzrekord mindestens sieben Tage lang ist und die Near-Miss-Regel erfüllt ist.

Bei Durststrecken muss auch der beendete Near-Miss-Lauf mindestens drei Tage lang sein. Dadurch erzeugt eine gewöhnliche Zwei-Tage-Phase bei einem Drei-Tage-Rekord keine Meldung.

---

## 9. Meldungsphasen eines Serienlaufs

### 9.1 Erstmaliges Übertreffen während des Laufs

Sobald ein laufender Serienlauf einen meldungsfähigen bisherigen Rekord strikt übertrifft, entsteht genau einmal pro betroffener Rekorddefinition eine unmittelbare Überschreitungsmeldung.

Weitere Verlängerungen desselben Laufs bleiben für diese Definition still. Derselbe Lauf darf später eine andere Definition übertreffen, beispielsweise zuerst den persönlichen und danach den serverweiten individuellen Rekord.

Erreicht eine Submission gleichzeitig mehrere Schwellen, werden die Fakten in einer Meldung aggregiert.

### 9.2 Gleichstand während des Laufs

Das bloße Erreichen eines bestehenden Serienrekords während eines noch laufenden Laufs bleibt still. Es ersetzt nicht die kanonische frühere Quelle.

### 9.3 Abschlussmeldung

Beim endgültigen Ende wird ein Lauf noch einmal zusammenfassend gemeldet, wenn mindestens eine der folgenden Bedingungen für einen geeigneten Vergleichsraum erfüllt ist:

1. Der Lauf hat den bisherigen Rekord strikt übertroffen.
2. Der Lauf stellt den gültigen Referenzrekord exakt ein.
3. Der Lauf verpasst den gültigen Referenzrekord nur knapp.

Die Abschlussmeldung ist auch dann erwünscht, wenn während des Laufs bereits eine Überschreitungsmeldung erschien. Die erste Meldung würdigt den Moment der Rekordübernahme; die zweite würdigt die endgültige Gesamtlänge.

### 9.4 Mehrere Vergleichsräume

Die Abschlussklassifikation erfolgt getrennt für persönlichen, serverweiten individuellen und gegebenenfalls gemeinsamen Vergleichsraum. Ein Lauf kann beispielsweise:

- einen persönlichen Rekord verbessern,
- gleichzeitig den Serverrekord knapp verfehlen,
- und beide Tatsachen in einer Nachricht darstellen.

---

## 10. Gleichstand und „knapp verpasst“ bei Serien

### 10.1 Gleichstand

Ein abgeschlossener Serienlauf mit exakt derselben Länge wie der gültige Referenzrekord erzeugt eine Meldung „Rekord eingestellt“, sofern die Schwellen aus Abschnitt 8 erfüllt sind.

Der kanonische erste Rekordhalter beziehungsweise erste Quelllauf bleibt im Rekordstand erhalten. Der Gleichstand ist ein historisches Ereignis, aber kein Halterwechsel.

### 10.2 Near-Miss-Fenster

Das relative Near-Miss-Fenster ist:

```text
max(1, ceil(Referenzrekord × 0,10))
```

Ein Lauf hat knapp verpasst, wenn:

```text
1 <= Referenzrekord - endgültige Länge <= Near-Miss-Fenster
```

Es gibt keine absolute Obergrenze von drei Tagen.

Beispiele:

| Referenzrekord | Fenster | Near Miss ab |
|---:|---:|---:|
| 7 | 1 | 6 |
| 10 | 1 | 9 |
| 15 | 2 | 13 |
| 30 | 3 | 27 |
| 50 | 5 | 45 |
| 80 | 8 | 72 |
| 120 | 12 | 108 |

### 10.3 Referenzrekord

Für die Abschlussklassifikation wird der beste aktuell gültige vergleichbare Lauf **unter Ausschluss des gerade endenden Laufs** verwendet.

Dadurch vergleicht sich eine bereits rekordführende Serie nicht mit ihrem eigenen Endwert. Bei serverweiten individuellen Rekorden darf die Referenz während des laufenden Kandidaten durch einen anderen Spieler verbessert worden sein; die Abschlussmeldung bildet dann den zum Abschluss gültigen Vergleich ab.

---

## 11. Zeitpunkt des Serienendes

### 11.1 Sofortiges Ende durch ein eindeutiges Ergebnis

Ein eingereichtes Ergebnis beendet betroffene Serien sofort, wenn die Tagesbedingung dadurch endgültig nicht mehr erreichbar beziehungsweise erfüllt ist.

Ein `X` beendet unmittelbar:

- die persönliche Lösungsserie des betreffenden Spiels,
- gegebenenfalls die persönliche Perfektserie,
- die entsprechende gemeinsame Lösungsserie,
- gegebenenfalls die gemeinsame Perfektserie.

Ein `X` beendet nicht:

- die Aktivitätsserie,
- die Komplettserie,
- die Lösungsserie des anderen Spiels,
- die gemeinsame Komplettserie.

Für negative Serien gilt umgekehrt:

- Ein `X` startet oder verlängert die betreffende Durststrecke.
- Ein nicht perfektionierbarer Tag startet oder verlängert die Serie ohne perfekten Tag.
- Eine erfolgreiche Lösung beendet die betreffende Durststrecke sofort.
- Ein perfekter Tag beendet die Serie ohne perfekten Tag sofort.

Eine dadurch erforderliche Abschlussmeldung wird unmittelbar nach erfolgreicher fachlicher Verarbeitung eingeplant.

### 11.2 Ende durch fehlende Einreichung

Fehlt eine notwendige Einreichung, wird die betroffene Serie beim logischen Tagesabschluss um **06:00 Uhr `Europe/Berlin`** endgültig beendet.

Der Abschlussjob darf technisch später anlaufen oder nach einem Neustart nachgeholt werden. Fachlicher Stichtag und Einreichungsgrenze bleiben dennoch 06:00 Uhr.

### 11.3 Teilnahmeänderung

Endet die für eine Serie erforderliche Teilnahme, endet ein laufender Serienlauf an der letzten noch geeigneten Tagesgrenze. Eine wirksame Teilnahmeänderung löst die notwendige Neuberechnung und Reconciliation aus.

---

## 12. Zulässigkeit neuer Vortagsergebnisse

Die ältere pauschale Regel „heutiger oder unmittelbar vorheriger Spieltag“ wird präzisiert:

```text
00:00 bis 05:59:59 Europe/Berlin
- Ergebnisse für den aktuellen oder den unmittelbar vorherigen Spieltag sind zulässig.

ab 06:00 Europe/Berlin
- neue Ergebnisse sind nur für den aktuellen Spieltag zulässig.
```

Der Cutoff folgt der fachlichen lokalen Uhrzeit und nicht dem tatsächlichen Startzeitpunkt des Scheduler-Jobs.

Korrekturen eines bereits vorhandenen kanonischen Ergebnisses bleiben davon getrennt und dürfen auch nach dem Tagesabschluss den fachlichen Zustand berichtigen. Sie lösen dann Rekord- und Meldungs-Reconciliation aus.

---

## 13. Historische Initialisierung und Aktivierung

Vor der ersten öffentlichen Rekordmeldung wird die vollständige bestehende Historie ausgewertet.

Der Bootstrap:

- erzeugt für jede Rekorddefinition und jeden vorhandenen Vergleichsraum den korrekten aktuellen Rekordstand,
- verweist auf die kanonische Ergebnis- oder Serienquelle,
- erzeugt höchstens einen stillen `INITIALIZED`-Auditanker je Zustand,
- rekonstruiert nicht öffentlich jede historische Rekordübernahme,
- erzeugt keine Discord-Meldung,
- löst keine späteren Achievements aus.

Bereits bei Aktivierung laufende Rekordserien gelten für die während der Vergangenheit überschrittenen Schwellen als konsumiert. Die nächste bloße Verlängerung darf daher keine nachträgliche Überschreitungsmeldung erzeugen. Endet ein solcher Lauf nach Aktivierung, ist seine Abschlussmeldung nach den normalen Regeln zulässig.

Importe, Backfills, unveränderte Replays und technische Wiederverarbeitung aktualisieren erforderlichenfalls den Rekordstand, bleiben aber öffentlich still.

---

## 14. Korrekturen und Reconciliation

Rekordmeldungen sind synchronisierte Discord-Projektionen des derzeit gültigen fachlichen Zustands.

Nach einer Ergebniskorrektur oder Teilnahmeberichtigung werden alle betroffenen Definitionen und Serienläufe neu abgeleitet. Anschließend gilt:

| Gewünschter Zustand | Discord-Aktion |
|---|---|
| Meldung bleibt vollständig korrekt und inhaltlich gleich | `NO_OP` |
| Meldung bleibt erforderlich, Inhalt ändert sich | bestehende Nachricht editieren |
| nur ein Teil aggregierter Fakten bleibt gültig | bestehende Nachricht auf gültige Fakten reduzieren |
| keine enthaltene Tatsache bleibt gültig | bestehende Nachricht löschen |
| durch eine normale Live-Korrektur entsteht erstmals ein meldungsfähiger Rekord | neue Nachricht veröffentlichen |

Es wird keine zusätzliche öffentliche „Rekord aberkannt“-Nachricht gesendet.

Historische Rekordereignisse bleiben auditierbar und werden bei fachlicher Entwertung als invalidiert beziehungsweise supersedet markiert.

### 14.1 Abgrenzung normaler Korrekturen

Eine normale Korrektur im kanonischen Ergebnislebenszyklus darf neue Meldungen erzeugen. Administrative historische Reparaturen, Importe und Backfills berichtigen dagegen nur den Zustand und bleiben öffentlich still.

### 14.2 Beispiele

- Wird eine schnellste Lösung zu `X` korrigiert, werden der Rekord neu bestimmt und die zugehörige Meldung gelöscht oder reduziert.
- Wird ein serienbeendendes `X` zu einer Lösung korrigiert, wird die Abschlussmeldung gelöscht und der wiederhergestellte Lauf neu ausgewertet.
- Bleibt ein Ergebnis Rekord, verbessert aber seinen Wert, wird die vorhandene Meldung editiert.
- Verbindet eine Korrektur zwei Serienläufe, werden alte Laufereignisse invalidiert und Projektionen gegen den neuen Lauf reconciled.

---

## 15. Meldungsaggregation

Mehrere Rekordfakten desselben fachlichen Auslösers dürfen nicht als Nachrichtensalve erscheinen.

Eine logische Meldung wird mindestens gruppiert nach:

```text
Guild und Channel
fachlicher Auslöser
betroffenes Subjekt oder gemeinsamer Vergleichsraum
Meldungsphase
```

Meldungsphasen sind mindestens:

- Ergebnis- beziehungsweise Live-Auswertung,
- erstmalige Serienüberschreitung,
- Serienabschluss.

Ein einzelnes Ergebnis kann beispielsweise gleichzeitig persönliche und serverweite Rekorde für wenigste Versuche und schnellste Lösung sowie eine Serienrekordschwelle auslösen. Soweit die Darstellung verständlich und innerhalb der Discord-Grenzen bleibt, werden diese Fakten in einer Meldung zusammengefasst.

Der Renderer darf eine logisch zusammengehörige Ausgabe deterministisch auf mehrere Discord-Nachrichten oder Embeds verteilen, wenn Discord-Grenzen dies erfordern. Die Projektion bleibt dennoch eine logische Delivery.

---

## 16. Discord-Darstellung

Rekordmeldungen enthalten mindestens:

- eindeutige Rekordart,
- Spiel oder Serienart,
- Vergleichsraum,
- neuen beziehungsweise endgültigen Wert,
- vorherigen oder verglichenen Wert,
- bei serverweiten individuellen Rekorden den bisherigen beziehungsweise neuen Halter,
- bei Serien den Laufstatus oder das Enddatum,
- bei Near Misses den Abstand zum Referenzrekord.

Sachliche Daten und humoristische Formulierung werden getrennt erzeugt. Der Renderer darf einen kurzen lockeren Zusatz verwenden, darf aber keine fachlichen Werte erfinden oder relativieren.

Verbindlich sind außerdem:

- keine echten oder maskierten User-Mentions,
- keine Allowed Mentions,
- stabile Anzeige bei ausgeschiedenen Spielern,
- neutrale anonymisierte Anzeige nach Datenschutzlöschung,
- kein Runtime-Einsatz generativer KI.

Beispiel Ergebnis:

```text
🏆 Neuer GridWords-Rekord
Georgia löst GridWords in 2 Versuchen nach 1:14.
Damit verbessert sie den persönlichen und den Serverrekord für die wenigsten Versuche.
```

Beispiel Abschluss:

```text
🏁 Rekordserie beendet
Tobias' GridWords-Lösungsserie endet nach 32 Tagen.
Neuer persönlicher Rekord; der Serverrekord von 33 Tagen wurde um einen Tag verpasst.
```

---

## 17. `/records`

Der lesende Slash-Command gehört zum MVP.

Unterstützte Varianten:

```text
/records
/records game:gridwords
/records game:quadwords
/records user:@Georgia
/records category:results
/records category:series
```

Optionen dürfen kombiniert werden, soweit fachlich sinnvoll.

### 17.1 Standardverhalten

Ohne `user` zeigt der persönliche Abschnitt die Rekorde des aufrufenden Spielers. Zusätzlich werden die serverweiten individuellen und gemeinsamen Rekorde angezeigt.

Die Antwort ist ephemer, damit reine Nachschlagevorgänge den Ergebniskanal nicht füllen.

### 17.2 Ergebnisabschnitt

Pro ausgewähltem Spiel werden angezeigt:

- persönliche wenigste Versuche,
- persönliche schnellste Lösung,
- persönliche langsamste erfolgreiche Lösung,
- jeweilige serverweite Rekorde,
- kanonischer Halter und fachlicher Spieltag.

### 17.3 Serienabschnitt

Angezeigt werden je nach Filter:

- persönliche positive Serienrekorde,
- serverweite individuelle positive Serienrekorde,
- gemeinsame Serienrekorde,
- persönliche und serverweite negative Serienrekorde,
- Start- und Enddatum abgeschlossener Läufe,
- bei laufenden Rekordläufen ein eindeutiger Hinweis „läuft“.

Der `game`-Filter umfasst nur eindeutig spielbezogene Serien:

- GridWords- beziehungsweise QuadWords-Lösungsserie,
- entsprechende gemeinsame Lösungsserie,
- entsprechende Durststrecke.

Aktivität, komplett, perfekt und die Serie ohne perfekten Tag werden keinem einzelnen Spiel künstlich zugeordnet und bei gesetztem `game`-Filter nicht angezeigt.

### 17.4 Leere Zustände und Gleichstände

Fehlt eine geeignete Historie, zeigt der Command einen neutralen Platzhalter statt einer künstlichen Null.

Gleichwertige Quellen werden nicht als Liste mehrerer Halter geführt. Die Darstellung verwendet bei Bedarf „zuerst erreicht von …“.

---

## 18. Fachliche Ereignisse

Der Rekordkern unterscheidet mindestens:

```text
RECORD_INITIALIZED
RESULT_RECORD_BROKEN
SERIES_RECORD_CROSSED
SERIES_RECORD_TIED_AT_END
SERIES_RECORD_NEAR_MISSED_AT_END
RECORD_SERIES_FINISHED
RECORD_EVENT_INVALIDATED
```

Diese Ereignisse sind transportneutral. Sie senden keine Discord-Nachrichten und kennen keine JDA-Typen.

Ein späterer Achievement-Evaluator darf auf gültige Rekordereignisse und deren Invalidierungen reagieren. Er darf nicht auf gerenderte Discord-Texte oder Delivery-Zustände angewiesen sein.

Inkrement 12 implementiert keine allgemeine Ereignis-DSL, keinen externen Message Broker und kein generisches Plugin-System.

---

## 19. Persistenz und Nebenläufigkeit

Die verbindliche Architekturentscheidung steht in ADR 0018. Fachlich erforderlich sind mindestens getrennte persistente Modelle für:

1. aktuellen Rekordstand,
2. historische Rekordereignisse,
3. logische Discord-Meldungsprojektionen und Delivery-Zustände,
4. Bootstrap- beziehungsweise Definitionsversionsstand.

Zwei konkurrierende Ergebnisse dürfen nicht beide denselben unveränderten Ausgangswert übernehmen. Rekordzustände werden pro Definition und Vergleichsraum atomar beziehungsweise transaktional geschützt.

Wenn zwei fast gleichzeitige Ergebnisse nacheinander tatsächlich streng bessere Werte setzen, sind beide fachlich mögliche Rekordereignisse. Ist die erste Meldung beim zweiten Ereignis noch nicht ausgeliefert, darf die Projektion auf den nun gültigen Stand reduziert werden. Ist sie bereits erfolgreich sichtbar, bleibt sie als zu diesem Zeitpunkt wahre historische Meldung bestehen.

Jede logische Meldung besitzt einen stabilen Idempotenzschlüssel und Inhalts-Fingerprint. Discord-I/O findet nicht innerhalb einer Datenbanktransaktion statt.

Nach bestätigter erfolgreicher Veröffentlichung wird eine später manuell extern gelöschte Rekordmeldung nicht unbegrenzt neu erzeugt. Der Delivery-Zustand wird als extern entfernt abgeschlossen; `/records` bleibt die aktuelle überprüfbare Wahrheit. Unklare Send-Ergebnisse, Prozessabstürze und retryfähige Fehler vor einem bestätigten Abschluss werden dagegen reconciled.

---

## 20. Konfiguration und Rollout

Der aktuelle Rekordstand wird unabhängig von öffentlichen Meldungen gepflegt.

Eine externe globale Konfiguration steuert mindestens, ob neue Rekordmeldungen öffentlich ausgeliefert werden. Die sichere Rollout-Reihenfolge ist:

1. Schema und Anwendung deployen,
2. historischen Bootstrap vollständig und idempotent abschließen,
3. `/records` und Zustände prüfen,
4. öffentliche Meldungen bewusst aktivieren.

Ein deaktivierter Meldungsmodus verhindert neue Discord-Deliveries, verwirft aber weder Rekordzustand noch Auditereignisse. Beim späteren Aktivieren werden während der Deaktivierung entstandene alte Ereignisse nicht nachträglich in den Channel gespült.

---

## 21. Ausdrückliche Nichtziele

Nicht Bestandteil von Inkrement 12 sind:

- Rekord für die meisten Versuche,
- langsamstes ungelöstes Spiel,
- Rekorde einzelner QuadWords-Boards,
- tägliche Führungen oder Ranglisten,
- öffentliche Ergebnisgleichstände,
- rückwirkende Feiern historischer Bootstrap-, Import- oder Backfill-Ereignisse,
- vollständige Rekordchronik im `/records`-Command,
- konfigurierbare Rekordregeln per Discord,
- Achievement-Katalog oder Achievement-UI,
- universelle Regelmaschine, Event-Bus oder Messaging-Plattform.

---

## 22. Verbindliche Akzeptanzfälle

### 22.1 Ergebnisrekorde

1. Die erste geeignete Submission initialisiert den persönlichen Zustand still.
2. Das sechste gelöste Ergebnis kann bei strikter Verbesserung erstmals eine persönliche Meldung auslösen.
3. Ein Serverrekord benötigt zehn frühere gelöste Ergebnisse von mindestens zwei Spielern.
4. Weniger Versuche schlagen immer mehr Versuche; bei gleicher Versuchszahl entscheidet die kürzere Dauer.
5. Für schnellste und langsamste erfolgreiche Lösung zählt ausschließlich die Dauer.
6. Ein `X` ist für keinen Ergebnisrekord geeignet.
7. Ein vollständig identischer Wert erzeugt keine Meldung und ersetzt nicht die erste Quelle.
8. Eine Submission mit mehreren Rekorden erzeugt eine aggregierte Meldung.
9. Eine Korrektur entfernt einzelne ungültige Fakten per Edit.
10. Eine Korrektur entfernt die gesamte Meldung per Delete, wenn kein Fakt gültig bleibt.
11. Bootstrap, Import, Backfill und Replay erzeugen keine öffentliche Meldung.

### 22.2 Positive Serien

12. Ein laufender Lauf meldet das erstmalige strikte Übertreffen genau einmal je Rekorddefinition.
13. Weitere Verlängerungen derselben Definition bleiben still.
14. Derselbe Lauf darf später zusätzlich den serverweiten Rekord übertreffen und dies einmal melden.
15. Ein Gleichstand während des laufenden Laufs bleibt still.
16. Ein abgeschlossener Gleichstand erzeugt „Rekord eingestellt“.
17. Ein Rekordlauf erzeugt beim Ende eine Abschlussmeldung, auch wenn seine Überschreitung bereits gemeldet wurde.
18. Ein 80-Tage-Rekord gilt bei einem Ende nach 72 Tagen als knapp verpasst.
19. Der gerade endende Lauf wird aus seinem Referenzvergleich ausgeschlossen.
20. Ein explizites `X` beendet betroffene Lösungs- und Perfektserien sofort.
21. Ein fehlendes Ergebnis beendet die betroffene Serie logisch um 06:00 Uhr.
22. Ein vor 06:00 Uhr eingereichtes gültiges Vortagsergebnis kann die Serie noch erhalten.
23. Ein neues Vortagsergebnis ab 06:00 Uhr wird abgelehnt.

### 22.3 Negative Serien

24. Drei aufeinanderfolgende `X/6` können einen GridWords-Durststreckenrekord melden.
25. Ein fehlendes Ergebnis trennt zwei Durststrecken und verlängert keine von ihnen.
26. Eine erfolgreiche Lösung beendet die betreffende Durststrecke sofort.
27. Ein nicht perfekter Zwei-Spiele-Teilnahmetag verlängert die Serie ohne perfekten Tag.
28. Ein perfekter Tag beendet sie sofort.
29. Eine Ein-Spiel-Teilnahme ist Grenze und keine unsichtbare Pause.
30. Negative Serien verwenden dieselben Korrektur-, Aggregations- und Abschlussprinzipien.

### 22.4 Reconciliation und Betrieb

31. Wird ein serienbeendendes `X` zu gelöst korrigiert, wird die Abschlussmeldung gelöscht oder angepasst.
32. Wird ein rekordverlängerndes Ergebnis zu `X` korrigiert, wird die Überschreitungsmeldung gelöscht oder reduziert.
33. Konkurrenz auf demselben Rekordzustand verliert keine bessere Aktualisierung.
34. Retry und Restart erzeugen keine doppelte Discord-Meldung.
35. Unklarer Discord-Ausgang wird über persistierten Delivery-Zustand reconciled.
36. Eine extern gelöschte, zuvor bestätigte Meldung wird nicht unbegrenzt wiederhergestellt.
37. Teilnahmeende löscht keine historischen Rekorde.
38. Datenschutzlöschung anonymisiert Identität ohne künstlichen neuen Rekord.
39. `/records` zeigt nach jeder Korrektur den aktuell gültigen Stand.
40. Alle Zeitgrenzen verwenden `Europe/Berlin` und eine injizierte `Clock`.
