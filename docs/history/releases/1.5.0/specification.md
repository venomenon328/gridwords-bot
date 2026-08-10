# Verbindlicher UX-/QoL-Nachtrag für bestehende Funktionen

**Status:** fachlich abgenommen  
**Stand:** 9. August 2026  
**Gültig ab:** Inkrement 14  
**Verbindliches Issue:** #105  
**Geplantes Release:** 1.5.0

Dieses Dokument definiert die fachlichen Änderungen von Inkrement 14. Es ergänzt die bestehenden Requirements zu Tagesstatus, Teilnahme, Remindern, Ausreden, Rekorden, Achievements und periodischen Berichten.

Bei Widersprüchen zu älteren **Darstellungs-, Command- oder Read-View-Festlegungen** gilt dieses Dokument. Die zugrunde liegende Spiel-, Teilnahme-, Serien-, Rekord-, Achievement-, Ausreden- und Delivery-Semantik bleibt unverändert, sofern dieses Dokument keine ausdrückliche UX-/Read-View-Erweiterung beschreibt.

Verbindliche Grundlagen bleiben insbesondere:

- [`game-specific-participation.md`](../../legacy/requirements/game-specific-participation.md),
- [`daily-status-reminders.md`](../../legacy/requirements/daily-status-reminders.md),
- [`series-model.md`](../../legacy/requirements/series-model.md),
- [`excuses.md`](../../legacy/requirements/excuses.md),
- [`records.md`](../../legacy/requirements/records.md),
- [`achievements.md`](../../legacy/requirements/achievements.md),
- [`achievement-list.md`](../../legacy/requirements/achievement-list.md),
- [`periodic-reports.md`](../../legacy/requirements/periodic-reports.md).

---

## 1. Ziel und Leitplanken

Inkrement 14 stärkt bestehende Funktionen, statt ein neues großes Fachsystem einzuführen. Vorhandene Informationen sollen dort sichtbar werden, wo Nutzer sie sinnvoll erwarten; bestehende Features sollen besser miteinander verknüpft werden; Self-Service-Antworten sollen ihre tatsächliche Wirkung verständlich ausdrücken.

Verbindlich gilt:

- keine neuen Achievement- oder Rekorddefinitionen,
- keine quantitative Achievement-Fortschrittsanzeige,
- kein Leaderboard oder Ranking,
- kein Reminderstatus pro Spiel,
- keine neue Spiel-, Serien- oder Teilnahmebedingung,
- keine generische Dashboard-, Query-, Gamification-, Event- oder Messaging-Plattform,
- keine Discord-/Announcement-Texte als fachliche Datenquelle,
- read-only Commands und Ergebnisdetail-Interactions bleiben strikt read-only,
- vorhandene materialisierte Fachprojektionen werden bevorzugt gelesen; History-Scans, Evaluatoren und Reconciler werden nicht nur für eine Anzeige ausgeführt,
- keine neue persistierte Fachwahrheit und voraussichtlich keine Liquibase-Migration,
- Discord-I/O bleibt außerhalb von Datenbanktransaktionen,
- vorhandene Idempotenz-, Recovery-, Mention-, Paging- und Sicherheitsgrenzen bleiben bestehen.

---

## 2. Tagesstatus: informativere Ergebnis-Dropdowns

Die vorhandenen GridWords-/QuadWords-Select-Menüs bleiben der spielbezogene Einstieg in die ephemeren Ergebnisdetails. Teilnehmermenge, Sortierung, Pagination, Component-Versionierung und serverseitige Validierung bleiben unverändert.

Jede Option zeigt bereits den aktuellen Ergebnisstatus des betreffenden Spielers für das betreffende Spiel und den dargestellten Spieltag.

### 2.1 Label

```text
✅ <Anzeigename>  gelöst
❌ <Anzeigename>  gültig eingereicht, aber nicht gelöst
⬜ <Anzeigename>  noch nicht eingereicht
```

Das Statussymbol ist Bestandteil des sichtbaren Labels. Der serverbezogene Anzeigename bleibt die sichtbare Identität; die Discord-User-ID bleibt der technische Optionswert beziehungsweise dessen Bestandteil und wird nicht durch den Anzeigenamen ersetzt.

### 2.2 Description

Bei vorhandener Einreichung:

```text
<n>/<max> · <Dauer>
X/<max> · <Dauer>
```

Bei fehlender Einreichung:

```text
Noch nicht eingereicht
```

Die Dauer verwendet das bestehende kompakte Format. Nichtteilnehmer eines Spiels erscheinen weiterhin nicht im Menü dieses Spiels.

Externe Anzeigenamen werden so behandelt, dass die Discord-Grenzen für Label und Description sicher eingehalten werden. Eine notwendige Darstellungsbegrenzung darf technische Identität, Teilnehmermenge oder Sortierung nicht verändern.

---

## 3. Tagesstatus: direkte Spiel-Links

Neue Tagesstatusnachrichten erhalten zusätzlich genau zwei reine Discord-Link-Buttons:

```text
🟩 GridWords spielen
🟦 QuadWords spielen
```

Sie verweisen auf dieselben GridGames-URLs, die bereits in den Remindern verwendet werden.

Regeln:

- reine Link-Buttons ohne Bot-Interaction und ohne Custom-ID,
- kein Tracking und keine Persistenzänderung,
- unabhängig vom Einreichungsstatus des klickenden Nutzers sichtbar,
- vorhandene Result-Select-Menüs bleiben vollständig erhalten,
- maximal vier Select-Menu-Action-Rows plus eine Link-Button-Row müssen innerhalb der Discord-Grenzen bleiben.

Bereits vor Deployment vorhandene historische Tagesstatusnachrichten werden nicht rückwirkend aktualisiert. Eine beim Deployment bereits bestehende heutige Nachricht darf die Buttons bei ihrem nächsten ohnehin stattfindenden normalen Edit oder Recreate erhalten. Es entsteht kein eigener Refresh-, Migrations- oder Startup-Pfad nur für diese Buttons.

---

## 4. Teilnahme-Commands: verständliche Wirksamkeit

Betroffen sind:

```text
/participation join
/participation leave
/player activate
/player deactivate
```

Die bestehende Fachsemantik bleibt unverändert:

- Join/Activate wirkt ab dem aktuellen fachlichen Berlin-Tag,
- Leave/Deactivate wirkt prospektiv ab dem folgenden Berlin-Tag,
- `game` bleibt optional und verwendet ohne Angabe `both`,
- `both` bleibt atomar und idempotent.

Die Antwort beschreibt künftig pro relevantem Spiel die tatsächliche Wirkung der Operation und unterscheidet klar zwischen heutigem Zustand und künftig wirksamer Änderung.

Beispiele:

```text
GridWords: ab heute aktiv
QuadWords: bereits aktiv
```

```text
GridWords: heute noch aktiv · ab 10.08.2026 inaktiv
QuadWords: bleibt aktiv
```

Idempotente Fälle werden verständlich als bereits bestehender Zustand beziehungsweise bereits vorgemerkte Änderung dargestellt. Die Antwort darf nicht allein deshalb widersprüchlich wirken, weil sie nach einer prospektiven Deaktivierung zusätzlich den heutigen Ist-Zustand ausgibt.

Self-Service- und Adminpfade verwenden dieselbe fachliche Textsemantik. Berechtigungen bleiben unverändert.

---

## 5. Reminderstatus und Reminderzeiten

Der globale Reminderstatus bleibt unverändert. Insbesondere bedeutet `aus` weiterhin:

- keine echte Discord-User-Mention in den aggregierten Reminder-Nachrichten,
- der mention-sicher entschärfte Klartextname darf weiterhin in der Übersicht offener Spiele erscheinen.

`/reminders on`, `/reminders off` und insbesondere `/reminders status` formulieren diese Semantik verständlich.

Beispiel:

```text
🔕 Reminder: aus
Du wirst bei offenen Spielen nicht erwähnt.
Dein Name kann weiterhin ohne Ping in der Reminder-Übersicht erscheinen.

Geplante Erinnerungen: 16:00 und 22:00 Uhr
```

Die ausgegebenen Zeiten stammen aus der tatsächlich typisiert gebundenen Laufzeitkonfiguration. Defaultwerte dürfen nicht als vermeintlich aktuelle Konfiguration hart codiert werden.

Im allgemeinen `/status` bleibt die Darstellung bewusst kompakt und zeigt nur `Reminder: an` beziehungsweise `Reminder: aus` ohne die ausführliche Erklärung.

---

## 6. `/achievements`: fachliches Freischaltdatum

Die bestehende ACTIVE-only-Profilansicht `/achievements` zeigt zusätzlich für jedes ausgegebene Achievement das fachliche Erwerbsdatum.

```text
Freigeschaltet am 18.07.2026
```

Maßgeblich ist ausschließlich `AchievementAwardState.Write.earnedOn` beziehungsweise der entsprechende transportneutrale Award-State.

Nicht maßgeblich sind:

- `detectedAt`,
- `createdAt`,
- `updatedAt`,
- der Zeitpunkt der historischen Einführungsmeldung.

Dadurch zeigen rückwirkend rekonstruierte Achievements den tatsächlichen historischen Erreichungstag.

Bestehende Self-/Other-/Game-Filter, ACTIVE-only-Semantik, Kategorie-/Katalogreihenfolge, Custom-Emoji-Auflösung und Pagination bleiben erhalten. `/achievement-list` zeigt kein Freischaltdatum.

---

## 7. `/achievement-list`: optionale Filter

Der Command bleibt:

- self-only,
- strikt read-only,
- vollständig ephemeral,
- vollständige binäre Katalogansicht ohne Filter,
- `✅` für aktuell ACTIVE,
- `❌` für fehlend oder aktuell INVALIDATED,
- ausdrücklich ohne `n/x`, Prozentwert oder Fortschrittsbalken.

Die ältere Festlegung „keine Optionen“ wird durch Inkrement 14 ersetzt.

### 7.1 `game`

Optionale Choices:

| Sichtbarer Wert | Fachlicher Filter |
|---|---|
| Alle | alle Scopes einschließlich `GLOBAL` |
| GridWords | ausschließlich `GRIDWORDS` |
| QuadWords | ausschließlich `QUADWORDS` |
| GW+QW | ausschließlich `CROSS_GAME` |

Es gibt bewusst keinen separaten Filter `Allgemein`. Globale Achievements erscheinen ausschließlich unter `Alle`.

### 7.2 `category`

| Sichtbarer Wert | Fachlicher Filter |
|---|---|
| Alle | alle Kategorien |
| Erfahrung | `EXPERIENCE` |
| Zuverlässigkeit | `RELIABILITY` |
| Leistung | `PERFORMANCE` |
| Besonderes | `SPECIAL` |

### 7.3 `status`

| Sichtbarer Wert | Fachlicher Filter |
|---|---|
| Alle | alle Definitionen |
| Freigeschaltet | aktuell ACTIVE |
| Offen | fehlend oder aktuell INVALIDATED |

Alle drei Filter sind frei kombinierbar. Fehlende Optionen bedeuten jeweils `Alle`. Filterung erfolgt ausschließlich über typisierte Definitions- und State-Metadaten und verändert nicht die Katalogreihenfolge.

Ergibt eine Kombination keine Definitionen, lautet der neutrale Leerzustand sinngemäß:

```text
Keine Achievements entsprechen den gewählten Filtern.
```

---

## 8. `/records`: optionaler Scope-Filter

Der bestehende strikt lesende `/records`-Command erhält zusätzlich die optionale Dimension `scope`.

Choices:

| Sichtbarer Wert | Fachlicher Filter |
|---|---|
| Alle | heutiges vollständiges Verhalten |
| Persönlich | ausschließlich persönlicher Scope des Zielspielers |
| Serverweit | ausschließlich serverweit individuelle Rekorde |
| Gemeinsam | ausschließlich gemeinsame Rekorde |

Der neue Filter ist frei mit `user`, `game` und `category` kombinierbar.

Die vorhandene Fremdansichts-Autorisierung bleibt unverändert: Ein normaler Nutzer darf über den Scope-Filter nicht die persönlichen Rekorde eines anderen Users auslesen. Leere Kombinationen sind normale neutrale Leerzustände.

Filterung erfolgt über typisierte Record-Definition-/Scope-Metadaten und den materialisierten aktuellen Record-State. Es entsteht kein History-Scan und keine neue Record-Evaluation.

---

## 9. Ergebnisdetails: gewählte Ausrede

Die ephemere Detailansicht aus der Tagesstatusnachricht zeigt zusätzlich die aktuell gültige gewählte Ausrede, sofern für das ausgewählte `game_result` ein persistierter Ausredenzustand `SELECTED` existiert.

Verbindlich:

- exakt der persistierte `selected_rendered_text`,
- keine erneute Katalog- oder Templateauswertung,
- keine Stil-, Topic-, Template-, Anlass-, Runden- oder sonstigen Metadaten,
- bei allen anderen Ausredenzuständen oder fehlendem Zustand entfällt der gesamte Ausredenblock.

Die Darstellung darf der kanonischen Ergebnisnachricht entsprechen und den Text beispielsweise alleinstehend zitieren.

---

## 10. Ergebnisdetails: aktuelle Rekorde

Die Detailansicht zeigt unter einer kompakten Überschrift wie `🏆 Aktuelle Rekorde` ausschließlich Rekordstände, deren **aktuelle gültige kanonische Quelle** genau das ausgewählte `game_result` ist.

Verbindlich:

- nur Ergebnisrekorde mit `RecordSourceReference.GameResult` beziehungsweise fachlich gleichwertiger aktueller Quelle und exakt passender Ergebnis-ID,
- persönliche und serverweit individuelle Rekorde können gleichzeitig erscheinen,
- Serienrekorde werden keinem einzelnen Ergebnis künstlich zugerechnet,
- historisch einmal durch das Ergebnis ausgelöste, inzwischen gebrochene Rekorde erscheinen nicht,
- Record-Events und Discord-Rekordmeldungen ersetzen den aktuellen `record_state` nicht,
- kein Rekord-History-Scan oder Evaluator auf Interaction-Aufruf.

Pro Rekord werden mindestens Scope und verständliche Metrik genannt. Der Ergebniswert selbst muss nicht redundant vollständig wiederholt werden, wenn er unmittelbar oberhalb bereits sichtbar ist.

Fehlt ein passender aktueller Rekord, entfällt der gesamte Block.

---

## 11. Ergebnisdetails: Achievements des Spieltags

Unter

```text
🏅 An diesem Spieltag freigeschaltet
```

werden alle aktuell ACTIVE Achievement-Awards des ausgewählten Spielers angezeigt, deren fachliches `earnedOn` exakt dem Spieltag des betrachteten Ergebnisses entspricht.

Regeln:

- alle Scopes sind zulässig: `GRIDWORDS`, `QUADWORDS`, `CROSS_GAME`, `GLOBAL`,
- die Darstellung behauptet ausdrücklich nicht, das konkrete ausgewählte Ergebnis habe jedes Achievement allein verursacht,
- pro Achievement ausschließlich Emoji + Anzeigename,
- bestehende Custom-Emoji-Auflösung und Unicode-Fallback wiederverwenden,
- INVALIDATED Awards fehlen,
- stabile Katalogreihenfolge,
- kein History-Scan, Evaluator oder Reconciler im Interaction-Pfad.

Fehlen passende Awards, entfällt der gesamte Block.

---

## 12. Reihenfolge der Ergebnisdetails

Die Detailansicht verwendet grundsätzlich diese logische Reihenfolge:

1. Spieler, Ergebnis und Dauer,
2. GridWords-Grid beziehungsweise QuadWords-Boards/boardloser Hinweis,
3. gegebenenfalls gewählte Ausrede,
4. gegebenenfalls aktuelle Rekorde,
5. gegebenenfalls an diesem Spieltag freigeschaltete Achievements.

Leere optionale Bereiche werden nicht mit `Keine …`-Platzhaltern dargestellt.

Fehlt das Ergebnis selbst, bleibt der vorhandene eindeutige Missing-Result-Zustand erhalten; Zusatzbereiche werden dann nicht konstruiert.

---

## 13. `/status` als persönliches Dashboard

Der Root-Command `/status` bleibt:

- self-only,
- ohne Optionen oder Unterbefehle,
- vollständig ephemeral,
- strikt read-only.

Die Darstellung erhält diese logische Reihenfolge.

### 13.1 Heute

Je Spiel:

```text
✅ <n>/<max> · <Dauer>
❌ X/<max> · <Dauer>
⬜ noch nicht eingereicht
— keine Teilnahme
```

Maßgeblich sind dieselben kanonischen heutigen Ergebnis- und historischen Teilnahmefakten wie im Tagesstatus.

### 13.2 Laufende persönliche Serien

Alle fünf persönlichen Serien:

1. Aktivitätsserie,
2. Komplettserie,
3. GridWords-Lösungsserie,
4. QuadWords-Lösungsserie,
5. Perfektserie.

Die bestehende vorläufige Heute-Semantik wird exakt wiederverwendet. Nicht anwendbare Werte erscheinen als `—`, nicht als künstliche Null.

### 13.3 Teilnahme und Reminder

- aktueller GridWords-Teilnahmestatus und wirksamer Zeitraum,
- aktueller QuadWords-Teilnahmestatus und wirksamer Zeitraum,
- kompakter globaler Reminderstatus `an/aus`.

### 13.4 Letzte Einreichungen

Die bestehende Darstellung der letzten gültigen Einreichung je Spiel bleibt erhalten, einschließlich:

- Ergebnis,
- Dauer,
- Spieltag,
- tatsächlichem Empfangszeitpunkt.

Frühere Einreichungen bleiben sichtbar, wenn die aktuelle Teilnahme am betreffenden Spiel beendet ist.

`/status` erhält in Inkrement 14 keine Rekord- oder Achievement-Sektion.

---

## 14. Wochen- und Monatsberichte: Highlight-Sektion

Die bestehenden periodischen Berichte erhalten am Ende eine optionale Highlight-Sektion. Sie ergänzt den Bericht, ändert aber keine bestehenden Statistik-, Serien-, Snapshot-, Catch-up- oder Deliveryregeln.

Es gibt zwei getrennte Arten von Highlights.

### 14.1 Achievement-Zahlen je Spieler

Für jeden bereits im Bericht enthaltenen Spieler wird die Zahl seiner aktuell ACTIVE Awards bestimmt, deren fachliches `earnedOn` inklusiv innerhalb der Berichtsperiode liegt.

Beispiel:

```text
🏅 Achievements
Tobias: 3 Achievements freigeschaltet
Georgia: 2 Achievements freigeschaltet
```

Regeln:

- nur ACTIVE Awards,
- Periodenzuordnung ausschließlich über `earnedOn`,
- Spieler mit 0 passenden Awards erscheinen in dieser Sektion nicht,
- keine einzelnen Achievement-Namen, Beschreibungen, Kategorien oder Scopes,
- Reihenfolge entspricht der bestehenden Teilnehmerreihenfolge des Reports,
- keine Achievement-History-Auswertung oder Reconciliation nur für den Bericht.

### 14.2 Vollständige Rekord-Highlights

Quelle sind ausschließlich persistierte aktuell `VALID`e Record-Events mit grundsätzlich öffentlicher Verarbeitungsursache.

Als echte Rekordverbesserungen zählen für diese Sektion ausschließlich:

```text
RESULT_RECORD_BROKEN
SERIES_RECORD_CROSSED
RECORD_SERIES_FINISHED
```

Zusätzlich muss `processingOrigin.publicAnnouncementEligible()` wahr sein.

Nicht geeignet sind insbesondere:

```text
RECORD_INITIALIZED
SERIES_RECORD_TIED_AT_END
SERIES_RECORD_NEAR_MISSED_AT_END
RECORD_EVENT_INVALIDATED
```

sowie INVALIDATED/SUPERSEDED Events.

Die Konfiguration für unmittelbare Discord-Rekordmeldungen ist nicht die fachliche Quelle der Report-Highlights. Ein gültiges Rekordereignis darf im periodischen Bericht erscheinen, auch wenn unmittelbare Record-Announcements zu diesem Zeitpunkt global deaktiviert waren.

### 14.3 Fachliche Periodenzuordnung der Rekorde

- Ergebnisrekord → `gameDate` der neuen `GameResult`-Quelle,
- Serienrekord → `endDate` des `StreakRecordValue` des Events,
- technische `detectedAt`-/Persistenz-/Delivery-Zeitpunkte dürfen einen fachlich alten Rekord nicht in eine spätere Periode verschieben.

Das fachliche Ereignisdatum muss inklusiv innerhalb `period_start..period_end` liegen.

### 14.4 Deduplizierung von Serienrekorden innerhalb einer Periode

Die normale Record-Funktion darf denselben Serienlauf beim erstmaligen Übertreffen und bei seinem späteren Abschluss separat würdigen. Im kompakten periodischen Bericht soll dieselbe Record-State-Key-/StreakRun-Kombination innerhalb **derselben Periode** nicht doppelt erscheinen.

- Liegt ein gültiges `RECORD_SERIES_FINISHED` für dieselbe Definition/Scope/StreakRun-Quelle in der Periode vor, wird dieses als endgültiger Eintrag verwendet.
- Ein `SERIES_RECORD_CROSSED` derselben Kombination in derselben Periode wird dann nicht zusätzlich dargestellt.
- Existiert nur das Crossing, wird es dargestellt.
- Es gibt keine periodenübergreifende Unterdrückung: Crossing in einer Woche und Abschluss in der Folgewoche dürfen jeweils in ihrem eigenen Bericht erscheinen.

Mehrere echte Ergebnisrekordverbesserungen derselben Definition innerhalb einer Periode bleiben mehrere eigenständige Highlights, sofern die Events gültig sind.

### 14.5 Rekorddarstellung

Jeder verbleibende Rekord wird einzeln so vollständig beschrieben, dass `/records` zum Verständnis nicht erforderlich ist.

Mindestens:

- Spieler beziehungsweise `Gemeinsam`,
- Spiel bei Ergebnisrekorden,
- verständliche Metrik/Serienart,
- Scope,
- neuer beziehungsweise endgültiger Wert.

Beispiele:

```text
Georgia: neuer serverweiter QuadWords-Rekord · wenigste Versuche · 4 Versuche · 1:38
Tobias: neuer persönlicher GridWords-Bestzeitrekord · 0:42
Gemeinsam: neuer Perfektserien-Rekord · 8 Tage
```

Rekorde werden nicht künstlich auf eine Maximalzahl begrenzt. Discord-Grenzen werden über die vorhandene deterministische Report-Pagination/Field-Aufteilung eingehalten; stilles Kürzen ist unzulässig.

### 14.6 Leere Highlight-Sektion

- Nur Achievement-Zahlen vorhanden → nur Achievement-Unterbereich.
- Nur Rekorde vorhanden → nur Rekord-Unterbereich.
- Beides vorhanden → beide Unterbereiche.
- Weder noch → gesamte Highlight-Sektion entfällt.

Es gibt keine künstliche `0 Highlights`-Zeile.

---

## 15. Report-Snapshot und Korrekturen

Die bestehende periodische Snapshot-Semantik gilt unverändert auch für Highlights:

- Der erfolgreich veröffentlichte Bericht wird durch spätere Ergebnis-, Award- oder Record-Änderungen nicht automatisch editiert.
- Recreate/Reconciliation innerhalb des bestehenden Catch-up-Fensters darf aus dem dann aktuellen, weiterhin strikt auf `period_end` begrenzten Fachstand neu erzeugen.
- Highlights werden nicht als zweite Report-Fachwahrheit persistiert; nur die bestehende Delivery-/Fingerprint-Semantik bleibt bestehen.

Die Highlight-Sektion führt keine Rankings, Gewinnerlogik oder neuen direkten Leistungsvergleich ein. Sie gibt ausschließlich bereits vorhandene Achievement-Vergaben und gültige Rekordereignisse wieder.

---

## 16. Paketgrenzen

Inkrement 14 wird in folgende Pakete umgesetzt:

| Paket | Issue | Inhalt |
|---|---:|---|
| 14.1 | #106 | Tagesstatus-Dropdowns, Spiel-Links und angereicherte Ergebnisdetails |
| 14.2 | #107 | Teilnahmebestätigungen, Reminderstatus/-zeiten und `/status`-Dashboard |
| 14.3 | #108 | `earnedOn`, `/achievement-list`-Filter und `/records`-Scopefilter |
| 14.4 | #109 | Achievement-/Record-Highlights in periodischen Berichten |
| 14.5 | #110 | Gesamthärtung, reale Abnahme und Releasevorbereitung |

Kein Paket darf Inhalte eines späteren Pakets vorziehen, sofern dies nicht für einen kleinen gemeinsamen transportneutralen Vertrag zwingend erforderlich und im PR ausdrücklich begründet ist.

---

## 17. Ausdrückliche Nichtziele

Nicht Bestandteil von Inkrement 14 sind:

- Achievement-Progressbars oder quantitative Teilfortschritte,
- neue Achievement-Regeln,
- neue Rekordmetriken,
- Ranglisten/Leaderboards,
- Spieler-der-Woche- oder Gewinnerlogik,
- Trendvergleich gegenüber Vorwoche/Vormonat,
- Reminderstatus pro Spiel,
- veränderbare Reminderzeiten per Discord-Command,
- historische Rekordchronik in Ergebnisdetails,
- Kausalitätsanalyse „welches einzelne Ergebnis verursachte welches Achievement“,
- Achievement-Beschreibungen in Ergebnisdetails,
- manuelle Report-Commands,
- allgemeines Dashboard- oder Analytics-Subsystem,
- Components-V2-Migration der Tagesstatusnachricht.

---

## 18. Übergreifende Akzeptanz

Mindestens nachzuweisen sind:

1. Dropdownoptionen bilden `✅`, `❌`, `⬜` sowie Ergebnis/Dauer korrekt ab.
2. Spielbezogene Teilnehmermenge, Sortierung und Pagination bleiben unverändert.
3. Neue Tagesstatusnachrichten besitzen beide Link-Buttons; kein Sonderrefresh alter Nachrichten.
4. SELECTED-Ausrede erscheint exakt in Ergebnisdetails; andere Ausredenzustände nicht.
5. Ergebnisdetails zeigen nur aktuelle, vom konkreten Resultat gehaltene Ergebnisrekorde.
6. Ergebnisdetails zeigen alle aktiven Awards mit `earnedOn = gameDate` als Emoji + Name.
7. Leere optionale Detailblöcke entfallen vollständig.
8. Teilnahmebestätigungen unterscheiden heutige und künftig wirksame Zustände eindeutig.
9. Reminderstatus erklärt Opt-out korrekt und zeigt tatsächliche konfigurierte Zeiten.
10. `/status` zeigt zuerst heute, danach alle fünf Serien, Teilnahme/Reminder und letzte Einreichungen.
11. `/achievements` zeigt das fachliche `earnedOn`.
12. `/achievement-list` filtert exakt und kombinierbar nach game/category/status; Global nur unter Alle.
13. `/records` filtert exakt und kombinierbar nach Scope.
14. Achievement-Reporthighlights werden je Spieler ausschließlich als Anzahl dargestellt.
15. Rekord-Reporthighlights nennen alle geeigneten gültigen Rekordverbesserungen vollständig.
16. Gleichstände/Near Misses/stille Origins erscheinen nicht als Report-Rekordhighlight.
17. Crossing+Finish derselben Serienrekordquelle wird innerhalb derselben Periode nicht doppelt dargestellt.
18. Alle neuen Read-Pfade bleiben ohne fachliche Writes, History-Scans oder Reconciliation.
19. Discord-Grenzen, Pagination, Mention-Sicherheit und bestehende Recoverypfade bleiben regressionsfrei.
20. Standardbuild, PostgreSQL-Integration und finaler Migration-/Upgrade-Gate sind grün.
