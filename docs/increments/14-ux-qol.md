# Inkrement 14 – UX- und QoL-Stärkung

**Status:** fachlicher Lieferumfang 14.1–14.4 integriert; technisches Abschlusspaket 14.5 automatisiert abgenommen, realer Discord-/Report-Smoke ausstehend  
**Stand:** 9. August 2026  
**Umbrella-Issue:** #105  
**Sammelbranch:** `feature/14-ux-qol`  
**Fachliche Grundlage:** [`../requirements/ux-qol.md`](../requirements/ux-qol.md)  
**Geplantes Release:** 1.5.0

## 1. Ziel

Inkrement 14 verbessert die Benutzerführung und Sichtbarkeit bereits vorhandener Bot-Funktionen. Es führt kein neues großes Fachsystem ein. Stattdessen werden Tagesstatus, Ergebnisdetails, Teilnahme-/Reminder-Antworten, Achievement-/Record-Commands, persönlicher Status und periodische Berichte gezielt erweitert.

Das Inkrement baut vollständig auf den produktiven Ständen der Inkremente 10–13 auf. Bestehende Fachlogik wird wiederverwendet; neue Anzeigeinformationen werden aus vorhandenen kanonischen Daten oder materialisierten Fachprojektionen gelesen.

## 2. Verbindlicher Umfang

Inkrement 14 umfasst:

- Statussymbol und Kurzinfo in den Ergebnis-Dropdowns der Tagesstatusnachricht,
- direkte Link-Buttons zu GridWords und QuadWords,
- gewählte Ausrede, aktuelle Ergebnisrekorde und Spieltag-Achievements in ephemeren Ergebnisdetails,
- verständlichere Teilnahmebestätigungen,
- klareren Reminderstatus einschließlich konfigurierter Zeiten,
- `/status` als persönliches Dashboard mit heutigem Zustand und allen fünf persönlichen Serien,
- `earnedOn` in `/achievements`,
- kombinierbare Filter für `/achievement-list`,
- Scope-Filter für `/records`,
- Achievement-Zahlen und vollständige Rekord-Highlights in Wochen-/Monatsberichten,
- End-to-End-Härtung und reale Discord-Abnahme der geänderten Pfade.

## 3. Architekturgrenzen

Für alle Pakete gilt:

1. `docs/requirements/ux-qol.md` ist der verbindliche Inkrement-14-Nachtrag.
2. Bestehende Spiel-, Teilnahme-, Serien-, Ausreden-, Rekord- und Achievement-Semantik wird nicht dupliziert oder neu interpretiert.
3. Read-only Commands/Interactions dürfen keine Player-, Award-, Record-, Excuse- oder Delivery-Writes auslösen.
4. Read-Views verwenden vorhandene materialisierte Fachprojektionen beziehungsweise schmale read-only Ports; kein History-Scan oder Evaluator/Reconciler nur für eine Anzeige.
5. JDA bleibt am Adapterrand; Application/Domain kennen keine JDA-Typen.
6. Discord-I/O findet niemals innerhalb einer Datenbanktransaktion statt.
7. Keine Discord-/Announcement-Texte als fachliche Datenquelle.
8. Keine neue Persistenzwahrheit; eine Liquibase-Migration ist nicht vorgesehen.
9. Falls ein Paket wider Erwarten eine Schemaänderung für notwendig hält, ist dies vor Umsetzung als konkrete Lücke nachzuweisen und darf nicht nur der Bequemlichkeit eines Read-Modells dienen.
10. Vorhandene Paging-, Mention-, Idempotenz-, Retry-, Recovery- und historische Teilnahmegrenzen bleiben verbindlich.
11. Keine generischen UI-, Query-, Dashboard-, Gamification-, Event- oder Messaging-Frameworks.
12. Keine Inhalte späterer Pakete vorziehen.

## 4. Branch- und PR-Modell

- Sammelbranch: `feature/14-ux-qol`.
- Pakete 14.1–14.4 wurden auf eigenen Arbeitsbranches umgesetzt und jeweils nach Review in den Sammelbranch gesquasht.
- Paket 14.5 arbeitet auf dem kumulierten Stand nach 14.4.
- Der **technische** 14.5-Hardening-/Dokumentationsstand darf nach vollständig grünen automatisierten Gates in `feature/14-ux-qol` integriert werden.
- Dieser technische Merge schließt Issue #110 ausdrücklich noch nicht: reale Discord-/Report-Abnahme bleibt dessen letzter manueller Nachweis.
- Gesamt-PR #111 gegen `main` bleibt bis zum dokumentierten realen Smoke im Draft.
- Release Candidate, Tag, GHCR-Publish und produktiver Rollout erfolgen erst nach vollständiger Abnahme, Merge nach `main` und separater Release-/Deploymentfreigabe.

## 5. Paketübersicht

| Paket | Issue | Branch | Inhalt | Stand |
|---|---:|---|---|---|
| 14.1 | #106 | `feature/14-1-daily-status-details-ux` | Tagesstatus-Dropdowns, Spiel-Link-Buttons, angereicherte Ergebnisdetails | ✅ PR #112 / Merge `cb667a72…` |
| 14.2 | #107 | `feature/14-2-personal-command-ux` | Teilnahmebestätigungen, Reminderstatus/-zeiten, `/status`-Dashboard | ✅ PR #113 / Merge `ee4a5e8b…` |
| 14.3 | #108 | `feature/14-3-read-command-ux` | `earnedOn`, `/achievement-list`-Filter, `/records`-Scopefilter | ✅ PR #114 / Merge `949115aa…` |
| 14.4 | #109 | `feature/14-4-report-highlights` | Achievement-/Record-Highlights in periodischen Berichten | ✅ PR #115 / Merge `d4bf877a…` |
| 14.5 | #110 | `feature/14-5-ux-qol-hardening` | Gesamthärtung, reale Abnahme und Releasevorbereitung | ✅ technische Gates in PR #117; ⬜ realer Smoke |

---

# Paket 14.1 – Tagesstatus und Ergebnisdetails UX

**Status:** abgeschlossen und in den Sammelbranch integriert  
**Issue:** #106  
**PR:** #112  
**Squash-Merge:** `cb667a72cdf6fa7aabf0056938a00bf37a2b0071`  
**Arbeitsbranch:** `feature/14-1-daily-status-details-ux`  
**PR-Ziel:** `feature/14-ux-qol`

## Ziel

Die Tagesstatusnachricht wird als zentraler Einstiegspunkt gestärkt. Nutzer erkennen in den Select-Menüs bereits den Ergebnisstatus, können beide Spiele direkt öffnen und erhalten in den ephemeren Ergebnisdetails vorhandenen Zusatzkontext aus Ausreden, Rekorden und Achievements.

## Lieferumfang

### Informativere Select-Optionen

- Statussymbol `✅`, `❌` oder `⬜` im Label,
- Description mit `n/max · Dauer`, `X/max · Dauer` oder `Noch nicht eingereicht`,
- bestehende spielbezogene Teilnehmermenge, Sortierung und Pagination unverändert,
- Präsentationsinformationen werden aus dem transportneutral vorhandenen Tagesstatus abgeleitet, ohne den persistierten Fingerprint historischer Nachrichten allein durch den Deploymentwechsel zu verändern.

### Spiel-Link-Buttons

- `🟩 GridWords spielen`,
- `🟦 QuadWords spielen`,
- dieselben URLs wie in Remindern,
- reine Link-Buttons ohne Interaction/State,
- neue Tagesstatusnachrichten immer mit Buttons,
- bestehende heutige Nachricht nur bei ohnehin erfolgendem normalem Edit/Recreate,
- kein Sonderrefresh historischer oder unveränderter Nachrichten.

### Ergebnisdetails

Zusätzlich zur vorhandenen Ergebnis-/Boarddarstellung:

- SELECTED-Ausrede: exakt persistierter Text,
- aktuelle Rekorde: nur aktueller `record_state`, dessen GameResult-Quelle exakt die ausgewählte Ergebnis-ID **und aktuelle Ergebnisversion** verwendet,
- aktive Record-Definitionsversion wird berücksichtigt,
- Achievements: alle aktuell ACTIVE Awards des Spielers mit `earnedOn == gameDate`, nur Emoji + Anzeigename,
- optionale leere Blöcke vollständig auslassen.

## Automatisierte Nachweise

Insbesondere:

- `DailyStatusComponentRendererTest`,
- `JdaDailyStatusComponentsTest`,
- `DailyStatusFingerprintTest`,
- `DailyResultDetailsServiceTest`,
- `DailyResultDetailsInteractionListenerTest`,
- `DailyResultDetailsEmbedRendererTest`,
- `PostgresDailyResultDetailsQueryIT`.

Standardbuild, PostgreSQL-Integration und Migration/Upgrade waren auf dem finalen Reviewstand grün. Reale sichtbare Discord-Abnahme erfolgt gesammelt in 14.5.

---

# Paket 14.2 – Persönlicher Status, Teilnahme und Reminder UX

**Status:** abgeschlossen und in den Sammelbranch integriert  
**Issue:** #107  
**PR:** #113  
**Finaler Paket-Head:** `240752a73bf533ff54bd375af5bb0d9e8451338d`  
**Squash-Merge:** `ee4a5e8bc639c7bfaf215c8ddc278d4d16d9e854`  
**Arbeitsbranch:** `feature/14-2-personal-command-ux`

## Ziel und Lieferumfang

- Join/Activate: `ab heute aktiv` beziehungsweise idempotent `bereits aktiv`,
- Leave/Deactivate: heutigen Zustand und konkrete Wirksamkeit ab morgen eindeutig darstellen,
- wiederholte/pending Änderungen werden ehrlich beschrieben,
- Self-Service- und Adminpfade verwenden dieselbe Effektsemantik,
- Reminder on/off/status erklärt Mention versus Klartextname,
- `/reminders status` zeigt tatsächlich konfigurierte Zeiten,
- `/status` in Reihenfolge:
  1. Heute je Spiel,
  2. alle fünf persönlichen Serien,
  3. Teilnahmezeiträume + kompakter Reminderstatus,
  4. letzte Einreichungen,
- `/status` ist strikt read-only und verwendet die vorhandene `DailyStatusProjector`-/Seriensemantik,
- unbekannte `/status`-Aufrufer werden nicht implizit registriert.

## Automatisierte Nachweise

Insbesondere:

- `PlayerParticipationServiceTest`,
- `DiscordParticipationCommandListenerTest`,
- `PersonalStatusServiceTest`,
- `PersonalStatusEmbedRendererTest`,
- `PostgresPersonalStatusReadOnlyIT`.

Alle drei Maven-Gates waren auf dem finalen Paketstand grün. Reale sichtbare Discord-Abnahme erfolgt gesammelt in 14.5.

---

# Paket 14.3 – Achievement- und Record-Command UX

**Status:** abgeschlossen und in den Sammelbranch integriert  
**Issue:** #108  
**PR:** #114  
**Finaler Paket-Head:** `d0e3fc5bcfb118eaff8e52ad90dba3c48fb16410`  
**Squash-Merge:** `949115aa16fa4e4615bb17dbc7fb2cf95557c8ad`  
**Arbeitsbranch:** `feature/14-3-read-command-ux`

## Ziel und Lieferumfang

- `/achievements`: `earnedOn` als fachliches Freischaltdatum,
- `/achievement-list`: kombinierbare Filter `game`, `category`, `status`,
- Global-Achievements nur bei `game=Alle`, kein eigener `Allgemein`-Choice,
- binäre ✅/❌-Semantik unverändert, kein Progress,
- neutraler Leerzustand bei Filterkombination ohne Treffer,
- `/records`: optionaler Scope `Alle|Persönlich|Serverweit|Gemeinsam`, kombinierbar mit bestehenden Filtern,
- bestehende Fremdansichts-Autorisierung greift vor Record-State-/Bootstrap-Zugriff und bleibt unverändert,
- alle Commands read-only, ephemeral und mention-sicher,
- keine neuen Persistenzqueries oder Migrationen.

## Automatisierte Nachweise

Insbesondere:

- `AchievementsQueryServiceTest`,
- `AchievementsOverviewEmbedRendererTest`,
- `AchievementCatalogQueryServiceTest`,
- `AchievementCatalogEmbedRendererTest`,
- `DiscordAchievementCatalogCommandListenerTest`,
- `DiscordAchievementsCommandListenerTest`,
- `RecordsScopeFilterTest`,
- `RecordsQueryReadOnlyInvariantTest`,
- `DiscordRecordsCommandListenerTest`,
- `PostgresAchievementsQueryIT`.

Alle drei Maven-Gates waren auf dem finalen Paketstand grün. Reale sichtbare Discord-Abnahme erfolgt gesammelt in 14.5.

---

# Paket 14.4 – Highlights in Wochen- und Monatsberichten

**Status:** abgeschlossen und in den Sammelbranch integriert  
**Issue:** #109  
**PR:** #115  
**Finaler Review-Head:** `88f2eb7a4babd290b4ec51c5359afa73b21947e5`  
**Squash-Merge:** `d4bf877a4c5bdcb00467bb1fbe3c19a3c97ef819`  
**Arbeitsbranch:** `feature/14-4-report-highlights`

## Ziel und Lieferumfang

### Achievements

- je Reportteilnehmer nur Anzahl ACTIVE Awards mit `earnedOn` in der Periode,
- Spieler mit 0 weglassen,
- keine einzelnen Achievement-Namen.

### Rekorde

- Quelle sind VALID Record-Events mit fachlichem GameResult-Spieltag beziehungsweise Streak-Enddatum in der Periode,
- geeignete Eventtypen: `RESULT_RECORD_BROKEN`, `SERIES_RECORD_CROSSED`, `RECORD_SERIES_FINISHED`,
- öffentliche Eignung wird zentral über `processingOrigin.publicAnnouncementEligible()` bestimmt,
- Gleichstand, Near Miss, Initialisierung, invalidierte/supersedete Events und stille Origins werden ausgelassen,
- Crossing+Finish derselben StateKey/StreakRun-Kombination innerhalb derselben Periode werden auf Finish reduziert,
- mehrere echte Ergebnisrekordverbesserungen werden nicht pauschal dedupliziert,
- jeder verbleibende Rekord wird vollständig genannt,
- keine künstliche Maximalzahl; vorhandene deterministische Pagination wird genutzt.

### Snapshot und Wiring

- bestehende Report-Snapshot-/Catch-up-Semantik unverändert,
- Guild-ID wird explizit aus der Reconciliation an die Read-Projektion weitergereicht,
- im `database`-Profil gibt es keinen stillen Empty-Fallback für ein fehlendes Highlight-Readmodell,
- keine neue persistierte Report-Fachwahrheit,
- keine Announcement-Texte als Datenquelle,
- keine Migration.

## Automatisierte Nachweise

Insbesondere:

- `PeriodicReportUseCaseTest`,
- `PeriodicReportRendererTest`,
- `PeriodicReportDeliveryConfigurationTest`,
- bestehende `PeriodicReportDeliveryService*`-Regressionstests,
- `PostgresAchievementsQueryIT` mit ACTIVE-/Perioden-, BACKFILL-, Gleichstand- und Invalidierungsfällen.

Alle drei Maven-Gates waren auf dem finalen Reviewstand grün. Reale Report-/Discord-Abnahme erfolgt gesammelt in 14.5.

---

# Paket 14.5 – Gesamthärtung, Abnahme und Releasevorbereitung

**Status:** technische Härtung und automatisierte Operations-Abnahme bestanden; realer Discord-/Report-Smoke ausstehend  
**Issue:** #110  
**PR:** #117  
**Arbeitsbranch:** `feature/14-5-ux-qol-hardening`  
**Ausgangsstand:** `d4bf877a4c5bdcb00467bb1fbe3c19a3c97ef819`

## Ziel

Kumulierten Stand ohne neuen Fachumfang technisch abnehmen, automatisierte Nachweislücken schließen, Dokumentation synchronisieren und den realen Discord-/Report-Smoke so vorbereiten, dass anschließend nur noch die externe Sichtprüfung dokumentiert werden muss.

## Ergebnis der technischen Härtung

- keine fachliche oder technische Code-Regression gefunden, die eine weitere Produktivcodeänderung erfordert,
- Abschlussdiff besteht ausschließlich aus Dokumentations-/Nachweisänderungen,
- Akzeptanzmatrix mit konkreten Paket-, Test- und Reviewnachweisen synchronisiert,
- erster vollständiger Abschlusslauf: CI #1664 / Run-ID `31323660350` grün,
- vollständiger `Container image`-Workflow #506 / Run-ID `31323660354` grün einschließlich Produktionsimage, Nicht-Root-/Runtime-Check, Compose, Backup, Restore, Resume und Application-Rollback,
- finaler Evidenz-Head wird vor technischem Merge nochmals vollständig revalidiert; finale Run-IDs stehen in PR #117.

## Pflichtgates

```text
mvn --batch-mode --no-transfer-progress clean verify
mvn --batch-mode --no-transfer-progress -Pdatabase-integration clean verify
mvn --batch-mode --no-transfer-progress -Pmigration-clean-install verify
```

Zusätzlich der vollständige vorhandene `Container image`-Workflow einschließlich Produktionsimage, Nicht-Root-/Runtime-Check, Compose, Backup, Restore, Resume und Application-Rollback.

## Abschlussgrenze

Der technische 14.5-PR darf nach vollständig grünen automatisierten Gates in `feature/14-ux-qol` gemergt werden. Danach gilt:

- Issue #110 bleibt offen,
- Gesamt-PR #111 bleibt Draft,
- `docs/operations/14-ux-qol-acceptance.md` weist den Discord-Smoke weiter als offen aus,
- erst der dokumentierte erfolgreiche reale Smoke vervollständigt die Definition of Done von #110.

Release/Produktivrollout bleiben ein separater Schritt.

---

## 6. Nicht Bestandteil des Gesamtinkrements

- quantitative Achievement-Fortschritte,
- neue Achievements oder Rekordmetriken,
- Rankings/Leaderboards/Spieler der Woche,
- Reminderstatus pro Spiel,
- Trendvergleiche in Reports,
- manuelle Report-Commands,
- generische Frameworks,
- produktiver Release innerhalb der Paket-PRs.
