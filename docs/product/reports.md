# Wochen- und Monatsberichte

## Perioden und Fälligkeit

Berichte verwenden ausschließlich vollständig abgeschlossene Kalenderperioden in `Europe/Berlin` und einen expliziten Periodenend-Stichtag.

| Typ | Periode | regulär fällig | Catch-up-Fenster |
|---|---|---|---|
| Woche | vorheriger Montag bis Sonntag | Montag 08:00 | 72 Stunden |
| Monat | vorheriger Kalendermonat | erster Tag 08:15 | 7 Tage |

Pro Berichtstyp wird höchstens die jüngste noch relevante versäumte Periode nachgeholt. Fälligkeit, Claim, Retry, `NO_OP` und Ablauf sind persistent; der Scheduler ist nur Trigger.

## Teilnehmer und Nenner

Berichtsteilnehmer haben an mindestens einem Periodentag zu `U(d)` gehört. Ihre Reihenfolge folgt dem frühesten Teilnahmebeginn, anschließend der stabilen Spieler-ID.

Spielstatistiken verwenden getrennte GridWords- und QuadWords-Teilnahmetage. Aktivität verwendet Unionstage, Komplett und Perfekt ausschließlich Zwei-Spiele-Tage. Gemeinsame Komplett- und Perfektnenner zählen nur Tage mit mindestens zwei Zwei-Spiele-Teilnehmern.

Je Spiel werden Einreichungen, gelöste und nicht gelöste Ergebnisse, fehlende Ergebnisse, Lösungsquote, durchschnittliche Versuche, durchschnittliche Lösungszeit und beste gelöste Leistung berechnet. `X` zählt als Einreichung. Fehlend ist `spielbezogene Teilnahmetage − Einreichungen`. Quoten teilen durch Einreichungen; Leistungsdurchschnitte berücksichtigen nur gelöste Ergebnisse. Nicht definierte Werte erscheinen neutral.

## Serien und Highlightquellen

Persönliche Berichte zeigen die relevanten Tageszahlen und Serien bis einschließlich Periodenende. Der gemeinsame Abschnitt enthält Komplett- und Perfektwerte für Tage mit mindestens zwei Zwei-Spiele-Teilnehmern. Gemeinsame spielbezogene Lösungsserien werden im Bericht nicht zusätzlich ausgewiesen.

Optional folgt nach allen Statistikseiten zwingend eine neue logische Highlightseite mit Titel `✨ Highlights der Woche · …` beziehungsweise `✨ Highlights des Monats · …`. Ohne Highlights gibt es keine leere Seite.

### Achievements

Für jeden bereits im Bericht enthaltenen Spieler zählt ausschließlich der **aktuelle `ACTIVE` Award-State**, dessen fachliches `earned_on` inklusiv innerhalb `period_start..period_end` liegt.

- Spieler mit null passenden Awards werden ausgelassen.
- Reihenfolge bleibt die bestehende Reportteilnehmerreihenfolge.
- Einzelne Achievement-Namen, Kategorien und Scopes werden nicht dargestellt.
- Es wird weder Achievement-History gescannt noch Reconciliation nur für den Bericht ausgelöst.
- Eine Invalidierung vor Reporterzeugung reduziert die Zahl; eine Änderung nach erfolgreicher Veröffentlichung editiert den eingefrorenen Report nicht automatisch.

Das Field `🏅 Achievements` zeigt damit beispielsweise:

```text
Eschi · **1** freigeschaltet
```

### Rekorde

Quelle sind ausschließlich die in [`records.md`](records.md) definierten aktuell `VALID`en und öffentlich zulässigen Record-Events. Die unmittelbare Record-Announcement-Konfiguration ist weder Quelle noch Sichtbarkeitskriterium.

Das fachliche Ereignisdatum ist:

- Ergebnisrekord → `gameDate` der neuen `GameResult`-Quelle,
- Serienrekord → `endDate` des neuen `StreakRecordValue`.

Nur dieses Datum entscheidet über die inklusive Periodenzuordnung. `detectedAt`, Persistenz-, Scheduler- oder Discord-Zeitpunkte sind dafür nicht maßgeblich.

Crossing und Finish derselben Record-State-Key-/StreakRun-Kombination werden **nur innerhalb derselben Periode** zugunsten des gültigen Finish zusammengeführt. Liegt nur ein Crossing in der Periode vor, wird es gezeigt. Crossing in einer Periode und Finish in einer späteren dürfen jeweils in ihrem eigenen Bericht erscheinen. Mehrere echte Ergebnisrekordverbesserungen derselben Definition werden nicht künstlich dedupliziert.

Das Field `🏆 Rekorde` verwendet atomare Zweizeiler:

```text
**Eschi** · persönlicher GridWords-Rekord
↳ GridWords-Lösungsserie · **6 Tage**
```

Zwischen Rekordblöcken liegt eine Leerzeile. Jeder Eintrag nennt verständlich Subjekt beziehungsweise `Gemeinsam`, Spiel soweit anwendbar, Metrik/Serienart, Scope und neuen beziehungsweise endgültigen Wert; `/records` darf zum Verständnis nicht nötig sein.

## Darstellung in 1.5.1

Der Statistikteil beginnt beispielsweise mit `📊 Wochenbericht · 3.–9. August 2026` oder `📊 Monatsbericht · 1.–31. August 2026`. Monat und Jahr werden nur wiederholt, wenn dies für einen periodenübergreifenden Titel nötig ist.

Jeder Spieler bleibt ein eigenes nicht-inline Field mit dem Namen `👤 <sicherer Anzeigename>`. Der Block folgt diesem Format:

```text
📅 Teilnahme 7/7 · Aktiv 7 · Komplett 7 · Perfekt 6
🟩 GW 7/7 ✅ · 100 % · ØVers. 4,3 · ØZeit 0:50 · Best 0:21
🟦 QW 7/7 · 6✅ 1❌ · 85,7 % · ØVers. 8,3 · ØZeit 3:05 · Best 2:01
🔥 Serien (Stand/Rekord)
↳ Aktiv 10/10 · Komplett 10/10 · GW 10/10 · QW 2/7 · Perfekt 2/7
```

Der Nenner in `Teilnahme x/y` ist nur die Zahl der Kalendertage der Periode und verändert keinen fachlichen Spielnenner. Spielzeilen unterscheiden kompakt gelöst `✅`, nicht gelöst `❌` und fehlend `⬜`. Ohne Einreichung erscheint etwa `GW 0/7 · 7⬜ · keine Einreichung`; ohne Teilnahme `QW — keine Teilnahme`. Nicht definierte Quote-, Durchschnitts- und Bestwerte entfallen. Gibt es keine gelösten Ergebnisse, entfallen gelöst-basierte Durchschnitte und Bestzeit.

Lösungsquoten haben höchstens eine Nachkommastelle und keine überflüssige Null; Durchschnittsversuche behalten genau eine Nachkommastelle. Zeiten verwenden `m:ss`. Serienwerte `x/y` bedeuten `Stand am Periodenende / Allzeitrekord bis Periodenende`; fachliche Nullwerte wie `0/0` oder `0/7` bleiben sichtbar.

Der gemeinsame Field `🤝 Gemeinsam` zeigt mögliche, komplette und perfekte Tage sowie Komplett-/Perfektserie. Ohne gemeinsam mögliche Tage lautet die Tageszeile `📅 Keine gemeinsam möglichen Tage`; historische Rekordwerte der Serien bleiben dennoch sichtbar.

Es gibt keine Mentions, Gewinnerlogik, Ranglisten oder direkten Leistungsvergleiche.

## Snapshot, Pagination und Delivery

Berechnete Statistik-, Achievement- und Record-Highlightwerte werden **nicht** als zweite Report-Fachwahrheit persistiert. Ein erfolgreich veröffentlichter Bericht gilt jedoch als eingefrorener sichtbarer Snapshot: Die bestehende Delivery speichert ihren Zustand, Content-Fingerprint und die geordneten Discord-Message-IDs. Spätere Ergebnisse, Awards, Record-Events oder Namensänderungen editieren diesen erfolgreichen Bericht im normalen Reconcile nicht automatisch.

Spielerfields und der gemeinsame Block bleiben atomar; Highlights beginnen auf einer neuen Seite; Rekord-Zweizeiler werden nie getrennt. Achievement-Zeilen und Rekordblöcke dürfen deterministisch über Fortsetzungsfields verteilt werden. Es gibt kein künstliches Rekordlimit und kein Abschneiden. Footer `Seite x/y` zählen Statistik- und Highlightseiten gemeinsam; identischer Input erzeugt identische Seiten und denselben Fingerprint.

Eine Periode ohne Teilnehmer endet als `NO_OP`. Teilnehmer ohne Ergebnisse erhalten dennoch einen Bericht. Eine zulässige Recovery/Recreate innerhalb des bestehenden Catch-up-Fensters darf den Bericht aus dem dann aktuellen, weiterhin strikt auf `period_end` begrenzten Fachstand neu ableiten.

Beim `ApplicationReadyEvent` verwendet der Wochenreport-Scheduler nur montags einen zusätzlichen expliziten 1.5.1-Refreshmodus für den vom Planner bestimmten letzten fälligen Wochenbericht innerhalb des unveränderten Catch-up-Fensters. Nur ein bereits erfolgreicher Snapshot mit abweichendem Content-Fingerprint wird unter bestehendem Claim-/Lease-Fencing als komplette Seitengruppe ersetzt. Gleicher Fingerprint ist ein No-op; nach erfolgreichem Ersatz sind weitere Montags-Restarts idempotent. Reguläre Scheduler-Ticks verwenden auch montags den normalen Frozen-Snapshot-Pfad. Dienstag bis Sonntag und Monatsberichte besitzen keinen entsprechenden Layout-Refresh.
