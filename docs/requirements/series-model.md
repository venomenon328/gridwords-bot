# Verbindliches Serienmodell

**Status:** fachlich abgenommen  
**Stand:** 2. August 2026  
**Gültig ab:** Version 1 einschließlich Zwischeninkrement 10.2

Dieses Dokument ergänzt und präzisiert die Anforderungsspezifikation. Bei widersprüchlichen älteren Formulierungen zu „Spielserie“, „Lösungsserie“, „gespieltem Tag“ oder „gelöstem Tag“ gilt dieses Dokument.

Betroffen sind insbesondere Serien, Tagesstatus, kanonische Ergebnisnachrichten, Statistik-Commands, Berichte und Tests. Die zwei gemeinsamen spielbezogenen Lösungsserien aus Zwischeninkrement 10.2 erweitern das bestehende Modell, ohne Definitionen der bisherigen sieben Serien zu verändern.

## 1. Grundzustand eines einzelnen Spiels

Für jeden Spieler, Spieltyp und Spieltag existiert genau einer dieser fachlichen Zustände:

1. **nicht gespielt:** kein gültiges Ergebnis eingereicht,
2. **gespielt, aber nicht gelöst:** gültiges Ergebnis mit `X/6` beziehungsweise `X/9`,
3. **gelöst:** gültiges Ergebnis mit numerischer Versuchszahl.

„Gespielt“ und „eingereicht“ sind in der Serienlogik gleichbedeutend. Ein korrekt eingereichtes, aber nicht gelöstes Spiel zählt als gespielt.

## 2. Persönliche Tagesmerkmale

### 2.1 Aktivitätstag

Ein Spieltag ist für einen Spieler ein Aktivitätstag, wenn mindestens eines der beiden Spiele gültig eingereicht wurde.

### 2.2 Kompletter Tag

Ein Spieltag ist für einen Spieler komplett, wenn GridWords und QuadWords gültig eingereicht wurden. Der Lösungsstatus ist dafür unerheblich.

### 2.3 Perfekter Tag

Ein Spieltag ist für einen Spieler perfekt, wenn GridWords und QuadWords eingereicht und beide gelöst wurden.

Jeder perfekte Tag ist zugleich komplett und aktiv. Ein kompletter Tag muss nicht perfekt sein.

## 3. Persönliche Serien

Für jeden Spieler werden fünf Serien unabhängig berechnet:

### 3.1 Aktivitätsserie

Aufeinanderfolgende Kalendertage, an denen mindestens eines der beiden Spiele eingereicht wurde.

### 3.2 Komplettserie

Aufeinanderfolgende Kalendertage, an denen beide Spiele eingereicht wurden.

### 3.3 GridWords-Lösungsserie

Aufeinanderfolgende Kalendertage, an denen GridWords gelöst wurde. Ein fehlendes oder nicht gelöstes GridWords-Ergebnis beendet die Serie. QuadWords hat keinen Einfluss.

### 3.4 QuadWords-Lösungsserie

Analog zur GridWords-Lösungsserie. GridWords hat keinen Einfluss.

### 3.5 Perfektserie

Aufeinanderfolgende perfekte Tage.

## 4. Gemeinsame Tagesmerkmale und Serien

Die aktive Teilnehmermenge wird für jeden Kalendertag ausschließlich aus den historisch wirksamen Teilnahmezeiträumen bestimmt. Beitritt, Austritt und Wiedereintritt verändern keine davor liegenden Tage.

Ein gemeinsamer Tag kann nur entstehen, wenn an diesem Tag mindestens zwei Spieler aktiv waren. Bei weniger als zwei aktiven Spielern sind alle gemeinsamen Tagesbedingungen verletzt.

### 4.1 Gemeinsam GridWords gelöst

Ein Tag erfüllt die gemeinsame GridWords-Bedingung, wenn jeder an diesem Tag aktive Spieler GridWords gelöst hat.

- Ein GridWords-`X` eines aktiven Spielers verletzt die Bedingung sofort.
- Ein fehlendes GridWords-Ergebnis ist am laufenden Tag zunächst vorläufig und historisch endgültig verletzend.
- QuadWords hat keinen Einfluss.

### 4.2 Gemeinsam QuadWords gelöst

Analog für QuadWords. GridWords hat keinen Einfluss.

### 4.3 Gemeinsam kompletter Tag

Ein Tag ist gemeinsam komplett, wenn jeder aktive Spieler GridWords und QuadWords eingereicht hat.

### 4.4 Gemeinsam perfekter Tag

Ein Tag ist gemeinsam perfekt, wenn jeder aktive Spieler beide Spiele gelöst hat.

### 4.5 Gemeinsame Serien

Es werden vier gemeinsame Serien unabhängig berechnet:

1. **gemeinsame GridWords-Lösungsserie**,
2. **gemeinsame QuadWords-Lösungsserie**,
3. **gemeinsame Komplettserie**,
4. **gemeinsame Perfektserie**.

Es gibt weiterhin keine gemeinsame Aktivitätsserie. Die Aussage wäre zu schwach und missverständlich, weil verschiedene Spieler unterschiedliche einzelne Spiele spielen könnten.

## 5. Laufende Serien am aktuellen Tag

Ein am aktuellen Tag noch fehlendes Ergebnis beendet eine bis gestern laufende Serie nicht vor Ablauf des Spieltags. Die Regel wird für jede Serie separat angewendet.

- Eine persönliche oder gemeinsame GridWords-Lösungsserie bleibt vorläufig bestehen, solange das erforderliche heutige GridWords-Ergebnis fehlt. Ein heutiges `X/6` beendet sie sofort.
- Für QuadWords gilt dieselbe Regel unabhängig.
- Eine Aktivitätsserie wird heute verlängert, sobald mindestens ein Spiel eingereicht wurde.
- Eine persönliche oder gemeinsame Komplettserie wird erst verlängert, sobald alle erforderlichen Einreichungen vorliegen. Bis dahin bleibt der gestrige Stand vorläufig bestehen.
- Eine persönliche oder gemeinsame Perfektserie wird erst verlängert, sobald alle erforderlichen Lösungen vorliegen. Ein nicht gelöstes Ergebnis beendet sie sofort.

Nach Ablauf des Spieltags ist eine fehlende Bedingung eine echte Lücke und beendet die betroffene Serie.

Zulässige Vortagsnachträge lösen eine vollständige Neuberechnung aller betroffenen persönlichen und gemeinsamen Serien aus.

## 6. Eindeutige Seriennamen

Damit existieren neun Serienarten:

```text
activity
complete
gridwords-solved
quadwords-solved
perfect
shared-gridwords-solved
shared-quadwords-solved
shared-complete
shared-perfect
```

Neue Ausgaben, APIs und Commands dürfen nicht auf unspezifische Namen wie `playStreak`, `solveStreak`, „Spielserie“ oder „Lösungsserie“ zurückfallen.

## 7. Kanonische Ergebnisnachrichten

Eine einzelne Ergebnisnachricht soll nicht alle Serienwerte ungefiltert anzeigen.

Verbindliche Mindestwerte:

- Aktivitätsserie des Spielers,
- Lösungsserie des gerade geposteten Spiels.

Wird mit dem Ergebnis der Tag komplett oder perfekt, dürfen die entsprechenden persönlichen Werte ergänzt werden. Die gemeinsamen spielbezogenen Serien müssen in einzelnen kanonischen Ergebnisnachrichten nicht angezeigt werden.

## 8. Tagesstatus

Der Tagesstatus zeigt pro Spieler:

- Status von GridWords und QuadWords,
- Aktivitätsserie,
- Komplettserie,
- GridWords-Lösungsserie,
- QuadWords-Lösungsserie,
- Perfektserie.

Gemeinsam werden eindeutig angezeigt:

- GridWords gelöst,
- QuadWords gelöst,
- komplett,
- perfekt.

Beispiel:

```text
Gemeinsame Serien
GridWords gelöst: 8 · QuadWords gelöst: 5
Komplett: 7 · Perfekt: 4
```

Die genaue visuelle Verdichtung darf der Discord-Adapter bestimmen, solange alle vier Werte unterscheidbar bleiben und Discord-Grenzen eingehalten werden.

## 9. Wochen- und Monatsberichte

Zwischeninkrement 10.2 erweitert die bereits implementierten Wochen- und Monatsberichte nicht automatisch.

Berichte weisen weiterhin mindestens aus:

### Pro Spieler

- Aktivitäts-, Komplett- und perfekte Tage,
- aktuelle und längste Werte aller fünf persönlichen Serien,
- die vorgesehenen spielbezogenen Einreichungs-, Lösungs-, Versuchs- und Zeitstatistiken.

### Gemeinsam

- gemeinsam komplette und perfekte Tage,
- aktuelle und längste gemeinsame Komplett- und Perfektserie.

Eine spätere Aufnahme der gemeinsamen GridWords- und QuadWords-Lösungsserien in Berichte benötigt eine gesonderte fachliche Entscheidung. Der Tagesstatus und die allgemeine Serienprojektion enthalten sie bereits.

## 10. Kommentare und Statistik-Commands ab Version 3

Regelbasierte Kommentare dürfen insbesondere durch Verlängerungen oder Rekorde jeder der neun eindeutig benannten Serien ausgelöst werden.

Ein Parameter für den Serientyp muss künftig diese Werte unterstützen:

```text
activity
complete
gridwords-solved
quadwords-solved
perfect
shared-gridwords-solved
shared-quadwords-solved
shared-complete
shared-perfect
```

Die konkrete Command-Syntax wird in Version 3 festgelegt.

## 11. Domänenmodell und Persistenz

Serien sind abgeleitete Werte aus `game_result` und den historischen Teilnahmezeiträumen. Sie werden nicht als fortlaufend mutierte Zähler oder zweite fachliche Wahrheit persistiert.

Ein aktuelles `StreakSummary` enthält mindestens:

```text
personalActivity
personalComplete
personalGridWordsSolved
personalQuadWordsSolved
personalPerfect
sharedGridWordsSolved
sharedQuadWordsSolved
sharedComplete
sharedPerfect
```

Die Berechnung erfolgt über klar benannte Tagesbedingungen. GridWords und QuadWords dürfen weder persönlich noch gemeinsam in eine unspezifische Lösungsserie zusammengefasst werden.

## 12. Verbindliche Testfälle

Mindestens automatisiert abzudecken sind:

1. Nur GridWords gespielt: ausschließlich passende persönliche Bedingungen steigen.
2. Nur QuadWords gespielt: analog.
3. Beide gespielt, eines nicht gelöst: Aktivitäts- und Komplettserie steigen; nur passende Lösungsserie steigt; Perfektserie endet.
4. Beide gelöst: alle persönlichen Tagesbedingungen sind erfüllt.
5. Fehlendes Ergebnis am laufenden Tag beendet eine bis gestern laufende Serie nicht vorzeitig.
6. Ein heutiges `X` beendet die betroffene persönliche und gemeinsame Spiel-Lösungsserie sowie gegebenenfalls Perfektserien sofort.
7. Fehlende historische Ergebnisse beenden ausschließlich die Bedingungen, für die sie erforderlich sind.
8. Vortagsnachträge stellen persönliche und gemeinsame Serien korrekt wieder her.
9. Beide oder alle aktiven Spieler lösen GridWords: gemeinsame GridWords-Lösungsserie steigt unabhängig von QuadWords.
10. Analog für QuadWords.
11. Ein `X` für einen Spieltyp beeinflusst die gemeinsame Lösungsserie des anderen Spieltyps nicht.
12. Bei drei oder mehr aktiven Spielern müssen alle das betreffende Spiel lösen.
13. Weniger als zwei aktive Spieler erzeugen keine gemeinsame Serie.
14. Wechselnde tägliche Teilnehmermengen verwenden die jeweilige historische Teilnehmermenge.
15. Beitritt, Austritt und Wiedereintritt verfälschen keine anderen Tage rückwirkend.
16. Gemeinsame Komplett- und Perfektserie behalten ihre bisherige Semantik.
17. Tagesstatus, Fingerprint, Create, Edit, inhaltlicher NO_OP, Recreate und Restart-Reconciliation berücksichtigen beide neuen Werte.
18. Sommer-/Winterzeitgrenzen verwenden weiterhin Kalendertage in `Europe/Berlin`.

## 13. Auswirkung auf ältere Dokumente

Ältere unspezifische Formulierungen werden wie folgt ersetzt:

- „persönliche Spielserie“ → je nach Kontext Aktivitäts- oder Komplettserie,
- „persönliche Lösungsserie“ → GridWords- oder QuadWords-Lösungsserie, gegebenenfalls Perfektserie,
- „gemeinsame Spielserie“ → gemeinsame Komplettserie,
- „gemeinsame Lösungsserie“ → je nach Kontext gemeinsame GridWords-, QuadWords- oder Perfektserie,
- „gemeinsam gespielt“ → gemeinsam komplett,
- „gemeinsam gelöst“ ohne Spielbezug → gemeinsam perfekt.

Wo ältere Beispiele nur `playStreak` und `solveStreak` nennen, sind stattdessen die eindeutig benannten Werte dieses Dokuments zu verwenden.
