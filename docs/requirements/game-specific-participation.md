# Verbindliches Modell für spielbezogene Teilnahme

**Status:** fachlich abgenommen  
**Stand:** 3. August 2026  
**Gültig ab:** Zwischeninkrement 10.6  
**Verbindliches Issue:** #39

Dieses Dokument erweitert und präzisiert das dynamische Spielermodell, das Serienmodell, Tagesstatus und Reminder, die interaktiven Ergebnisdetails sowie Wochen- und Monatsberichte. Bei Widersprüchen zu älteren Aussagen über einen einzigen globalen Teilnahmezeitraum, eine für beide Spiele identische Teilnehmermenge oder einen gemeinsamen Nenner für GridWords und QuadWords gilt ab Zwischeninkrement 10.6 dieses Dokument.

Betroffen sind insbesondere:

- `docs/requirements/dynamic-player-model.md`,
- `docs/requirements/series-model.md`,
- `docs/requirements/daily-status-reminders.md`,
- `docs/requirements/periodic-reports.md`,
- `docs/increments/10.5-interactive-result-details.md`.

## 1. Ziel und Grundmodell

Ein Spieler kann unabhängig an GridWords, QuadWords, beiden Spielen oder keinem Spiel teilnehmen.

Für jeden Spieler, Spieltyp und Kalendertag wird die Teilnahme ausschließlich aus historisch stabilen spielbezogenen Teilnahmezeiträumen abgeleitet. Es gibt keine feste globale Teilnehmermenge mehr, die ungeprüft für beide Spiele verwendet werden darf.

Für einen Spieltag `d` gelten folgende Mengen:

```text
G(d) = alle an GridWords teilnehmenden Spieler
Q(d) = alle an QuadWords teilnehmenden Spieler
U(d) = G(d) ∪ Q(d)
B(d) = G(d) ∩ Q(d)
```

Bedeutung:

- `G(d)` ist die GridWords-Teilnehmermenge.
- `Q(d)` ist die QuadWords-Teilnehmermenge.
- `U(d)` enthält alle Spieler, die an mindestens einem Spiel teilnehmen.
- `B(d)` enthält ausschließlich Zwei-Spiele-Teilnehmer.

Diese Mengen werden für jeden historischen Tag neu aus den damals wirksamen Zeiträumen bestimmt. Aktuelle Profilflags dürfen historische Berechnungen nicht ersetzen.

## 2. Spielerprofil und globaler Aktivstatus

Das Spielerprofil bleibt eindeutig durch die Discord-User-ID identifiziert und enthält weiterhin mindestens:

- Discord-User-ID,
- serverbezogenen Anzeigenamen,
- Administratorstatus,
- globalen Reminderstatus,
- abgeleiteten aktuellen Aktivstatus,
- Erstellungs- und Änderungszeitpunkt.

`player.active` bleibt aus Kompatibilitätsgründen erhalten, ist aber keine eigenständige fachliche Teilnahmequelle. Es gilt:

```text
player.active = heute in Europe/Berlin Mitglied von U(heute)
```

Folgen:

- GridWords-only und QuadWords-only sind beide global aktiv.
- Nur wenn heute weder GridWords- noch QuadWords-Teilnahme besteht, ist das Profil global inaktiv.
- Serien, Status, Reminder, Ergebnisdetails und Berichte dürfen ihre Teilnehmermengen nicht aus `player.active` ableiten.
- Historische Aussagen werden ausschließlich aus spielbezogenen Teilnahmezeiträumen berechnet.

## 3. Spielbezogene Teilnahmezeiträume

Ein Teilnahmezeitraum enthält mindestens:

```text
player_id
game_type
active_from
inactive_from
```

Semantik:

```text
active_from    inklusiv
inactive_from  exklusiv, NULL bei laufender Teilnahme
```

Ein Spieler nimmt an einem Spieltag an einem Spiel teil, wenn:

```text
period.player_id = player
und period.game_type = spiel
und active_from <= spieltag
und (inactive_from IS NULL oder spieltag < inactive_from)
```

Invarianten:

- höchstens ein offener Zeitraum je Spieler und Spieltyp,
- keine überlappenden Zeiträume je Spieler und Spieltyp,
- Zeiträume verschiedener Spieltypen dürfen beliebig überlappen,
- Aktivierung und Deaktivierung sind je ausgewähltem Spiel idempotent,
- frühere abgeschlossene Zeiträume werden nicht nachträglich verändert,
- ein Wiedereintritt erzeugt für das betreffende Spiel einen neuen Zeitraum,
- ein Wechsel bei einem Spiel verändert niemals den Zeitraum des anderen Spiels.

Ein transportneutraler Domänentyp soll die Spielzuordnung ausdrücklich tragen, bevorzugt beispielsweise:

```text
GameParticipationPeriod(
    long playerId,
    GameType gameType,
    LocalDate activeFrom,
    LocalDate inactiveFrom
)
```

Ein spielunspezifischer `ParticipationPeriod` darf nach Abschluss der Migration nicht mehr die fachliche Grundlage neuer Berechnungen sein.

## 4. Aktivierung durch gültige Shares

Ein vollständig gültiges Share aktiviert ausschließlich den Spieltyp des Shares ab dessen fachlichem `game_date`, sofern dort noch keine Teilnahme besteht.

Beispiele:

- gültiges GridWords-Share → GridWords-Teilnahme,
- gültiges QuadWords-Share → QuadWords-Teilnahme,
- späteres Share des anderen Spiels → zusätzliche Teilnahme an diesem Spiel,
- Vortagsnachtrag → Aktivierung nur dieses Spiels ab gestern.

Unverändert gelten:

- nur heute und gestern sind als automatische Ergebnistage zulässig,
- ein ungültiges oder technisch noch nicht erfolgreich verarbeitetes Share aktiviert nichts,
- Profil, spielbezogene Aktivierung, Reminder-Reaktivierung, Ergebnis und Submission werden atomar gespeichert,
- ein Replay desselben Ergebnisses erzeugt keinen zusätzlichen Zeitraum,
- die Aktivierung eines Spiels deaktiviert das andere Spiel niemals.

### 4.1 Reminder-Reaktivierung

Der Reminderstatus bleibt global.

Er wird bei einer Aktivierung nur dann automatisch auf `true` gesetzt, wenn der Spieler dadurch nach tatsächlicher Gesamtinaktivität wieder Mitglied von `U(heute)` wird beziehungsweise ein unbekannter Spieler erstmals aktiv wird.

Insbesondere:

- erstes aktives Spiel nach Gesamtinaktivität → Reminder standardmäßig an,
- Hinzufügen des zweiten Spiels bei bereits global aktiver Teilnahme → vorhandenen Reminderstatus bewahren,
- ein bereits aktiver Opt-out darf durch ein weiteres gültiges Share nicht zurückgesetzt werden,
- Deaktivierung nur eines von zwei Spielen verändert den Reminderstatus nicht,
- nach vollständiger Inaktivität setzt ein späterer Wiedereintritt die Reminder wieder auf an.

## 5. Self-Service- und Admin-Commands

Die Teilnahmecommands erhalten eine optionale Spielauswahl:

```text
/participation join game:<gridwords|quadwords|both>
/participation leave game:<gridwords|quadwords|both>

/player activate user:<Discord-User> game:<gridwords|quadwords|both>
/player deactivate user:<Discord-User> game:<gridwords|quadwords|both>
/player status user:<Discord-User>
```

Regeln:

- `game` ist optional; ohne Angabe gilt aus Kompatibilitätsgründen `both`.
- `join` beziehungsweise `activate` wirkt ab dem aktuellen Berlin-Tag.
- `leave` beziehungsweise `deactivate` wirkt prospektiv ab dem folgenden Berlin-Tag.
- `both` verändert GridWords und QuadWords in einer atomaren Operation.
- Ein Fehler bei einem der beiden ausgewählten Spiele rollt die gesamte `both`-Änderung zurück.
- Bereits bestehende passende Zustände machen die Operation nicht fehlerhaft.
- Eine Änderung eines Spiels darf keinen Zeitraum des anderen Spiels verkürzen, erweitern oder neu eröffnen.
- Admin-Autorisierung und Anzeigenamensynchronisierung bleiben unverändert.
- Teilnahmeänderungen aktualisieren einen bereits vorhandenen heutigen Tagesstatus, erzeugen ihn aber nicht vorzeitig.

Der parameterlose Root-Command `/status` bleibt Self-Service. Er zeigt künftig getrennt:

- aktuellen GridWords-Teilnahmestatus und wirksamen Zeitraum,
- aktuellen QuadWords-Teilnahmestatus und wirksamen Zeitraum,
- globalen Reminderstatus,
- letzte gültige GridWords-Einreichung,
- letzte gültige QuadWords-Einreichung.

Frühere Ergebnisse bleiben sichtbar, auch wenn die aktuelle Teilnahme am betreffenden Spiel beendet ist.

## 6. Reminder

Der globale Reminderstatus gilt für alle Spiele, an denen der Spieler am betreffenden Tag teilnimmt.

Ein Spieler ist für ein Spiel Reminderkandidat, wenn:

```text
Spieler ∈ Teilnehmermenge dieses Spiels am Spieltag
und für dieses Spiel liegt kein gültiges Ergebnis vor
```

Daraus folgt:

- GridWords-only erscheint niemals in der QuadWords-Zeile.
- QuadWords-only erscheint niemals in der GridWords-Zeile.
- Ein nicht teilgenommenes Spiel gilt nicht als fehlend.
- Ein gültiges `X/6` beziehungsweise `X/9` gilt weiterhin als eingereicht.
- Opt-in steuert echte ID-basierte Mention.
- Opt-out steuert entschärften Klartextnamen.
- Die Spielzeile entfällt, wenn diesem Spiel bei keinem Teilnehmer ein Ergebnis fehlt.
- Sind für beide Spiele keine Kandidaten vorhanden, wird die Delivery persistent als No-op abgeschlossen.
- Audience und fehlende Spiele werden bei jedem tatsächlichen Sendeversuch neu gelesen.

Ein separater Reminderstatus pro Spiel ist nicht Bestandteil von Zwischeninkrement 10.6.

## 7. Serien und Tagesmerkmale

Alle Serien bleiben Kalendersequenzen. Nicht anwendbare Tage werden nicht übersprungen und pausieren keine Serie, sondern bilden eine Grenze. Die vorläufige Heute-Semantik gilt nur für Bedingungen, für die der Spieler beziehungsweise die gemeinsame Gruppe heute teilnahmeberechtigt ist.

### 7.1 Persönliche Aktivitätsserie

Ein Tag ist für einen Spieler ein Aktivitätstag, wenn:

```text
Spieler ∈ U(d)
und mindestens ein gültiges Ergebnis für ein an diesem Tag teilgenommenes Spiel vorliegt
```

Ein Tag ohne Teilnahme an irgendeinem Spiel ist nicht anwendbar und beendet eine bis zum Vortag laufende Aktivitätsserie. Ein gültiges Share aktiviert das betreffende Spiel für den Ergebnistag, sodass das Ergebnis nicht außerhalb seiner Teilnahme liegt.

### 7.2 Persönliche spielbezogene Lösungsserien

Für GridWords gilt:

```text
Spieler ∈ G(d)
und GridWords wurde gelöst
```

Für QuadWords analog mit `Q(d)`.

Regeln:

- Ein nicht gelöstes Ergebnis beendet die betreffende Serie sofort.
- Ein heute noch fehlendes Ergebnis ist vorläufig.
- Ein historisch fehlendes Ergebnis beendet die Serie.
- Ein Tag ohne Teilnahme an diesem Spiel ist nicht anwendbar und bildet eine Seriengrenze.
- Eine Serie darf weder einen Austritt noch eine Lücke zwischen Teilnahmezeiträumen überbrücken.
- Das jeweils andere Spiel hat keinen Einfluss.

### 7.3 Persönliche Komplett- und Perfektmetriken

Komplett und perfekt bleiben echte Zwei-Spiele-Metriken.

Ein persönlicher kompletter Tag liegt nur vor, wenn:

```text
Spieler ∈ B(d)
und GridWords und QuadWords gültig eingereicht wurden
```

Ein persönlicher perfekter Tag liegt nur vor, wenn:

```text
Spieler ∈ B(d)
und beide Spiele gelöst wurden
```

Folgen:

- Single-Game-Tage sind nicht anwendbar, nicht „fehlgeschlagen“.
- In Statusdarstellungen erscheint für Komplett und Perfekt an Single-Game-Tagen `—` statt eines künstlichen Nullwerts.
- Für Serien bildet ein Single-Game-Tag dennoch eine Kalendergrenze; die Serie pausiert nicht.
- Ein späterer Wechsel zu beiden Spielen beginnt eine neue mögliche Komplett- beziehungsweise Perfektsequenz.
- Nicht gelöste Ergebnisse beenden eine anwendbare Perfektserie sofort.

### 7.4 Gemeinsame spielbezogene Lösungsserien

Die gemeinsame GridWords-Lösungsbedingung verwendet ausschließlich `G(d)`:

```text
|G(d)| >= 2
und jeder Spieler in G(d) hat GridWords gelöst
```

Die gemeinsame QuadWords-Lösungsbedingung verwendet ausschließlich `Q(d)`:

```text
|Q(d)| >= 2
und jeder Spieler in Q(d) hat QuadWords gelöst
```

Single-Game-Spieler beeinflussen damit nur die gemeinsame Lösungsserie ihres Spiels.

### 7.5 Gemeinsame Komplett- und Perfektserien

Gemeinsame Komplett- und Perfektmetriken verwenden ausschließlich `B(d)`.

Gemeinsam komplett:

```text
|B(d)| >= 2
und jeder Spieler in B(d) hat beide Spiele eingereicht
```

Gemeinsam perfekt:

```text
|B(d)| >= 2
und jeder Spieler in B(d) hat beide Spiele gelöst
```

Spieler außerhalb von `B(d)` werden vollständig ignoriert. Dadurch können insbesondere ein GridWords-only- und ein QuadWords-only-Spieler zusammen keinen gemeinsamen Komplett- oder Perfekttag erzeugen.

Es gibt weiterhin keine gemeinsame Aktivitätsserie.

## 8. Kanonische Ergebnisnachrichten und PublicationContext

Kanonische Ergebnisnachrichten zeigen weiterhin mindestens:

- persönliche Aktivitätsserie,
- persönliche Lösungsserie des gerade eingereichten Spiels.

Komplett- und Perfekthinweise dürfen nur entstehen, wenn der Spieler am Ergebnistag Mitglied von `B(d)` ist und das neue Ergebnis die jeweilige Zwei-Spiele-Bedingung tatsächlich herstellt.

Für gemeinsame Komplett- und Perfektübergänge gilt dieselbe Beschränkung auf `B(d)` mit mindestens zwei Zwei-Spiele-Teilnehmern.

Der persistierte `PublicationContext` bleibt ein historischer Auslöserkontext und muss mit der spielbezogenen Teilnahme des Ergebnistags berechnet werden. Korrekturen, Replays, Vortagsnachträge und Reconciliation dürfen einen Übergang nicht doppelt etablieren.

Die sichere Publish-/Edit-/Delete-Zustandsmaschine bleibt unverändert.

## 9. Tagesstatus

Der Tagesstatus enthält genau die Spieler aus `U(d)`, weiterhin deterministisch sortiert nach Anzeigename und Discord-User-ID.

Pro Spieler und Spiel wird unterschieden:

- teilgenommen und gelöst,
- teilgenommen und nicht gelöst,
- teilgenommen und noch nicht eingereicht,
- nicht teilgenommen.

Empfohlene Darstellung:

```text
GridWords: ✅ 4/6 · 3:42
QuadWords: — nimmt nicht teil
```

`⬜ noch nicht eingereicht` darf nur für ein Spiel erscheinen, an dem der Spieler teilnimmt.

Serienwerte pro Spieler:

- Aktivität ist für Mitglieder von `U(d)` anwendbar.
- GridWords gelöst ist nur für Mitglieder von `G(d)` anwendbar.
- QuadWords gelöst ist nur für Mitglieder von `Q(d)` anwendbar.
- Komplett und perfekt sind nur für Mitglieder von `B(d)` anwendbar.
- Nicht anwendbare persönliche Werte werden sichtbar als `—` dargestellt.

Die vier gemeinsamen Serienwerte werden nach den Mengen aus Abschnitt 7 berechnet.

Teilnahmeänderungen, Shares und Korrekturen müssen den Statusfingerprint beeinflussen, wenn sich dadurch eine spielbezogene Teilnehmermenge, Anwendbarkeit, Darstellung oder Menüoption ändert.

## 10. Interaktive Ergebnisdetails

Die Auswahlmenüs verwenden nicht mehr dieselbe globale Optionsmenge.

- GridWords-Menüs enthalten ausschließlich `G(d)`.
- QuadWords-Menüs enthalten ausschließlich `Q(d)`.
- Spieler ohne Ergebnis bleiben im Menü ihres teilgenommenen Spiels auswählbar.
- Ein Spiel ohne Teilnehmer erhält kein leeres Auswahlmenü.
- Die bestehende Seitengröße von 25 bleibt.
- Die bestehende Grenze von höchstens zwei Seiten gilt je Spieltyp, also höchstens 50 Teilnehmer pro Spiel.
- Die vollständige Tagesnachricht unterliegt zusätzlich weiterhin allen Discord-Feld-, Embed-, Action-Row- und Gesamtzeichenlimits.
- Niemand darf bei einer Grenzüberschreitung stillschweigend entfallen.

Die serverseitige Interaction-Validierung prüft zusätzlich, dass der Zielspieler am dargestellten Tag am ausgewählten Spiel teilnahm. Eine Teilnahme nur am anderen Spiel reicht nicht aus.

Component-ID und Optionswert bleiben unverändert:

```text
daily-result:v1:<yyyy-MM-dd>:<g|q>:<pageIndex>
user:<discordUserId>
```

Eine neue persistente Session- oder Componenttabelle ist weiterhin nicht vorgesehen.

## 11. Wochen- und Monatsberichte

Ein Spieler erscheint im Bericht, wenn innerhalb der Periode mindestens ein Teilnahmetag in `U(d)` existiert.

Pro Spieler werden mindestens getrennt abgeleitet:

- Teilnahmetage an mindestens einem Spiel: `|U|`,
- GridWords-Teilnahmetage,
- QuadWords-Teilnahmetage,
- Zwei-Spiele-Teilnahmetage: `|B|`,
- Aktivitätstage,
- komplette Tage,
- perfekte Tage.

Die Spielstatistik verwendet ausschließlich ihren eigenen Nenner:

```text
GridWords fehlend
= GridWords-Teilnahmetage - gültige GridWords-Einreichungen

QuadWords fehlend
= QuadWords-Teilnahmetage - gültige QuadWords-Einreichungen
```

Bei null Teilnahmetagen für ein Spiel gilt:

- `possibleDays = 0`,
- `submitted = 0`,
- `missing = 0`,
- Lösungsquote und Durchschnitte sind nicht definiert,
- der Renderer zeigt eindeutig `Nicht teilgenommen` beziehungsweise einen neutralen gleichwertigen Text,
- es wird keine künstliche Fehlmenge erzeugt.

Tagesmerkmale:

- Aktivitätstage werden nur innerhalb der Union-Teilnahmetage gezählt.
- Komplette und perfekte Tage werden nur innerhalb der Zwei-Spiele-Teilnahmetage gezählt.
- Single-Game-Tage erhöhen weder komplette noch perfekte Tage und gehören nicht zu deren fachlichem Nenner.

Persönliche Serien und Rekorde folgen Abschnitt 7. Ein Single-Game-Tag darf einen Komplett- oder Perfektrekord nicht verbinden.

Für die bereits vorhandenen gemeinsamen Reportwerte gilt:

```text
gemeinsam möglicher Komplett-/Perfekttag
= mindestens zwei Spieler in B(d)
```

Nur diese Zwei-Spiele-Teilnehmer müssen komplett beziehungsweise perfekt sein. Single-Game-Spieler werden für diese Reportwerte ignoriert.

Die gemeinsamen GridWords- und QuadWords-Lösungsserien werden durch Zwischeninkrement 10.6 nicht zusätzlich in Wochen- oder Monatsberichte aufgenommen.

Erfolgreich veröffentlichte Berichte bleiben unveränderte Snapshots.

## 12. Persistenz und Migration

Die bestehende Persistenz wird über Liquibase auf spielbezogene Zeiträume migriert.

Das Zielschema besitzt mindestens:

- `player_id BIGINT NOT NULL`,
- `game_type VARCHAR(...) NOT NULL`,
- `active_from DATE NOT NULL`,
- `inactive_from DATE NULL`,
- Zeitstempel,
- Bounds-Check,
- Fremdschlüssel zum Spieler.

PostgreSQL sichert:

- erlaubte Spieltypen,
- höchstens einen offenen Zeitraum je `(player_id, game_type)`,
- keine Überlappung je `(player_id, game_type)` über einen Exclusion Constraint,
- parallele Aktivierungen und `both`-Operationen konfliktfest.

### 12.1 Backfill

Jeder vor der Migration bestehende globale Teilnahmezeitraum wird exakt dupliziert:

```text
alter Zeitraum → identischer GridWords-Zeitraum
alter Zeitraum → identischer QuadWords-Zeitraum
```

Dabei gelten:

- Datumsgrenzen bleiben unverändert.
- Bestehende Ergebnisse, Submissions, Boards, Parser-Versionen und Delivery-Zustände bleiben unverändert.
- Interne Zeitraum-IDs müssen nicht fachlich stabil bleiben.
- Es wird nicht aus vorhandenen Ergebnissen geraten, an welchem Spiel ein Spieler historisch teilnehmen wollte.
- Nach der Migration müssen alle bisherigen Status-, Serien- und Reportwerte für bestehende Daten fachlich identisch sein.
- `player.active` wird aus den migrierten aktuellen Zeiträumen reconciled.

Eine Migration, die bestehende Spieler anhand fehlender Ergebnisse rückwirkend zu Single-Game-Spielern erklärt, ist verboten.

## 13. Transaktionen und Konkurrenz

Mindestens atomar auszuführen sind:

- Aktivierung eines ausgewählten Spiels,
- Deaktivierung eines ausgewählten Spiels,
- `both`-Änderung beider Spiele,
- Aktivierung durch Ergebnis zusammen mit Ergebnis- und Submissionspeicherung,
- globale Reminder-Reaktivierung beim Übergang aus vollständiger Inaktivität.

Abzudeckende Konkurrenzfälle:

- paralleler erster GridWords- und QuadWords-Share desselben Nutzers,
- paralleles `join both` und gültiges Share,
- parallele Aktivierungen desselben Spiels,
- Deaktivierung ab morgen bei gleichzeitigem heutigem Share,
- Vortagsnachtrag neben heutiger Aktivierung,
- Fehler beim zweiten Teil einer `both`-Operation,
- konkurrierter Wiedereintritt nach Gesamtinaktivität.

Discord-I/O bleibt außerhalb von Datenbanktransaktionen.

## 14. Verbindliche Testmatrix

Mindestens automatisiert abzudecken sind:

1. Migration dupliziert jeden alten Zeitraum exakt für beide Spiele.
2. Migration verändert historische Status-, Serien- und Reportwerte nicht.
3. GridWords-only und QuadWords-only funktionieren symmetrisch.
4. Gültiges GridWords-Share aktiviert nur GridWords.
5. Gültiges QuadWords-Share aktiviert nur QuadWords.
6. Ein späteres Share des anderen Spiels ergänzt die Teilnahme.
7. Ein Replay erzeugt keinen weiteren Zeitraum.
8. `join`, `leave`, Admin-Aktivierung und Admin-Deaktivierung unterstützen alle drei Auswahlen.
9. Fehlende Spielauswahl entspricht `both`.
10. `both` ist atomar und idempotent.
11. Hinzufügen des zweiten Spiels bewahrt einen aktiven Reminder-Opt-out.
12. Wiedereintritt nach vollständiger Inaktivität schaltet Reminder wieder ein.
13. Single-Game-Spieler erhält keinen Reminder für das andere Spiel.
14. Reminder-Audience bleibt für Opt-in und Opt-out korrekt.
15. Gemeinsame GridWords-Serie verwendet nur `G(d)`.
16. Gemeinsame QuadWords-Serie verwendet nur `Q(d)`.
17. Gemeinsame Komplett-/Perfektserie verwendet nur `B(d)`.
18. Weniger als zwei Spieler in der jeweils relevanten Menge erzeugen keine gemeinsame Serie.
19. Ein nicht teilgenommener Tag überbrückt keine persönliche Serie.
20. Single-Game-Tage zeigen Komplett und Perfekt als nicht anwendbar.
21. Tagesstatus unterscheidet fehlend von nicht teilgenommen.
22. GridWords- und QuadWords-Menüs besitzen unterschiedliche Optionsmengen.
23. Manipulierte Detailabfrage für das nicht teilgenommene Spiel wird abgelehnt.
24. Fingerprint, Create, Edit, Recreate und Restart-Reconciliation berücksichtigen spielbezogene Mengen.
25. Berichte verwenden getrennte Spielnenner.
26. Null Teilnahmetage eines Spiels erzeugen keine Fehlmenge.
27. Komplette und perfekte Reporttage verwenden nur Zwei-Spiele-Teilnahmetage.
28. Teilnahmewechsel innerhalb einer Woche oder eines Monats wird taggenau berücksichtigt.
29. Vortagsnachtrag rekonstruiert die richtigen Mengen und Serien.
30. DST- und Tagesgrenzen verwenden weiterhin `Europe/Berlin`.
31. Parallelitäts- und Constraintfälle laufen gegen echtes PostgreSQL.
32. Bestehende Parser-, Publish-, Delete-, Cleanup- und Report-Deliverypfade bleiben regressionsfrei.

## 15. Nicht Bestandteil

Nicht Bestandteil von Zwischeninkrement 10.6 sind:

- ein Reminderstatus pro Spiel,
- beliebige weitere Spiele oder ein generisches Spiele-Pluginmodell,
- mehrere Guilds oder Channels,
- historische Teilnahmebearbeitung per Discord-Command,
- automatische Deaktivierung beim Serveraustritt,
- Aufnahme der gemeinsamen spielbezogenen Lösungsserien in Berichte,
- allgemeine Statistik- oder Konfigurations-Commands aus Inkrement 11,
- regelbasierte Kommentare aus Inkrement 12,
- Änderungen an Parserformaten, Boarddarstellung oder sicherer Nachrichtenersetzung,
- eine neue persistente Component-Session für Ergebnisdetails.
