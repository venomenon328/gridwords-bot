# Verbindliches Serienmodell

**Status:** fachlich abgenommen  
**Stand:** 28. Juli 2026  
**Gültig ab:** Version 1

Dieses Dokument ergänzt und präzisiert die Anforderungsspezifikation. Bei widersprüchlichen älteren Formulierungen zu „Spielserie“, „Lösungsserie“, „gespieltem Tag“ oder „gelöstem Tag“ gilt dieses Dokument.

Betroffen sind insbesondere die Serien-, Ausgabe-, Tagesstatus-, Berichts- und Testanforderungen der Anforderungsspezifikation sowie die entsprechenden Beispiele in `docs/architecture.md` und `docs/implementation-plan.md`.

## 1. Grundzustand eines einzelnen Spiels

Für jeden Spieler, Spieltyp und Spieltag existiert genau einer dieser fachlichen Zustände:

1. **nicht gespielt:** kein gültiges Ergebnis eingereicht,
2. **gespielt, aber nicht gelöst:** gültiges Ergebnis mit `X/6` beziehungsweise `X/9`,
3. **gelöst:** gültiges Ergebnis mit numerischer Versuchszahl.

„Gespielt“ und „eingereicht“ sind in der Serienlogik gleichbedeutend. Ein korrekt eingereichtes, aber nicht gelöstes Spiel zählt als gespielt.

## 2. Persönliche Tagesmerkmale

### 2.1 Aktivitätstag

Ein Spieltag ist für einen Spieler ein **Aktivitätstag**, wenn mindestens eines der beiden Spiele gültig eingereicht wurde.

Beispiele:

- nur GridWords eingereicht: Aktivitätstag,
- nur QuadWords eingereicht: Aktivitätstag,
- beide eingereicht: Aktivitätstag,
- kein Ergebnis: kein Aktivitätstag.

### 2.2 Kompletter Tag

Ein Spieltag ist für einen Spieler ein **kompletter Tag**, wenn GridWords und QuadWords gültig eingereicht wurden. Der Lösungsstatus ist dafür unerheblich.

### 2.3 Perfekter Tag

Ein Spieltag ist für einen Spieler ein **perfekter Tag**, wenn GridWords und QuadWords eingereicht und beide gelöst wurden.

Jeder perfekte Tag ist zugleich ein kompletter Tag und ein Aktivitätstag. Ein kompletter Tag muss nicht perfekt sein.

## 3. Persönliche Serien

Für jeden Spieler werden diese fünf Serien unabhängig berechnet:

### 3.1 Aktivitätsserie

Anzahl aufeinanderfolgender Kalendertage, die Aktivitätstage sind.

Die Aktivitätsserie ist bewusst großzügig. Sie zeigt, ob der Spieler täglich wenigstens eines der beiden Spiele gespielt hat. Sie darf nicht als vollständige Erledigung beider Spiele dargestellt werden.

### 3.2 Komplettserie

Anzahl aufeinanderfolgender Kalendertage, an denen beide Spiele eingereicht wurden.

Die Komplettserie bildet die tägliche vollständige Spielroutine ab und ist die zentrale persönliche Routine-Serie.

### 3.3 GridWords-Lösungsserie

Anzahl aufeinanderfolgender Kalendertage, an denen GridWords gelöst wurde.

Sie endet durch:

- ein nicht gelöstes GridWords-Ergebnis oder
- ein fehlendes GridWords-Ergebnis.

QuadWords hat keinen Einfluss auf diese Serie.

### 3.4 QuadWords-Lösungsserie

Anzahl aufeinanderfolgender Kalendertage, an denen QuadWords gelöst wurde.

Sie endet durch:

- ein nicht gelöstes QuadWords-Ergebnis oder
- ein fehlendes QuadWords-Ergebnis.

GridWords hat keinen Einfluss auf diese Serie.

### 3.5 Perfektserie

Anzahl aufeinanderfolgender perfekter Tage.

Die Perfektserie ist eine ergänzende, strenge Kennzahl. Sie wird berechnet und kann in Tagesstatus, Statistiken, Berichten und Rekordmeldungen verwendet werden, muss aber nicht in jeder einzelnen Ergebnisnachricht prominent erscheinen.

## 4. Gemeinsame Tagesmerkmale und Serien

### 4.1 Gemeinsam kompletter Tag

Ein Spieltag ist **gemeinsam komplett**, wenn beide Spieler jeweils GridWords und QuadWords eingereicht haben.

### 4.2 Gemeinsam perfekter Tag

Ein Spieltag ist **gemeinsam perfekt**, wenn beide Spieler jeweils beide Spiele gelöst haben.

### 4.3 Gemeinsame Komplettserie

Anzahl aufeinanderfolgender gemeinsam kompletter Tage.

Sie ist die zentrale gemeinsame Paarserie.

### 4.4 Gemeinsame Perfektserie

Anzahl aufeinanderfolgender gemeinsam perfekter Tage.

Sie ist eine zusätzliche, strengere Paarserie.

### 4.5 Keine gemeinsame Aktivitätsserie

Eine gemeinsame Aktivitätsserie wird nicht berechnet. Die Aussage wäre zu schwach und missverständlich, weil beide Spieler an einem Tag unterschiedliche einzelne Spiele spielen könnten, ohne den gemeinsamen Tagesumfang zu erfüllen.

## 5. Laufende Serien am aktuellen Tag

Ein am aktuellen Tag noch fehlendes Ergebnis beendet eine bis gestern laufende Serie nicht vor Ablauf des Spieltags.

Die Regel wird für jede Serie separat angewendet:

- Eine GridWords-Lösungsserie bleibt tagsüber vorläufig bestehen, solange GridWords heute noch nicht eingereicht wurde. Ein heutiges `X/6` beendet sie sofort.
- Eine QuadWords-Lösungsserie verhält sich entsprechend unabhängig.
- Eine Aktivitätsserie wird heute verlängert, sobald mindestens ein Spiel eingereicht wurde.
- Eine Komplettserie wird heute erst verlängert, sobald beide Spiele eingereicht wurden; vorher bleibt die bis gestern laufende Serie vorläufig bestehen.
- Eine Perfektserie wird heute erst verlängert, sobald beide Spiele gelöst vorliegen. Ein nicht gelöstes Ergebnis beendet sie sofort.
- Gemeinsame Serien werden analog über die Ergebnisse beider Spieler berechnet.

Nach Ablauf des Spieltags ist eine nicht erfüllte Bedingung eine echte Lücke und beendet die betroffene Serie.

Nachträge für den zulässigen Vortag lösen eine vollständige Neuberechnung aller betroffenen Serien aus.

## 6. Anzeige in kanonischen Ergebnisnachrichten

Eine einzelne Ergebnisnachricht soll nicht alle Serienwerte ungefiltert anzeigen.

Verbindliche Mindestwerte:

- **Aktivitätsserie** des Spielers,
- die **Lösungsserie des gerade geposteten Spiels**.

Zusätzliche kontextabhängige Werte:

- Wird mit dem Ergebnis der Tag komplett, darf die **Komplettserie** ergänzt werden.
- Wird mit dem Ergebnis der Tag perfekt, darf die **Perfektserie** ergänzt werden.
- Die Lösungsserie des jeweils anderen Spiels wird in der einzelnen Ergebnisnachricht nicht benötigt.

Beispiel GridWords:

```text
Tobias · GridWords · 27. Juli 2026
Gelöst in 5/6 · 1:25

…

🔥 Aktivität: 12 Tage · GridWords gelöst: 9 Tage
```

Beispiel eines Ergebnisses, das den Tag komplettiert:

```text
🔥 Aktivität: 12 · Komplett: 8 · QuadWords gelöst: 4
```

## 7. Tagesstatus

Der Tagesstatus zeigt pro Spieler:

- Status von GridWords,
- Status von QuadWords,
- Aktivitätsserie,
- Komplettserie,
- GridWords-Lösungsserie,
- QuadWords-Lösungsserie,
- Perfektserie.

Gemeinsam werden angezeigt:

- gemeinsame Komplettserie,
- gemeinsame Perfektserie.

Beispiel:

```text
Wortspiele · 27. Juli 2026

Tobias
✅ GridWords · gelöst · 5/6 · 1:25
⬜ QuadWords
Aktivität: 11 · Komplett: 7
GridWords gelöst: 8 · QuadWords gelöst: 3 · Perfekt: 2

Georgia
✅ GridWords · gelöst · 4/6 · 1:47
✅ QuadWords · gelöst · 8/9 · 3:51
Aktivität: 12 · Komplett: 12
GridWords gelöst: 10 · QuadWords gelöst: 6 · Perfekt: 5

Gemeinsam komplett: 7 Tage
Gemeinsam perfekt: 2 Tage
```

Die genaue visuelle Verdichtung darf der Discord-Ausgabeadapter bestimmen, solange die Bezeichnungen eindeutig bleiben und Aktivität nicht mit vollständiger Erledigung verwechselt wird.

## 8. Wochen- und Monatsberichte

Berichte verwenden die neue Terminologie.

Mindestens auszuweisen sind:

### Pro Spieler

- Anzahl Aktivitätstage,
- Anzahl kompletter Tage,
- Anzahl perfekter Tage,
- aktuelle und längste Aktivitätsserie,
- aktuelle und längste Komplettserie,
- aktuelle und längste GridWords-Lösungsserie,
- aktuelle und längste QuadWords-Lösungsserie,
- aktuelle und längste Perfektserie,
- bisher bereits vorgesehene spielbezogene Einreichungs-, Lösungs-, Versuchs- und Zeitstatistiken.

### Gemeinsam

- Anzahl gemeinsam kompletter Tage,
- Anzahl gemeinsam perfekter Tage,
- aktuelle und längste gemeinsame Komplettserie,
- aktuelle und längste gemeinsame Perfektserie.

Die früheren unspezifischen Begriffe „persönlich gespielt“, „persönlich gelöst“, „gemeinsam gespielt“, „gemeinsam gelöst“, „Spielserie“ und „Lösungsserie“ sollen in neuen Ausgaben nicht ohne präzisierenden Zusatz verwendet werden.

## 9. Kommentare und Statistik-Commands ab Version 3

Regelbasierte Kommentare dürfen insbesondere ausgelöst werden durch:

- Verlängerung oder neuen Rekord einer Aktivitätsserie,
- Verlängerung oder neuen Rekord einer Komplettserie,
- Verlängerung oder neuen Rekord der jeweiligen spielbezogenen Lösungsserie,
- einen perfekten Tag,
- Verlängerung oder neuen Rekord einer Perfektserie,
- einen gemeinsam kompletten oder gemeinsam perfekten Tag,
- Verlängerung oder neuen Rekord der beiden gemeinsamen Serien.

Statistik-Commands müssen die Serien eindeutig benennen. Ein Parameter für den Serientyp darf beispielsweise diese Werte anbieten:

```text
activity
complete
gridwords-solved
quadwords-solved
perfect
shared-complete
shared-perfect
```

Die konkrete Command-Syntax wird in Version 3 festgelegt.

## 10. Domänenmodell und Persistenz

Serien sind abgeleitete Werte aus den persistierten `game_result`-Datensätzen. Sie müssen nicht als fortlaufend mutierte Zähler gespeichert werden.

Ein fachliches `StreakSummary` beziehungsweise entsprechendes Ergebnisobjekt enthält mindestens:

```text
personalActivity
personalComplete
personalGridWordsSolved
personalQuadWordsSolved
personalPerfect
sharedComplete
sharedPerfect
```

Je nach Use Case kann zwischen aktuellen und längsten Serien unterschieden werden.

Die Berechnung soll über allgemeine, klar benannte Serienbedingungen erfolgen, ohne GridWords und QuadWords in eine einzige unspezifische Lösungsserie zusammenzufassen.

## 11. Verbindliche Testfälle

Die Serienlogik benötigt mindestens folgende automatisierte Fälle:

1. Nur GridWords gespielt: Aktivitätsserie steigt; Komplettserie nicht; GridWords-Lösungsserie abhängig vom Ergebnis; QuadWords-Lösungsserie nicht.
2. Nur QuadWords gespielt: analog.
3. Beide gespielt, eines nicht gelöst: Aktivitäts- und Komplettserie steigen; nur die passende Spiel-Lösungsserie steigt; Perfektserie endet.
4. Beide gelöst: alle persönlichen Tagesbedingungen sind erfüllt.
5. Ein Spiel fehlt am laufenden heutigen Tag: bis gestern laufende Komplett- und Perfektserie werden vor Tagesende nicht vorzeitig als beendet angezeigt.
6. Ein nicht gelöstes Ergebnis heute beendet die betroffene Spiel-Lösungsserie und die Perfektserie sofort.
7. Fehlender Vortag beendet nur die Serien, deren Bedingung dadurch verletzt ist.
8. Zulässiger Vortagsnachtrag stellt betroffene Serien korrekt wieder her.
9. Beide Spieler komplett, aber nicht beide perfekt: gemeinsame Komplettserie steigt; gemeinsame Perfektserie endet.
10. Beide Spieler perfekt: beide gemeinsamen Serien steigen.
11. Unterschiedliche einzelne Aktivitäten beider Spieler erzeugen keine gemeinsame Aktivitätsserie.
12. Berechnung an Sommer-/Winterzeitgrenzen verwendet weiterhin Kalendertage in `Europe/Berlin`.

## 12. Auswirkung auf ältere Dokumente

Folgende ältere Formulierungen werden durch dieses Dokument ersetzt:

- „persönliche Spielserie“ ohne Zusatz → je nach Kontext Aktivitäts- oder Komplettserie; bei vollständiger täglicher Routine ist **Komplettserie** gemeint,
- „persönliche Lösungsserie“ → getrennte GridWords- und QuadWords-Lösungsserie; optional zusätzlich Perfektserie,
- „gemeinsame Spielserie“ → gemeinsame Komplettserie,
- „gemeinsame Lösungsserie“ → gemeinsame Perfektserie,
- „persönlich gespielter Tag“ mit beiden Spielen → kompletter Tag,
- „persönlich gelöster Tag“ → perfekter Tag,
- „gemeinsam gespielter Tag“ → gemeinsam kompletter Tag,
- „gemeinsam gelöster Tag“ → gemeinsam perfekter Tag.

Wo ältere Beispiele nur `playStreak` und `solveStreak` nennen, sind stattdessen die eindeutig benannten Werte dieses Dokuments zu verwenden.